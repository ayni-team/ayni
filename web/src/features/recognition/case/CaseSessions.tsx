import { Badge, IconAlert, cx } from '@/shared/ui';
import { alertText, alertTitle, sessionFigures, shortDay, timeRange, type CaseSession } from './recognitionCase';

export interface CaseSessionsProps {
  sessions: CaseSession[];
  selected: string | undefined;
  onSelect: (sessionId: string) => void;
  zone: string;
}

/**
 * The sessions that back the request, as they were presented. Choosing one opens its evidence; the
 * alerts of the audit are drawn inside the session they are about.
 */
export function CaseSessions({ sessions, selected, onSelect, zone }: CaseSessionsProps) {
  return (
    <section aria-labelledby="case-sessions">
      <h2 id="case-sessions" className="mb-3 font-display text-lg font-semibold text-ink">
        Sesiones que la respaldan
      </h2>
      {sessions.length === 0 ? (
        <p className="rounded-[20px] border border-line bg-white p-4 text-sm text-ink-2">
          Esta solicitud no tiene sesiones para mostrar.
        </p>
      ) : (
        <ul className="flex flex-col gap-3">
          {sessions.map((session, index) => {
            const alerts = session.alerts ?? [];
            const isSelected = session.sessionId === selected;
            const day = shortDay(session.startedAt, zone);
            const range = timeRange(session.startedAt, session.endedAt, zone);
            return (
              <li key={session.sessionId ?? index}>
                <button
                  type="button"
                  aria-pressed={isSelected}
                  aria-label={`Ver la evidencia de la sesión del ${day}, ${range}`}
                  onClick={() => session.sessionId && onSelect(session.sessionId)}
                  className={cx(
                    'w-full rounded-[16px] border bg-white p-4 text-left hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand',
                    isSelected ? 'border-brand' : alerts.length > 0 ? 'border-amber-300' : 'border-line',
                  )}
                >
                  <span className="flex flex-wrap items-start justify-between gap-2">
                    <span className="min-w-0">
                      <span className="block font-semibold text-ink">{[day, range].filter(Boolean).join(' · ')}</span>
                      <span className="mt-0.5 block text-xs text-ink-2">{sessionFigures(session)}</span>
                    </span>
                    {session.presenceVerified ? (
                      <Badge tone="info">Presencia verificada</Badge>
                    ) : (
                      <Badge tone="warning">Presencia sin verificar</Badge>
                    )}
                  </span>
                  {alerts.map((alert, alertIndex) => (
                    <span
                      key={`${alert.kind}-${alertIndex}`}
                      className="mt-3 flex gap-2 rounded-xl bg-amber-50 px-3 py-2.5 text-amber-900"
                    >
                      <IconAlert className="mt-0.5 h-4 w-4 shrink-0" />
                      <span className="min-w-0">
                        <span className="block text-sm font-semibold">{alertTitle(alert)}</span>
                        <span className="block text-xs">{alertText(alert)}</span>
                      </span>
                    </span>
                  ))}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
