/**
 * Serves Excalidraw's fonts from this origin (ADR 0010). By default Excalidraw fetches the fonts of
 * the canvas from a CDN; the boards set `window.EXCALIDRAW_ASSET_PATH` to `/excalidraw/` instead,
 * and this plugin answers that path from the package itself: in development straight from
 * `node_modules`, and on a build by copying the folder into `dist`. No dependency and nothing to
 * install: it only reads files the package already has.
 */
import { cpSync, createReadStream, existsSync, statSync } from 'node:fs';
import { dirname, extname, isAbsolute, join, normalize, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const PREFIX = '/excalidraw/fonts/';
const TYPES = { '.woff2': 'font/woff2', '.woff': 'font/woff', '.ttf': 'font/ttf' };

const here = dirname(fileURLToPath(import.meta.url));
const fonts = join(here, '..', 'node_modules', '@excalidraw', 'excalidraw', 'dist', 'prod', 'fonts');

/** The file a request asks for, only inside the folder of the fonts and only a font. */
export function fontFile(url, root = fonts) {
  const path = decodeURIComponent((url ?? '').split('?')[0]);
  if (!path.startsWith(PREFIX)) return null;
  const file = normalize(join(root, path.slice(PREFIX.length)));
  if (!file.startsWith(root + sep) || !TYPES[extname(file)]) return null;
  return existsSync(file) && statSync(file).isFile() ? file : null;
}

export function excalidrawAssets() {
  let outDir = 'dist';
  let base = '/';
  let building = false;
  return {
    name: 'ayni-excalidraw-assets',
    configResolved(config) {
      outDir = isAbsolute(config.build.outDir) ? config.build.outDir : join(config.root, config.build.outDir);
      base = config.base;
      // Vitest and the dev server close their bundle too; only a build has a folder to fill.
      building = config.command === 'build' && !config.mode.startsWith('test');
    },
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        const url = (request.url ?? '').replace(new RegExp(`^${base.replace(/\/$/, '')}`), '');
        const file = fontFile(url);
        if (!file) return next();
        response.setHeader('Content-Type', TYPES[extname(file)]);
        response.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
        createReadStream(file).pipe(response);
      });
    },
    closeBundle() {
      if (!building) return;
      if (!existsSync(fonts)) throw new Error(`Excalidraw's fonts are not at ${fonts}`);
      cpSync(fonts, join(outDir, 'excalidraw', 'fonts'), { recursive: true });
    },
  };
}
