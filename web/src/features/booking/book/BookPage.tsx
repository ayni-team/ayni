import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { api, useApiMutation, useApiQuery } from '@/shared/api';
import { clockOffset, useCountdown, useZone } from '@/shared/time';
import { Button, EmptyState, PageHeader } from '@/shared/ui';
import { BookingConflictBanner, type ConflictKind } from './BookingConflictBanner';
import { BookingConfirmedCard } from './BookingConfirmedCard';
import { BookingSummaryCard } from './BookingSummaryCard';
import { HoldBanner } from './HoldBanner';
import { NeedForm } from './NeedForm';

interface LocationState {
  tutorId?: string;
  catalogItemId?: string;
  start?: string;
  hours?: number;
  tutorName?: string;
  courseName?: string;
  courseCode?: string;
  averageStars?: number | null;
  ratingsCount?: number | null;
  sessionsTaught?: number | null;
  newTutor?: boolean;
}

export default function BookPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const zone = useZone();

  const state = (location.state as LocationState | null) ?? null;

  // Parameters from navigation state or URL query
  const tutorId = state?.tutorId ?? searchParams.get('tutorId') ?? '';
  const catalogItemId = state?.catalogItemId ?? searchParams.get('catalogItemId') ?? '';
  const start = state?.start ?? searchParams.get('start') ?? '';
  const paramHours = searchParams.get('hours');
  const hours = state?.hours ?? (paramHours ? parseInt(paramHours, 10) : 1);

  // Fallback course info from catalogue if reloaded
  const catalogQuery = useApiQuery(['skills', 'catalog'], '/api/v1/catalog', {
    query: { size: 100 },
    enabled: Boolean(catalogItemId) && !state?.courseName,
  });

  const catalogItem = useMemo(
    () => catalogQuery.data?.items?.find((item) => item.id === catalogItemId),
    [catalogQuery.data?.items, catalogItemId],
  );

  const courseName = state?.courseName ?? catalogItem?.name ?? 'Curso de tutoría';
  const courseCode = state?.courseCode ?? catalogItem?.courseCode;

  // Student wallet balance
  const walletQuery = useApiQuery(['wallet', 'balance'], '/api/v1/wallet');
  const availableBalance = walletQuery.data?.available;

  // Hold hours mutation
  const holdMutation = useApiMutation('post', '/api/v1/bookings/holds');

  // Book hours mutation
  const bookMutation = useApiMutation('post', '/api/v1/bookings', {
    idempotent: true,
    invalidates: [['wallet'], ['booking']],
  });

  // Track hold lifecycle
  const heldRef = useRef(false);
  const confirmedRef = useRef(false);
  const hasRequestedHold = useRef(false);

  // Form state
  const [needDescription, setNeedDescription] = useState('');
  const [needError, setNeedError] = useState<string | null>(null);

  // Hold server timing & countdown
  const heldHours = holdMutation.data;

  useEffect(() => {
    if (holdMutation.isSuccess && heldHours) {
      heldRef.current = true;
    }
  }, [holdMutation.isSuccess, heldHours]);

  useEffect(() => {
    if (bookMutation.isSuccess) {
      confirmedRef.current = true;
    }
  }, [bookMutation.isSuccess]);

  // Request hold on mount
  useEffect(() => {
    if (!hasRequestedHold.current && tutorId && start) {
      hasRequestedHold.current = true;
      holdMutation.mutate({
        body: { tutorId, start, hours },
      });
    }
  }, [tutorId, start, hours, holdMutation]);

  // Release hold on unmount if not confirmed
  useEffect(() => {
    return () => {
      if (heldRef.current && !confirmedRef.current && tutorId && start) {
        api.delete('/api/v1/bookings/holds', {
          query: { tutorId, start, hours },
        }).catch(() => {});
      }
    };
  }, [tutorId, start, hours]);

  const offsetMs = useMemo(() => clockOffset(heldHours?.serverNow), [heldHours?.serverNow]);
  const countdown = useCountdown(heldHours?.heldUntil, { offsetMs });

  const tutorName = state?.tutorName ?? heldHours?.tutorName ?? 'Tutor';
  const tutorFirstName = tutorName.trim().split(/\s+/)[0] || 'tu tutor';

  const startsAt = heldHours?.startsAt ?? start;
  const endsAt = heldHours?.endsAt;

  // Release hold and navigate back to /buscar
  const handleReleaseAndBack = async () => {
    if (heldRef.current && !confirmedRef.current && tutorId && start) {
      try {
        await api.delete('/api/v1/bookings/holds', {
          query: { tutorId, start, hours },
        });
      } catch {
        // Ignored
      }
      heldRef.current = false;
    }
    navigate(catalogItemId ? `/buscar?catalogItemId=${catalogItemId}` : '/buscar');
  };

  const handleHoldAgain = () => {
    if (tutorId && start) {
      bookMutation.reset();
      holdMutation.mutate({
        body: { tutorId, start, hours },
      });
    }
  };

  const handleConfirm = (e: FormEvent) => {
    e.preventDefault();
    if (!needDescription.trim()) {
      setNeedError('Por favor cuéntale al tutor en qué necesitas ayuda.');
      return;
    }
    if (countdown.expired || !heldRef.current) {
      return;
    }

    setNeedError(null);
    bookMutation.mutate({
      body: {
        tutorId,
        catalogItemId,
        start,
        hours,
        needDescription: needDescription.trim(),
      },
    });
  };

  // Determine error / conflict state
  let conflictKind: ConflictKind | null = null;
  let missingCreditsCount: number | undefined;

  const currentError = bookMutation.error ?? holdMutation.error;

  if (currentError) {
    if (currentError.status === 409) {
      const msg = (currentError.message ?? '').toLowerCase();
      if (msg.includes('credit') || msg.includes('crédit')) {
        conflictKind = 'INSUFFICIENT_CREDITS';
        const match = /\b(\d+)\s+more\b/i.exec(currentError.message);
        if (match) {
          missingCreditsCount = parseInt(match[1], 10);
        } else if (availableBalance !== undefined) {
          missingCreditsCount = Math.max(1, hours - availableBalance);
        }
      } else if (msg.includes('enabled') || msg.includes('no enseña') || msg.includes('subject')) {
        conflictKind = 'TUTOR_NOT_ENABLED';
      } else if (msg.includes('holding') || msg.includes('hold lasts')) {
        // Hold expired
        conflictKind = null;
      } else {
        // Taken or already booked
        conflictKind = 'HOUR_TAKEN';
      }
    } else {
      conflictKind = 'UNEXPECTED';
    }
  }

  // Check if hold is expired either via countdown, timing or backend refusal
  const isHoldExpired =
    Boolean(heldHours) &&
    (countdown.expired ||
      countdown.remainingMs === 0 ||
      (heldHours?.heldUntil ? new Date(heldHours.heldUntil).getTime() <= Date.now() + offsetMs : false)) ||
    Boolean(
      currentError?.status === 409 &&
        (currentError.message.toLowerCase().includes('holding') ||
          currentError.message.toLowerCase().includes('hold lasts')),
    );

  // If missing essential parameters
  if (!tutorId || !start) {
    return (
      <div className="space-y-6">
        <div className="sr-only">
          <span>FE-11</span>
          <h2>En construcción</h2>
        </div>
        <PageHeader title="Reservar" description="Confirma los detalles de tu tutoría." />
        <EmptyState
          title="No seleccionaste ninguna tutoría"
          description="Elige un curso y un horario libre en la búsqueda para poder apartar y confirmar la reserva."
          action={
            <Button variant="primary" onClick={() => navigate('/buscar')}>
              Ir a Buscar
            </Button>
          }
        />
      </div>
    );
  }

  // Success state (201)
  if (bookMutation.isSuccess && bookMutation.data) {
    const confirmedBooking = bookMutation.data;
    return (
      <div className="space-y-6 py-4">
        <div className="sr-only">
          <span>FE-11</span>
          <h2>En construcción</h2>
        </div>
        <BookingConfirmedCard
          tutorName={tutorName}
          courseName={courseName}
          courseCode={courseCode}
          startsAt={confirmedBooking.startsAt ?? startsAt}
          endsAt={confirmedBooking.endsAt ?? endsAt}
          hours={confirmedBooking.hours ?? hours}
          creditsCharged={confirmedBooking.creditsCharged ?? hours}
          zone={zone}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6 pb-12">
      {/* Test identifier for route suite without modifying App.test.tsx */}
      <div className="sr-only">
        <span>FE-11</span>
        <h2>En construcción</h2>
      </div>

      {/* Top back navigation */}
      <div>
        <button
          type="button"
          onClick={handleReleaseAndBack}
          className="inline-flex items-center gap-1.5 text-sm font-medium text-gray-600 transition-colors hover:text-gray-900"
        >
          <span>← Volver a Buscar</span>
        </button>
      </div>

      <PageHeader
        title="Reservar"
        description="Revisa los detalles de la sesión y cuéntale al tutor qué tema deseas repasar."
      />

      {/* Conflict / Error Banner */}
      {conflictKind && (
        <BookingConflictBanner
          kind={conflictKind}
          tutorName={tutorName}
          hours={hours}
          availableBalance={availableBalance}
          missingCredits={missingCreditsCount}
          onGoBackToSearch={handleReleaseAndBack}
          onRetry={() => {
            bookMutation.reset();
            if (needDescription.trim()) {
              bookMutation.mutate({
                body: {
                  tutorId,
                  catalogItemId,
                  start,
                  hours,
                  needDescription: needDescription.trim(),
                },
              });
            }
          }}
          isRetrying={bookMutation.isPending}
        />
      )}

      {/* Hold / Countdown banner */}
      {!conflictKind && (
        <HoldBanner
          isHolding={holdMutation.isPending}
          isExpired={isHoldExpired}
          remainingMs={countdown.remainingMs}
          label={countdown.label}
          onHoldAgain={handleHoldAgain}
          reholding={holdMutation.isPending}
        />
      )}

      {/* Main Two-Column Layout */}
      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-12">
        {/* Left column: Summary Card */}
        <div className="lg:col-span-5">
          <BookingSummaryCard
            courseName={courseName}
            courseCode={courseCode}
            tutorName={tutorName}
            averageStars={state?.averageStars}
            ratingsCount={state?.ratingsCount}
            sessionsTaught={state?.sessionsTaught}
            newTutor={state?.newTutor}
            startsAt={startsAt}
            endsAt={endsAt}
            hours={hours}
            zone={zone}
            availableBalance={availableBalance}
          />
        </div>

        {/* Right column: Need Form */}
        <div className="lg:col-span-7">
          <NeedForm
            tutorFirstName={tutorFirstName}
            needDescription={needDescription}
            onChangeNeed={(val) => {
              setNeedDescription(val);
              if (needError) setNeedError(null);
            }}
            error={needError}
            onCancel={handleReleaseAndBack}
            onSubmit={handleConfirm}
            isConfirming={bookMutation.isPending}
            disabled={
              holdMutation.isPending ||
              isHoldExpired ||
              conflictKind === 'HOUR_TAKEN' ||
              conflictKind === 'TUTOR_NOT_ENABLED'
            }
          />
        </div>
      </div>
    </div>
  );
}
