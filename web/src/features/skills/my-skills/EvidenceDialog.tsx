import { useQueryClient } from '@tanstack/react-query';
import { useState, type InputHTMLAttributes } from 'react';
import { errorText, isApiError, uploadFiles } from '@/shared/api';
import { Button, Dialog, Field, TextArea, useFieldControl, useToast } from '@/shared/ui';
import { FILE_ACCEPT, MAX_NOTE, evidenceProblem } from './skills';

/** The file picker, tied to the label of its `Field`. `shared/ui` has none. */
function FileInput(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input type="file" {...useFieldControl(props)} />;
}

export interface EvidenceTarget {
  catalogItemId: string;
  name: string;
}

/**
 * The evidence for a tool of the catalogue, which a coordinator reviews: 1 to 3 files and a note. The
 * limits are checked here to name the file; the server checks them again, and its text is English.
 */
export function EvidenceDialog({ target, onClose }: { target: EvidenceTarget | null; onClose: () => void }) {
  const toast = useToast();
  const client = useQueryClient();
  const [files, setFiles] = useState<File[]>([]);
  const [note, setNote] = useState('');
  const [problem, setProblem] = useState<string>();
  const [sending, setSending] = useState(false);

  function close() {
    if (sending) return;
    setFiles([]);
    setNote('');
    setProblem(undefined);
    onClose();
  }

  async function send() {
    if (!target || sending) return;
    const wrong = evidenceProblem(files);
    setProblem(wrong);
    if (wrong) return;
    setSending(true);
    try {
      await uploadFiles(
        '/api/v1/tutor/skills/{id}/validation',
        files,
        { note: note.trim() || undefined },
        { path: { id: target.catalogItemId } },
      );
      await client.invalidateQueries({ queryKey: ['skills'] });
      toast.success('Enviamos tu evidencia. Tu coordinación la revisará');
      setSending(false);
      close();
    } catch (failure) {
      setSending(false);
      if (isApiError(failure, 400)) {
        setProblem('Revisa los archivos: deben ser PDF, PNG o JPEG, de hasta 5 MB, y lo que dicen ser');
      } else if (isApiError(failure, 409)) {
        setProblem('Ya hay evidencia en revisión para esta herramienta, o ya la tienes habilitada');
      } else {
        setProblem(errorText(failure));
      }
    }
  }

  return (
    <Dialog
      open={target !== null}
      onClose={close}
      dismissible={!sending}
      title={`Evidencia de ${target?.name ?? 'la herramienta'}`}
      description="Tu coordinación la revisa y te avisamos. Si la aprueba, la herramienta queda habilitada."
      footer={
        <>
          <Button onClick={() => void send()} loading={sending}>
            Enviar evidencia
          </Button>
          <Button variant="secondary" onClick={close} disabled={sending}>
            Cancelar
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <Field label="Archivos" hint="De 1 a 3, en PDF, PNG o JPEG, de hasta 5 MB cada uno." error={problem}>
          <FileInput
            multiple
            accept={FILE_ACCEPT}
            onChange={(event) => {
              setFiles(Array.from(event.target.files ?? []));
              setProblem(undefined);
            }}
            className="block w-full text-sm text-ink-2 file:mr-3 file:rounded-xl file:border file:border-line file:bg-white file:px-3 file:py-2 file:text-sm file:font-semibold file:text-ink"
          />
        </Field>
        <Field label="Nota para quien revisa (opcional)">
          <TextArea rows={3} maxLength={MAX_NOTE} value={note} onChange={(event) => setNote(event.target.value)} />
        </Field>
      </div>
    </Dialog>
  );
}
