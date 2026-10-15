import { BrowserRouter, Route, Routes } from 'react-router-dom';
import Layout from './shared/ui/Layout';
import HomePage from './features/home/HomePage';
import TutoringPage from './features/booking/TutoringPage';
import SignInPage from './features/access/SignInPage';
import ConfirmAccessPage from './features/access/ConfirmAccessPage';

/**
 * The routes of the application.
 *
 * Every screen hangs from `Layout`, so the header is declared once. Adding a
 * screen means adding one line here and one folder under `features/`.
 * `/access/confirm` is where the emailed access link lands.
 */
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/tutorias" element={<TutoringPage />} />
          <Route path="/ingresar" element={<SignInPage />} />
          <Route path="/access/confirm" element={<ConfirmAccessPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
