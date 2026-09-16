import { Link, Outlet } from 'react-router-dom';

/**
 * The frame every screen is drawn inside: header, navigation and content.
 *
 * Pages render through `Outlet`, so none of them repeats the header or has to
 * know it exists.
 */
export default function Layout() {
  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', color: '#1a1a1a' }}>
      <header
        style={{
          display: 'flex',
          alignItems: 'baseline',
          gap: '2rem',
          padding: '1rem 2rem',
          borderBottom: '1px solid #e5e5e5',
        }}
      >
        <Link to="/" style={{ fontWeight: 700, fontSize: '1.25rem', textDecoration: 'none', color: 'inherit' }}>
          Ayni
        </Link>
        <nav style={{ display: 'flex', gap: '1.25rem' }}>
          <Link to="/" style={{ color: '#444' }}>
            Inicio
          </Link>
          <Link to="/tutorias" style={{ color: '#444' }}>
            Tutorías
          </Link>
        </nav>
      </header>

      <main style={{ padding: '2rem', maxWidth: '900px', lineHeight: 1.6 }}>
        <Outlet />
      </main>
    </div>
  );
}
