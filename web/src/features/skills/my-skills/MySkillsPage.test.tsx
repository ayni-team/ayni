import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import MySkillsPage from './MySkillsPage';

const SKILLS = '/api/v1/tutor/skills';
const SUGGESTIONS = '/api/v1/tutor/skills/suggestions';
const PROPOSALS = '/api/v1/tutor/skills/proposals';
const CATALOG = '/api/v1/catalog';
const CATEGORIES = '/api/v1/catalog/categories';

const ENABLED = {
  id: 's1',
  catalogItemId: 'c1',
  name: 'Algoritmos',
  scope: 'UNIVERSITY',
  courseCode: 'CC215',
  accreditedGrade: 18,
  status: 'ENABLED',
  statusChangedAt: '2026-10-23T21:00:00Z',
};

function setup(skills: unknown = [ENABLED]) {
  const server = fakeServer();
  serveFrame(server);
  signIn('STUDENT');
  server
    .on('GET', SKILLS, { body: skills })
    .on('GET', SUGGESTIONS, { body: [{ item: { id: 'c2', name: 'Física II', courseCode: 'MA462' }, grade: 16.5 }] })
    .on('GET', PROPOSALS, { body: [] })
    .on('GET', CATEGORIES, { body: [{ id: 'cat', name: 'Design' }] })
    .on('GET', CATALOG, { body: { items: [{ id: 'figma', name: 'Figma', scope: 'GLOBAL' }] } });
  return server;
}

function open() {
  return renderWithProviders(<MySkillsPage />, { route: '/tutor/habilidades' });
}

describe('FE-42 MySkillsPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows each skill with its status and the date it changed', async () => {
    setup();
    open();

    expect(await screen.findByText('Algoritmos')).toBeInTheDocument();
    expect(screen.getByText('Habilitada')).toBeInTheDocument();
    expect(screen.getByText(/CC215 · nota 18/)).toBeInTheDocument();
    expect(screen.getByText(/23 oct/)).toBeInTheDocument();
  });

  it('says there are none yet, and offers to retry when the list fails', async () => {
    const server = setup([]);
    const first = open();
    expect(await screen.findByText('Todavía no ofreces ninguna habilidad')).toBeInTheDocument();
    first.unmount();

    server.on('GET', SKILLS, { status: 500, body: { status: 500, message: 'boom' } });
    open();
    expect(await screen.findByText('No pudimos cargar tus habilidades')).toBeInTheDocument();
    expect(screen.queryByText('boom')).not.toBeInTheDocument();
  });

  it('offers a course and says it is enabled', async () => {
    const server = setup().on('POST', SKILLS, {
      status: 201,
      body: { id: 's2', name: 'Física II', status: 'ENABLED' },
    });
    const { user } = open();

    await user.click(await screen.findByRole('button', { name: 'Ofrecer Física II' }));

    expect(await screen.findByText('Ya puedes enseñar Física II')).toBeInTheDocument();
    expect(server.requests('POST', SKILLS)[0].body).toEqual({ catalogItemId: 'c2' });
  });

  it('says why a course did not enable', async () => {
    setup().on('POST', SKILLS, { status: 201, body: { id: 's2', name: 'Física II', status: 'REJECTED' } });
    const { user } = open();

    await user.click(await screen.findByRole('button', { name: 'Ofrecer Física II' }));

    expect(await screen.findByText(/su nota no alcanza el mínimo de tu universidad/)).toBeInTheDocument();
  });

  it('asks before withdrawing, and says confirmed tutorings stand', async () => {
    const server = setup().on('DELETE', `${SKILLS}/s1`, { status: 204 });
    const { user } = open();

    await user.click(await screen.findByRole('button', { name: 'Retirar Algoritmos' }));
    const dialog = within(screen.getByRole('dialog'));
    expect(dialog.getByText(/Las tutorías ya confirmadas se mantienen/)).toBeInTheDocument();
    expect(server.requests('DELETE')).toHaveLength(0);

    await user.click(dialog.getByRole('button', { name: 'Retirar' }));

    await waitFor(() => expect(server.requests('DELETE', `${SKILLS}/s1`)).toHaveLength(1));
  });

  it('shows the reason of a rejected tool and lets the person send evidence again', async () => {
    setup([
      {
        id: 's3',
        catalogItemId: 'figma',
        name: 'Figma',
        scope: 'GLOBAL',
        status: 'REJECTED',
        review: { status: 'REJECTED', decisionReason: 'El portafolio no se abre' },
      },
    ]);
    const { user } = open();

    expect(await screen.findByText('El portafolio no se abre')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Subir nueva evidencia' }));
    expect(screen.getByRole('dialog', { name: 'Evidencia de Figma' })).toBeInTheDocument();
  });

  it('checks the files before sending, and sends them with the note', async () => {
    const server = setup([{ id: 's3', catalogItemId: 'figma', name: 'Figma', scope: 'GLOBAL', status: 'REJECTED' }]).on(
      'POST',
      `${SKILLS}/figma/validation`,
      { status: 201, body: {} },
    );
    const { user } = open();

    await user.click(await screen.findByRole('button', { name: 'Subir nueva evidencia' }));
    const dialog = within(screen.getByRole('dialog'));
    await user.click(dialog.getByRole('button', { name: 'Enviar evidencia' }));
    expect(dialog.getByText('Elige al menos un archivo')).toBeInTheDocument();
    expect(server.requests('POST', `${SKILLS}/figma/validation`)).toHaveLength(0);

    const input = dialog.getByLabelText('Archivos');
    await user.upload(input, new File(['x'], 'portafolio.pdf', { type: 'application/pdf' }));
    await user.type(dialog.getByLabelText('Nota para quien revisa (opcional)'), 'Mi trabajo');
    await user.click(dialog.getByRole('button', { name: 'Enviar evidencia' }));

    await waitFor(() => expect(server.requests('POST', `${SKILLS}/figma/validation`)).toHaveLength(1));
    const form = server.requests('POST', `${SKILLS}/figma/validation`)[0].body as FormData;
    expect((form.get('files') as File).name).toBe('portafolio.pdf');
    expect(form.get('note')).toBe('Mi trabajo');
  });

  it('challenges a proposal with what looks like it, and lets the person insist', async () => {
    const server = setup();
    server.on('POST', PROPOSALS, (request) =>
      (request.body as { confirmDistinct: boolean }).confirmDistinct
        ? { status: 201, body: { id: 'p1', name: 'Figmita', status: 'PROPOSED' } }
        : { status: 409, body: { status: 409, message: 'similar', similar: [{ id: 'figma', name: 'Figma' }] } },
    );
    const { user } = open();

    await user.type(await screen.findByLabelText('Nombre'), 'Figmita');
    await waitFor(() => expect(screen.getByRole('option', { name: 'Diseño' })).toBeInTheDocument());
    await user.selectOptions(screen.getByLabelText('Categoría'), 'Diseño');
    await user.click(screen.getByRole('button', { name: 'Proponer' }));

    expect(await screen.findByText('¿Es alguna de estas?')).toBeInTheDocument();
    expect(screen.queryByText('similar')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'No, es otra: proponerla igual' }));

    expect(await screen.findByText('Enviamos tu propuesta. Tu coordinación la revisará.')).toBeInTheDocument();
    expect(server.requests('POST', PROPOSALS)[1].body).toMatchObject({
      name: 'Figmita',
      categoryId: 'cat',
      confirmDistinct: true,
    });
  });

  it('says a proposal with the name of a tool of the catalogue has nothing to insist on', async () => {
    setup().on('POST', PROPOSALS, { status: 409, body: { status: 409, message: 'the catalogue already has it' } });
    const { user } = open();

    await user.type(await screen.findByLabelText('Nombre'), 'Figma');
    await waitFor(() => expect(screen.getByRole('option', { name: 'Diseño' })).toBeInTheDocument());
    await user.selectOptions(screen.getByLabelText('Categoría'), 'Diseño');
    await user.click(screen.getByRole('button', { name: 'Proponer' }));

    expect(await screen.findByText(/Esa herramienta ya está en el catálogo/)).toBeInTheDocument();
    expect(screen.queryByText(/the catalogue/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Proponer' })).toBeInTheDocument();
  });
});
