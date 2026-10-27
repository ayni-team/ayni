import { HOME_BY_ROLE } from '../session/roles';
import { useSession } from '../session/useSession';
import { ButtonLink } from '../ui/Button';
import { ErrorState } from '../ui/ErrorState';

export function NotFound() {
  const session = useSession();
  return (
    <ErrorState
      title="No encontramos lo que buscas"
      description="La dirección no existe o cambió."
      action={<ButtonLink to={session ? HOME_BY_ROLE[session.role] : '/ingresar'}>Ir a mi inicio</ButtonLink>}
    />
  );
}
