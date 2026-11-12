import { useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { errorText, useApiMutation } from '@/shared/api';
import { Button, ButtonLink, Card, Field, IconAlert, IconCheck, PageHeader, TextField, useToast } from '@/shared/ui';
import { endedFrom, openRefusal, practiceLink, type OpenRefusal } from './practice';

/**
 * `/practica` · student. A tool of the team, not a screen of the product, so no menu leads here.
 *
 * Opens a practice room: the real screen of a session, with its video and its whiteboard, between
 * two accounts, at any time, with no booking, no credits and no record. The link goes to
 * `/sesion/<id>`, the same screen every tutoring uses.
 */
export default function PracticePage() {
  const toast = useToast();
  const location = useLocation();
  const open = useApiMutation('post', '/api/v1/practice-rooms');
  const [path, setPath] = useState<string | null>(null);
  const [refusal, setRefusal] = useState<OpenRefusal | null>(null);
  const linkInput = useRef<HTMLInputElement>(null);
  const link = path ? practiceLink(window.location.origin, path) : null;

  async function create() {
    setRefusal(null);
    try {
      const room = await open.mutateAsync({});
      setPath(room.path ?? null);
    } catch (failure) {
      const why = openRefusal(failure);
      setRefusal(why);
      if (why === 'OTHER') toast.error(errorText(failure));
    }
  }

  async function copy() {
    if (!link) return;
    try {
      await navigator.clipboard.writeText(link);
      toast.success('Enlace copiado');
    } catch {
      // Outside https (another computer on the local network) the browser does not let us copy.
      linkInput.current?.select();
      toast.error('No pudimos copiarlo: selecciónalo y cópialo a mano');
    }
  }

  return (
    <>
      <PageHeader
        title="Sala de práctica"
        description="Una sesión de prueba con video y pizarra entre dos cuentas. Sin reservas, sin créditos y sin dejar ningún registro."
      />
      <div className="flex max-w-2xl flex-col gap-4">
        {endedFrom(location.state) && (
          <Card className="flex items-start gap-3">
            <IconCheck className="mt-0.5 h-5 w-5 shrink-0 text-info-text" />
            <p className="text-sm text-ink">La sala de práctica terminó. No quedó ningún registro.</p>
          </Card>
        )}

        <Card>
          <ol className="flex list-decimal flex-col gap-1.5 pl-5 text-sm text-ink-2">
            <li>Crea la sala y comparte el enlace con la otra cuenta, de tu misma universidad.</li>
            <li>Quien entra primero es tutor y quien entra después, estudiante.</li>
            <li>Para confirmar la presencia sirve cualquier código de 6 dígitos: no se envía ningún correo.</li>
            <li>Cuando las dos terminan la sesión, la sala se borra. Si no, se borra sola a las 4 horas.</li>
          </ol>
          <Button className="mt-4" onClick={() => void create()} loading={open.isPending}>
            Crear sala de práctica
          </Button>
        </Card>

        {refusal === 'OFF' && (
          <div role="alert" className="flex items-start gap-3 rounded-xl bg-credit-soft p-4">
            <IconAlert className="mt-0.5 h-5 w-5 shrink-0 text-credit-text" />
            <p className="text-sm font-semibold text-ink">Las salas de práctica no están activas</p>
          </div>
        )}
        {refusal === 'FULL' && (
          <div role="alert" className="flex items-start gap-3 rounded-xl bg-credit-soft p-4">
            <IconAlert className="mt-0.5 h-5 w-5 shrink-0 text-credit-text" />
            <div>
              <p className="text-sm font-semibold text-ink">Hay demasiadas salas abiertas</p>
              <p className="mt-0.5 text-[13px] text-ink-2">Termina alguna o espera a que se borre.</p>
            </div>
          </div>
        )}

        {link && path && (
          <Card>
            <Field label="Enlace de la sala" hint="Ábrelo en el otro navegador o en la otra computadora.">
              <TextField ref={linkInput} value={link} readOnly onFocus={(event) => event.target.select()} />
            </Field>
            <div className="mt-4 flex flex-wrap gap-2">
              <Button variant="secondary" onClick={() => void copy()}>
                Copiar enlace
              </Button>
              <ButtonLink to={path}>Entrar</ButtonLink>
            </div>
          </Card>
        )}
      </div>
    </>
  );
}
