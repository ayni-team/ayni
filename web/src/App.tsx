import { BrowserRouter, Route, Routes } from 'react-router-dom';
import Layout from './shared/ui/Layout';
import HomePage from './features/home/HomePage';
import TutoringPage from './features/booking/TutoringPage';

/**
 * The routes of the application.
 *
 * Every screen hangs from `Layout`, so the header is declared once. Adding a
 * screen means adding one line here and one folder under `features/`.
 */
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/tutorias" element={<TutoringPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
