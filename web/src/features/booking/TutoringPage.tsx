/**
 * Placeholder for the tutoring search and booking screen.
 *
 * It is here as the second route, so that the structure shows how a feature is
 * added rather than describing it. Replace this content when US01 and US03 are
 * implemented: the search goes through `api.get` and the results come from the
 * matching module.
 */
export default function TutoringPage() {
  return (
    <>
      <h1>Tutorías</h1>
      <p>
        Aquí irá la búsqueda de tutorías por curso y horario, y la reserva de un bloque. Todavía no
        está implementada: corresponde a US01, US02 y US03.
      </p>
      <p style={{ color: '#666' }}>
        Esta pantalla vive en <code>src/features/booking/</code>, el mismo nombre que el módulo del
        backend que la atiende.
      </p>
    </>
  );
}
