#!/usr/bin/env node
/**
 * Looks for code injected into the repository, before anything of the repository runs.
 *
 * It exists because of a real incident (docs/security.md): an obfuscated loader, hidden after a run
 * of whitespace at the end of `tailwind.config.js`, ran on every build; another ran on opening the
 * folder in an editor, through a task, from a file named like a font. So this script reads files
 * and imports nothing of the project: the CI runs it before `npm ci`, and `npm run dev` and `npm run
 * build` run it before Tailwind or Vite load any configuration.
 *
 * It is a net for what has already been seen, not a proof of absence. A rule that fires by mistake
 * is fixed here, in a reviewed change, and never skipped.
 */
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { basename, dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const SKIPPED_FOLDERS = new Set(['.git', 'node_modules', 'dist', 'target', '.vite', '.idea', 'coverage', 'build']);
const MAX_BYTES = 3 * 1024 * 1024;

/** This script and its test name what they look for. */
const SELF = /(^|\/)web\/scripts\/check-injected-code(\.test)?\.mjs$|(^|\/)check-injected-code(\.test)?\.mjs$/;

/** Marks of the code found in the incident. */
const KNOWN_MARKS = [
  'A8-8806',
  "global['\\u0072']",
  "global['\\u0069']",
  '.split("").reverse().join("")',
  '0x1eEf0849',
  '/0x/cls',
  'eth_getTransactionByHash',
  'public.blastapi.io',
  'ethereum-rpc.publicnode.com',
  'eth.drpc.org',
];

/** Fonts start with one of these. A "font" that does not is something else with a font's name. */
const FONT_EXTENSIONS = /\.(ttf|otf|woff2?)$/i;
const FONT_SIGNATURES = ['00010000', '4f54544f', '74727565', '74797031', '774f4646', '774f4632'];

const CONFIG_FILE =
  /(^|\/)((tailwind|vite|vitest|postcss|eslint|prettier|babel|webpack|rollup|next|jest)\.config\.[a-z]+|\.eslintrc[^/]*|package\.json|Dockerfile[^/]*|docker-compose[^/]*\.ya?ml)$|(^|\/)\.github\/workflows\/[^/]+$/;
const CODE_CONFIG_FILE = /(^|\/)[a-z]+\.config\.(js|cjs|mjs|ts)$/;
const LONGEST_CONFIG_LINE = 500;
const DANGEROUS_IN_CONFIG = /child_process|createRequire|\beval\s*\(|new Function\s*\(|\\u00[0-9a-f]{2}/i;
const HIDDEN_AFTER_WHITESPACE = /[ \t]{200,}\S/;
const INSTALL_SCRIPTS = ['preinstall', 'install', 'postinstall'];

function* filesUnder(root, folder = root) {
  for (const name of readdirSync(folder)) {
    const path = join(folder, name);
    const stats = statSync(path, { throwIfNoEntry: false });
    if (stats?.isDirectory()) {
      if (!SKIPPED_FOLDERS.has(name)) yield* filesUnder(root, path);
    } else if (stats?.isFile() && stats.size <= MAX_BYTES) {
      yield { path, name: relative(root, path).split('\\').join('/') };
    }
  }
}

/** Every problem found under a folder: `{ file, rule, detail }`. */
export function findProblems(root) {
  const problems = [];
  for (const { path, name } of filesUnder(root)) {
    if (SELF.test(name)) continue;
    const report = (rule, detail) => problems.push({ file: name, rule, detail });
    const bytes = readFileSync(path);
    const text = bytes.toString('latin1');

    for (const mark of KNOWN_MARKS) {
      if (text.includes(mark)) report('known-mark', `contains a mark of the incident: ${mark}`);
    }
    if (name.endsWith('.lfk')) report('loader-name', 'a file with the extension of the loader');
    if (FONT_EXTENSIONS.test(name) && !FONT_SIGNATURES.includes(bytes.subarray(0, 4).toString('hex'))) {
      report('fake-font', 'named like a font, and it is not one');
    }
    if (basename(name) === 'tasks.json' && text.includes('folderOpen')) {
      report('autorun-task', 'an editor task that runs by itself on opening the folder');
    }
    if (basename(name) === 'settings.json' && text.includes('task.allowAutomaticTasks')) {
      report('autorun-setting', 'a setting that lets editor tasks run by themselves');
    }
    if (bytes.includes(0)) continue;

    if (HIDDEN_AFTER_WHITESPACE.test(text)) {
      report('hidden-code', 'text after a long run of whitespace, out of sight on the screen');
    }
    if (CONFIG_FILE.test(name) && text.split('\n').some((line) => line.length > LONGEST_CONFIG_LINE)) {
      report('long-line', `a configuration file with a line over ${LONGEST_CONFIG_LINE} characters`);
    }
    if (CODE_CONFIG_FILE.test(name) && DANGEROUS_IN_CONFIG.test(text)) {
      report('dangerous-config', 'a configuration file that runs processes or hides what it runs');
    }
    if (basename(name) === 'package.json') {
      const scripts = JSON.parse(bytes.toString('utf8')).scripts ?? {};
      for (const script of INSTALL_SCRIPTS) {
        if (scripts[script]) report('install-script', `"${script}" runs code on npm install`);
      }
    }
  }
  return problems;
}

/** The repository, or only `web/` where it runs alone: docker compose mounts just that folder. */
function defaultRoot() {
  const web = dirname(dirname(fileURLToPath(import.meta.url)));
  return existsSync(join(web, '..', '.github')) ? join(web, '..') : web;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const problems = findProblems(process.argv[2] ?? defaultRoot());
  if (problems.length > 0) {
    console.error('\nPossible injected code. Nothing else was run. Read docs/security.md before going on.\n');
    for (const problem of problems) console.error(`  ${problem.file}: [${problem.rule}] ${problem.detail}`);
    console.error('');
    process.exit(1);
  }
  console.log('No injected code found.');
}
