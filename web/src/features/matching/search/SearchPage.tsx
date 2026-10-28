import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { Schema } from '@/shared/api';
import { useApiQuery } from '@/shared/api';
import { useUniversity } from '@/shared/brand';
import { addDays, dayInZone, formatInZone, useUniversityToday, useZone } from '@/shared/time';
import { Button, EmptyState, ErrorState, IconClock, PageHeader, Skeleton } from '@/shared/ui';
import { formatWeekRange, getMondayOfWeek, startOfDayInZone } from './dateUtils';
import { SearchDayList } from './SearchDayList';
import { SearchFilters } from './SearchFilters';
import { SearchGrid, type SelectedSlot } from './SearchGrid';
import { TutorDrawer } from './TutorDrawer';

type Offer = Schema<'Offer'>;

/**
 * Tray / inbox empty state icon matching Figma frames.
 */
function IconTray({ className = 'h-6 w-6' }: { className?: string }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
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
 * WifiOff icon matching the Figma error state frame.
 */
function IconWifiOff({ className = 'h-6 w-6' }: { className?: string }) {
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
      <path d="M12 20h.01" />
      <path d="M8.5 16.429a5 5 0 0 1 7 0" />
      <path d="M5 12.859a10 10 0 0 1 5.17-2.69" />
      <path d="M19 12.859a10 10 0 0 0-2.007-1.523" />
      <path d="M2 8.82a15 15 0 0 1 4.177-2.643" />
      <path d="M22 8.82a15 15 0 0 0-11.288-3.764" />
      <path d="m2 2 20 20" />
    </svg>
  );
}

/**
 * FE-10 · `/buscar`.
 * What every screen shows, which endpoint it calls, which states it handles:
 * See docs/frontend-guide.md and docs/design/field-map.md.
 */
export default function SearchPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const zone = useZone();
  const today = useUniversityToday();
  // The window is in the university's days: search only once its zone is known.
  const zoneKnown = Boolean(useUniversity()?.timezone);

  // Selected course
  const paramCatalogItemId = searchParams.get('catalogItemId') ?? '';
  const [selectedCatalogItemId, setSelectedCatalogItemId] = useState<string>(paramCatalogItemId);

  // Sync state if query param changes
  useEffect(() => {
    if (paramCatalogItemId && paramCatalogItemId !== selectedCatalogItemId) {
      setSelectedCatalogItemId(paramCatalogItemId);
    }
  }, [paramCatalogItemId, selectedCatalogItemId]);

  // Current week (Monday), counted from the university's today, derived on every render so that
  // it follows the zone instead of being frozen in the initial state.
  const [weekOffset, setWeekOffset] = useState(0);
  const mondayStr = addDays(getMondayOfWeek(today), 7 * weekOffset);
  const [page, setPage] = useState<number>(0);
  const [selectedSlot, setSelectedSlot] = useState<SelectedSlot | null>(null);

  // 1. Fetch Catalogue for course picker
  const catalog = useApiQuery(['skills', 'catalog'], '/api/v1/catalog', {
    query: { size: 100 },
  });
  const catalogItems = useMemo(() => catalog.data?.items ?? [], [catalog.data?.items]);

  // Selected course object
  const selectedCourse = useMemo(
    () => catalogItems.find((item) => item.id === selectedCatalogItemId),
    [catalogItems, selectedCatalogItemId],
  );

  // 2. Fetch User's upcoming bookings to highlight in the grid
  const bookingsQuery = useApiQuery(
    ['booking', 'mine', { view: 'UPCOMING' }],
    '/api/v1/bookings/mine',
    {
      query: { view: 'UPCOMING' },
    },
  );
  const myBookings = useMemo(() => bookingsQuery.data?.items ?? [], [bookingsQuery.data?.items]);

  // 3. Search offers for the selected course and week
  const fromInstant = startOfDayInZone(mondayStr, zone);
  const toInstant = startOfDayInZone(addDays(mondayStr, 7), zone);

  const search = useApiQuery(
    ['search', 'offers', { catalogItemId: selectedCatalogItemId, from: fromInstant, to: toInstant, page }],
    '/api/v1/search/offers',
    {
      query: {
        catalogItemId: selectedCatalogItemId,
        from: fromInstant,
        to: toInstant,
        page,
        size: 100,
      },
      enabled: Boolean(selectedCatalogItemId) && zoneKnown,
    },
  );

  const effectiveZone = search.data?.timezone || zone;
  const offers = useMemo(() => search.data?.offers ?? [], [search.data?.offers]);
  const exactMatch = search.data?.exactMatch ?? true;
  const totalPages = search.data?.totalPages ?? 1;

  // Auto-select first slot with offers on desktop when offers change
  useEffect(() => {
    if (offers.length > 0 && exactMatch) {
      const first = offers[0];
      if (first.startsAt) {
        const dayStr = dayInZone(first.startsAt, effectiveZone);
        const timeStr = formatInZone(first.startsAt, effectiveZone, 'time');
        const hourNum = parseInt(timeStr.split(':')[0], 10);

        const slotOffers = offers.filter((o) => {
          if (!o.startsAt) return false;
          const oDay = dayInZone(o.startsAt, effectiveZone);
          const oHour = parseInt(formatInZone(o.startsAt, effectiveZone, 'time').split(':')[0], 10);
          return oDay === dayStr && oHour === hourNum;
        });

        setSelectedSlot({
          day: dayStr,
          hour: hourNum,
          offers: slotOffers,
        });
      }
    } else {
      setSelectedSlot(null);
    }
  }, [offers, exactMatch, effectiveZone]);

  // Handlers
  const handleCatalogItemChange = (newId: string) => {
    setSelectedCatalogItemId(newId);
    setPage(0);
    if (newId) {
      setSearchParams({ catalogItemId: newId });
    } else {
      setSearchParams({});
    }
  };

  const handlePrevWeek = () => {
    setWeekOffset((prev) => prev - 1);
    setPage(0);
  };

  const handleNextWeek = () => {
    setWeekOffset((prev) => prev + 1);
    setPage(0);
  };

  const handleSelectOffer = (offer: Offer) => {
    navigate('/reservar', {
      state: {
        tutorId: offer.tutorId,
        catalogItemId: offer.catalogItemId ?? selectedCatalogItemId,
        start: offer.startsAt,
        hours: 1,
        tutorName: offer.tutorName,
        averageStars: offer.averageStars,
        ratingsCount: offer.ratingsCount,
        sessionsTaught: offer.sessionsTaught,
        newTutor: offer.newTutor,
        courseName: selectedCourse?.name,
        courseCode: selectedCourse?.courseCode,
      },
    });
  };

  const weekRangeText = formatWeekRange(mondayStr);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Buscar tutoría"
        description="Elige un curso y una semana. Verás cada hora libre de los tutores habilitados para ese curso."
      />

      {catalog.isError && (
        <ErrorState
          title="No pudimos cargar los cursos"
          error={catalog.error}
          onRetry={() => void catalog.refetch()}
          retrying={catalog.isFetching}
        />
      )}

      {/* Filters: Course dropdown & Week picker */}
      <SearchFilters
        catalogItems={catalogItems}
        selectedCatalogItemId={selectedCatalogItemId}
        onCatalogItemChange={handleCatalogItemChange}
        weekRangeText={weekRangeText}
        onPrevWeek={handlePrevWeek}
        onNextWeek={handleNextWeek}
        onSearchClick={() => search.refetch()}
      />

      {/* State 1: No course selected */}
      {!selectedCatalogItemId && (
        <div className="mt-8">
          <EmptyState
            title="Elige un curso para ver sus horas libres"
            description="Te mostramos los próximos 7 días; puedes cambiar el rango."
            icon={
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
                <IconTray className="h-6 w-6" />
              </div>
            }
          />
        </div>
      )}

      {/* State 2: Loading */}
      {selectedCatalogItemId && (search.isLoading || !zoneKnown) && (
        <div className="mt-6 space-y-4 rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
          <Skeleton className="h-6 w-48" />
          <Skeleton className="h-4 w-72" />
          <div className="grid grid-cols-2 gap-3 pt-4 sm:grid-cols-4 lg:grid-cols-7">
            {Array.from({ length: 7 }).map((_, i) => (
              <Skeleton key={i} className="h-36 w-full rounded-xl" />
            ))}
          </div>
        </div>
      )}

      {/* State 3: Error */}
      {selectedCatalogItemId && search.isError && (
        <div className="mt-8">
          <div
            role="alert"
            className="flex flex-col items-center gap-3 rounded-xl border border-gray-200 bg-white px-6 py-10 text-center shadow-sm"
          >
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-rose-500">
              <IconWifiOff className="h-6 w-6" />
            </div>
            <h2 className="text-base font-semibold text-gray-900">No pudimos cargar esto</h2>
            <p className="max-w-md text-sm text-gray-600">
              La plataforma no respondió. Lo que escribiste sigue aquí.
            </p>
            <div className="mt-2">
              <Button
                variant="secondary"
                onClick={() => search.refetch()}
                loading={search.isFetching}
              >
                Reintentar
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* State 4: Empty */}
      {selectedCatalogItemId && !search.isLoading && !search.isError && offers.length === 0 && (
        <div className="mt-8">
          <EmptyState
            title="No hay horas libres para este curso"
            description="Prueba con otro rango de fechas."
            icon={
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
                <IconTray className="h-6 w-6" />
              </div>
            }
            action={
              <Button variant="secondary" onClick={handleNextWeek}>
                Ver la semana siguiente
              </Button>
            }
          />
        </div>
      )}

      {/* State 5: Results (Exact or closest hours) */}
      {selectedCatalogItemId && !search.isLoading && !search.isError && offers.length > 0 && (
        <div>
          {/* If exactMatch === false: show notice and list on all screens */}
          {!exactMatch ? (
            <SearchDayList
              offers={offers}
              exactMatch={false}
              zone={effectiveZone}
              page={page}
              totalPages={totalPages}
              onPageChange={setPage}
              onSelectOffer={handleSelectOffer}
            />
          ) : (
            <>
              {/* Desktop view: Weekly Grid + Tutor Drawer */}
              <div className="hidden lg:flex lg:items-start lg:gap-5">
                <SearchGrid
                  courseName={selectedCourse?.name ?? 'Curso'}
                  mondayStr={mondayStr}
                  offers={offers}
                  myBookings={myBookings}
                  zone={effectiveZone}
                  selectedSlot={selectedSlot}
                  onSelectSlot={setSelectedSlot}
                />

                {selectedSlot && selectedSlot.offers.length > 0 && (
                  <TutorDrawer
                    day={selectedSlot.day}
                    hour={selectedSlot.hour}
                    offers={selectedSlot.offers}
                    allOffers={offers}
                    zone={effectiveZone}
                    onClose={() => setSelectedSlot(null)}
                    onSelectOffer={handleSelectOffer}
                  />
                )}
              </div>

              {/* Mobile view: List grouped by day + Pagination */}
              <div className="lg:hidden space-y-4">
                <div className="flex items-center gap-1.5 text-xs text-gray-500 px-1">
                  <IconClock className="h-4 w-4 text-gray-400" />
                  <span>Hora de Lima · 1 hora = 1 crédito</span>
                </div>

                <SearchDayList
                  offers={offers}
                  exactMatch={true}
                  zone={effectiveZone}
                  page={page}
                  totalPages={totalPages}
                  onPageChange={setPage}
                  onSelectOffer={handleSelectOffer}
                />
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
