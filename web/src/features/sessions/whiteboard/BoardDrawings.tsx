import { NOTE_WIDTH, lineHeightOf, noteHeight, pathOf, type Drawing } from './board';

const NOTE_FILL = '#FFF6D6';
const NOTE_INK = '#1D2033';
const NOTE_AUTHOR = '#7A5E00';

function Lines({
  x,
  y,
  lines,
  height,
  color,
}: {
  x: number;
  y: number;
  lines: string[];
  height: number;
  color: string;
}) {
  return (
    <text x={x} y={y} fill={color} className="select-none" style={{ fontSize: 22 }}>
      {lines.map((line, index) => (
        <tspan key={index} x={x} dy={index === 0 ? 0 : height}>
          {line || ' '}
        </tspan>
      ))}
    </text>
  );
}

/** One element of a board, inside an `svg` in the coordinates of the board. */
export function BoardDrawing({ drawing }: { drawing: Drawing }) {
  if (drawing.kind === 'stroke') {
    return (
      <path
        d={pathOf(drawing.points)}
        fill="none"
        stroke={drawing.color}
        strokeWidth={drawing.width}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    );
  }
  if (drawing.kind === 'shape') {
    const common = { fill: 'none', stroke: drawing.color, strokeWidth: 3 };
    return drawing.shape === 'ellipse' ? (
      <ellipse
        cx={drawing.x + drawing.width / 2}
        cy={drawing.y + drawing.height / 2}
        rx={drawing.width / 2}
        ry={drawing.height / 2}
        {...common}
      />
    ) : (
      <rect x={drawing.x} y={drawing.y} width={drawing.width} height={drawing.height} rx={4} {...common} />
    );
  }
  if (drawing.kind === 'text') {
    return (
      <Lines x={drawing.x} y={drawing.y} lines={drawing.lines} height={lineHeightOf('text')} color={drawing.color} />
    );
  }
  const height = noteHeight(drawing.lines.length, Boolean(drawing.author));
  const firstLine = drawing.y + 36;
  return (
    <g>
      <rect x={drawing.x} y={drawing.y} width={NOTE_WIDTH} height={height} rx={6} fill={NOTE_FILL} />
      <Lines x={drawing.x + 18} y={firstLine} lines={drawing.lines} height={lineHeightOf('note')} color={NOTE_INK} />
      {drawing.author && (
        <text x={drawing.x + 18} y={drawing.y + height - 16} fill={NOTE_AUTHOR} style={{ fontSize: 16 }}>
          — {drawing.author}
        </text>
      )}
    </g>
  );
}

/** Words a screen reader can read instead of the drawing. */
export function boardSummary(drawings: readonly Drawing[]): string {
  const words = drawings.flatMap((drawing) =>
    drawing.kind === 'text' || drawing.kind === 'note' ? drawing.lines : [],
  );
  const strokes = drawings.filter((drawing) => drawing.kind === 'stroke' || drawing.kind === 'shape').length;
  const drawn = strokes === 1 ? '1 trazo' : `${strokes} trazos`;
  return [`Pizarra con ${drawn}`, ...words].join('. ');
}
