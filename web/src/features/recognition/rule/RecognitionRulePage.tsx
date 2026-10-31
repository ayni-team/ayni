import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { errorText, isApiError, useApiMutation, useApiQuery } from '@/shared/api';
import { useUniversity } from '@/shared/brand';
import { useUniversityToday } from '@/shared/time';
import { Button, Card, ErrorState, Field, IconAward, PageHeader, Skeleton, TextField, useToast } from '@/shared/ui';
import { dayText, hoursError, hoursText, savedText, startError } from './rule';

interface RuleForm {
  hours: string;
  validFrom: string;
}

/** The rule in force: the hours asked for and since when, or that there is none yet. */
function RuleInForce({ who }: { who: string }) {
  const rule = useApiQuery(['recognition', 'rule'], '/api/v1/coordinator/recognition/rule', { retry: false });

  if (rule.isPending) {
    return (
      <Card title="Regla en vigor">
        <Skeleton lines={2} label="Cargando la regla" />
      </Card>
    );
  }
  if (rule.isError && isApiError(rule.error, 404)) {
    return (
      <Card title="Regla en vigor">
        <p className="font-semibold text-ink">Todavía no definiste cuántas horas pide tu universidad</p>
        <p className="mt-1 text-sm text-ink-2">
          Mientras no la definas, los estudiantes no ven cuánto les falta ni pueden pedir reconocimiento.
        </p>
      </Card>
    );
  }
  if (rule.isError) {
    return <ErrorState error={rule.error} onRetry={() => void rule.refetch()} retrying={rule.isFetching} />;
  }
  const since = dayText(rule.data?.validFrom);
  return (
    <Card title="Regla en vigor">
      <div className="flex items-center gap-4">
        <span
          aria-hidden="true"
          className="inline-flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-brand-soft text-brand-text"
        >
          <IconAward className="h-6 w-6" />
        </span>
        <div>
          <p className="font-display text-3xl font-semibold text-ink">{hoursText(rule.data?.minimumHours)}</p>
          <p className="text-sm text-ink-2">
            de sesiones verificadas, lo que {who} pide para considerar el reconocimiento
            {since && `, desde el ${since}`}.
          </p>
        </div>
      </div>
    </Card>
  );
}

/**
 * FE-52 · `/coordinador/regla` · coordinator.
 *
 * How many hours of verified sessions the university asks before it considers recognising them, and a
 * form to set a new rule. Rules are never edited: the new one replaces the one in force from its day,
 * and the requests already submitted keep the rule they were presented with.
 */
export default function RecognitionRulePage() {
  const toast = useToast();
  const today = useUniversityToday();
  const university = useUniversity();
  const who = university?.code ? `la ${university.code}` : 'tu universidad';
  const [saved, setSaved] = useState<string>();
  const save = useApiMutation('put', '/api/v1/coordinator/recognition/rule', { invalidates: [['recognition']] });
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<RuleForm>({ defaultValues: { hours: '', validFrom: '' } });

  async function submit(values: RuleForm) {
    setSaved(undefined);
    const minimumHours = Number(values.hours.trim());
    try {
      const rule = await save.mutateAsync({
        body: { minimumHours, validFrom: values.validFrom || null },
      });
      setSaved(savedText(rule.minimumHours ?? minimumHours, rule.validFrom ?? (values.validFrom || undefined), today));
      toast.success('Guardaste la regla de reconocimiento');
      reset();
    } catch (failure) {
      if (isApiError(failure, 400)) {
        setError('hours', { message: 'Revisa los datos e inténtalo de nuevo' });
      } else {
        toast.error(errorText(failure));
      }
    }
  }

  return (
    <>
      <PageHeader
        title="Regla de reconocimiento"
        description={`Las horas de tutoría que ${who} pide a un estudiante para considerar su reconocimiento.`}
      />
      <div className="grid gap-6 lg:grid-cols-2">
        <RuleInForce who={who} />
        <Card
          title="Nueva regla"
          description="Las solicitudes ya enviadas conservan la regla con la que se presentaron."
        >
          <form noValidate onSubmit={(event) => void handleSubmit(submit)(event)} className="flex flex-col gap-4">
            <Field
              label="Horas de sesiones verificadas"
              hint="Al menos 1. Cada hora de tutoría verificada es un crédito ganado."
              error={errors.hours?.message}
              required
            >
              <TextField
                type="number"
                inputMode="numeric"
                min={1}
                step={1}
                {...register('hours', { validate: (value) => hoursError(value) ?? true })}
              />
            </Field>
            <Field
              label="Desde (opcional)"
              hint="Si lo dejas vacío, rige desde hoy. No puede ser un día que ya pasó."
              error={errors.validFrom?.message}
            >
              <TextField
                type="date"
                min={today}
                {...register('validFrom', { validate: (value) => startError(value, today) ?? true })}
              />
            </Field>
            {saved && (
              <p role="status" className="rounded-xl bg-info-soft px-3 py-2.5 text-sm text-info-text">
                {saved}
              </p>
            )}
            <div className="flex justify-end">
              <Button type="submit" loading={save.isPending}>
                Guardar regla
              </Button>
            </div>
          </form>
        </Card>
      </div>
    </>
  );
}
