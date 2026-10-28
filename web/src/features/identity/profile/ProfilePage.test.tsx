import {
    screen,
} from '@testing-library/react';
import {
    afterEach,
    describe,
    expect,
    it,
    vi,
} from 'vitest';
import type {
    Schema,
} from '@/shared/api';
import {
    ANA,
    signIn,
} from '@/test/fixtures';
import {
    fakeServer,
} from '@/test/fakeServer';
import {
    renderWithProviders,
} from '@/test/render';
import ProfilePage from './ProfilePage';

const MATH:
    Schema<'CatalogItem'> = {
    id: 'b0000000-0000-4000-8000-000000000201',
    scope: 'UNIVERSITY',
    name: 'Matemática Básica',
    courseCode: 'MA420',
};

const ALGORITHMS:
    Schema<'CatalogItem'> = {
    id: 'b0000000-0000-4000-8000-000000000202',
    scope: 'UNIVERSITY',
    name: 'Algoritmos',
    courseCode: 'CC215',
};

const REQUIREMENTS:
    Schema<'CatalogItem'> = {
    id: 'b0000000-0000-4000-8000-000000000203',
    scope: 'UNIVERSITY',
    name:
        'Especificación y Análisis de Requerimientos',
    courseCode: 'SI397',
};

const INITIAL_SUGGESTIONS:
    Schema<'SuggestedCourse'>[] = [
    {
        item: REQUIREMENTS,
        grade: 18.88,
    },
];

function profile(
    overrides:
    Partial<
        Schema<'MyProfileView'>
    > = {},
): Schema<'MyProfileView'> {
    return {
        ...ANA,
        photoUrl:
            'https://example.test/old.jpg',
        bio:
            'Me gustan los ejemplos.',
        ...overrides,
    };
}

function baseServer(
    initial:
    Schema<'MyProfileView'> =
    profile(),
) {
    let me =
        initial;

    const server =
        fakeServer().on(
            'GET',
            '/api/v1/me',
            () => ({
                body: me,
            }),
        );

    return {
        server,

        setMe(
            next:
            Schema<'MyProfileView'>,
        ) {
            me =
                next;
        },

        getMe() {
            return me;
        },
    };
}

describe(
    'FE-04 profile',
    () => {
        afterEach(() => {
            vi.unstubAllGlobals();
        });

        it(
            'shows university academic data as read-only and uses currentCycle',
            async () => {
                signIn(
                    'STUDENT',
                );

                const state =
                    baseServer();

                state.server
                    .on(
                        'GET',
                        '/api/v1/me/learning-interests',
                        {
                            body: [],
                        },
                    )
                    .on(
                        'GET',
                        '/api/v1/tutor/skills/suggestions',
                        {
                            body: [],
                        },
                    );

                renderWithProviders(
                    <ProfilePage />,
                    {
                        route:
                            '/perfil',
                    },
                );

                expect(
                    await screen.findByRole(
                        'heading',
                        {
                            name:
                                'Mi perfil',
                        },
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByText(
                        'Ana Torres',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByText(
                        'u202400001@upc.edu.pe',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByText(
                        'Ing. de Software',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByText(
                        '4.° ciclo',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByText(
                        '2026-2',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByText(
                        'U202400001',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.queryByRole(
                        'textbox',
                        {
                            name:
                                'Nombre',
                        },
                    ),
                ).not.toBeInTheDocument();
            },
        );

        it(
            'updates only photo and bio',
            async () => {
                signIn(
                    'STUDENT',
                );

                const state =
                    baseServer();

                state.server
                    .on(
                        'GET',
                        '/api/v1/me/learning-interests',
                        {
                            body: [],
                        },
                    )
                    .on(
                        'GET',
                        '/api/v1/tutor/skills/suggestions',
                        {
                            body: [],
                        },
                    )
                    .on(
                        'PUT',
                        '/api/v1/me/profile',
                        ({
                             body,
                         }) => {
                            const request =
                                body as
                                    Schema<'UpdateMyProfileRequest'>;

                            state.setMe({
                                ...state.getMe(),
                                photoUrl:
                                request.photoUrl,
                                bio:
                                request.bio,
                            });

                            return {
                                body:
                                    state.getMe(),
                            };
                        },
                    );

                const {
                    user,
                } =
                    renderWithProviders(
                        <ProfilePage />,
                        {
                            route:
                                '/perfil',
                        },
                    );

                const photo =
                    await screen.findByRole(
                        'textbox',
                        {
                            name:
                                'Foto (URL, opcional)',
                        },
                    );

                const bio =
                    screen.getByRole(
                        'textbox',
                        {
                            name:
                                'Presentación (opcional)',
                        },
                    );

                await user.clear(
                    photo,
                );

                await user.type(
                    photo,
                    'https://example.test/new.jpg',
                );

                await user.clear(
                    bio,
                );

                await user.type(
                    bio,
                    'Aprendo mejor con casos prácticos.',
                );

                await user.click(
                    screen.getByRole(
                        'button',
                        {
                            name:
                                'Guardar perfil',
                        },
                    ),
                );

                expect(
                    await screen.findByText(
                        'Perfil actualizado.',
                    ),
                ).toBeInTheDocument();

                expect(
                    state.server.requests(
                        'PUT',
                        '/api/v1/me/profile',
                    )[0].body,
                ).toEqual({
                    photoUrl:
                        'https://example.test/new.jpg',
                    bio:
                        'Aprendo mejor con casos prácticos.',
                });
            },
        );

        it(
            'replaces the whole set of learning needs',
            async () => {
                signIn(
                    'STUDENT',
                );

                const state =
                    baseServer();

                state.server
                    .on(
                        'GET',
                        '/api/v1/me/learning-interests',
                        {
                            body: [
                                MATH,
                            ],
                        },
                    )
                    .on(
                        'GET',
                        '/api/v1/tutor/skills/suggestions',
                        {
                            body: [],
                        },
                    )
                    .on(
                        'GET',
                        '/api/v1/catalog',
                        ({
                             query,
                         }) => ({
                            body: {
                                items:
                                    query.get(
                                        'q',
                                    )
                                        ? [
                                            ALGORITHMS,
                                        ]
                                        : [],
                                page: 0,
                                size: 20,
                                totalElements:
                                    1,
                                totalPages: 1,
                            },
                        }),
                    )
                    .on(
                        'PUT',
                        '/api/v1/me/learning-interests',
                        ({
                             body,
                         }) => {
                            const request =
                                body as
                                    Schema<'LearningInterestsRequest'>;

                            expect(
                                request.catalogItemIds,
                            ).toEqual([
                                ALGORITHMS.id,
                            ]);

                            return {
                                body: [
                                    ALGORITHMS,
                                ],
                            };
                        },
                    );

                const {
                    user,
                } =
                    renderWithProviders(
                        <ProfilePage />,
                        {
                            route:
                                '/perfil',
                        },
                    );

                const math =
                    await screen.findByRole(
                        'checkbox',
                        {
                            name:
                                'Matemática Básica · MA420',
                        },
                    );

                expect(
                    math,
                ).toBeChecked();

                await user.click(
                    math,
                );

                await user.type(
                    screen.getByRole(
                        'searchbox',
                        {
                            name:
                                'Agregar otro curso',
                        },
                    ),
                    'algo',
                );

                const algorithms =
                    await screen.findByRole(
                        'checkbox',
                        {
                            name:
                                'Algoritmos · CC215',
                        },
                    );

                expect(
                    algorithms,
                ).not.toBeChecked();

                await user.click(
                    algorithms,
                );

                await user.click(
                    screen.getByRole(
                        'button',
                        {
                            name:
                                'Guardar necesidades',
                        },
                    ),
                );

                expect(
                    await screen.findByText(
                        'Necesidades actualizadas.',
                    ),
                ).toBeInTheDocument();

                expect(
                    state.server.requests(
                        'PUT',
                        '/api/v1/me/learning-interests',
                    ),
                ).toHaveLength(
                    1,
                );

                expect(
                    state.server.requests(
                        'POST',
                    ),
                ).toHaveLength(
                    0,
                );
            },
        );

        it(
            'shows an empty interests state',
            async () => {
                signIn(
                    'STUDENT',
                );

                const state =
                    baseServer();

                state.server
                    .on(
                        'GET',
                        '/api/v1/me/learning-interests',
                        {
                            body: [],
                        },
                    )
                    .on(
                        'GET',
                        '/api/v1/tutor/skills/suggestions',
                        {
                            body: [],
                        },
                    );

                renderWithProviders(
                    <ProfilePage />,
                    {
                        route:
                            '/perfil',
                    },
                );

                expect(
                    await screen.findByText(
                        'Todavía no elegiste cursos.',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByRole(
                        'button',
                        {
                            name:
                                'Guardar necesidades',
                        },
                    ),
                ).toBeDisabled();
            },
        );

        it(
            're-imports the academic record, updates currentCycle and shows newly approved courses',
            async () => {
                signIn(
                    'STUDENT',
                );

                const state =
                    baseServer(
                        profile({
                            currentCycle:
                                4,
                            currentTerm:
                                '2026-2',
                        }),
                    );

                let imported =
                    false;

                state.server
                    .on(
                        'GET',
                        '/api/v1/me/learning-interests',
                        {
                            body: [],
                        },
                    )
                    .on(
                        'GET',
                        '/api/v1/tutor/skills/suggestions',
                        () => ({
                            body:
                                imported
                                    ? [
                                        ...INITIAL_SUGGESTIONS,
                                        {
                                            item:
                                            MATH,
                                            grade:
                                                17.38,
                                        },
                                    ]
                                    : INITIAL_SUGGESTIONS,
                        }),
                    )
                    .on(
                        'POST',
                        '/api/v1/me/academic-record/import',
                        () => {
                            imported =
                                true;

                            state.setMe({
                                ...state.getMe(),
                                currentCycle:
                                    5,
                                currentTerm:
                                    '2027-1',
                            });

                            return {
                                status: 204,
                            };
                        },
                    );

                const {
                    user,
                } =
                    renderWithProviders(
                        <ProfilePage />,
                        {
                            route:
                                '/perfil',
                        },
                    );

                expect(
                    await screen.findByText(
                        '4.° ciclo',
                    ),
                ).toBeInTheDocument();

                await user.click(
                    screen.getByRole(
                        'button',
                        {
                            name:
                                'Actualizar mi récord',
                        },
                    ),
                );

                expect(
                    await screen.findByText(
                        '5.° ciclo',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByText(
                        '2027-1',
                    ),
                ).toBeInTheDocument();

                expect(
                    await screen.findByText(
                        'Estos cursos aparecen desde la última actualización:',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByText(
                        'Matemática Básica',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByText(
                        'MA420',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByText(
                        'Nuevo',
                    ),
                ).toBeInTheDocument();

                expect(
                    state.server.requests(
                        'POST',
                        '/api/v1/me/academic-record/import',
                    ),
                ).toHaveLength(
                    1,
                );
            },
        );

        it(
            'shows its own Spanish error when learning interests fail',
            async () => {
                signIn(
                    'STUDENT',
                );

                const state =
                    baseServer();

                state.server
                    .on(
                        'GET',
                        '/api/v1/me/learning-interests',
                        {
                            status:
                                503,

                            body: {
                                message:
                                    'Backend message must not be shown',
                            },
                        },
                    )
                    .on(
                        'GET',
                        '/api/v1/tutor/skills/suggestions',
                        {
                            body: [],
                        },
                    );

                renderWithProviders(
                    <ProfilePage />,
                    {
                        route:
                            '/perfil',
                    },
                );

                expect(
                    await screen.findByRole(
                        'heading',
                        {
                            name:
                                'No pudimos cargar tus necesidades',
                        },
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.queryByText(
                        'Backend message must not be shown',
                    ),
                ).not.toBeInTheDocument();
            },
        );
    },
);