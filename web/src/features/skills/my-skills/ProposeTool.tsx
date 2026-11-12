import { useState } from 'react';
import { errorBody, errorText, isApiError, useApiMutation, useApiQuery, type Schema } from '@/shared/api';
import { Badge, Button, Card, Field, Select, Skeleton, TextArea, TextField } from '@/shared/ui';
import type { EvidenceTarget } from './EvidenceDialog';
import { categoryName, proposalStatus, type CatalogItem } from './skills';

const MAX_NAME = 120;

/**
 * Proposing a tool the catalogue lacks. The server answers 409 with the items that look like it until
 * the person says theirs is a different one; one of those may be what they wanted.
 */
export function ProposeTool({ onEvidence }: { onEvidence: (target: EvidenceTarget) => void }) {
  const categories = useApiQuery(['skills', 'categories'], '/api/v1/catalog/categories');
  const proposals = useApiQuery(['skills', 'proposals'], '/api/v1/tutor/skills/proposals');
  const propose = useApiMutation('post', '/api/v1/tutor/skills/proposals', { invalidates: [['skills', 'proposals']] });
  const [name, setName] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [description, setDescription] = useState('');
  const [similar, setSimilar] = useState<CatalogItem[]>([]);
  const [problem, setProblem] = useState<string>();
  const [sent, setSent] = useState(false);

  const options = (categories.data ?? []).map((category) => ({
    value: category.id ?? '',
    label: categoryName(category.name),
  }));

  async function send(confirmDistinct: boolean) {
    setSent(false);
    if (!name.trim() || !categoryId) {
      setProblem('Escribe el nombre y elige una categoría');
      return;
    }
    setProblem(undefined);
    try {
      await propose.mutateAsync({
        body: { name: name.trim(), categoryId, description: description.trim() || null, confirmDistinct },
      });
      setName('');
      setDescription('');
      setSimilar([]);
      setSent(true);
    } catch (failure) {
      if (isApiError(failure, 409)) {
        const found = errorBody<Schema<'SimilarSkillsError'>>(failure)?.similar ?? [];
        if (found.length > 0) setSimilar(found);
        // With the same name as one of the catalogue there is nothing to insist on.
        else setProblem('Esa herramienta ya está en el catálogo. Búscala arriba para subir tu evidencia');
      } else {
        setProblem(isApiError(failure, 400) ? 'Revisa el nombre y la categoría' : errorText(failure));
      }
    }
  }

  const mine = proposals.data ?? [];

  return (
    <Card
      title="Proponer una herramienta"
      description="Si la que enseñas no está en el catálogo, propónla a tu coordinación."
    >
      <div className="flex flex-col gap-4">
        <Field label="Nombre" error={problem}>
          <TextField
            maxLength={MAX_NAME}
            value={name}
            onChange={(event) => {
              setName(event.target.value);
              setSimilar([]);
              setSent(false);
            }}
          />
        </Field>
        <Field label="Categoría">
          <Select
            value={categoryId}
            placeholder="Elige una categoría"
            options={options}
            onChange={(event) => setCategoryId(event.target.value)}
          />
        </Field>
        <Field label="Descripción (opcional)">
          <TextArea rows={2} value={description} onChange={(event) => setDescription(event.target.value)} />
        </Field>
        {similar.length > 0 && (
          <div role="status" className="rounded-xl bg-credit-soft p-4">
            <p className="text-sm font-semibold text-ink">¿Es alguna de estas?</p>
            <ul className="mt-2 flex flex-col gap-2">
              {similar.map((item) => (
                <li key={item.id} className="flex items-center justify-between gap-3">
                  <span className="min-w-0 text-sm text-ink">{item.name}</span>
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => onEvidence({ catalogItemId: item.id ?? '', name: item.name ?? 'la herramienta' })}
                  >
                    Es esta
                  </Button>
                </li>
              ))}
            </ul>
          </div>
        )}
        {sent && (
          <p role="status" className="rounded-xl bg-info-soft p-3 text-sm text-ink">
            Enviamos tu propuesta. Tu coordinación la revisará.
          </p>
        )}
        <div className="flex justify-end">
          {similar.length > 0 ? (
            <Button variant="secondary" loading={propose.isPending} onClick={() => void send(true)}>
              No, es otra: proponerla igual
            </Button>
          ) : (
            <Button loading={propose.isPending} onClick={() => void send(false)}>
              Proponer
            </Button>
          )}
        </div>
        {proposals.isPending ? (
          <Skeleton lines={1} label="Cargando tus propuestas" />
        ) : (
          mine.length > 0 && (
            <div className="border-t border-line pt-3">
              <h3 className="text-[13px] font-semibold text-ink">Tus propuestas</h3>
              <ul className="mt-2 flex flex-col gap-2">
                {mine.map((proposal) => {
                  const badge = proposalStatus(proposal.status);
                  return (
                    <li key={proposal.id}>
                      <div className="flex items-center justify-between gap-3">
                        <span className="min-w-0 text-sm text-ink">{proposal.name}</span>
                        <Badge tone={badge.tone}>{badge.label}</Badge>
                      </div>
                      {proposal.decisionReason && (
                        <p className="mt-0.5 text-xs text-ink-2">{proposal.decisionReason}</p>
                      )}
                    </li>
                  );
                })}
              </ul>
            </div>
          )
        )}
      </div>
    </Card>
  );
}
