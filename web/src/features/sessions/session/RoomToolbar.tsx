import { useState, type ReactNode } from 'react';
import { Button, IconPencil, Tabs } from '@/shared/ui';
import type { ShareControl } from '../video/VideoCall';
import { IconColumns, IconScreenShare, IconVideo } from './icons';
import { SPLIT_RATIOS, canShareScreen, type RoomView, type SplitRatio } from './roomLayout';

const VIEWS = [
  { value: 'board', label: 'Pizarra', Icon: IconPencil },
  { value: 'split', label: 'Dividida', Icon: IconColumns },
  { value: 'video', label: 'Video', Icon: IconVideo },
] as const;

const VIEW_TABS = VIEWS.map(({ value, label, Icon }) => ({
  value,
  label: (
    <>
      <Icon className="hidden h-4 w-4 sm:block" />
      {label}
    </>
  ),
}));

const RATIO_WORDS: Record<SplitRatio, string> = {
  video: 'Más espacio al video',
  even: 'Mitad y mitad',
  board: 'Más espacio a la pizarra',
};

/** The divider of the split view: further right, more whiteboard, as if dragging the line between them. */
function RatioControl({ ratio, onChange }: { ratio: SplitRatio; onChange: (ratio: SplitRatio) => void }) {
  return (
    <label className="hidden items-center gap-2 text-[13px] text-ink-2 lg:flex">
      <span aria-hidden="true">Video</span>
      <input
        type="range"
        min={0}
        max={SPLIT_RATIOS.length - 1}
        step={1}
        value={SPLIT_RATIOS.indexOf(ratio)}
        onChange={(event) => onChange(SPLIT_RATIOS[Number(event.target.value)] ?? 'even')}
        aria-label="Espacio entre la pizarra y el video"
        aria-valuetext={RATIO_WORDS[ratio]}
        className="w-24 accent-brand focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
      />
      <span aria-hidden="true">Pizarra</span>
    </label>
  );
}

export interface RoomToolbarProps {
  view: RoomView;
  onView: (view: RoomView) => void;
  ratio: SplitRatio;
  onRatio: (ratio: SplitRatio) => void;
  /** How the call shares a screen, or null without a call. */
  share: ShareControl | null;
  /** This person is sharing their screen (only known through the API of the call). */
  sharingMine: boolean;
  /** What the split view folds out of the side column on a desktop: presence and the need. */
  folded?: ReactNode;
}

/**
 * Over the work: which view, how the split view shares the width, and sharing a screen. The views
 * are tabs of the same room, so the arrows move between them as in every other tab of the web.
 */
export function RoomToolbar({ view, onView, ratio, onRatio, share, sharingMine, folded }: RoomToolbarProps) {
  const [ownButtonHint, setOwnButtonHint] = useState(false);
  const canShare = share !== null && canShareScreen();

  function shareScreen() {
    if (share?.kind === 'command') {
      share.toggle();
      return;
    }
    // Without the API only the call's button shares, and the call shows it only in a wide box.
    if (view === 'board') onView('split');
    setOwnButtonHint(true);
  }

  return (
    <div className="shrink-0 border-b border-line bg-white px-4 lg:px-6">
      <div className="flex items-center gap-3 lg:gap-5">
        <Tabs label="Vista de la sala" items={VIEW_TABS} value={view} onChange={onView} className="-mb-px self-end" />
        {view === 'split' && <RatioControl ratio={ratio} onChange={onRatio} />}
        <div className="ml-auto flex items-center gap-2 py-2">
          {view === 'split' && folded && <div className="hidden items-center gap-2 lg:flex">{folded}</div>}
          {canShare && (
            <Button
              variant={sharingMine ? 'primary' : 'secondary'}
              size="sm"
              onClick={shareScreen}
              className="whitespace-nowrap"
              title={sharingMine ? 'Dejar de compartir pantalla' : 'Compartir pantalla'}
              aria-label={sharingMine ? 'Dejar de compartir pantalla' : 'Compartir pantalla'}
              icon={<IconScreenShare className="h-4 w-4" />}
            >
              {/* In the split view a desktop's toolbar also holds presence and the need: the icon is enough until 1280 px. */}
              <span className={view === 'split' ? 'hidden xl:inline' : 'hidden sm:inline'}>
                {sharingMine ? 'Dejar de compartir' : 'Compartir pantalla'}
              </span>
            </Button>
          )}
        </div>
      </div>
      {ownButtonHint && share?.kind === 'own-button' && (
        <div
          role="status"
          className="mb-2 flex items-start justify-between gap-3 rounded-xl bg-info-soft p-3 text-[13px] text-ink"
        >
          <p>
            En esta llamada se comparte con su propio botón: busca <strong>Compartir pantalla</strong> en la barra de la
            videollamada (si no lo ves, en <strong>Más acciones</strong>).
          </p>
          <Button variant="ghost" size="sm" onClick={() => setOwnButtonHint(false)}>
            Entendido
          </Button>
        </div>
      )}
    </div>
  );
}
