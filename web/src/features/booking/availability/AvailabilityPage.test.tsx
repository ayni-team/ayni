import {
    screen,
    within,
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
    fakeServer,
} from '@/test/fakeServer';
import {
    signIn,
} from '@/test/fixtures';
import {
    renderWithProviders,
} from '@/test/render';
import AvailabilityPage from './AvailabilityPage';

const MONDAY:
    Schema<'TutorAvailabilityWeeklyRange'> = {
    id: 'a0000000-0000-4000-8000-000000000001',
    dayOfWeek:
        'MONDAY',
    startsAtTime:
        '16:00:00',
    endsAtTime:
        '19:00:00',
    validFrom:
        '2026-10-01',
    validUntil:
        null,
};

const TUESDAY:
    Schema<'TutorAvailabilityWeeklyRange'> = {
    id: 'a0000000-0000-4000-8000-000000000002',
    dayOfWeek:
        'TUESDAY',
    startsAtTime:
        '17:00:00',
    endsAtTime:
        '19:00:00',
    validFrom:
        '2026-10-01',
    validUntil:
        null,
};

const THURSDAY:
    Schema<'TutorAvailabilityWeeklyRange'> = {
    id: 'a0000000-0000-4000-8000-000000000003',
    dayOfWeek:
        'THURSDAY',
    startsAtTime:
        '18:00:00',
    endsAtTime:
        '20:00:00',
    validFrom:
        '2026-10-15',
    validUntil:
        null,
};

function initialAvailability():
    Schema<'TutorAvailability'> {
    return {
        timezone:
            'America/Lima',

        patterns: [
            MONDAY,
            TUESDAY,
            THURSDAY,
        ],

        exceptions: [],

        pauses: [],
    };
}

function initialPause():
    Schema<'CurrentAvailabilityPause'> {
    return {
        paused: false,
        pauseId: null,
        startsOn: null,
        endsOn: null,
    };
}

function serveAvailability() {
    let availability =
        initialAvailability();

    let currentPause =
        initialPause();

    const server =
        fakeServer()
            .on(
                'GET',
                '/api/v1/tutor/availability',
                () => ({
                    body:
                    availability,
                }),
            )
            .on(
                'GET',
                '/api/v1/tutor/availability/pauses/current',
                () => ({
                    body:
                    currentPause,
                }),
            );

    return {
        server,

        getAvailability() {
            return availability;
        },

        setAvailability(
            next:
            Schema<'TutorAvailability'>,
        ) {
            availability =
                next;
        },

        getPause() {
            return currentPause;
        },

        setPause(
            next:
            Schema<'CurrentAvailabilityPause'>,
        ) {
            currentPause =
                next;
        },
    };
}

describe(
    'FE-40 tutor availability',
    () => {
        afterEach(() => {
            vi.unstubAllGlobals();
        });

        it(
            'reads back the weekly ranges already declared',
            async () => {
                signIn(
                    'STUDENT',
                );

                const state =
                    serveAvailability();

                renderWithProviders(
                    <AvailabilityPage />,
                    {
                        route:
                            '/tutor/disponibilidad',
                    },
                );

                expect(
                    await screen.findByRole(
                        'heading',
                        {
                            name:
                                'Disponibilidad',
                        },
                    ),
                ).toBeInTheDocument();

                expect(
                    await screen.findByText(
                        'Lunes · 16:00 – 19:00',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByText(
                        'Martes · 17:00 – 19:00',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByText(
                        'Jueves · 18:00 – 20:00',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByText(
                        /Hora de Lima/,
                    ),
                ).toBeInTheDocument();

                expect(
                    state.server.requests(
                        'GET',
                        '/api/v1/tutor/availability',
                    ),
                ).toHaveLength(
                    1,
                );
            },
        );

        it(
            'declares a range, reports generated hours and shows it after the GET refresh',
            async () => {
                signIn(
                    'STUDENT',
                );

                const state =
                    serveAvailability();

                state.server.on(
                    'POST',
                    '/api/v1/tutor/availability',
                    ({
                         body,
                     }) => {
                        const request =
                            body as {
                                dayOfWeek:
                                    string;
                                startsAtTime:
                                    string;
                                endsAtTime:
                                    string;
                                validFrom:
                                    string;
                            };

                        state.setAvailability({
                            ...state.getAvailability(),

                            patterns: [
                                ...(
                                    state
                                        .getAvailability()
                                        .patterns ??
                                    []
                                ),
                                {
                                    id:
                                        'a0000000-0000-4000-8000-000000000004',

                                    dayOfWeek:
                                        request.dayOfWeek as
                                            Schema<'TutorAvailabilityWeeklyRange'>['dayOfWeek'],

                                    startsAtTime:
                                    request.startsAtTime,

                                    endsAtTime:
                                    request.endsAtTime,

                                    validFrom:
                                    request.validFrom,

                                    validUntil:
                                        null,
                                },
                            ],
                        });

                        return {
                            status: 201,

                            body: {
                                id:
                                    'a0000000-0000-4000-8000-000000000004',

                                generatedHours:
                                    12,
                            },
                        };
                    },
                );

                const {
                    user,
                } =
                    renderWithProviders(
                        <AvailabilityPage />,
                        {
                            route:
                                '/tutor/disponibilidad',
                        },
                    );

                const weekly =
                    await screen.findByRole(
                        'region',
                        {
                            name:
                                'Horario semanal',
                        },
                    );

                await user.selectOptions(
                    within(
                        weekly,
                    ).getByLabelText(
                        /^Día/,
                    ),
                    'WEDNESDAY',
                );

                const from =
                    within(
                        weekly,
                    ).getByLabelText(
                        /^Desde/,
                    );

                const to =
                    within(
                        weekly,
                    ).getByLabelText(
                        /^Hasta/,
                    );

                const validFrom =
                    within(
                        weekly,
                    ).getByLabelText(
                        /^Vigente desde/,
                    );

                await user.clear(
                    from,
                );

                await user.type(
                    from,
                    '15:00',
                );

                await user.clear(
                    to,
                );

                await user.type(
                    to,
                    '17:00',
                );

                await user.clear(
                    validFrom,
                );

                await user.type(
                    validFrom,
                    '2026-10-26',
                );

                await user.click(
                    within(
                        weekly,
                    ).getByRole(
                        'button',
                        {
                            name:
                                'Agregar',
                        },
                    ),
                );

                expect(
                    await screen.findByText(
                        'Agregamos tu horario: se generaron 12 horas libres.',
                    ),
                ).toBeInTheDocument();

                expect(
                    await screen.findByText(
                        'Miércoles · 15:00 – 17:00',
                    ),
                ).toBeInTheDocument();

                expect(
                    state.server.requests(
                        'POST',
                        '/api/v1/tutor/availability',
                    )[0].body,
                ).toEqual({
                    dayOfWeek:
                        'WEDNESDAY',

                    startsAtTime:
                        '15:00:00',

                    endsAtTime:
                        '17:00:00',

                    validFrom:
                        '2026-10-26',
                });

                expect(
                    state.server.requests(
                        'GET',
                        '/api/v1/tutor/availability',
                    ).length,
                ).toBeGreaterThan(
                    1,
                );
            },
        );

        it(
            'does not send a weekly range when Hasta is not after Desde',
            async () => {
                signIn(
                    'STUDENT',
                );

                const state =
                    serveAvailability();

                const {
                    user,
                } =
                    renderWithProviders(
                        <AvailabilityPage />,
                        {
                            route:
                                '/tutor/disponibilidad',
                        },
                    );

                const weekly =
                    await screen.findByRole(
                        'region',
                        {
                            name:
                                'Horario semanal',
                        },
                    );

                const from =
                    within(
                        weekly,
                    ).getByLabelText(
                        /^Desde/,
                    );

                const to =
                    within(
                        weekly,
                    ).getByLabelText(
                        /^Hasta/,
                    );

                await user.clear(
                    from,
                );

                await user.type(
                    from,
                    '15:00',
                );

                await user.clear(
                    to,
                );

                await user.type(
                    to,
                    '14:00',
                );

                await user.click(
                    within(
                        weekly,
                    ).getByRole(
                        'button',
                        {
                            name:
                                'Agregar',
                        },
                    ),
                );

                expect(
                    await screen.findByText(
                        'Debe ser después de Desde',
                    ),
                ).toBeInTheDocument();

                expect(
                    state.server.requests(
                        'POST',
                        '/api/v1/tutor/availability',
                    ),
                ).toHaveLength(
                    0,
                );
            },
        );

        it(
            'removes a range, tells the tutor what happened to the hours and refreshes the list',
            async () => {
                signIn(
                    'STUDENT',
                );

                const state =
                    serveAvailability();

                state.server.on(
                    'DELETE',
                    '/api/v1/tutor/availability/a0000000-0000-4000-8000-000000000001',
                    () => {
                        state.setAvailability({
                            ...state.getAvailability(),

                            patterns:
                                (
                                    state
                                        .getAvailability()
                                        .patterns ??
                                    []
                                ).filter(
                                    (
                                        range,
                                    ) =>
                                        range.id !==
                                        MONDAY.id,
                                ),
                        });

                        return {
                            body: {
                                patternId:
                                MONDAY.id,

                                withdrawnHours:
                                    6,

                                bookedHoursKept:
                                    2,
                            },
                        };
                    },
                );

                const {
                    user,
                } =
                    renderWithProviders(
                        <AvailabilityPage />,
                        {
                            route:
                                '/tutor/disponibilidad',
                        },
                    );

                const weekly =
                    await screen.findByRole(
                        'region',
                        {
                            name:
                                'Horario semanal',
                        },
                    );

                expect(
                    within(
                        weekly,
                    ).getByText(
                        'Lunes · 16:00 – 19:00',
                    ),
                ).toBeInTheDocument();

                const removeButtons =
                    within(
                        weekly,
                    ).getAllByRole(
                        'button',
                        {
                            name:
                                'Quitar',
                        },
                    );

                await user.click(
                    removeButtons[0],
                );

                expect(
                    await screen.findByText(
                        'Quitamos el rango: se retiraron 6 horas y se mantuvieron 2 ya reservadas.',
                    ),
                ).toBeInTheDocument();

                expect(
                    within(
                        weekly,
                    ).queryByText(
                        'Lunes · 16:00 – 19:00',
                    ),
                ).not.toBeInTheDocument();

                expect(
                    state.server.requests(
                        'DELETE',
                        '/api/v1/tutor/availability/a0000000-0000-4000-8000-000000000001',
                    ),
                ).toHaveLength(
                    1,
                );
            },
        );

        it(
            'saves a one-day ADD exception and reports the generated hours',
            async () => {
                signIn(
                    'STUDENT',
                );

                const state =
                    serveAvailability();

                state.server.on(
                    'POST',
                    '/api/v1/tutor/availability/exceptions',
                    {
                        status: 201,

                        body: {
                            id:
                                'b0000000-0000-4000-8000-000000000001',

                            generatedHours:
                                2,

                            withdrawnHours:
                                0,

                            bookedHoursKept:
                                0,
                        },
                    },
                );

                const {
                    user,
                } =
                    renderWithProviders(
                        <AvailabilityPage />,
                        {
                            route:
                                '/tutor/disponibilidad',
                        },
                    );

                const exceptionCard =
                    await screen.findByRole(
                        'region',
                        {
                            name:
                                'Excepción de un día',
                        },
                    );

                const date =
                    within(
                        exceptionCard,
                    ).getByLabelText(
                        /^Fecha/,
                    );

                await user.clear(
                    date,
                );

                await user.type(
                    date,
                    '2026-10-30',
                );

                await user.selectOptions(
                    within(
                        exceptionCard,
                    ).getByLabelText(
                        /^Tipo/,
                    ),
                    'ADD',
                );

                const from =
                    within(
                        exceptionCard,
                    ).getByLabelText(
                        /^Desde/,
                    );

                const to =
                    within(
                        exceptionCard,
                    ).getByLabelText(
                        /^Hasta/,
                    );

                await user.clear(
                    from,
                );

                await user.type(
                    from,
                    '16:00',
                );

                await user.clear(
                    to,
                );

                await user.type(
                    to,
                    '18:00',
                );

                await user.click(
                    within(
                        exceptionCard,
                    ).getByRole(
                        'button',
                        {
                            name:
                                'Guardar',
                        },
                    ),
                );

                expect(
                    await screen.findByText(
                        'Guardamos la excepción: se generaron 2 horas libres.',
                    ),
                ).toBeInTheDocument();

                expect(
                    state.server.requests(
                        'POST',
                        '/api/v1/tutor/availability/exceptions',
                    )[0].body,
                ).toEqual({
                    exceptionDate:
                        '2026-10-30',

                    kind:
                        'ADD',

                    startsAtTime:
                        '16:00:00',

                    endsAtTime:
                        '18:00:00',
                });
            },
        );

        it(
            'creates a pause, shows the active pause and can end it early without resending the pause',
            async () => {
                signIn(
                    'STUDENT',
                );

                const state =
                    serveAvailability();

                state.server
                    .on(
                        'POST',
                        '/api/v1/tutor/availability/pauses',
                        () => {
                            state.setPause({
                                paused:
                                    true,

                                pauseId:
                                    'c0000000-0000-4000-8000-000000000001',

                                startsOn:
                                    '2026-10-26',

                                endsOn:
                                    '2026-11-02',
                            });

                            state.setAvailability({
                                ...state.getAvailability(),

                                pauses: [
                                    {
                                        id:
                                            'c0000000-0000-4000-8000-000000000001',

                                        startsOn:
                                            '2026-10-26',

                                        endsOn:
                                            '2026-11-02',
                                    },
                                ],
                            });

                            return {
                                status: 201,

                                body: {
                                    id:
                                        'c0000000-0000-4000-8000-000000000001',

                                    startsOn:
                                        '2026-10-26',

                                    endsOn:
                                        '2026-11-02',

                                    withdrawnHours:
                                        6,

                                    bookedHoursKept:
                                        1,
                                },
                            };
                        },
                    )
                    .on(
                        'DELETE',
                        '/api/v1/tutor/availability/pauses/c0000000-0000-4000-8000-000000000001',
                        () => {
                            state.setPause({
                                paused:
                                    false,

                                pauseId:
                                    null,

                                startsOn:
                                    null,

                                endsOn:
                                    null,
                            });

                            state.setAvailability({
                                ...state.getAvailability(),

                                pauses: [],
                            });

                            return {
                                body: {
                                    pauseId:
                                        'c0000000-0000-4000-8000-000000000001',

                                    restoredHours:
                                        5,
                                },
                            };
                        },
                    );

                const {
                    user,
                } =
                    renderWithProviders(
                        <AvailabilityPage />,
                        {
                            route:
                                '/tutor/disponibilidad',
                        },
                    );

                const pauseCard =
                    await screen.findByRole(
                        'region',
                        {
                            name:
                                'Pausa',
                        },
                    );

                const from =
                    within(
                        pauseCard,
                    ).getByLabelText(
                        /^Desde/,
                    );

                const to =
                    within(
                        pauseCard,
                    ).getByLabelText(
                        /^Hasta/,
                    );

                await user.clear(
                    from,
                );

                await user.type(
                    from,
                    '2026-10-26',
                );

                await user.clear(
                    to,
                );

                await user.type(
                    to,
                    '2026-11-02',
                );

                await user.click(
                    within(
                        pauseCard,
                    ).getByRole(
                        'button',
                        {
                            name:
                                'Pausar',
                        },
                    ),
                );

                expect(
                    await screen.findByText(
                        'Pausa guardada: se retiraron 6 horas y se mantuvieron 1 ya reservada.',
                    ),
                ).toBeInTheDocument();

                expect(
                    await screen.findByText(
                        /En pausa hasta el 2 de noviembre/i,
                    ),
                ).toBeInTheDocument();

                expect(
                    within(
                        pauseCard,
                    ).getByRole(
                        'button',
                        {
                            name:
                                'Terminar la pausa',
                        },
                    ),
                ).toBeInTheDocument();

                await user.click(
                    within(
                        pauseCard,
                    ).getByRole(
                        'button',
                        {
                            name:
                                'Terminar la pausa',
                        },
                    ),
                );

                expect(
                    await screen.findByText(
                        'Terminamos la pausa: se restauraron 5 horas libres.',
                    ),
                ).toBeInTheDocument();

                expect(
                    await screen.findByText(
                        'No estás en pausa.',
                    ),
                ).toBeInTheDocument();

                expect(
                    state.server.requests(
                        'POST',
                        '/api/v1/tutor/availability/pauses',
                    ),
                ).toHaveLength(
                    1,
                );

                expect(
                    state.server.requests(
                        'DELETE',
                        '/api/v1/tutor/availability/pauses/c0000000-0000-4000-8000-000000000001',
                    ),
                ).toHaveLength(
                    1,
                );
            },
        );

        it(
            'keeps the forms usable when the declared-list GET fails',
            async () => {
                signIn(
                    'STUDENT',
                );

                const server =
                    fakeServer()
                        .on(
                            'GET',
                            '/api/v1/tutor/availability',
                            {
                                status:
                                    503,

                                body: {
                                    message:
                                        'Backend technical message',
                                },
                            },
                        )
                        .on(
                            'GET',
                            '/api/v1/tutor/availability/pauses/current',
                            {
                                body:
                                    initialPause(),
                            },
                        );

                renderWithProviders(
                    <AvailabilityPage />,
                    {
                        route:
                            '/tutor/disponibilidad',
                    },
                );

                expect(
                    await screen.findByText(
                        'No pudimos mostrar lo que ya declaraste',
                    ),
                ).toBeInTheDocument();

                expect(
                    screen.getByRole(
                        'button',
                        {
                            name:
                                'Agregar',
                        },
                    ),
                ).toBeEnabled();

                expect(
                    screen.queryByText(
                        'Backend technical message',
                    ),
                ).not.toBeInTheDocument();

                expect(
                    server.requests(
                        'GET',
                        '/api/v1/tutor/availability',
                    ),
                ).toHaveLength(
                    1,
                );
            },
        );
    },
);