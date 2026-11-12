import { vi } from 'vitest';

export interface FakeJitsiInstance {
  domain: string;
  options: Record<string, unknown>;
  /** Sends an event of the call, as Jitsi would. */
  fire: (event: string, payload?: unknown) => void;
  dispose: ReturnType<typeof vi.fn>;
  executeCommand: ReturnType<typeof vi.fn>;
}

/** A double of what `external_api.js` defines: `window.JitsiMeetExternalAPI`. For tests only. */
export function fakeJitsi() {
  const instances: FakeJitsiInstance[] = [];
  class Jitsi {
    listeners = new Map<string, (payload?: unknown) => void>();
    dispose = vi.fn();
    executeCommand = vi.fn();
    constructor(
      public domain: string,
      public options: Record<string, unknown>,
    ) {
      instances.push({
        domain,
        options,
        dispose: this.dispose,
        executeCommand: this.executeCommand,
        fire: (event, payload) => this.listeners.get(event)?.(payload),
      });
    }
    addListener(event: string, listener: (payload?: unknown) => void) {
      this.listeners.set(event, listener);
    }
  }
  return { Jitsi, instances };
}

/** The script of the call arrives (or fails, without the API). */
export function jitsiScriptLoads(withApi = true) {
  const script = document.head.querySelector('script');
  if (!script) throw new Error('no script');
  if (!withApi) delete window.JitsiMeetExternalAPI;
  script.dispatchEvent(new Event(withApi ? 'load' : 'error'));
}
