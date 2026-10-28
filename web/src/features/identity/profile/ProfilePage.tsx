import {
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from 'react';
import {
  useApiMutation,
  useApiQuery,
  type Schema,
} from '@/shared/api';
import {
  ME_KEY,
  useMe,
} from '@/shared/session';
import {
  Badge,
  Button,
  Card,
  Checkbox,
  ErrorState,
  Field,
  PageHeader,
  PageLoading,
  Skeleton,
  Spinner,
  TextArea,
  TextField,
  useToast,
} from '@/shared/ui';

type CatalogItem = Schema<'CatalogItem'>;
type SuggestedCourse = Schema<'SuggestedCourse'>;

const INTERESTS_KEY =
    ['skills', 'learning-interests'] as const;

const SUGGESTIONS_KEY =
    ['skills', 'suggestions'] as const;

const CATALOG_DEBOUNCE_MS = 300;

function cycleLabel(
    cycle?: number,
): string {
  return cycle
      ? `${cycle}.° ciclo`
      : '—';
}

function itemId(
    item: CatalogItem,
): string | null {
  return item.id ?? null;
}

function sameIds(
    left: CatalogItem[],
    right: CatalogItem[],
): boolean {
  const leftIds = left
      .map(itemId)
      .filter(
          (
              id,
          ): id is string =>
              id !== null,
      )
      .sort();

  const rightIds = right
      .map(itemId)
      .filter(
          (
              id,
          ): id is string =>
              id !== null,
      )
      .sort();

  return (
      leftIds.length ===
      rightIds.length &&
      leftIds.every(
          (id, index) =>
              id ===
              rightIds[index],
      )
  );
}

function AcademicData({
                        profile,
                      }: {
  profile: Schema<'MyProfileView'>;
}) {
  const fields = [
    [
      'Nombre',
      profile.fullName ?? '—',
    ],
    [
      'Correo institucional',
      profile.email ?? '—',
    ],
    [
      'Carrera',
      profile.career ?? '—',
    ],
    [
      'Ciclo actual',
      cycleLabel(
          profile.currentCycle,
      ),
    ],
    [
      'Periodo',
      profile.currentTerm ?? '—',
    ],
    [
      'Código',
      profile.studentCode ?? '—',
    ],
  ] as const;

  return (
      <Card
          title="Datos académicos"
          description="Estos datos vienen de tu universidad y no se editan en Ayni."
      >
        <dl className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {fields.map(
              ([label, value]) => (
                  <div key={label}>
                    <dt className="text-xs font-medium text-gray-500">
                      {label}
                    </dt>

                    <dd className="mt-1 text-sm font-semibold text-gray-900">
                      {value}
                    </dd>
                  </div>
              ),
          )}
        </dl>
      </Card>
  );
}

function EditableProfile({
                           profile,
                         }: {
  profile: Schema<'MyProfileView'>;
}) {
  const toaster =
      useToast();

  const [
    photoUrl,
    setPhotoUrl,
  ] = useState(
      profile.photoUrl ?? '',
  );

  const [
    bio,
    setBio,
  ] = useState(
      profile.bio ?? '',
  );

  const save =
      useApiMutation(
          'put',
          '/api/v1/me/profile',
          {
            invalidates: [
              ME_KEY,
            ],

            onSuccess: () => {
              toaster.success(
                  'Perfil actualizado.',
              );
            },
          },
      );

  function submit(
      event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    save.mutate({
      body: {
        photoUrl:
            photoUrl.trim() ||
            undefined,

        bio:
            bio.trim() ||
            undefined,
      },
    });
  }

  return (
      <Card
          title="Sobre mí"
          description="La foto y la presentación son opcionales."
      >
        <form
            onSubmit={submit}
            className="flex flex-col gap-5"
            noValidate
        >
          <Field
              label="Foto (URL, opcional)"
              hint="Solo tú y tus tutores ven tu foto en tu perfil."
          >
            <TextField
                type="url"
                inputMode="url"
                maxLength={512}
                placeholder="https://…"
                value={photoUrl}
                onChange={(
                    event,
                ) =>
                    setPhotoUrl(
                        event.target.value,
                    )
                }
            />
          </Field>

          <Field
              label="Presentación (opcional)"
          >
            <TextArea
                rows={4}
                maxLength={500}
                showCount
                placeholder="Cuéntales a tus tutores qué estudias y cómo aprendes mejor."
                value={bio}
                onChange={(
                    event,
                ) =>
                    setBio(
                        event.target.value,
                    )
                }
            />
          </Field>

          {save.isError && (
              <p
                  role="alert"
                  className="text-sm text-red-700"
              >
                No pudimos guardar tu perfil.
                Inténtalo de nuevo.
              </p>
          )}

          <div className="flex justify-end">
            <Button
                type="submit"
                loading={
                  save.isPending
                }
            >
              Guardar perfil
            </Button>
          </div>
        </form>
      </Card>
  );
}

function toggleItem(
    current: CatalogItem[],
    item: CatalogItem,
): CatalogItem[] {
  const id =
      itemId(item);

  if (!id) {
    return current;
  }

  const exists =
      current.some(
          (candidate) =>
              itemId(candidate) ===
              id,
      );

  return exists
      ? current.filter(
          (candidate) =>
              itemId(candidate) !==
              id,
      )
      : [
        ...current,
        item,
      ];
}

function InterestCheckbox({
                            item,
                            checked,
                            onToggle,
                          }: {
  item: CatalogItem;
  checked: boolean;
  onToggle: () => void;
}) {
  const label = `${
      item.name ?? 'Curso'
  }${
      item.courseCode
          ? ` · ${item.courseCode}`
          : ''
  }`;

  return (
      <Checkbox
          checked={checked}
          onChange={onToggle}
          label={label}
      />
  );
}

function LearningInterests() {
  const toaster =
      useToast();

  const interests =
      useApiQuery(
          INTERESTS_KEY,
          '/api/v1/me/learning-interests',
      );

  const [
    selected,
    setSelected,
  ] = useState<
      CatalogItem[] | null
  >(null);

  const [
    search,
    setSearch,
  ] = useState('');

  const [
    debouncedSearch,
    setDebouncedSearch,
  ] = useState('');

  useEffect(() => {
    if (
        interests.data !==
        undefined &&
        selected === null
    ) {
      setSelected(
          interests.data,
      );
    }
  }, [
    interests.data,
    selected,
  ]);

  useEffect(() => {
    const timer =
        window.setTimeout(
            () => {
              setDebouncedSearch(
                  search.trim(),
              );
            },
            CATALOG_DEBOUNCE_MS,
        );

    return () => {
      window.clearTimeout(
          timer,
      );
    };
  }, [search]);

  const catalog =
      useApiQuery(
          [
            'skills',
            'profile-catalog',
            {
              q:
              debouncedSearch,
            },
          ],
          '/api/v1/catalog',
          {
            enabled:
                debouncedSearch.length >
                0,

            query: {
              q:
              debouncedSearch,
              page: 0,
              size: 20,
            },
          },
      );

  const save =
      useApiMutation(
          'put',
          '/api/v1/me/learning-interests',
          {
            invalidates: [
              INTERESTS_KEY,
            ],

            onSuccess: (
                response,
            ) => {
              setSelected(
                  response,
              );

              toaster.success(
                  'Necesidades actualizadas.',
              );
            },
          },
      );

  /*
   * Error must be checked BEFORE the loading/null state.
   * On a failed request there is no data, so selected remains null.
   * If the null check ran first, the screen would remain as skeleton forever.
   */
  if (
      interests.isError
  ) {
    return (
        <Card
            title="Necesito ayuda con"
        >
          <ErrorState
              title="No pudimos cargar tus necesidades"
              description="Vuelve a intentarlo para editar los cursos en los que necesitas ayuda."
              onRetry={() =>
                  void interests.refetch()
              }
              retrying={
                interests.isFetching
              }
          />
        </Card>
    );
  }

  if (
      interests.isPending ||
      selected === null
  ) {
    return (
        <Card
            title="Necesito ayuda con"
            description="Puedes cambiar estos cursos cuando quieras."
        >
          <div className="flex flex-col gap-4">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-4 w-1/2" />
          </div>
        </Card>
    );
  }

  const currentSelection:
      CatalogItem[] =
      selected;

  const original =
      interests.data ?? [];

  const dirty =
      !sameIds(
          original,
          currentSelection,
      );

  const selectedIds =
      new Set(
          currentSelection
              .map(itemId)
              .filter(
                  (
                      id,
                  ): id is string =>
                      id !== null,
              ),
      );

  function submit(
      event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    save.mutate({
      body: {
        catalogItemIds:
            currentSelection
                .map(itemId)
                .filter(
                    (
                        id,
                    ): id is string =>
                        id !== null,
                ),
      },
    });
  }

  return (
      <Card
          title="Necesito ayuda con"
          description="Estos cursos se usan para personalizar tu experiencia. Cambiarlos no modifica tutorías ya confirmadas."
      >
        <form
            onSubmit={submit}
            className="flex flex-col gap-6"
        >
          <div>
            <h3 className="text-sm font-semibold text-gray-900">
              Mis necesidades actuales
            </h3>

            {currentSelection.length ===
            0 ? (
                <div className="mt-3 rounded-xl border border-dashed border-gray-300 px-5 py-6 text-center">
                  <p className="text-sm font-medium text-gray-900">
                    Todavía no elegiste cursos.
                  </p>

                  <p className="mt-1 text-sm text-gray-600">
                    Busca uno abajo para agregarlo.
                  </p>
                </div>
            ) : (
                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                  {currentSelection.map(
                      (item) => {
                        const id =
                            itemId(item);

                        if (!id) {
                          return null;
                        }

                        return (
                            <InterestCheckbox
                                key={id}
                                item={item}
                                checked
                                onToggle={() =>
                                    setSelected(
                                        (
                                            current,
                                        ) =>
                                            toggleItem(
                                                current ??
                                                [],
                                                item,
                                            ),
                                    )
                                }
                            />
                        );
                      },
                  )}
                </div>
            )}
          </div>

          <div className="border-t border-gray-200 pt-5">
            <Field
                label="Agregar otro curso"
                hint="Busca por nombre del curso."
            >
              <TextField
                  type="search"
                  placeholder="Ej. Algoritmos"
                  value={search}
                  onChange={(
                      event,
                  ) =>
                      setSearch(
                          event.target.value,
                      )
                  }
              />
            </Field>

            {debouncedSearch &&
                (
                    catalog.isPending ||
                    catalog.isFetching
                ) && (
                    <div className="mt-4 flex items-center gap-2 text-sm text-gray-600">
                      <Spinner
                          size="sm"
                          decorative
                      />

                      <span>
                  Buscando cursos…
                </span>
                    </div>
                )}

            {debouncedSearch &&
                catalog.isError && (
                    <div className="mt-4">
                      <ErrorState
                          title="No pudimos buscar cursos"
                          description="Inténtalo nuevamente."
                          onRetry={() =>
                              void catalog.refetch()
                          }
                          retrying={
                            catalog.isFetching
                          }
                      />
                    </div>
                )}

            {debouncedSearch &&
                !catalog.isPending &&
                !catalog.isFetching &&
                !catalog.isError && (
                    <div className="mt-4">
                      {(
                          catalog.data
                              ?.items ?? []
                      ).length ===
                      0 ? (
                          <p className="text-sm text-gray-600">
                            No encontramos cursos con esa búsqueda.
                          </p>
                      ) : (
                          <div className="grid gap-3 sm:grid-cols-2">
                            {(
                                catalog.data
                                    ?.items ??
                                []
                            ).map(
                                (item) => {
                                  const id =
                                      itemId(
                                          item,
                                      );

                                  if (!id) {
                                    return null;
                                  }

                                  return (
                                      <InterestCheckbox
                                          key={id}
                                          item={item}
                                          checked={selectedIds.has(
                                              id,
                                          )}
                                          onToggle={() =>
                                              setSelected(
                                                  (
                                                      current,
                                                  ) =>
                                                      toggleItem(
                                                          current ??
                                                          [],
                                                          item,
                                                      ),
                                              )
                                          }
                                      />
                                  );
                                },
                            )}
                          </div>
                      )}
                    </div>
                )}
          </div>

          {save.isError && (
              <p
                  role="alert"
                  className="text-sm text-red-700"
              >
                No pudimos guardar tus necesidades.
                Ningún cambio fue aplicado.
              </p>
          )}

          <div className="flex justify-end">
            <Button
                type="submit"
                disabled={!dirty}
                loading={
                  save.isPending
                }
            >
              Guardar necesidades
            </Button>
          </div>
        </form>
      </Card>
  );
}

function suggestedId(
    suggestion: SuggestedCourse,
): string | null {
  return (
      suggestion.item?.id ??
      null
  );
}

function AcademicRecordRefresh() {
  const toaster =
      useToast();

  const suggestions =
      useApiQuery(
          SUGGESTIONS_KEY,
          '/api/v1/tutor/skills/suggestions',
      );

  const beforeImport =
      useRef<Set<string>>(
          new Set(),
      );

  const [
    imported,
    setImported,
  ] = useState(false);

  const [
    newCourseIds,
    setNewCourseIds,
  ] = useState<
      Set<string>
  >(
      new Set(),
  );

  const importRecord =
      useApiMutation(
          'post',
          '/api/v1/me/academic-record/import',
          {
            invalidates: [
              ME_KEY,
              SUGGESTIONS_KEY,
            ],

            onSuccess: () => {
              setImported(true);

              void suggestions
                  .refetch()
                  .then(
                      (
                          result,
                      ) => {
                        const next =
                            result.data ??
                            [];

                        const newIds =
                            new Set(
                                next
                                    .map(
                                        suggestedId,
                                    )
                                    .filter(
                                        (
                                            id,
                                        ): id is string =>
                                            id !==
                                            null &&
                                            !beforeImport.current.has(
                                                id,
                                            ),
                                    ),
                            );

                        setNewCourseIds(
                            newIds,
                        );

                        if (
                            newIds.size >
                            0
                        ) {
                          toaster.success(
                              newIds.size ===
                              1
                                  ? 'Récord actualizado. Encontramos un curso nuevo que podrías enseñar.'
                                  : `Récord actualizado. Encontramos ${newIds.size} cursos nuevos que podrías enseñar.`,
                          );
                        } else {
                          toaster.success(
                              'Récord académico actualizado.',
                          );
                        }
                      },
                  );
            },
          },
      );

  function importNow() {
    beforeImport.current =
        new Set(
            (
                suggestions.data ??
                []
            )
                .map(
                    suggestedId,
                )
                .filter(
                    (
                        id,
                    ): id is string =>
                        id !== null,
                ),
        );

    setImported(false);

    setNewCourseIds(
        new Set(),
    );

    importRecord.reset();

    importRecord.mutate({});
  }

  const newCourses =
      (
          suggestions.data ??
          []
      ).filter(
          (
              suggestion,
          ) => {
            const id =
                suggestedId(
                    suggestion,
                );

            return (
                id !== null &&
                newCourseIds.has(
                    id,
                )
            );
          },
      );

  return (
      <Card
          title="Récord académico"
          description="Actualízalo cuando tu universidad publique un nuevo periodo o apruebes cursos nuevos."
          actions={
            <Button
                onClick={
                  importNow
                }
                loading={
                  importRecord.isPending
                }
                disabled={
                  suggestions.isPending
                }
            >
              Actualizar mi récord
            </Button>
          }
      >
        {suggestions.isPending && (
            <div className="flex items-center gap-2 text-sm text-gray-600">
              <Spinner
                  size="sm"
                  decorative
              />

              <span>
            Revisando tu récord actual…
          </span>
            </div>
        )}

        {suggestions.isError && (
            <ErrorState
                title="No pudimos revisar tus cursos"
                description="Vuelve a intentarlo antes de actualizar tu récord."
                onRetry={() =>
                    void suggestions.refetch()
                }
                retrying={
                  suggestions.isFetching
                }
            />
        )}

        {importRecord.isError && (
            <p
                role="alert"
                className="text-sm text-red-700"
            >
              No pudimos actualizar tu récord académico.
              Inténtalo de nuevo.
            </p>
        )}

        {!suggestions.isPending &&
            !suggestions.isError &&
            !imported && (
                <p className="text-sm text-gray-600">
                  Ayni volverá a consultar el sistema académico de tu universidad y actualizará tu carrera, ciclo, periodo y cursos aprobados.
                </p>
            )}

        {imported &&
            newCourses.length ===
            0 && (
                <div className="rounded-lg bg-green-50 px-4 py-3 text-sm text-green-800">
                  Récord actualizado.
                  No aparecieron cursos nuevos que puedas enseñar.
                </div>
            )}

        {newCourses.length >
            0 && (
                <div className="flex flex-col gap-3">
                  <div className="rounded-lg bg-green-50 px-4 py-3 text-sm font-medium text-green-800">
                    Estos cursos aparecen desde la última actualización:
                  </div>

                  <div className="overflow-hidden rounded-xl border border-gray-200">
                    {newCourses.map(
                        (
                            suggestion,
                            index,
                        ) => {
                          const item =
                              suggestion.item;

                          const id =
                              item?.id;

                          if (
                              !item ||
                              !id
                          ) {
                            return null;
                          }

                          return (
                              <div
                                  key={id}
                                  className={
                                    index ===
                                    0
                                        ? 'flex items-center justify-between gap-4 px-4 py-3'
                                        : 'flex items-center justify-between gap-4 border-t border-gray-200 px-4 py-3'
                                  }
                              >
                                <div>
                                  <p className="text-sm font-medium text-gray-900">
                                    {item.name ??
                                        'Curso'}
                                  </p>

                                  {item.courseCode && (
                                      <p className="mt-0.5 text-xs text-gray-500">
                                        {
                                          item.courseCode
                                        }
                                      </p>
                                  )}
                                </div>

                                <div className="flex items-center gap-2">
                                  <Badge tone="success">
                                    Nuevo
                                  </Badge>

                                  {suggestion.grade !==
                                      undefined && (
                                          <span className="text-xs text-gray-600">
                          Nota{' '}
                                            {suggestion.grade.toLocaleString(
                                                'es-PE',
                                                {
                                                  minimumFractionDigits: 2,
                                                  maximumFractionDigits: 2,
                                                },
                                            )}
                        </span>
                                      )}
                                </div>
                              </div>
                          );
                        },
                    )}
                  </div>
                </div>
            )}
      </Card>
  );
}

/**
 * FE-04 · `/perfil` · student.
 *
 * Academic data belongs to the university and is read-only.
 * currentCycle is the source of truth for the student's current cycle.
 */
export default function ProfilePage() {
  const me =
      useMe();

  if (me.isPending) {
    return (
        <PageLoading label="Cargando tu perfil" />
    );
  }

  if (me.isError) {
    return (
        <ErrorState
            title="No pudimos cargar tu perfil"
            description="Vuelve a intentarlo."
            onRetry={() =>
                void me.refetch()
            }
            retrying={
              me.isFetching
            }
        />
    );
  }

  const profile =
      me.data;

  if (!profile) {
    return (
        <PageLoading label="Cargando tu perfil" />
    );
  }

  return (
      <div className="mx-auto w-full max-w-4xl">
        <PageHeader
            title="Mi perfil"
            description="Revisa tus datos, actualiza tu presentación y cambia los cursos en los que necesitas ayuda."
        />

        <div className="flex flex-col gap-6">
          <AcademicData
              profile={profile}
          />

          <EditableProfile
              profile={profile}
          />

          <LearningInterests />

          <AcademicRecordRefresh />
        </div>
      </div>
  );
}