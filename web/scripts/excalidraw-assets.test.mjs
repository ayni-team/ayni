import { describe, expect, it } from 'vitest';
import { fontFile } from './excalidraw-assets.mjs';

describe("serving Excalidraw's fonts from this origin", () => {
  it('answers a font of the package under /excalidraw/fonts/', () => {
    expect(fontFile('/excalidraw/fonts/Excalifont/Excalifont-Regular-349fac6ca4700ffec595a7150a0d1e1d.woff2')).toMatch(
      /node_modules\/@excalidraw\/excalidraw\/dist\/prod\/fonts\/Excalifont\/.+\.woff2$/,
    );
  });

  it('answers nothing outside that folder, nothing that is not a font, and nothing missing', () => {
    expect(fontFile('/excalidraw/fonts/../index.js')).toBeNull();
    expect(fontFile('/excalidraw/fonts/%2e%2e/%2e%2e/package.json')).toBeNull();
    expect(fontFile('/excalidraw/fonts/Excalifont')).toBeNull();
    expect(fontFile('/excalidraw/fonts/Nope/missing.woff2')).toBeNull();
    expect(fontFile('/src/main.tsx')).toBeNull();
    expect(fontFile(undefined)).toBeNull();
  });
});
