import type { FormEvent } from 'react';
import { Button, cx } from '@/shared/ui';

export interface NeedFormProps {
  needDescription: string;
  onChangeNeed: (value: string) => void;
  error?: string | null;
  onCancel: () => void;
  onSubmit: (e: FormEvent) => void;
  isConfirming: boolean;
  disabled: boolean;
  onBuyCredits?: () => void;
}

export function NeedForm({
  needDescription,
  onChangeNeed,
  error,
  onCancel,
  onSubmit,
  isConfirming,
  disabled,
  onBuyCredits,
}: NeedFormProps) {
  return (
    <form noValidate onSubmit={onSubmit} className="space-y-4 rounded-[20px] border border-line bg-white p-6 shadow-sm">
      <div>
        <label htmlFor="needDescription" className="block text-sm font-semibold text-ink mb-2">
          ¿En qué necesitas ayuda?
        </label>
        <textarea
          id="needDescription"
          value={needDescription}
          onChange={(e) => onChangeNeed(e.target.value)}
          placeholder="Repasar recursividad y el análisis de complejidad para la PC2."
          rows={4}
          maxLength={500}
          disabled={disabled || isConfirming}
          className="block w-full rounded-xl border border-line p-3 text-sm text-ink placeholder:text-gray-400 focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand disabled:bg-surface disabled:text-gray-400 resize-y"
        />
        <p className="mt-1.5 text-xs text-ink-2">Obligatorio. Tu tutor lo lee antes de la sesión.</p>
        {error && (
          <p className="mt-1.5 text-xs font-medium text-red-600" role="alert">
            {error}
          </p>
        )}
      </div>

      <div className="flex items-center justify-end gap-6 pt-2">
        {onBuyCredits ? (
          <button
            type="button"
            onClick={onBuyCredits}
            disabled={isConfirming}
            className="text-sm font-medium text-brand hover:underline"
          >
            Comprar créditos
          </button>
        ) : (
          <button
            type="button"
            onClick={onCancel}
            disabled={isConfirming}
            className="text-sm font-medium text-brand hover:underline"
          >
            Elegir otra hora
          </button>
        )}
        <Button
          variant="primary"
          type="submit"
          loading={isConfirming}
          disabled={disabled || isConfirming}
          className={cx(
            'rounded-xl px-6 py-2.5 font-medium transition-colors',
            disabled && 'bg-gray-100 text-gray-400 border-none pointer-events-none hover:bg-gray-100',
          )}
        >
          Confirmar reserva
        </Button>
      </div>
    </form>
  );
}
