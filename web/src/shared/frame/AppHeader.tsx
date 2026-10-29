import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useUniversity } from '../brand/BrandProvider';
import { useMe } from '../session/useMe';
import { useSession } from '../session/useSession';
import { useSignOut } from '../session/useSignOut';
import { Avatar } from '../ui/Avatar';
import { AyniLogo } from '../ui/AyniLogo';
import { Button } from '../ui/Button';
import { formatCredits } from '../ui/Credits';
import { IconCoins, IconLogOut } from '../ui/icons';
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
  if (balance.isPending) return <Skeleton className="h-8 w-24" label="Cargando tu saldo" />;
  if (balance.isError || balance.data?.available === undefined) return null;
  const text = formatCredits(balance.data.available);
  return (
    <Link
      to="/creditos"
      aria-label={`Tu saldo: ${text}`}
      className="inline-flex items-center gap-1.5 rounded-full bg-brand-soft px-3 py-1.5 text-sm font-medium text-brand-text hover:bg-gray-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
    >
      <IconCoins className="h-4 w-4" />
      <span className="tabular-nums">{text}</span>
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
    <div className="flex min-w-0 items-center gap-2">
      <Avatar name={name} photoUrl={me.data?.photoUrl} size="sm" decorative />
      <div className={compact ? 'min-w-0' : 'hidden min-w-0 lg:block'}>
        <p className="truncate text-sm font-medium text-ink">{name}</p>
        {caption && <p className="truncate text-xs text-ink-2">{caption}</p>}
      </div>
    </div>
  );
}

function SignOut({ fullWidth = false }: { fullWidth?: boolean }) {
  const { signOut, signingOut } = useSignOut();
  return (
    <Button
      variant="ghost"
      size="sm"
      fullWidth={fullWidth}
      loading={signingOut}
      icon={<IconLogOut className="h-4 w-4" />}
      onClick={() => void signOut()}
    >
      Salir
    </Button>
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
      <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3">
        <MobileMenu
          sections={sections}
          footer={
            <div className="flex items-center justify-between gap-3">
              <Person compact />
              <SignOut />
            </div>
          }
        />
        <Link
          to="/"
          className="flex min-w-0 items-center gap-3 rounded-md focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
          aria-label={university?.name ? `${university.name}, ir a mi inicio` : 'Ayni, ir a mi inicio'}
        >
          {university ? <UniversityLogo university={university} size="sm" /> : <AyniLogo className="text-brand-text" />}
          {university?.name && (
            <span className="hidden truncate text-sm font-semibold text-ink sm:block">{university.name}</span>
          )}
        </Link>
        <div className="ml-auto flex items-center gap-2 sm:gap-3">
          {student && <Balance />}
          {bell}
          <div className="hidden items-center gap-3 md:flex">
            <Person />
            <SignOut />
          </div>
        </div>
      </div>
      <AppNav sections={sections} />
    </header>
  );
}
