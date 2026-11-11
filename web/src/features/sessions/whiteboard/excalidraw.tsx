import { MainMenu, restoreElements } from '@excalidraw/excalidraw';
import type { ExcalidrawProps } from '@excalidraw/excalidraw/types';
import '@excalidraw/excalidraw/index.css';
import './excalidraw.css';
import type { SceneElement } from './scene';

/**
 * What the room and the review share of Excalidraw (ADR 0014). This module, and the two that
 * import it, are only loaded on the screens that draw a board: Excalidraw weighs more than the
 * rest of the application.
 */

declare global {
  interface Window {
    EXCALIDRAW_ASSET_PATH?: string | string[];
  }
}

// The fonts of the canvas come from this origin: `vite.config.ts` serves them in development and
// copies them into the build. Without it, Excalidraw would fetch them from a CDN.
window.EXCALIDRAW_ASSET_PATH = `${window.location.origin}${import.meta.env.BASE_URL}excalidraw/`;

/** Excalidraw's own canvas actions, without the ones that open, save or share files. */
export const UI_OPTIONS: ExcalidrawProps['UIOptions'] = {
  canvasActions: {
    changeViewBackgroundColor: true,
    clearCanvas: true,
    export: false,
    loadScene: false,
    saveAsImage: true,
    saveToActiveFile: false,
    toggleTheme: null,
  },
  tools: { image: false },
};

/** No embedded page is ever loaded: it would be another site inside the board. */
export const rejectEmbeds = () => false;

const DROPPED_TYPES = new Set(['image', 'embeddable', 'iframe', 'magicframe']);

/**
 * Pasting text and Excalidraw's own elements works; files, images and pages pasted as content do
 * not reach the board. Answering false tells Excalidraw not to handle the paste.
 */
export const onPaste: NonNullable<ExcalidrawProps['onPaste']> = (data, event) => {
  if (event?.clipboardData?.files && event.clipboardData.files.length > 0) return false;
  if (data.files && Object.keys(data.files).length > 0) return false;
  if (data.mixedContent?.some((part) => part.type === 'imageUrl')) return false;
  if (data.elements?.some((element) => DROPPED_TYPES.has(element.type))) return false;
  return true;
};

/** The menu of the board: what Excalidraw has, without files, links, collaboration or socials. */
export function BoardMenu({ live }: { live: boolean }) {
  return (
    <MainMenu>
      {live && <MainMenu.DefaultItems.ClearCanvas />}
      <MainMenu.DefaultItems.SaveAsImage />
      <MainMenu.DefaultItems.SearchMenu />
      <MainMenu.DefaultItems.Help />
      {live && <MainMenu.Separator />}
      {live && <MainMenu.DefaultItems.ChangeCanvasBackground />}
    </MainMenu>
  );
}

/** What a board of the server becomes before Excalidraw draws it: every field in place. */
export function restore(elements: SceneElement[]): SceneElement[] {
  return restoreElements(elements, null, { repairBindings: true }) as SceneElement[];
}

/** The style a new drawing starts with: clean lines and the "normal" font. Anyone can change it. */
export const INITIAL_APP_STATE = {
  currentItemRoughness: 0,
  currentItemFontFamily: 6,
  viewBackgroundColor: '#ffffff',
} as const;
