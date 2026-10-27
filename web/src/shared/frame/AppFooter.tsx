import { useUniversity } from '../brand/BrandProvider';

export function AppFooter() {
  const university = useUniversity();
  return (
    <footer className="border-t border-gray-200 bg-white">
      <div className="mx-auto flex max-w-6xl flex-col gap-1 px-4 py-4 text-xs text-gray-600 sm:flex-row sm:justify-between">
        <p>Ayni · Banco de tiempo académico. Una hora enseñada vale una hora recibida.</p>
        {university?.name && <p>{university.name}</p>}
      </div>
    </footer>
  );
}
