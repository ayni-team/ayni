import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useUniversity } from '../brand/BrandProvider';
import { useMe } from '../session/useMe';
import { useSession } from '../session/useSession';
import { useSignOut } from '../session/useSignOut';
import { Avatar } from '../ui/Avatar';
import { AyniLogo } from '../ui/AyniLogo';
import { CreditCoin } from '../ui/Credits';
import { IconLogOut } from '../ui/icons';
import { Spinner } from '../ui/Spinner';
import { Skeleton } from '../ui/Skeleton';
import { UniversityLogo } from '../ui/UniversityLogo';
import { AppNav, type NavSection } from './AppNav';
import { MobileMenu } from './MobileMenu';
import { useBalance } from './useBalance';

export interface AppHeaderProps {
  sections: NavSection[];
  /** The notices' bell, which belongs to features/notifications. */
  bell?: ReactNode;
}

/** "Ing. de Software · 4.° ciclo" for a student; nothing for a coordinator. */
export function profileCaption(me: { career?: string; currentCycle?: number } | undefined): string | null {
  if (!me?.career) return null;
  return me.currentCycle ? `${me.career} · ${me.currentCycle}.° ciclo` : me.career;
}

function Balance() {
  const balance = useBalance();
  if (balance.isPending) return <Skeleton className="h-9 w-28 rounded-full" label="Cargando tu saldo" />;
  if (balance.isError || balance.data?.available === undefined) return null;
  const amount = balance.data.available;
  const unit = amount === 1 ? 'crédito' : 'créditos';
  const number = new Intl.NumberFormat('es-PE').format(amount);
  return (
    <Link
      to="/creditos"
      aria-label={`Tu saldo: ${number} ${unit}`}
      className="inline-flex h-9 items-center gap-2 rounded-full bg-credit-soft pl-1.5 pr-3.5 text-sm hover:brightness-95 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
    >
      <CreditCoin className="h-6 w-6" />
      <span className="font-semibold tabular-nums text-ink">{number}</span>
      <span className="hidden font-medium text-credit-text sm:inline">{unit}</span>
    </Link>
  );
}

function Person({ compact = false }: { compact?: boolean }) {
  const session = useSession();
  const me = useMe();
  const name = session?.role === 'PLATFORM_ADMIN' ? 'Administración de Ayni' : me.data?.fullName;
  const caption = session?.role === 'PLATFORM_ADMIN' ? null : profileCaption(me.data);
  if (!name) return null;
  return (
    <div className="flex min-w-0 items-center gap-2.5">
      <Avatar name={name} photoUrl={me.data?.photoUrl} decorative own />
      <div className={compact ? 'min-w-0' : 'hidden min-w-0 lg:block'}>
        <p className="truncate text-sm font-semibold text-ink">{name}</p>
        {caption && <p className="truncate text-xs text-ink-2">{caption}</p>}
      </div>
    </div>
  );
}

/** The red square of the design: the way out, named for those who cannot see the icon. */
function SignOut() {
  const { signOut, signingOut } = useSignOut();
  return (
    <button
      type="button"
      onClick={() => void signOut()}
      disabled={signingOut}
      aria-busy={signingOut || undefined}
      aria-label="Cerrar sesión"
      title="Cerrar sesión"
      className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand text-brand-secondary hover:bg-brand-pressed focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand disabled:opacity-60"
    >
      {signingOut ? <Spinner size="sm" decorative /> : <IconLogOut className="h-5 w-5" />}
    </button>
  );
}

/** The way out at the end of the phone menu: a row in red, like the frame. */
function SignOutRow() {
  const { signOut, signingOut } = useSignOut();
  return (
    <button
      type="button"
      onClick={() => void signOut()}
      disabled={signingOut}
      aria-busy={signingOut || undefined}
      className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-brand-text hover:bg-brand-soft focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand disabled:opacity-60"
    >
      {signingOut ? <Spinner size="sm" decorative /> : <IconLogOut className="h-5 w-5" />}
      Cerrar sesión
    </button>
  );
}

/** The university as the design draws it: its logo and its name on two short lines. */
function University({ university }: { university: NonNullable<ReturnType<typeof useUniversity>> }) {
  return (
    <div className="flex min-w-0 items-center gap-3">
      <UniversityLogo university={university} size="md" />
      {university.name && (
        <span className="hidden max-w-[10.5rem] text-xs font-medium leading-snug text-ink lg:block">
          {university.name}
        </span>
      )}
    </div>
  );
}

/**
 * The header of every signed-in screen: the university's logo, the navigation of the role, the
 * student's balance, the bell, the person and the way out.
 */
export function AppHeader({ sections, bell }: AppHeaderProps) {
  const session = useSession();
  const university = useUniversity();
  const student = session?.role === 'STUDENT';

  return (
    <header className="relative border-b border-line bg-white">
      <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3 md:py-4">
        <Link
          to="/"
          aria-label="Ayni, ir a mi inicio"
          className="shrink-0 rounded-md focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
        >
          <AyniLogo className="h-7 md:h-10" />
        </Link>
        {university && (
          <div className="md:hidden">
            <UniversityLogo university={university} size="sm" />
          </div>
        )}
        <div className="ml-auto flex items-center gap-2 md:gap-3">
          {university && (
            <div className="hidden items-center gap-4 border-r border-line pr-4 md:flex">
              <University university={university} />
            </div>
          )}
          {student && <Balance />}
          {bell && <div className="rounded-xl border border-line bg-white">{bell}</div>}
          <div className="hidden items-center gap-3 md:flex">
            <Person />
            <SignOut />
          </div>
          <MobileMenu
            sections={sections}
            person={<Person compact />}
            extra={[
              ...(bell ? [{ to: '/avisos', label: 'Avisos' }] : []),
              ...(student ? [{ to: '/perfil', label: 'Mi perfil' }] : []),
            ]}
            signOut={<SignOutRow />}
          />
        </div>
      </div>
      <AppNav sections={sections} />
    </header>
  );
}
