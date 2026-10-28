import { useEffect, useRef, useState } from 'react';
import {
    useNavigate,
    useSearchParams,
} from 'react-router-dom';
import {
    api,
    isApiError,
} from '@/shared/api';
import {
    rememberSession,
} from '@/shared/session';
import {
    AyniLogo,
    ButtonLink,
    Card,
    ErrorState,
    Spinner,
} from '@/shared/ui';

type Failure =
    | 'spent'
    | 'confirm'
    | 'profile';

type Phase =
    | 'confirming'
    | 'profile';

/**
 * FE-02 · `/access/confirm?token=` · public.
 *
 * Exchanges the emailed one-use token for a session and sends each role to
 * its start. A student's MyProfileView decides whether onboarding still has
 * to be completed.
 *
 * The confirmation POST is protected against React StrictMode's development
 * double effect, so the same component never spends the link twice.
 *
 * If the session was already opened and GET /me fails, retrying reloads only
 * the profile. The one-use link is never posted again.
 */
export default function ConfirmAccessPage() {
    const [params] =
        useSearchParams();

    const navigate =
        useNavigate();

    const [
        failure,
        setFailure,
    ] = useState<Failure | null>(
        null,
    );

    const [
        phase,
        setPhase,
    ] = useState<Phase>(
        'confirming',
    );

    const [
        confirmAttempt,
        setConfirmAttempt,
    ] = useState(0);

    const [
        profileAttempt,
        setProfileAttempt,
    ] = useState(0);

    const [
        studentSessionOpened,
        setStudentSessionOpened,
    ] = useState(false);

    /*
     * StrictMode executes effects twice in development.
     * The token is one-use, so the POST must happen only once per attempt.
     */
    const confirmationSent =
        useRef(-1);

    /*
     * The student's profile request is also guarded so StrictMode does not
     * duplicate it.
     */
    const profileRequested =
        useRef(-1);

    useEffect(() => {
        /*
         * Once a STUDENT session exists, never confirm the magic link again.
         * From this point we only need MyProfileView to choose the destination.
         */
        if (
            studentSessionOpened
        ) {
            if (
                profileRequested.current ===
                profileAttempt
            ) {
                return;
            }

            profileRequested.current =
                profileAttempt;

            setFailure(null);
            setPhase('profile');

            api
                .get('/api/v1/me')
                .then(
                    (profile) => {
                        navigate(
                            profile.onboardingStep ===
                            'COMPLETED'
                                ? '/buscar'
                                : '/onboarding',
                            {
                                replace: true,
                            },
                        );
                    },
                )
                .catch(() => {
                    setFailure(
                        'profile',
                    );
                });

            return;
        }

        const token =
            params.get('token');

        if (!token) {
            setFailure('spent');
            return;
        }

        if (
            confirmationSent.current ===
            confirmAttempt
        ) {
            return;
        }

        confirmationSent.current =
            confirmAttempt;

        setFailure(null);
        setPhase('confirming');

        api
            .post(
                '/api/v1/access/confirm',
                {
                    body: {
                        token,
                    },
                },
            )
            .then(
                (answer) => {
                    const session =
                        rememberSession(
                            answer,
                        );

                    if (
                        session.role ===
                        'PLATFORM_ADMIN'
                    ) {
                        navigate(
                            '/admin',
                            {
                                replace: true,
                            },
                        );

                        return;
                    }

                    if (
                        session.role ===
                        'COORDINATOR'
                    ) {
                        navigate(
                            '/coordinador/reconocimiento',
                            {
                                replace: true,
                            },
                        );

                        return;
                    }

                    setStudentSessionOpened(
                        true,
                    );
                },
            )
            .catch(
                (
                    error: unknown,
                ) => {
                    /*
                     * 400: unknown, expired or already-used token.
                     * 409: the token was used concurrently.
                     *
                     * Never show the backend's English message.
                     */
                    if (
                        isApiError(
                            error,
                        ) &&
                        (
                            error.status ===
                            400 ||
                            error.status ===
                            409
                        )
                    ) {
                        setFailure(
                            'spent',
                        );

                        return;
                    }

                    setFailure(
                        'confirm',
                    );
                },
            );
    }, [
        params,
        navigate,
        confirmAttempt,
        profileAttempt,
        studentSessionOpened,
    ]);

    if (
        failure === 'spent'
    ) {
        return (
            <div className="flex w-full flex-col items-center gap-7">
                <AyniLogo className="text-gray-950" />

                <ErrorState
                    title="El enlace venció o ya se usó"
                    description="Pide uno nuevo: llega a tu correo institucional y funciona una sola vez."
                    action={
                        <ButtonLink to="/ingresar">
                            Pedir otro enlace
                        </ButtonLink>
                    }
                />
            </div>
        );
    }

    if (
        failure === 'confirm'
    ) {
        return (
            <div className="flex w-full flex-col items-center gap-7">
                <AyniLogo className="text-gray-950" />

                <ErrorState
                    title="No pudimos abrir tu sesión"
                    description="Hubo un problema al validar el enlace. Inténtalo de nuevo."
                    onRetry={() => {
                        setFailure(null);

                        setConfirmAttempt(
                            (
                                value,
                            ) =>
                                value + 1,
                        );
                    }}
                />
            </div>
        );
    }

    if (
        failure === 'profile'
    ) {
        return (
            <div className="flex w-full flex-col items-center gap-7">
                <AyniLogo className="text-gray-950" />

                <ErrorState
                    title="Tu sesión ya está abierta"
                    description="No pudimos cargar tu perfil para continuar. Tu enlace no se volverá a usar."
                    onRetry={() => {
                        setFailure(null);

                        setProfileAttempt(
                            (
                                value,
                            ) =>
                                value + 1,
                        );
                    }}
                />
            </div>
        );
    }

    return (
        <div className="flex w-full flex-col items-center gap-7">
            <AyniLogo className="text-gray-950" />

            <Card className="flex w-full flex-col items-center gap-4 py-10 text-center">
                <Spinner
                    size="lg"
                    label={
                        phase ===
                        'confirming'
                            ? 'Abriendo tu sesión'
                            : 'Cargando tu perfil'
                    }
                />

                <div>
                    <h1 className="text-xl font-semibold text-gray-900">
                        {phase ===
                        'confirming'
                            ? 'Abriendo tu sesión'
                            : 'Preparando Ayni para ti'}
                    </h1>

                    <p className="mt-2 text-sm text-gray-600">
                        {phase ===
                        'confirming'
                            ? 'Estamos validando el enlace de un solo uso.'
                            : 'Ya abrimos tu sesión. Estamos revisando dónde debes continuar.'}
                    </p>
                </div>
            </Card>
        </div>
    );
}