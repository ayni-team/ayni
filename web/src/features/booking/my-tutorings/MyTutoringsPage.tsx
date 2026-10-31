import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, errorBody, errorText, isApiError, useApiQuery, type Schema } from '@/shared/api';
import { useZone } from '@/shared/time';
import { ButtonLink, EmptyState, PageHeader, Tabs, pageCount, useToast, type TabItem } from '@/shared/ui';
import { CancellationBanner } from './CancellationBanner';
import { LateCancellationDialog } from './LateCancellationDialog';
import { TutoringCard } from './TutoringCard';
import { TutoringsErrorState } from './TutoringsErrorState';
import { TutoringsPagination } from './TutoringsPagination';
import { TutoringsSkeleton } from './TutoringsSkeleton';

type ViewMode = 'UPCOMING' | 'HISTORY';

const TABS: readonly TabItem<ViewMode>[] = [
  { value: 'UPCOMING', label: 'Próximas' },
  { value: 'HISTORY', label: 'Historial' },
];

/**
 * Tray icon for empty state, matching the Lucide inbox icon from the Figma empty state frame.
 */
function IconInbox({ className = 'h-6 w-6' }: { className?: string }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <polyline points="22 12 16 12 14 15 10 15 8 12 2 12" />
      <path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" />
    </svg>
  );
}

/**
 * FE-12 · My tutorings · `/mis-tutorias` · student.
 *
 * Displays confirmed upcoming tutorings and tutoring history, supporting joining in-progress sessions,
 * reviewing completed ones, pagination, and two-step late cancellations.
 */
export default function MyTutoringsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const fallbackZone = useZone();

  const [view, setView] = useState<ViewMode>('UPCOMING');
  const [page, setPage] = useState(0);
  const pageSize = 5;

  // Banner message displayed above cards upon cancellation (204)
  const [bannerMessage, setBannerMessage] = useState<string | null>(null);

  // Late cancellation confirmation state (409)
  const [lateConfirmation, setLateConfirmation] = useState<{
    booking: Schema<'StudentBooking'>;
    confirmation?: Schema<'LateCancellationConfirmation'> | null;
  } | null>(null);

  // Loading states for actions
  const [cancellingBookingId, setCancellingBookingId] = useState<string | null>(null);
  const [isConfirmingLate, setIsConfirmingLate] = useState(false);
  const [joiningBookingId, setJoiningBookingId] = useState<string | null>(null);
  const [reviewingBookingId, setReviewingBookingId] = useState<string | null>(null);

  // Fetch student bookings for active tab & page
  const { data, isLoading, error, refetch, isFetching } = useApiQuery(
    ['booking', 'mine', { view, page, size: pageSize }],
    '/api/v1/bookings/mine',
    {
      query: { view, page, size: pageSize },
    },
  );

  const timezone = data?.timezone ?? fallbackZone;
  const zoneCity = timezone.split('/')[1]?.replace(/_/g, ' ') ?? 'Lima';
  const totalPages = pageCount(data?.total ?? 0, data?.size ?? pageSize);
  const items = data?.items ?? [];

  const handleTabChange = (nextView: ViewMode) => {
    setView(nextView);
    setPage(0);
    setBannerMessage(null);
  };

  /**
   * First cancellation attempt:
   * If outside 12h, server returns 204 directly.
   * If inside 12h, server returns 409 Conflict with late=true -> open confirmation dialog.
   */
  const handleCancel = async (booking: Schema<'StudentBooking'>) => {
    if (!booking.bookingId) return;
    setCancellingBookingId(booking.bookingId);
    try {
      await api.delete('/api/v1/bookings/{id}', {
        path: { id: booking.bookingId },
      });

      const hours = booking.hours ?? 1;
      const creditText = hours === 1 ? '1 crédito' : `${hours} créditos`;
      setBannerMessage(`Cancelaste la tutoría y te devolvimos ${creditText}`);

      await queryClient.invalidateQueries({ queryKey: ['booking'] });
      await queryClient.invalidateQueries({ queryKey: ['wallet'] });
    } catch (err) {
      const confirmation = isApiError(err, 409) ? errorBody<Schema<'LateCancellationConfirmation'>>(err) : undefined;
      if (confirmation?.confirmationRequired || confirmation?.late) {
        setLateConfirmation({
          booking,
          confirmation,
        });
      } else if (isApiError(err, 409)) {
        // Not the late warning: the session already started, or the booking is no longer cancellable.
        toast.error('Esta tutoría ya no se puede cancelar.');
        await queryClient.invalidateQueries({ queryKey: ['booking'] });
      } else {
        toast.error(errorText(err));
      }
    } finally {
      setCancellingBookingId(null);
    }
  };

  /**
   * Second step of late cancellation:
   * Repeat DELETE with confirmLate=true.
   */
  const handleConfirmLateCancellation = async () => {
    if (!lateConfirmation?.booking.bookingId) return;
    setIsConfirmingLate(true);
    try {
      await api.delete('/api/v1/bookings/{id}', {
        path: { id: lateConfirmation.booking.bookingId },
        query: { confirmLate: true },
      });

      setLateConfirmation(null);
      setBannerMessage('Cancelaste la tutoría');

      await queryClient.invalidateQueries({ queryKey: ['booking'] });
      await queryClient.invalidateQueries({ queryKey: ['wallet'] });
    } catch (err) {
      setLateConfirmation(null);
      toast.error(isApiError(err, 409) ? 'Esta tutoría ya no se puede cancelar.' : errorText(err));
    } finally {
      setIsConfirmingLate(false);
    }
  };

  /**
   * Joining an in-progress session:
   * Calls GET /sessions?bookingId= to get the sessionId and navigates to /sesion/:id.
   */
  const handleJoin = async (booking: Schema<'StudentBooking'>) => {
    if (!booking.bookingId) return;
    setJoiningBookingId(booking.bookingId);
    try {
      const session = await api.get('/api/v1/sessions', {
        query: { bookingId: booking.bookingId },
      });
      if (session?.sessionId) {
        navigate(`/sesion/${session.sessionId}`);
      } else {
        toast.error('No se encontró la sala de la sesión.');
      }
    } catch (err) {
      toast.error(errorText(err));
    } finally {
      setJoiningBookingId(null);
    }
  };

  /**
   * Reviewing a past session:
   * Navigates to FE-24 `/sesion/:id/repaso`.
   */
  const handleReview = async (booking: Schema<'StudentBooking'>) => {
    if (!booking.bookingId) return;
    setReviewingBookingId(booking.bookingId);
    try {
      const session = await api.get('/api/v1/sessions', {
        query: { bookingId: booking.bookingId },
      });
      if (session?.sessionId) {
        navigate(`/sesion/${session.sessionId}/repaso`);
      } else {
        toast.error('No encontramos la sesión de esta tutoría.');
      }
    } catch (err) {
      toast.error(errorText(err));
    } finally {
      setReviewingBookingId(null);
    }
  };

  return (
    <div className="mx-auto max-w-5xl py-4 sm:py-6">
      <PageHeader title="Mis tutorías" description={`Las que recibes. Horas en hora de ${zoneCity}.`} />

      <Tabs label="Vistas de tutorías" items={TABS} value={view} onChange={handleTabChange} className="mb-6" />

      {bannerMessage && <CancellationBanner message={bannerMessage} />}

      {isLoading && <TutoringsSkeleton />}

      {!isLoading && error && <TutoringsErrorState onRetry={() => void refetch()} retrying={isFetching} />}

      {!isLoading && !error && items.length === 0 && (
        <EmptyState
          icon={
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-gray-100 text-gray-500">
              <IconInbox className="h-6 w-6" />
            </div>
          }
          title={view === 'UPCOMING' ? 'Todavía no tienes tutorías' : 'No tienes tutorías en tu historial'}
          description={
            view === 'UPCOMING' ? 'Busca una en Buscar.' : 'Las tutorías que recibas o canceles aparecerán aquí.'
          }
          action={
            view === 'UPCOMING' ? (
              <ButtonLink to="/buscar" variant="primary">
                Buscar tutoría
              </ButtonLink>
            ) : undefined
          }
        />
      )}

      {!isLoading && !error && items.length > 0 && (
        <div className="space-y-4">
          {items.map((booking) => (
            <TutoringCard
              key={booking.bookingId}
              booking={booking}
              view={view}
              timezone={timezone}
              onJoin={handleJoin}
              onCancel={handleCancel}
              onReview={handleReview}
              isJoining={joiningBookingId === booking.bookingId}
              isCancelling={cancellingBookingId === booking.bookingId}
              isReviewing={reviewingBookingId === booking.bookingId}
            />
          ))}

          <TutoringsPagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
            label="Paginación de tutorías"
          />
        </div>
      )}

      <LateCancellationDialog
        open={Boolean(lateConfirmation)}
        onClose={() => setLateConfirmation(null)}
        onConfirm={handleConfirmLateCancellation}
        booking={lateConfirmation?.booking ?? null}
        confirmation={lateConfirmation?.confirmation ?? null}
        timezone={timezone}
        confirming={isConfirmingLate}
      />
    </div>
  );
}
