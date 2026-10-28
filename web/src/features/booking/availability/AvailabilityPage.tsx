import {
  useState,
  type FormEvent,
} from 'react';
import {
  isApiError,
  useApiMutation,
  useApiQuery,
  type Schema,
} from '@/shared/api';
import {
  Button,
  Card,
  Field,
  IconInfo,
  PageHeader,
  PageLoading,
  Select,
  Skeleton,
  TextField,
  useToast,
} from '@/shared/ui';

type WeeklyRange =
    Schema<'TutorAvailabilityWeeklyRange'>;

type DayOfWeek =
    NonNullable<
        WeeklyRange['dayOfWeek']
    >;

type ExceptionKind =
    Schema<'AvailabilityExceptionRequest'>['kind'];

type ApiLocalTime =
    Schema<'LocalTime'>;

const AVAILABILITY_KEY =
    [
      'booking',
      'tutor-availability',
    ] as const;

const CURRENT_PAUSE_KEY =
    [
      'booking',
      'current-availability-pause',
    ] as const;

const DAY_OPTIONS = [
  {
    value: 'MONDAY',
    label: 'Lunes',
  },
  {
    value: 'TUESDAY',
    label: 'Martes',
  },
  {
    value: 'WEDNESDAY',
    label: 'Miércoles',
  },
  {
    value: 'THURSDAY',
    label: 'Jueves',
  },
  {
    value: 'FRIDAY',
    label: 'Viernes',
  },
  {
    value: 'SATURDAY',
    label: 'Sábado',
  },
  {
    value: 'SUNDAY',
    label: 'Domingo',
  },
] as const;

const DAY_LABELS:
    Record<
        DayOfWeek,
        string
    > = {
  MONDAY: 'Lunes',
  TUESDAY: 'Martes',
  WEDNESDAY: 'Miércoles',
  THURSDAY: 'Jueves',
  FRIDAY: 'Viernes',
  SATURDAY: 'Sábado',
  SUNDAY: 'Domingo',
};

const EXCEPTION_OPTIONS = [
  {
    value: 'REMOVE',
    label: 'Quitar horas',
  },
  {
    value: 'ADD',
    label: 'Agregar horas',
  },
] as const;

function apiTime(
    value: string,
): ApiLocalTime {
  const normalized =
      value.length === 5
          ? `${value}:00`
          : value;

  return normalized as unknown as ApiLocalTime;
}

function inputDate(
    offsetDays = 0,
): string {
  const date =
      new Date();

  date.setHours(
      12,
      0,
      0,
      0,
  );

  date.setDate(
      date.getDate() +
      offsetDays,
  );

  const year =
      date.getFullYear();

  const month =
      String(
          date.getMonth() +
          1,
      ).padStart(
          2,
          '0',
      );

  const day =
      String(
          date.getDate(),
      ).padStart(
          2,
          '0',
      );

  return `${year}-${month}-${day}`;
}

function weekdayFor(
    dateValue: string,
): DayOfWeek {
  const date =
      new Date(
          `${dateValue}T12:00:00`,
      );

  const days:
      DayOfWeek[] = [
    'SUNDAY',
    'MONDAY',
    'TUESDAY',
    'WEDNESDAY',
    'THURSDAY',
    'FRIDAY',
    'SATURDAY',
  ];

  return (
      days[
          date.getDay()
          ] ?? 'MONDAY'
  );
}

function formatTime(
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

function dateObject(
    value: string,
): Date {
  return new Date(
      `${value}T12:00:00Z`,
  );
}

function formatShortDate(
    value?: string | null,
): string {
  if (!value) {
    return '—';
  }

  return new Intl.DateTimeFormat(
      'es-PE',
      {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        timeZone: 'UTC',
      },
  ).format(
      dateObject(value),
  );
}

function formatLongDate(
    value?: string | null,
): string {
  if (!value) {
    return '—';
  }

  return new Intl.DateTimeFormat(
      'es-PE',
      {
        day: 'numeric',
        month: 'long',
        timeZone: 'UTC',
      },
  ).format(
      dateObject(value),
  );
}

function hoursText(
    value: number,
): string {
  return `${value} ${
      value === 1
          ? 'hora'
          : 'horas'
  }`;
}

function bookedText(
    value: number,
): string {
  return value === 1
      ? '1 ya reservada'
      : `${value} ya reservadas`;
}

function zoneText(
    timezone?: string,
): string {
  if (!timezone) {
    return '';
  }

  if (
      timezone ===
      'America/Lima'
  ) {
    return ' Hora de Lima.';
  }

  return ` Zona horaria: ${timezone}.`;
}

function WeeklyRangeList({
                           ranges,
                           loading,
                           error,
                           retrying,
                           onRetry,
                           removingId,
                           onRemove,
                         }: {
  ranges: WeeklyRange[];
  loading: boolean;
  error: boolean;
  retrying: boolean;
  onRetry: () => void;
  removingId: string | null;
  onRemove: (
      id: string,
  ) => void;
}) {
  if (loading) {
    return (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
        </div>
    );
  }

  if (error) {
    return (
        <div
            role="alert"
            className="flex flex-col gap-3 rounded-lg bg-gray-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
        >
          <div className="flex items-start gap-2">
            <IconInfo className="mt-0.5 h-4 w-4 shrink-0 text-gray-600" />

            <div>
              <p className="text-sm font-medium text-gray-900">
                No pudimos mostrar lo que ya declaraste
              </p>

              <p className="mt-1 text-xs text-gray-600">
                Puedes declarar horarios nuevos mientras vuelves a cargar la lista.
              </p>
            </div>
          </div>

          <Button
              variant="secondary"
              size="sm"
              loading={retrying}
              onClick={onRetry}
          >
            Reintentar lista
          </Button>
        </div>
    );
  }

  if (
      ranges.length ===
      0
  ) {
    return (
        <div className="rounded-lg border border-dashed border-gray-300 px-4 py-6 text-center">
          <p className="text-sm font-medium text-gray-900">
            Aún no declaraste horarios semanales
          </p>

          <p className="mt-1 text-xs text-gray-600">
            Agrega un rango y aparecerá aquí también cuando vuelvas a entrar.
          </p>
        </div>
    );
  }

  return (
      <div className="overflow-hidden rounded-xl border border-gray-200">
        {ranges.map(
            (
                range,
                index,
            ) => {
              const id =
                  range.id;

              const day =
                  range.dayOfWeek
                      ? DAY_LABELS[
                          range.dayOfWeek
                          ]
                      : 'Día';

              return (
                  <div
                      key={
                          id ??
                          `${range.dayOfWeek}-${range.startsAtTime}-${range.validFrom}`
                      }
                      className={
                        index === 0
                            ? 'flex items-center justify-between gap-4 px-4 py-3'
                            : 'flex items-center justify-between gap-4 border-t border-gray-200 px-4 py-3'
                      }
                  >
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-gray-900">
                        {day} ·{' '}
                        {formatTime(
                            range.startsAtTime,
                        )}{' '}
                        –{' '}
                        {formatTime(
                            range.endsAtTime,
                        )}
                      </p>

                      <p className="mt-0.5 text-xs text-gray-500">
                        Vigente desde el{' '}
                        {formatShortDate(
                            range.validFrom,
                        )}
                        {range.validUntil
                            ? ` hasta el ${formatShortDate(
                                range.validUntil,
                            )}`
                            : ''}
                      </p>
                    </div>

                    {id && (
                        <Button
                            variant="ghost"
                            size="sm"
                            loading={
                                removingId ===
                                id
                            }
                            className="shrink-0 text-red-700 hover:bg-red-50 active:bg-red-100"
                            onClick={() =>
                                onRemove(id)
                            }
                        >
                          Quitar
                        </Button>
                    )}
                  </div>
              );
            },
        )}
      </div>
  );
}

function HelpfulInformation() {
  return (
      <Card title="Lo que conviene saber">
        <div className="flex flex-col gap-4 text-sm text-gray-600">
          <div className="flex items-start gap-2">
            <IconInfo className="mt-0.5 h-4 w-4 shrink-0" />

            <p>
              Quitar un rango o pausar nunca cancela tutorías ya reservadas.
            </p>
          </div>

          <div className="flex items-start gap-2">
            <IconInfo className="mt-0.5 h-4 w-4 shrink-0" />

            <p>
              Te diremos cuántas horas se generaron, cuántas se retiraron y cuántas reservadas se mantuvieron.
            </p>
          </div>
        </div>
      </Card>
  );
}

export default function AvailabilityPage() {
  const toaster =
      useToast();

  const availability =
      useApiQuery(
          AVAILABILITY_KEY,
          '/api/v1/tutor/availability',
      );

  const currentPause =
      useApiQuery(
          CURRENT_PAUSE_KEY,
          '/api/v1/tutor/availability/pauses/current',
      );

  const initialValidFrom =
      inputDate(1);

  const [
    range,
    setRange,
  ] = useState(() => ({
    dayOfWeek:
        weekdayFor(
            initialValidFrom,
        ),
    startsAtTime:
        '15:00',
    endsAtTime:
        '17:00',
    validFrom:
    initialValidFrom,
  }));

  const [
    rangeEndError,
    setRangeEndError,
  ] = useState<
      string | undefined
  >();

  const [
    rangeRequestError,
    setRangeRequestError,
  ] = useState<
      string | undefined
  >();

  const [
    removingId,
    setRemovingId,
  ] = useState<
      string | null
  >(null);

  const [
    exception,
    setException,
  ] = useState<{
    exceptionDate: string;
    kind: ExceptionKind;
    startsAtTime: string;
    endsAtTime: string;
  }>(() => ({
    exceptionDate:
        inputDate(5),
    kind: 'REMOVE',
    startsAtTime:
        '16:00',
    endsAtTime:
        '19:00',
  }));

  const [
    exceptionTimeError,
    setExceptionTimeError,
  ] = useState<
      string | undefined
  >();

  const [
    exceptionRequestError,
    setExceptionRequestError,
  ] = useState<
      string | undefined
  >();

  const [
    pause,
    setPause,
  ] = useState(
      () => ({
        startsOn:
            inputDate(1),
        endsOn:
            inputDate(8),
      }),
  );

  const [
    pauseDateError,
    setPauseDateError,
  ] = useState<
      string | undefined
  >();

  const [
    pauseRequestError,
    setPauseRequestError,
  ] = useState<
      string | undefined
  >();

  const declareRange =
      useApiMutation(
          'post',
          '/api/v1/tutor/availability',
          {
            invalidates: [
              AVAILABILITY_KEY,
            ],

            onSuccess: (
                answer,
            ) => {
              setRangeEndError(
                  undefined,
              );

              setRangeRequestError(
                  undefined,
              );

              const generated =
                  answer.generatedHours ??
                  0;

              if (
                  generated === 0 &&
                  answer.notice
              ) {
                toaster.info(
                    'Guardamos tu horario, pero todavía no aparecerá en Buscar porque no tienes una habilidad habilitada.',
                );

                return;
              }

              toaster.success(
                  `Agregamos tu horario: se generaron ${hoursText(
                      generated,
                  )} libres.`,
              );
            },

            onError: (
                error,
            ) => {
              if (
                  isApiError(
                      error,
                      400,
                  )
              ) {
                setRangeRequestError(
                    'No pudimos guardar el rango. Revisa que no se superponga con otro horario declarado.',
                );

                return;
              }

              toaster.error(
                  'No pudimos agregar el horario. Inténtalo de nuevo.',
              );
            },
          },
      );

  const removeRange =
      useApiMutation(
          'delete',
          '/api/v1/tutor/availability/{id}',
          {
            invalidates: [
              AVAILABILITY_KEY,
            ],

            onSuccess: (
                answer,
            ) => {
              setRemovingId(
                  null,
              );

              const withdrawn =
                  answer.withdrawnHours ??
                  0;

              const booked =
                  answer.bookedHoursKept ??
                  0;

              toaster.success(
                  `Quitamos el rango: se retiraron ${hoursText(
                      withdrawn,
                  )} y se mantuvieron ${bookedText(
                      booked,
                  )}.`,
              );
            },

            onError: () => {
              setRemovingId(
                  null,
              );

              toaster.error(
                  'No pudimos quitar ese rango. Actualiza la lista e inténtalo de nuevo.',
              );
            },
          },
      );

  const saveException =
      useApiMutation(
          'post',
          '/api/v1/tutor/availability/exceptions',
          {
            invalidates: [
              AVAILABILITY_KEY,
            ],

            onSuccess: (
                answer,
                variables,
            ) => {
              setExceptionTimeError(
                  undefined,
              );

              setExceptionRequestError(
                  undefined,
              );

              const kind =
                  variables.body.kind;

              if (
                  kind === 'ADD'
              ) {
                const generated =
                    answer.generatedHours ??
                    0;

                if (
                    generated === 0 &&
                    answer.notice
                ) {
                  toaster.info(
                      'Guardamos las horas extra, pero todavía no aparecerán en Buscar porque no tienes una habilidad habilitada.',
                  );

                  return;
                }

                toaster.success(
                    `Guardamos la excepción: se generaron ${hoursText(
                        generated,
                    )} libres.`,
                );

                return;
              }

              toaster.success(
                  `Guardamos la excepción: se retiraron ${hoursText(
                      answer.withdrawnHours ??
                      0,
                  )} y se mantuvieron ${bookedText(
                      answer.bookedHoursKept ??
                      0,
                  )}.`,
              );
            },

            onError: (
                error,
            ) => {
              if (
                  isApiError(
                      error,
                      400,
                  )
              ) {
                setExceptionRequestError(
                    'Revisa la fecha y las horas de la excepción e inténtalo de nuevo.',
                );

                return;
              }

              toaster.error(
                  'No pudimos guardar la excepción. Inténtalo de nuevo.',
              );
            },
          },
      );

  const createPause =
      useApiMutation(
          'post',
          '/api/v1/tutor/availability/pauses',
          {
            invalidates: [
              AVAILABILITY_KEY,
              CURRENT_PAUSE_KEY,
            ],

            onSuccess: (
                answer,
            ) => {
              setPauseDateError(
                  undefined,
              );

              setPauseRequestError(
                  undefined,
              );

              toaster.success(
                  `Pausa guardada: se retiraron ${hoursText(
                      answer.withdrawnHours ??
                      0,
                  )} y se mantuvieron ${bookedText(
                      answer.bookedHoursKept ??
                      0,
                  )}.`,
              );
            },

            onError: (
                error,
            ) => {
              if (
                  isApiError(
                      error,
                      400,
                  )
              ) {
                setPauseRequestError(
                    'No pudimos crear la pausa. Revisa las fechas y que no se cruce con otra pausa.',
                );

                return;
              }

              toaster.error(
                  'No pudimos pausar tu disponibilidad. Inténtalo de nuevo.',
              );
            },
          },
      );

  const reactivate =
      useApiMutation(
          'delete',
          '/api/v1/tutor/availability/pauses/{id}',
          {
            invalidates: [
              AVAILABILITY_KEY,
              CURRENT_PAUSE_KEY,
            ],

            onSuccess: (
                answer,
            ) => {
              toaster.success(
                  `Terminamos la pausa: se restauraron ${hoursText(
                      answer.restoredHours ??
                      0,
                  )} libres.`,
              );
            },

            onError: () => {
              toaster.error(
                  'No pudimos terminar la pausa. Actualiza el estado e inténtalo de nuevo.',
              );

              void currentPause.refetch();
              void availability.refetch();
            },
          },
      );

  function submitRange(
      event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    setRangeEndError(
        undefined,
    );

    setRangeRequestError(
        undefined,
    );

    if (
        !range.startsAtTime ||
        !range.endsAtTime
    ) {
      setRangeEndError(
          'Completa Desde y Hasta',
      );

      return;
    }

    if (
        range.endsAtTime <=
        range.startsAtTime
    ) {
      setRangeEndError(
          'Debe ser después de Desde',
      );

      return;
    }

    if (!range.validFrom) {
      setRangeRequestError(
          'Indica desde qué fecha estará vigente el horario.',
      );

      return;
    }

    declareRange.mutate({
      body: {
        dayOfWeek:
        range.dayOfWeek,
        startsAtTime:
            apiTime(
                range.startsAtTime,
            ),
        endsAtTime:
            apiTime(
                range.endsAtTime,
            ),
        validFrom:
        range.validFrom,
      },
    });
  }

  function removePattern(
      id: string,
  ) {
    setRemovingId(id);

    removeRange.mutate({
      path: {
        id,
      },
    });
  }

  function submitException(
      event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    setExceptionTimeError(
        undefined,
    );

    setExceptionRequestError(
        undefined,
    );

    if (
        !exception.exceptionDate
    ) {
      setExceptionRequestError(
          'Indica la fecha de la excepción.',
      );

      return;
    }

    const hasStart =
        Boolean(
            exception.startsAtTime,
        );

    const hasEnd =
        Boolean(
            exception.endsAtTime,
        );

    if (
        exception.kind ===
        'ADD' &&
        (!hasStart ||
            !hasEnd)
    ) {
      setExceptionTimeError(
          'Agregar horas requiere Desde y Hasta',
      );

      return;
    }

    if (
        exception.kind ===
        'REMOVE' &&
        hasStart !== hasEnd
    ) {
      setExceptionTimeError(
          'Completa ambas horas o deja ambas vacías para quitar todo el día',
      );

      return;
    }

    if (
        hasStart &&
        hasEnd &&
        exception.endsAtTime <=
        exception.startsAtTime
    ) {
      setExceptionTimeError(
          'Debe ser después de Desde',
      );

      return;
    }

    saveException.mutate({
      body: {
        exceptionDate:
        exception.exceptionDate,
        kind:
        exception.kind,
        startsAtTime:
            hasStart
                ? apiTime(
                    exception.startsAtTime,
                )
                : undefined,
        endsAtTime:
            hasEnd
                ? apiTime(
                    exception.endsAtTime,
                )
                : undefined,
      },
    });
  }

  function submitPause(
      event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    setPauseDateError(
        undefined,
    );

    setPauseRequestError(
        undefined,
    );

    if (
        !pause.startsOn ||
        !pause.endsOn
    ) {
      setPauseDateError(
          'Completa Desde y Hasta',
      );

      return;
    }

    if (
        pause.endsOn <
        pause.startsOn
    ) {
      setPauseDateError(
          'Hasta no puede ser anterior a Desde',
      );

      return;
    }

    createPause.mutate({
      body: {
        startsOn:
        pause.startsOn,
        endsOn:
        pause.endsOn,
      },
    });
  }

  function endPause() {
    const pauseId =
        currentPause.data
            ?.pauseId;

    if (!pauseId) {
      return;
    }

    reactivate.mutate({
      path: {
        id: pauseId,
      },
    });
  }

  if (
      availability.isPending &&
      currentPause.isPending
  ) {
    return (
        <PageLoading label="Cargando tu disponibilidad" />
    );
  }

  const patterns =
      availability.data
          ?.patterns ?? [];

  const paused =
      currentPause.data
          ?.paused === true;

  const timezone =
      availability.data
          ?.timezone;

  return (
      <div className="mx-auto w-full max-w-6xl">
        <PageHeader
            title="Disponibilidad"
            description={
              <>
                Las horas que declares aparecen en Buscar para los cursos que enseñas.
                {zoneText(
                    timezone,
                )}
              </>
            }
        />

        {paused &&
            currentPause.data
                ?.endsOn && (
                <div className="mb-5 flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-amber-950">
            <span
                className="mt-0.5 text-lg leading-none"
                aria-hidden="true"
            >
              Ⅱ
            </span>

                  <div>
                    <p className="text-sm font-semibold">
                      En pausa hasta el{' '}
                      {formatLongDate(
                          currentPause
                              .data.endsOn,
                      )}
                    </p>

                    <p className="mt-1 text-xs text-amber-900">
                      Tus horas libres no aparecen en Buscar. Las tutorías ya reservadas se mantienen.
                    </p>
                  </div>
                </div>
            )}

        <div className="grid gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(280px,1fr)] lg:items-start">
          <div className="flex min-w-0 flex-col gap-5">
            <Card
                title="Horario semanal"
                description="Cada rango genera horas de una hora mientras esté vigente."
            >
              <div className="flex flex-col gap-5">
                <WeeklyRangeList
                    ranges={
                      patterns
                    }
                    loading={
                      availability.isPending
                    }
                    error={
                      availability.isError
                    }
                    retrying={
                      availability.isFetching
                    }
                    onRetry={() =>
                        void availability.refetch()
                    }
                    removingId={
                      removingId
                    }
                    onRemove={
                      removePattern
                    }
                />

                <form
                    onSubmit={
                      submitRange
                    }
                    className="flex flex-col gap-3 border-t border-gray-200 pt-4"
                    noValidate
                >
                  <p className="text-sm font-semibold text-gray-900">
                    Agregar un rango
                  </p>

                  <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[1.25fr_0.85fr_0.85fr_1.25fr_auto] lg:items-end">
                    <Field
                        label="Día"
                        required
                    >
                      <Select
                          options={
                            DAY_OPTIONS
                          }
                          value={
                            range.dayOfWeek
                          }
                          onChange={(
                              event,
                          ) => {
                            setRangeRequestError(
                                undefined,
                            );

                            setRange(
                                (
                                    current,
                                ) => ({
                                  ...current,
                                  dayOfWeek:
                                      event
                                          .target
                                          .value as DayOfWeek,
                                }),
                            );
                          }}
                      />
                    </Field>

                    <Field
                        label="Desde"
                        required
                    >
                      <TextField
                          type="time"
                          value={
                            range.startsAtTime
                          }
                          onChange={(
                              event,
                          ) => {
                            setRangeEndError(
                                undefined,
                            );

                            setRange(
                                (
                                    current,
                                ) => ({
                                  ...current,
                                  startsAtTime:
                                  event
                                      .target
                                      .value,
                                }),
                            );
                          }}
                      />
                    </Field>

                    <Field
                        label="Hasta"
                        required
                        error={
                          rangeEndError
                        }
                    >
                      <TextField
                          type="time"
                          value={
                            range.endsAtTime
                          }
                          onChange={(
                              event,
                          ) => {
                            setRangeEndError(
                                undefined,
                            );

                            setRange(
                                (
                                    current,
                                ) => ({
                                  ...current,
                                  endsAtTime:
                                  event
                                      .target
                                      .value,
                                }),
                            );
                          }}
                      />
                    </Field>

                    <Field
                        label="Vigente desde"
                        required
                    >
                      <TextField
                          type="date"
                          value={
                            range.validFrom
                          }
                          onChange={(
                              event,
                          ) => {
                            setRangeRequestError(
                                undefined,
                            );

                            setRange(
                                (
                                    current,
                                ) => ({
                                  ...current,
                                  validFrom:
                                  event
                                      .target
                                      .value,
                                }),
                            );
                          }}
                      />
                    </Field>

                    <Button
                        type="submit"
                        loading={
                          declareRange.isPending
                        }
                        className="w-full lg:w-auto"
                    >
                      Agregar
                    </Button>
                  </div>

                  {rangeRequestError && (
                      <p
                          role="alert"
                          className="text-xs font-medium text-red-700"
                      >
                        {rangeRequestError}
                      </p>
                  )}
                </form>
              </div>
            </Card>

            <Card
                title="Excepción de un día"
                description="Agrega o quita horas solo en una fecha."
            >
              <form
                  onSubmit={
                    submitException
                  }
                  className="flex flex-col gap-3"
                  noValidate
              >
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[1.2fr_1.35fr_0.9fr_0.9fr_auto] lg:items-end">
                  <Field
                      label="Fecha"
                      required
                  >
                    <TextField
                        type="date"
                        value={
                          exception.exceptionDate
                        }
                        onChange={(
                            event,
                        ) => {
                          setExceptionRequestError(
                              undefined,
                          );

                          setException(
                              (
                                  current,
                              ) => ({
                                ...current,
                                exceptionDate:
                                event
                                    .target
                                    .value,
                              }),
                          );
                        }}
                    />
                  </Field>

                  <Field
                      label="Tipo"
                      required
                  >
                    <Select
                        options={
                          EXCEPTION_OPTIONS
                        }
                        value={
                          exception.kind
                        }
                        onChange={(
                            event,
                        ) => {
                          setExceptionTimeError(
                              undefined,
                          );

                          setException(
                              (
                                  current,
                              ) => ({
                                ...current,
                                kind:
                                    event
                                        .target
                                        .value as ExceptionKind,
                              }),
                          );
                        }}
                    />
                  </Field>

                  <Field label="Desde">
                    <TextField
                        type="time"
                        value={
                          exception.startsAtTime
                        }
                        onChange={(
                            event,
                        ) => {
                          setExceptionTimeError(
                              undefined,
                          );

                          setException(
                              (
                                  current,
                              ) => ({
                                ...current,
                                startsAtTime:
                                event
                                    .target
                                    .value,
                              }),
                          );
                        }}
                    />
                  </Field>

                  <Field
                      label="Hasta"
                      error={
                        exceptionTimeError
                      }
                  >
                    <TextField
                        type="time"
                        value={
                          exception.endsAtTime
                        }
                        onChange={(
                            event,
                        ) => {
                          setExceptionTimeError(
                              undefined,
                          );

                          setException(
                              (
                                  current,
                              ) => ({
                                ...current,
                                endsAtTime:
                                event
                                    .target
                                    .value,
                              }),
                          );
                        }}
                    />
                  </Field>

                  <Button
                      type="submit"
                      variant="secondary"
                      loading={
                        saveException.isPending
                      }
                      className="w-full lg:w-auto"
                  >
                    Guardar
                  </Button>
                </div>

                <p className="text-xs text-gray-500">
                  En “Quitar horas” puedes dejar Desde y Hasta vacíos para quitar todo el día.
                </p>

                {exceptionRequestError && (
                    <p
                        role="alert"
                        className="text-xs font-medium text-red-700"
                    >
                      {
                        exceptionRequestError
                      }
                    </p>
                )}
              </form>
            </Card>
          </div>

          <aside className="flex min-w-0 flex-col gap-5">
            <Card
                title="Pausa"
                description="Retira tus horas libres de Buscar por unos días."
            >
              {currentPause.isPending ? (
                  <div className="flex flex-col gap-3">
                    <Skeleton className="h-4 w-28" />
                    <Skeleton className="h-10 w-full" />
                    <Skeleton className="h-10 w-full" />
                  </div>
              ) : currentPause.isError ? (
                  <div
                      role="alert"
                      className="flex flex-col gap-3"
                  >
                    <p className="text-sm font-medium text-gray-900">
                      No pudimos revisar si estás en pausa.
                    </p>

                    <Button
                        variant="secondary"
                        fullWidth
                        loading={
                          currentPause.isFetching
                        }
                        onClick={() =>
                            void currentPause.refetch()
                        }
                    >
                      Reintentar
                    </Button>
                  </div>
              ) : paused ? (
                  <div className="flex flex-col gap-4">
                    <div className="rounded-lg bg-amber-50 px-4 py-3">
                      <p className="text-sm font-semibold text-amber-950">
                        Del{' '}
                        {formatShortDate(
                            currentPause
                                .data
                                ?.startsOn,
                        )}{' '}
                        al{' '}
                        {formatShortDate(
                            currentPause
                                .data
                                ?.endsOn,
                        )}
                      </p>
                    </div>

                    <Button
                        variant="secondary"
                        fullWidth
                        loading={
                          reactivate.isPending
                        }
                        disabled={
                          !currentPause
                              .data
                              ?.pauseId
                        }
                        onClick={
                          endPause
                        }
                    >
                      Terminar la pausa
                    </Button>
                  </div>
              ) : (
                  <form
                      onSubmit={
                        submitPause
                      }
                      className="flex flex-col gap-4"
                      noValidate
                  >
                    <p className="text-sm text-gray-600">
                      No estás en pausa.
                    </p>

                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-2">
                      <Field
                          label="Desde"
                          required
                      >
                        <TextField
                            type="date"
                            value={
                              pause.startsOn
                            }
                            onChange={(
                                event,
                            ) => {
                              setPauseDateError(
                                  undefined,
                              );

                              setPause(
                                  (
                                      current,
                                  ) => ({
                                    ...current,
                                    startsOn:
                                    event
                                        .target
                                        .value,
                                  }),
                              );
                            }}
                        />
                      </Field>

                      <Field
                          label="Hasta"
                          required
                          error={
                            pauseDateError
                          }
                      >
                        <TextField
                            type="date"
                            value={
                              pause.endsOn
                            }
                            onChange={(
                                event,
                            ) => {
                              setPauseDateError(
                                  undefined,
                              );

                              setPause(
                                  (
                                      current,
                                  ) => ({
                                    ...current,
                                    endsOn:
                                    event
                                        .target
                                        .value,
                                  }),
                              );
                            }}
                        />
                      </Field>
                    </div>

                    {pauseRequestError && (
                        <p
                            role="alert"
                            className="text-xs font-medium text-red-700"
                        >
                          {pauseRequestError}
                        </p>
                    )}

                    <Button
                        type="submit"
                        variant="secondary"
                        fullWidth
                        loading={
                          createPause.isPending
                        }
                    >
                      Pausar
                    </Button>
                  </form>
              )}
            </Card>

            <HelpfulInformation />
          </aside>
        </div>
      </div>
  );
}