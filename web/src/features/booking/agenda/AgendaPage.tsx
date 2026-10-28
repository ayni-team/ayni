import {
  useState,
} from 'react';
import {
  useNavigate,
} from 'react-router-dom';
import {
  api,
  errorBody,
  isApiError,
  useApiMutation,
  useApiQuery,
  type Schema,
} from '@/shared/api';
import {
  addDays,
  formatDay,
  formatInZone,
  todayInZone,
  useZone,
} from '@/shared/time';
import {
  Avatar,
  Badge,
  Button,
  ButtonLink,
  Dialog,
  EmptyState,
  ErrorState,
  IconCalendar,
  IconChevronLeft,
  IconChevronRight,
  PageHeader,
  Pagination,
  Skeleton,
  Tabs,
  pageCount,
  useToast,
} from '@/shared/ui';

type AgendaLine =
    Schema<'TutorAgendaLine'>;

type TutorBooking =
    Schema<'TutorBooking'>;

type LateCancellation =
    Schema<'LateCancellationConfirmation'>;

type View =
    'week' |
    'history';

interface Period {
  from: string;
  to: string;
}

interface LateWarning {
  bookingId: string;
  studentName: string;
  warning: LateCancellation;
}

const AGENDA_ROOT =
    [
      'booking',
      'tutor-agenda',
    ] as const;

const HISTORY_ROOT =
    [
      'booking',
      'tutor-history',
    ] as const;

const HISTORY_PAGE_SIZE =
    4;

const TABS = [
  {
    value: 'week',
    label: 'Semana',
  },
  {
    value: 'history',
    label: 'Historial',
  },
] as const;

function agendaKey(
    period: Period | null,
) {
  return [
    'booking',
    'tutor-agenda',
    period ?? {
      defaultWeek: true,
    },
  ] as const;
}

function historyKey(
    page: number,
) {
  return [
    'booking',
    'tutor-history',
    {
      page,
      size:
      HISTORY_PAGE_SIZE,
    },
  ] as const;
}

function capitalize(
    value: string,
): string {
  if (!value) {
    return value;
  }

  return (
      value.charAt(0)
          .toLocaleUpperCase(
              'es-PE',
          ) +
      value.slice(1)
  );
}

function shortPeriodDate(
    day: string,
): string {
  return formatDay(
      day,
      {
        day: 'numeric',
        month: 'short',
      },
  ).replaceAll(
      '.',
      '',
  );
}

function periodLabel(
    from?: string,
    to?: string,
): string {
  if (
      !from ||
      !to
  ) {
    return 'Cargando semana…';
  }

  return `${shortPeriodDate(
      from,
  )} – ${shortPeriodDate(
      to,
  )}`;
}

function dayLabel(
    day: string,
    timezone: string,
): string {
  const formatted =
      formatDay(
          day,
          'longDate',
      ).replace(
          ',',
          '',
      );

  if (
      day ===
      todayInZone(
          timezone,
      )
  ) {
    return `Hoy · ${formatted}`;
  }

  return capitalize(
      formatted,
  );
}

function localTime(
    value?: string | null,
): string {
  if (!value) {
    return '—';
  }

  return value.slice(
      0,
      5,
  );
}

function studentName(
    booking?: TutorBooking,
): string {
  return (
      booking?.student
          ?.name ??
      'Estudiante'
  );
}

function courseName(
    booking?: TutorBooking,
): string {
  return (
      booking
          ?.catalogItem
          ?.name ??
      'Tutoría'
  );
}

function absenceLabel(
    count: number,
): string {
  return count === 1
      ? '1 inasistencia'
      : `${count} inasistencias`;
}

function groupLines(
    lines: AgendaLine[],
): Array<{
  date: string;
  lines: AgendaLine[];
}> {
  const groups =
      new Map<
          string,
          AgendaLine[]
      >();

  for (
      const line of lines
      ) {
    const date =
        line.date ??
        'sin-fecha';

    const current =
        groups.get(date) ??
        [];

    current.push(line);

    groups.set(
        date,
        current,
    );
  }

  return Array.from(
      groups.entries(),
  ).map(
      ([
         date,
         grouped,
       ]) => ({
        date,
        lines:
        grouped,
      }),
  );
}

function historyStatus(
    status:
    TutorBooking['status'],
): {
  label: string;
  tone:
      | 'neutral'
      | 'brand'
      | 'warning'
      | 'danger';
} {
  switch (status) {
    case 'COMPLETED':
      return {
        label:
            'Completada',
        tone:
            'neutral',
      };

    case 'NO_SHOW':
      return {
        label:
            'No asistió',
        tone:
            'warning',
      };

    case 'CANCELLED':
      return {
        label:
            'Cancelada',
        tone:
            'danger',
      };

    case 'CONFIRMED':
      return {
        label:
            'Confirmada',
        tone:
            'brand',
      };

    default:
      return {
        label:
            'Tutoría',
        tone:
            'neutral',
      };
  }
}

function historyDate(
    booking: TutorBooking,
    timezone: string,
): string {
  if (
      !booking.startsAt
  ) {
    return 'Fecha no disponible';
  }

  const date =
      formatInZone(
          booking.startsAt,
          timezone,
          {
            weekday: 'short',
            day: 'numeric',
            month: 'short',
          },
      ).replaceAll(
          '.',
          '',
      );

  const start =
      formatInZone(
          booking.startsAt,
          timezone,
          'time',
      );

  const end =
      booking.endsAt
          ? formatInZone(
              booking.endsAt,
              timezone,
              'time',
          )
          : '—';

  return `${capitalize(
      date,
  )} · ${start} – ${end}`;
}

function WeekLoading() {
  return (
      <div className="flex flex-col gap-4">
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-20 w-full" />
      </div>
  );
}

function FreeLine({
                    line,
                  }: {
  line: AgendaLine;
}) {
  const start =
      localTime(
          line.startTime,
      );

  const end =
      localTime(
          line.endTime,
      );

  return (
      <article
          aria-label={`Hora libre ${start} a ${end}`}
          className="grid grid-cols-[54px_1fr] gap-3 rounded-xl border border-dashed border-gray-200 px-4 py-3 text-gray-600 sm:grid-cols-[70px_1fr]"
      >
        <div>
          <p className="text-sm font-semibold text-gray-900">
            {start}
          </p>

          <p className="text-xs text-gray-500">
            {end}
          </p>
        </div>

        <div className="flex items-center">
          <p className="text-sm">
            Hora libre · visible en Buscar
          </p>
        </div>
      </article>
  );
}

function BookedLine({
                      line,
                      joining,
                      cancelling,
                      onJoin,
                      onCancel,
                    }: {
  line: AgendaLine;
  joining: boolean;
  cancelling: boolean;
  onJoin: (
      booking: TutorBooking,
  ) => void;
  onCancel: (
      booking: TutorBooking,
  ) => void;
}) {
  const booking =
      line.booking;

  if (!booking) {
    return null;
  }

  const name =
      studentName(
          booking,
      );

  const course =
      courseName(
          booking,
      );

  const start =
      localTime(
          line.startTime,
      );

  const end =
      localTime(
          line.endTime,
      );

  const absenceCount =
      booking.student
          ?.absenceCount ??
      0;

  const joinable =
      booking.joinable ===
      true;

  return (
      <article
          aria-label={`Tutoría con ${name} a las ${start}`}
          className={
            joinable
                ? 'grid gap-3 rounded-xl border border-brand px-4 py-3 sm:grid-cols-[70px_40px_minmax(0,1fr)_auto] sm:items-center'
                : 'grid gap-3 rounded-xl border border-gray-200 bg-white px-4 py-3 sm:grid-cols-[70px_40px_minmax(0,1fr)_auto] sm:items-center'
          }
      >
        <div>
          <p className="text-sm font-semibold text-gray-900">
            {start}
          </p>

          <p className="text-xs text-gray-500">
            {end}
          </p>
        </div>

        <Avatar
            name={name}
            size="sm"
            decorative
        />

        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm font-semibold text-gray-900">
              {name}
            </p>

            {joinable && (
                <Badge tone="danger">
                  Ahora
                </Badge>
            )}

            {absenceCount >
                0 && (
                    <Badge tone="warning">
                      {absenceLabel(
                          absenceCount,
                      )}
                    </Badge>
                )}
          </div>

          <p className="mt-1 text-sm text-gray-700">
            {course}
          </p>

          {booking.needDescription && (
              <p className="mt-1 text-xs text-gray-500">
                “
                {
                  booking.needDescription
                }
                ”
              </p>
          )}
        </div>

        <div className="flex justify-start sm:justify-end">
          {joinable &&
          booking.bookingId ? (
              <Button
                  loading={joining}
                  onClick={() =>
                      onJoin(
                          booking,
                      )
                  }
              >
                Entrar
              </Button>
          ) : booking.status ===
          'CONFIRMED' &&
          booking.bookingId ? (
              <Button
                  variant="danger"
                  loading={cancelling}
                  onClick={() =>
                      onCancel(
                          booking,
                      )
                  }
              >
                Cancelar
              </Button>
          ) : null}
        </div>
      </article>
  );
}

function AgendaDays({
                      lines,
                      timezone,
                      joiningId,
                      cancellingId,
                      onJoin,
                      onCancel,
                    }: {
  lines: AgendaLine[];
  timezone: string;
  joiningId: string | null;
  cancellingId: string | null;
  onJoin: (
      booking: TutorBooking,
  ) => void;
  onCancel: (
      booking: TutorBooking,
  ) => void;
}) {
  const groups =
      groupLines(
          lines,
      );

  return (
      <div className="flex flex-col gap-6">
        {groups.map(
            (group) => (
                <section
                    key={
                      group.date
                    }
                    aria-label={
                      group.date ===
                      'sin-fecha'
                          ? 'Horario'
                          : dayLabel(
                              group.date,
                              timezone,
                          )
                    }
                    className="flex flex-col gap-2"
                >
                  <h2 className="text-sm font-medium text-gray-600">
                    {group.date ===
                    'sin-fecha'
                        ? 'Horario'
                        : dayLabel(
                            group.date,
                            timezone,
                        )}
                  </h2>

                  {group.lines.map(
                      (
                          line,
                          index,
                      ) => {
                        const bookingId =
                            line.booking
                                ?.bookingId;

                        const key =
                            bookingId ??
                            `${group.date}-${line.startTime}-${index}`;

                        if (
                            line.kind ===
                            'FREE'
                        ) {
                          return (
                              <FreeLine
                                  key={key}
                                  line={line}
                              />
                          );
                        }

                        return (
                            <BookedLine
                                key={key}
                                line={line}
                                joining={
                                  Boolean(
                                      bookingId &&
                                      joiningId ===
                                      bookingId,
                                  )
                                }
                                cancelling={
                                  Boolean(
                                      bookingId &&
                                      cancellingId ===
                                      bookingId,
                                  )
                                }
                                onJoin={
                                  onJoin
                                }
                                onCancel={
                                  onCancel
                                }
                            />
                        );
                      },
                  )}
                </section>
            ),
        )}
      </div>
  );
}

function HistoryRow({
                      booking,
                      timezone,
                    }: {
  booking: TutorBooking;
  timezone: string;
}) {
  const name =
      studentName(
          booking,
      );

  const course =
      courseName(
          booking,
      );

  const status =
      historyStatus(
          booking.status,
      );

  return (
      <article className="flex flex-col gap-3 rounded-xl border border-gray-200 bg-white px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 items-start gap-3">
          <Avatar
              name={name}
              size="sm"
              decorative
          />

          <div className="min-w-0">
            <p className="text-sm font-semibold text-gray-900">
              {name} · {course}
            </p>

            <p className="mt-1 text-xs text-gray-500">
              {historyDate(
                  booking,
                  timezone,
              )}
            </p>

            {booking.needDescription && (
                <p className="mt-1 text-xs text-gray-500">
                  {
                    booking.needDescription
                  }
                </p>
            )}
          </div>
        </div>

        <Badge
            tone={
              status.tone
            }
        >
          {status.label}
        </Badge>
      </article>
  );
}

export default function AgendaPage() {
  const navigate =
      useNavigate();

  // The university's zone, for whatever the answers do not carry their own.
  const universityZone =
      useZone();

  const toaster =
      useToast();

  const [
    view,
    setView,
  ] = useState<View>(
      'week',
  );

  const [
    period,
    setPeriod,
  ] = useState<
      Period | null
  >(null);

  const [
    historyPage,
    setHistoryPage,
  ] = useState(0);

  const [
    joiningId,
    setJoiningId,
  ] = useState<
      string | null
  >(null);

  const [
    cancellingId,
    setCancellingId,
  ] = useState<
      string | null
  >(null);

  const [
    lateWarning,
    setLateWarning,
  ] = useState<
      LateWarning | null
  >(null);

  const agenda =
      useApiQuery(
          agendaKey(
              period,
          ),
          '/api/v1/tutor/agenda',
          {
            enabled:
                view ===
                'week',

            query:
                period
                    ? {
                      from:
                      period.from,
                      to:
                      period.to,
                    }
                    : {},
          },
      );

  const history =
      useApiQuery(
          historyKey(
              historyPage,
          ),
          '/api/v1/tutor/agenda/history',
          {
            enabled:
                view ===
                'history',

            query: {
              page:
              historyPage,
              size:
              HISTORY_PAGE_SIZE,
            },
          },
      );

  const cancelBooking =
      useApiMutation(
          'delete',
          '/api/v1/bookings/{id}',
          {
            invalidates: [
              AGENDA_ROOT,
              HISTORY_ROOT,
            ],

            onSuccess: () => {
              setCancellingId(
                  null,
              );

              setLateWarning(
                  null,
              );

              toaster.success(
                  'Tutoría cancelada.',
              );
            },

            onError: (
                error,
                variables,
            ) => {
              const id =
                  variables.path.id;

              setCancellingId(
                  null,
              );

              if (
                  isApiError(
                      error,
                      409,
                  )
              ) {
                const warning =
                    errorBody<LateCancellation>(
                        error,
                    );

                if (
                    warning
                        ?.confirmationRequired &&
                    variables.query
                        ?.confirmLate !==
                    true
                ) {
                  const booking =
                      agenda.data
                          ?.lines
                          ?.find(
                              (line) =>
                                  line.booking
                                      ?.bookingId ===
                                  id,
                          )
                          ?.booking;

                  setLateWarning({
                    bookingId:
                    id,

                    studentName:
                        studentName(
                            booking,
                        ),

                    warning,
                  });

                  return;
                }

                // Also after confirmLate=true: the warning no longer applies, so it closes.
                setLateWarning(
                    null,
                );

                toaster.error(
                    'Esta tutoría ya empezó o ya no puede cancelarse.',
                );

                void agenda.refetch();

                return;
              }

              setLateWarning(
                  null,
              );

              toaster.error(
                  'No pudimos cancelar la tutoría. Inténtalo de nuevo.',
              );
            },
          },
      );

  const displayFrom =
      period?.from ??
      agenda.data?.from;

  const displayTo =
      period?.to ??
      agenda.data?.to;

  const timezone =
      agenda.data
          ?.timezone ??
      history.data
          ?.timezone ??
      universityZone;

  function moveWeek(
      weeks: number,
  ) {
    if (
        !displayFrom ||
        !displayTo
    ) {
      return;
    }

    setPeriod({
      from:
          addDays(
              displayFrom,
              weeks * 7,
          ),

      to:
          addDays(
              displayTo,
              weeks * 7,
          ),
    });
  }

  async function joinBooking(
      booking: TutorBooking,
  ) {
    const bookingId =
        booking.bookingId;

    if (!bookingId) {
      return;
    }

    setJoiningId(
        bookingId,
    );

    try {
      const session =
          await api.get(
              '/api/v1/sessions',
              {
                query: {
                  bookingId,
                },
              },
          );

      if (
          !session.sessionId
      ) {
        toaster.error(
            'No encontramos la sesión de esta tutoría.',
        );

        return;
      }

      navigate(
          `/sesion/${session.sessionId}`,
      );
    } catch {
      toaster.error(
          'No pudimos abrir la sesión. Inténtalo de nuevo.',
      );
    } finally {
      setJoiningId(
          null,
      );
    }
  }

  function requestCancellation(
      booking: TutorBooking,
  ) {
    const id =
        booking.bookingId;

    if (!id) {
      return;
    }

    setLateWarning(
        null,
    );

    setCancellingId(
        id,
    );

    cancelBooking.reset();

    cancelBooking.mutate({
      path: {
        id,
      },
    });
  }

  function confirmLateCancellation() {
    if (!lateWarning) {
      return;
    }

    setCancellingId(
        lateWarning.bookingId,
    );

    cancelBooking.mutate({
      path: {
        id:
        lateWarning.bookingId,
      },

      query: {
        confirmLate:
            true,
      },
    });
  }

  const lateDescription =
      lateWarning
          ? `${
              lateWarning.warning
                  .incidentWillBeRecorded
                  ? `Si cancelas la tutoría con ${lateWarning.studentName}, quedará registrada como una incidencia tuya.`
                  : `Si cancelas la tutoría con ${lateWarning.studentName}, la cancelación se realizará de inmediato.`
          } ${
              lateWarning.warning
                  .refundWillBeIssued
                  ? `${lateWarning.studentName} recupera su crédito.`
                  : ''
          }`
          : '';

  const historyItems =
      history.data
          ?.items ?? [];

  const historyTotal =
      history.data
          ?.total ?? 0;

  const historySize =
      history.data
          ?.size ??
      HISTORY_PAGE_SIZE;

  const totalHistoryPages =
      pageCount(
          historyTotal,
          historySize,
      );

  return (
      <div className="mx-auto w-full max-w-6xl">
        <PageHeader
            title="Agenda"
            description="Tus horas como tutor, reservadas y libres. Hora de la universidad."
            actions={
              <ButtonLink
                  to="/tutor/disponibilidad"
                  variant="secondary"
              >
                Disponibilidad
              </ButtonLink>
            }
        />

        <Tabs
            label="Vista de agenda"
            items={TABS}
            value={view}
            onChange={(
                next,
            ) => {
              setView(
                  next,
              );
            }}
        >
          {view ===
          'week' ? (
              <div className="flex flex-col gap-4">
                <div className="flex w-full max-w-xs items-center justify-between rounded-lg border border-gray-200 bg-white">
                  <Button
                      aria-label="Semana anterior"
                      variant="ghost"
                      size="sm"
                      icon={
                        <IconChevronLeft className="h-4 w-4" />
                      }
                      disabled={
                        !displayFrom
                      }
                      onClick={() =>
                          moveWeek(
                              -1,
                          )
                      }
                  />

                  <p className="px-3 text-sm font-medium text-gray-800">
                    {periodLabel(
                        displayFrom,
                        displayTo,
                    )}
                  </p>

                  <Button
                      aria-label="Semana siguiente"
                      variant="ghost"
                      size="sm"
                      icon={
                        <IconChevronRight className="h-4 w-4" />
                      }
                      disabled={
                        !displayTo
                      }
                      onClick={() =>
                          moveWeek(
                              1,
                          )
                      }
                  />
                </div>

                {agenda.isPending ? (
                    <WeekLoading />
                ) : agenda.isError ? (
                    <ErrorState
                        title="No pudimos cargar esto"
                        description="La plataforma no respondió. Vuelve a intentarlo."
                        onRetry={() =>
                            void agenda.refetch()
                        }
                        retrying={
                          agenda.isFetching
                        }
                    />
                ) : (
                    <>
                      {(
                          agenda.data
                              ?.lines ?? []
                      ).length ===
                      0 ? (
                          <EmptyState
                              icon={
                                <IconCalendar className="h-8 w-8" />
                              }
                              title="No tienes horas en estas fechas"
                              description="Declara tu disponibilidad para que los estudiantes te encuentren."
                              action={
                                <ButtonLink to="/tutor/disponibilidad">
                                  Declarar disponibilidad
                                </ButtonLink>
                              }
                          />
                      ) : (
                          <AgendaDays
                              lines={
                                  agenda.data
                                      ?.lines ??
                                  []
                              }
                              timezone={
                                  agenda.data
                                      ?.timezone ??
                                  universityZone
                              }
                              joiningId={
                                joiningId
                              }
                              cancellingId={
                                cancellingId
                              }
                              onJoin={
                                joinBooking
                              }
                              onCancel={
                                requestCancellation
                              }
                          />
                      )}
                    </>
                )}
              </div>
          ) : (
              <div className="flex flex-col gap-4">
                {history.isPending ? (
                    <WeekLoading />
                ) : history.isError ? (
                    <ErrorState
                        title="No pudimos cargar el historial"
                        description="Vuelve a intentarlo."
                        onRetry={() =>
                            void history.refetch()
                        }
                        retrying={
                          history.isFetching
                        }
                    />
                ) : historyItems.length ===
                0 ? (
                    <EmptyState
                        icon={
                          <IconCalendar className="h-8 w-8" />
                        }
                        title="Aún no tienes tutorías en el historial"
                        description="Cuando una tutoría termine, se cancele o alguien no asista, aparecerá aquí."
                    />
                ) : (
                    <>
                      <div className="flex flex-col gap-3">
                        {historyItems.map(
                            (
                                booking,
                                index,
                            ) => (
                                <HistoryRow
                                    key={
                                        booking.bookingId ??
                                        `${booking.startsAt}-${index}`
                                    }
                                    booking={
                                      booking
                                    }
                                    timezone={
                                        history.data
                                            ?.timezone ??
                                        timezone
                                    }
                                />
                            ),
                        )}
                      </div>

                      <Pagination
                          page={
                            historyPage
                          }
                          totalPages={
                            totalHistoryPages
                          }
                          onPageChange={
                            setHistoryPage
                          }
                          label="Páginas del historial"
                      />
                    </>
                )}
              </div>
          )}
        </Tabs>

        <Dialog
            open={
                lateWarning !==
                null
            }
            onClose={() => {
              if (
                  !cancelBooking.isPending
              ) {
                setLateWarning(
                    null,
                );
              }
            }}
            dismissible={
              !cancelBooking.isPending
            }
            title="Faltan menos de 12 horas"
            description={
              lateDescription
            }
            footer={
              <>
                <Button
                    variant="danger"
                    loading={
                      cancelBooking.isPending
                    }
                    onClick={
                      confirmLateCancellation
                    }
                >
                  Cancelar igual
                </Button>

                <Button
                    variant="secondary"
                    disabled={
                      cancelBooking.isPending
                    }
                    onClick={() =>
                        setLateWarning(
                            null,
                        )
                    }
                >
                  No, mantenerla
                </Button>
              </>
            }
        />
      </div>
  );
}