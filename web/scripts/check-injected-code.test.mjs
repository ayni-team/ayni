import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { findProblems } from './check-injected-code.mjs';

let root;

function write(file, content) {
  const path = join(root, file);
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, content);
}

function rules() {
  return findProblems(root).map((problem) => problem.rule);
}

describe('the check for injected code', () => {
  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), 'injected-'));
  });

  afterEach(() => {
    rmSync(root, { recursive: true, force: true });
  });

  it('says nothing about a clean project', () => {
    write('web/tailwind.config.js', 'export default { content: ["./src/**/*.tsx"] };\n');
    write('web/package.json', '{"scripts":{"dev":"vite"}}');
    write('web/public/fonts/real.woff2', Buffer.from('774f463200000000', 'hex'));
    write('.vscode/tasks.json', '{"tasks":[{"label":"lint","command":"npm run lint"}]}');

    expect(rules()).toEqual([]);
  });

  it('finds code hidden after a run of whitespace, as in the configuration of Tailwind', () => {
    write('web/tailwind.config.js', `export default {};${'\t'.repeat(300)}run();\n`);

    expect(rules()).toContain('hidden-code');
  });

  it('finds a mark of the incident in any file, a binary one too', () => {
    write('a/anything.bin', Buffer.concat([Buffer.from([0, 1, 2]), Buffer.from(['A8', '8806'].join('-'))]));

    expect(rules()).toContain('known-mark');
  });

  it('finds a font that is not a font, and the extension of the loader', () => {
    write('public/fonts/fa-solid-300.ttf', 'global.run = true;');
    write('public/fonts/fa-solid.lfk', 'global.run = true;');

    expect(rules()).toEqual(expect.arrayContaining(['fake-font', 'loader-name']));
  });

  it('finds an editor task that runs on opening the folder, and the setting that allows it', () => {
    write('.vscode/tasks.json', '{"tasks":[{"runOptions":{"runOn":"folderOpen"}}]}');
    write('.vscode/settings.json', '{"task.allowAutomaticTasks":true}');

    expect(rules()).toEqual(expect.arrayContaining(['autorun-task', 'autorun-setting']));
  });

  it('finds a very long line in a configuration file, and dangerous calls in one', () => {
    write('web/vite.config.ts', `const a = "${'x'.repeat(600)}";\n`);
    write('web/postcss.config.js', `import cp from 'node:child_process';\n`);

    expect(rules()).toEqual(expect.arrayContaining(['long-line', 'dangerous-config']));
  });

  it('finds a script that runs on npm install', () => {
    write('web/package.json', '{"scripts":{"postinstall":"node x.js"}}');

    expect(rules()).toContain('install-script');
  });

  it('does not read what is not the project', () => {
    write('web/node_modules/lib/font.ttf', 'not a font');
    write('web/dist/index.js', `x${' '.repeat(300)}y`);

    expect(rules()).toEqual([]);
  });
});
