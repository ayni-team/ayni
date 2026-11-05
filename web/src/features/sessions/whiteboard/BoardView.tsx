import { BoardDrawing, boardSummary } from './BoardDrawings';
import { drawingsOf, fittedViewBox, type BoardElement } from './board';

/** A board that is only read: no connection, no tools. It shows the part that was used. */
export function BoardView({ elements }: { elements: readonly BoardElement[] | undefined }) {
  const drawings = drawingsOf(elements);
  return (
    <svg
      role="img"
      aria-label={boardSummary(drawings)}
      viewBox={fittedViewBox(drawings)}
      className="h-auto w-full rounded-xl border border-line bg-white"
    >
      {drawings.map((drawing) => (
        <BoardDrawing key={drawing.id} drawing={drawing} />
      ))}
    </svg>
  );
}
