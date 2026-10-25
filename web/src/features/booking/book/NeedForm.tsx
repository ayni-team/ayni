import type { FormEvent } from 'react';
import { Button, Field, TextArea } from '@/shared/ui';

export interface NeedFormProps {
  tutorFirstName: string;
  needDescription: string;
  onChangeNeed: (value: string) => void;
  error?: string | null;
  onCancel: () => void;
  onSubmit: (e: FormEvent) => void;
  isConfirming: boolean;
  disabled: boolean;
}

export function NeedForm({
  tutorFirstName,
  needDescription,
  onChangeNeed,
  error,
  onCancel,
  onSubmit,
  isConfirming,
  disabled,
}: NeedFormProps) {
  return (
    <form
      noValidate
      onSubmit={onSubmit}
      className="space-y-5 rounded-xl border border-gray-200 bg-white p-5 shadow-sm sm:p-6"
    >
      <Field
        label="¿En qué necesitas ayuda?"
        hint={`Cuéntale a ${tutorFirstName} en qué tema te trabaste o qué examen estás preparando para que llegue listo.`}
        error={error}
        required
      >
        <TextArea
          value={needDescription}
          onChange={(e) => onChangeNeed(e.target.value)}
          placeholder="Ej: Tengo dudas con la normalización de tablas en BD2, en especial con Boyce-Codd..."
          rows={5}
          maxLength={500}
          showCount
          disabled={disabled || isConfirming}
          required
        />
      </Field>

      <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:items-center sm:justify-between">
        <Button variant="secondary" onClick={onCancel} disabled={isConfirming}>
          Elegir otra hora
        </Button>
        <Button
          variant="primary"
          type="submit"
          loading={isConfirming}
          disabled={disabled || isConfirming}
        >
          Confirmar reserva
        </Button>
      </div>
    </form>
  );
}
