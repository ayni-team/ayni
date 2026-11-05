import { useState, type ReactNode } from 'react';
import { useApiQuery } from '@/shared/api';
import { useZone } from '@/shared/time';
import { Avatar, Badge, Button, ButtonLink, Card, IconAlert, IconCheck, IconClock, PageHeader, cx } from '@/shared/ui';
import { IconShieldCheck, IconVideo } from './icons';
import { RoomMedia } from './RoomMedia';
import {
  arrivalText,
  courseName,
  myArrival,
  otherArrival,
  otherName,
  roomLine,
  roomNotice,
  statusBadge,
  whenLine,
  type JoinedSession,
  type Phase,
  type RoomNotice,
  type Session,
} from './session';

function Notice({ notice }: { notice: RoomNotice }) {
  const warning = notice.tone === 'warning';
  return (
    <div className={cx('flex items-start gap-3 rounded-xl p-4', warning ? 'bg-credit-soft' : 'bg-info-soft')}>
      {warning ? (
        <IconAlert className="mt-0.5 h-5 w-5 shrink-0 text-credit-text" />
      ) : (
        <IconClock className="mt-0.5 h-5 w-5 shrink-0 text-info-text" />
      )}
      <div>
        <p className="text-sm font-semibold text-ink">{notice.title}</p>
        <p className="mt-0.5 text-[13px] text-ink-2">{notice.detail}</p>
      </div>
    </div>
  );
}

function Participant({
  name,
  own,
  arrival,
}: {
  name: string;
  own?: boolean;
  arrival: { text: string; arrived: boolean };
}) {
  return (
    <li className="flex items-center gap-3">
      <Avatar name={name} own={own} decorative />
      <span className="min-w-0 flex-1 truncate text-sm font-semibold text-ink">{own ? 'Tú' : name}</span>
      <Badge tone={arrival.arrived ? 'info' : 'neutral'}>{arrival.text}</Badge>
    </li>
  );
}

function HowItWorks() {
  const steps: { icon: ReactNode; title: string; detail: string }[] = [
    {
      icon: <IconVideo className="h-5 w-5 text-brand-text" />,
      title: 'Entra a la sala',
      detail: 'Se abre 15 minutos antes. La sesión empieza cuando entran los dos.',
    },
    {
      icon: <IconShieldCheck className="h-5 w-5 text-brand-text" />,
      title: 'Confirma tu presencia',
      detail: 'A los 5 minutos te llega un código de 6 dígitos al correo.',
    },
    {
      icon: <IconCheck className="h-5 w-5 text-brand-text" />,
      title: 'Termina y califica',
      detail: 'Si los dos confirmaron, el tutor gana sus horas.',
    },
  ];
  return (
    <Card title="Cómo funciona">
      <ul className="flex flex-col gap-4">
        {steps.map((step) => (
          <li key={step.title} className="flex items-start gap-3">
            <span className="mt-0.5 shrink-0">{step.icon}</span>
            <div>
              <p className="text-sm font-semibold text-ink">{step.title}</p>
              <p className="mt-0.5 text-[13px] text-ink-2">{step.detail}</p>
            </div>
          </li>
        ))}
      </ul>
    </Card>
  );
}

/** What the student asked for and the material prepared from it, before the session. */
export function NeedAndMaterial({ session }: { session: Session }) {
  const material = session.supportMaterial;
  const asTutor = session.role === 'TUTOR';
  if (!session.needDescription && !material) return null;
  return (
    <Card>
      <p className="text-[13px] font-semibold text-ink-2">
        {asTutor ? `Lo que necesita ${otherName(session)}` : 'Lo que pediste'}
      </p>
      {session.needDescription && <p className="mt-3 text-[15px] text-ink">“{session.needDescription}”</p>}
      {material && (
        <div className="mt-4 rounded-xl bg-surface p-4">
          <p className="text-xs font-semibold text-ink-2">
            Material de apoyo · preparado a partir de lo que {asTutor ? 'pidió' : 'pediste'}
          </p>
          {material.summary && <p className="mt-2 text-sm text-ink">{material.summary}</p>}
          {(material.suggestions?.length ?? 0) > 0 && (
            <ul className="mt-2 flex flex-wrap gap-x-4 gap-y-1">
              {material.suggestions?.map((suggestion) => (
                <li key={suggestion} className="text-[13px] font-medium text-ink-2">
                  {suggestion}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Card>
  );
}

export interface RoomSummaryProps {
  session: Session;
  phase: Phase;
  onEnter: () => void;
  entering: boolean;
  joined: JoinedSession | null;
  renewPass: () => Promise<unknown>;
  /** What the closing of the session (FE-23) adds, under the participants. */
  children?: ReactNode;
}

/**
 * FE-20 · the session before it starts and once it is over: who has arrived, the button to enter
 * and what the student asked for.
 */
export function RoomSummary({ session, phase, onEnter, entering, joined, renewPass, children }: RoomSummaryProps) {
  const zone = useZone();
  const profile = useApiQuery(['identity', 'me'], '/api/v1/me');
  const badge = statusBadge(session.status);
  const line = roomLine(session, phase, zone);
  const notice = roomNotice(session, phase, zone);
  const me = profile.data?.fullName ?? 'Tú';
  const canEnter = phase === 'OPEN';
  const [videoOpen, setVideoOpen] = useState(false);

  return (
    <>
      <PageHeader title={courseName(session)} description={whenLine(session, zone)} />
      <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_420px]">
        <div className="flex min-w-0 flex-col gap-4">
          <Card
            title={
              <span className="flex items-center gap-2">
                La sala <Badge tone={badge.tone}>{badge.label}</Badge>
              </span>
            }
          >
            <div className="flex flex-col gap-4">
              {line && <p className="text-sm text-ink-2">{line}</p>}
              {notice && <Notice notice={notice} />}
              <ul className="flex flex-col gap-3">
                <Participant name={me} own arrival={arrivalText(myArrival(session), 'ME', session, zone)} />
                <Participant
                  name={otherName(session)}
                  arrival={arrivalText(otherArrival(session), 'OTHER', session, zone)}
                />
              </ul>
              {(phase === 'BEFORE_OPEN' || phase === 'OPEN') && (
                <Button fullWidth disabled={!canEnter} loading={entering} onClick={onEnter}>
                  Entrar a la sala
                </Button>
              )}
              {phase === 'WAITING' && !videoOpen && (
                <Button fullWidth onClick={() => setVideoOpen(true)}>
                  Abrir el video
                </Button>
              )}
              {phase === 'WAITING' && videoOpen && (
                <RoomMedia kind="video" session={session} joined={joined} renewPass={renewPass} />
              )}
              {notice?.action && (
                <ButtonLink to={notice.action.to} fullWidth>
                  {notice.action.label}
                </ButtonLink>
              )}
              {children}
            </div>
          </Card>
          <NeedAndMaterial session={session} />
        </div>
        <HowItWorks />
      </div>
    </>
  );
}
