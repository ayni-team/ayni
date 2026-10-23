import { useSignOut } from '@/shared/session';
import { Button, Card, IconLogOut, PageHeader } from '@/shared/ui';

/** `/admin` · platform administrator. In this sprint the administrator can only sign out. */
export default function AdminPage() {
  const { signOut, signingOut } = useSignOut();
  return (
    <>
      <PageHeader title="Administración de la plataforma" />
      <Card>
        <p className="text-sm text-gray-700">En este sprint la administración solo puede cerrar sesión.</p>
        <Button className="mt-4" variant="secondary" loading={signingOut} icon={<IconLogOut className="h-4 w-4" />} onClick={() => void signOut()}>
          Cerrar sesión
        </Button>
      </Card>
    </>
  );
}
