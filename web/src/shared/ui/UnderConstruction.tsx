import { Badge } from './Badge';
import { EmptyState } from './EmptyState';
import { IconConstruction } from './icons';
import { PageHeader } from './PageHeader';

export interface UnderConstructionProps {
  /** The id of the screen in the guide ("FE-10"). */
  screen: string;
  title: string;
}

/**
 * The page a route shows until its screen is built. It names the screen so that whoever opens the
 * route knows which pull request will fill it. It goes away with the last stub.
 */
export function UnderConstruction({ screen, title }: UnderConstructionProps) {
  return (
    <>
      <PageHeader title={title} actions={<Badge tone="brand">{screen}</Badge>} />
      <EmptyState
        icon={<IconConstruction className="h-10 w-10" />}
        title="En construcción"
        description={`Esta pantalla (${screen}) todavía no está lista.`}
      />
    </>
  );
}
