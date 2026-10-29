import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/shared/api';
import { fakeServer } from '@/test/fakeServer';
import { ANA, UPC, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import OnboardingPage from './OnboardingPage';

const ALGORITHMS: Schema<'CatalogItem'> = {
  id: 'b0000000-0000-4000-8000-000000000201',
  scope: 'UNIVERSITY',
  name: 'Algoritmos',
  courseCode: 'CC215',
};

const MATH: Schema<'CatalogItem'> = {
  id: 'b0000000-0000-4000-8000-000000000202',
  scope: 'UNIVERSITY',
  name: 'Matemática Básica',
  courseCode: 'MA420',
};

const REQUIREMENTS: Schema<'CatalogItem'> = {
  id: 'b0000000-0000-4000-8000-000000000203',
  scope: 'UNIVERSITY',
  name: 'Especificación y Análisis de Requerimientos',
  courseCode: 'SI397',
};

const SUGGESTIONS: Schema<'SuggestedCourse'>[] = [
  {
    item: REQUIREMENTS,
    grade: 18.88,
  },
  {
    item: MATH,
    grade: 17.38,
  },
];

function student(step: NonNullable<Schema<'MyProfileView'>['onboardingStep']>): Schema<'MyProfileView'> {
  return {
    ...ANA,
    career: 'Ingeniería de Software',
    currentTerm: '2026-2',
    onboardingStep: step,
  };
}

function baseServer(initial: Schema<'MyProfileView'>) {
  let me = initial;

  const server = fakeServer()
    .on('GET', '/api/v1/university', {
      body: UPC,
    })
    .on('GET', '/api/v1/me', () => ({
      body: me,
    }));

  return {
    server,

    setMe(next: Schema<'MyProfileView'>) {
      me = next;
    },

    getMe() {
      return me;
    },
  };
}

describe('FE-03 onboarding', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('starts in PROFILE, keeps academic data read-only and advances with the optional fields', async () => {
    signIn('STUDENT');

    const state = baseServer(student('PROFILE'));

    state.server.on('PUT', '/api/v1/me/profile', ({ body }) => {
      const request = body as Schema<'UpdateMyProfileRequest'>;

      state.setMe({
        ...state.getMe(),
        photoUrl: request.photoUrl,
        bio: request.bio,
        onboardingStep: 'ACADEMIC_RECORD',
      });

      return {
        body: state.getMe(),
      };
    });

    const { user } = renderWithProviders(<OnboardingPage />, {
      route: '/onboarding',
    });

    expect(
      await screen.findByRole('heading', {
        name: 'Bienvenida a Ayni, Ana',
      }),
    ).toBeInTheDocument();

    expect(screen.getByText('U202400001')).toBeInTheDocument();

    expect(screen.getByText('Ingeniería de Software')).toBeInTheDocument();

    expect(
      screen.queryByRole('textbox', {
        name: 'Nombre',
      }),
    ).not.toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: 'Foto (URL, opcional)',
      }),
      'https://example.test/ana.jpg',
    );

    await user.type(
      screen.getByRole('textbox', {
        name: 'Presentación (opcional)',
      }),
      'Aprendo mejor con ejemplos.',
    );

    await user.click(
      screen.getByRole('button', {
        name: 'Continuar',
      }),
    );

    expect(
      await screen.findByRole('heading', {
        name: 'Tu récord académico',
      }),
    ).toBeInTheDocument();

    expect(state.server.requests('PUT', '/api/v1/me/profile')[0].body).toEqual({
      photoUrl: 'https://example.test/ana.jpg',
      bio: 'Aprendo mejor con ejemplos.',
    });
  });

  it('imports the academic record and shows its suggestions', async () => {
    signIn('STUDENT');

    const state = baseServer(student('ACADEMIC_RECORD'));

    let imported = false;

    state.server
      .on('GET', '/api/v1/tutor/skills/suggestions', () => ({
        body: imported ? SUGGESTIONS : [],
      }))
      .on('POST', '/api/v1/me/academic-record/import', () => {
        imported = true;

        state.setMe({
          ...state.getMe(),
          onboardingStep: 'LEARNING_INTERESTS',
        });

        return {
          status: 204,
        };
      });

    const { user } = renderWithProviders(<OnboardingPage />, {
      route: '/onboarding',
    });

    expect(
      await screen.findByRole('heading', {
        name: 'Tu récord académico',
      }),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {
        name: 'Importar mi récord',
      }),
    );

    expect(await screen.findByText('Especificación y Análisis de Requerimientos')).toBeInTheDocument();

    expect(screen.getByText('Nota 18.88')).toBeInTheDocument();

    expect(state.server.requests('POST', '/api/v1/me/academic-record/import')).toHaveLength(1);
  });

  it('continues from the imported record to interests', async () => {
    signIn('STUDENT');

    const state = baseServer(student('ACADEMIC_RECORD'));

    state.server
      .on('GET', '/api/v1/tutor/skills/suggestions', {
        body: SUGGESTIONS,
      })
      .on('POST', '/api/v1/me/academic-record/import', () => {
        state.setMe({
          ...state.getMe(),
          onboardingStep: 'LEARNING_INTERESTS',
        });

        return {
          status: 204,
        };
      })
      .on('GET', '/api/v1/catalog', {
        body: {
          items: [ALGORITHMS, MATH],
          page: 0,
          size: 20,
          totalElements: 2,
          totalPages: 1,
        },
      });

    const { user } = renderWithProviders(<OnboardingPage />, {
      route: '/onboarding',
    });

    await screen.findByRole('heading', {
      name: 'Tu récord académico',
    });

    await user.click(
      screen.getByRole('button', {
        name: 'Importar mi récord',
      }),
    );

    await screen.findByText('Especificación y Análisis de Requerimientos');

    await user.click(
      screen.getByRole('button', {
        name: 'Continuar',
      }),
    );

    expect(
      await screen.findByRole('heading', {
        name: 'Lo que necesitas y lo que puedes enseñar',
      }),
    ).toBeInTheDocument();
  });

  it('resumes directly in LEARNING_INTERESTS and always sends both lists', async () => {
    signIn('STUDENT');

    const state = baseServer(student('LEARNING_INTERESTS'));

    state.server
      .on('GET', '/api/v1/tutor/skills/suggestions', {
        body: SUGGESTIONS,
      })
      .on('GET', '/api/v1/catalog', {
        body: {
          items: [ALGORITHMS, MATH],
          page: 0,
          size: 20,
          totalElements: 2,
          totalPages: 1,
        },
      })
      .on('POST', '/api/v1/me/onboarding/interests', () => {
        state.setMe({
          ...state.getMe(),
          onboardingStep: 'COMPLETED',
        });

        return {
          body: {
            learning: [ALGORITHMS],
            teaching: [
              {
                id: 'c0000000-0000-4000-8000-000000000001',
                catalogItemId: REQUIREMENTS.id,
                status: 'ENABLED',
                accreditationPath: 'ACADEMIC_RECORD',
                accreditedGrade: 18.88,
              },
            ],
          },
        };
      });

    const { user, location } = renderWithProviders(<OnboardingPage />, {
      route: '/onboarding',
    });

    expect(
      await screen.findByRole('heading', {
        name: 'Lo que necesitas y lo que puedes enseñar',
      }),
    ).toBeInTheDocument();

    await user.click(
      await screen.findByRole('checkbox', {
        name: 'Algoritmos · CC215',
      }),
    );

    await user.click(
      await screen.findByRole('checkbox', {
        name: 'Especificación y Análisis de Requerimientos · SI397',
      }),
    );

    await user.click(
      screen.getByRole('button', {
        name: 'Terminar',
      }),
    );

    await waitFor(() => expect(location()).toBe('/buscar'));

    expect(state.server.requests('POST', '/api/v1/me/onboarding/interests')[0].body).toEqual({
      learning: [ALGORITHMS.id],
      teaching: [REQUIREMENTS.id],
    });
  });

  it('asks the catalogue to leave out what I already approved', async () => {
    signIn('STUDENT');

    const state = baseServer(student('LEARNING_INTERESTS'));

    state.server
      .on('GET', '/api/v1/tutor/skills/suggestions', {
        body: SUGGESTIONS,
      })
      .on('GET', '/api/v1/catalog', {
        body: {
          items: [ALGORITHMS],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
      });

    renderWithProviders(<OnboardingPage />, {
      route: '/onboarding',
    });

    await screen.findByRole('heading', {
      name: 'Lo que necesitas y lo que puedes enseñar',
    });

    expect(state.server.requests('GET', '/api/v1/catalog')[0].query.get('excludeApproved')).toBe('true');
  });

  it('shows a message of its own when every course was already approved', async () => {
    signIn('STUDENT');

    const state = baseServer(student('LEARNING_INTERESTS'));

    state.server
      .on('GET', '/api/v1/tutor/skills/suggestions', {
        body: SUGGESTIONS,
      })
      .on('GET', '/api/v1/catalog', {
        body: {
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        },
      });

    renderWithProviders(<OnboardingPage />, {
      route: '/onboarding',
    });

    await screen.findByRole('heading', {
      name: 'Lo que necesitas y lo que puedes enseñar',
    });

    expect(
      await screen.findByText(
        'Ya aprobaste todos los cursos del catálogo: no tienes nada pendiente en qué pedir ayuda.',
      ),
    ).toBeInTheDocument();
  });

  it('keeps the search-miss message when a search finds nothing, instead of the approved-everything one', async () => {
    signIn('STUDENT');

    const state = baseServer(student('LEARNING_INTERESTS'));

    state.server
      .on('GET', '/api/v1/tutor/skills/suggestions', {
        body: SUGGESTIONS,
      })
      .on('GET', '/api/v1/catalog', {
        body: {
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        },
      });

    const { user } = renderWithProviders(<OnboardingPage />, {
      route: '/onboarding',
    });

    await screen.findByRole('heading', {
      name: 'Lo que necesitas y lo que puedes enseñar',
    });

    await user.type(
      screen.getByRole('searchbox', {
        name: 'Buscar en el catálogo',
      }),
      'zzz',
    );

    expect(await screen.findByText('No encontramos cursos con esa búsqueda.')).toBeInTheDocument();
  });

  it('sends empty learning and teaching lists when nothing is selected', async () => {
    signIn('STUDENT');

    const state = baseServer(student('LEARNING_INTERESTS'));

    state.server
      .on('GET', '/api/v1/tutor/skills/suggestions', {
        body: SUGGESTIONS,
      })
      .on('GET', '/api/v1/catalog', {
        body: {
          items: [ALGORITHMS],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
      })
      .on('POST', '/api/v1/me/onboarding/interests', () => {
        state.setMe({
          ...state.getMe(),
          onboardingStep: 'COMPLETED',
        });

        return {
          body: {
            learning: [],
            teaching: [],
          },
        };
      });

    const { user, location } = renderWithProviders(<OnboardingPage />, {
      route: '/onboarding',
    });

    await screen.findByRole('heading', {
      name: 'Lo que necesitas y lo que puedes enseñar',
    });

    await user.click(
      screen.getByRole('button', {
        name: 'Terminar',
      }),
    );

    await waitFor(() => expect(location()).toBe('/buscar'));

    expect(state.server.requests('POST', '/api/v1/me/onboarding/interests')[0].body).toEqual({
      learning: [],
      teaching: [],
    });
  });

  it('explains when a requested teaching course was not enabled', async () => {
    signIn('STUDENT');

    const state = baseServer(student('LEARNING_INTERESTS'));

    state.server
      .on('GET', '/api/v1/tutor/skills/suggestions', {
        body: SUGGESTIONS,
      })
      .on('GET', '/api/v1/catalog', {
        body: {
          items: [ALGORITHMS],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
      })
      .on('POST', '/api/v1/me/onboarding/interests', () => {
        state.setMe({
          ...state.getMe(),
          onboardingStep: 'COMPLETED',
        });

        return {
          body: {
            learning: [],
            teaching: [],
          },
        };
      });

    const { user, location } = renderWithProviders(<OnboardingPage />, {
      route: '/onboarding',
    });

    await screen.findByRole('heading', {
      name: 'Lo que necesitas y lo que puedes enseñar',
    });

    await user.click(
      await screen.findByRole('checkbox', {
        name: 'Matemática Básica · MA420',
      }),
    );

    await user.click(
      screen.getByRole('button', {
        name: 'Terminar',
      }),
    );

    await waitFor(() => expect(location()).toBe('/buscar'));

    expect(
      await screen.findByText('Matemática Básica no quedó habilitado: su nota no alcanza el mínimo de la universidad.'),
    ).toBeInTheDocument();
  });

  it('redirects a completed student to search', async () => {
    signIn('STUDENT');

    baseServer(student('COMPLETED'));

    const { location } = renderWithProviders(<OnboardingPage />, {
      route: '/onboarding',
    });

    await waitFor(() => expect(location()).toBe('/buscar'));
  });

  it('on 409 reloads the server step and returns to the missing step', async () => {
    signIn('STUDENT');

    const state = baseServer(student('LEARNING_INTERESTS'));

    state.server
      .on('GET', '/api/v1/tutor/skills/suggestions', {
        body: SUGGESTIONS,
      })
      .on('GET', '/api/v1/catalog', {
        body: {
          items: [ALGORITHMS],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
      })
      .on('POST', '/api/v1/me/onboarding/interests', () => {
        state.setMe({
          ...state.getMe(),
          onboardingStep: 'ACADEMIC_RECORD',
        });

        return {
          status: 409,
          body: {
            status: 409,
            message: 'Academic record step is missing',
          },
        };
      });

    const { user } = renderWithProviders(<OnboardingPage />, {
      route: '/onboarding',
    });

    await screen.findByRole('heading', {
      name: 'Lo que necesitas y lo que puedes enseñar',
    });

    await user.click(
      screen.getByRole('button', {
        name: 'Terminar',
      }),
    );

    expect(
      await screen.findByRole('heading', {
        name: 'Tu récord académico',
      }),
    ).toBeInTheDocument();
  });
});
