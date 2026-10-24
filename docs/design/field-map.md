# Mapa de campos del diseño · Ayni Sprint 2

Cada dato que muestra un frame y de dónde sale. La única fuente de campos es `ayni-openapi.json`
(snapshot del 22 de octubre de 2026). Un dato sin origen no está en ningún frame; lo que se dibujó y
todavía no existe en el backend está en la sección final, **Requiere backend**, para que el líder
decida.

- Archivo: [Figma · Ayni](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título)
- Página nueva: [Ayni · Sprint 2](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=28-165)
- Página existente corregida: [Ayni · Web](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=0-1)

## Cómo leer las tablas

| Marca | Significa |
|---|---|
| `GET /x → Esquema.campo` | El dato viene tal cual de ese campo |
| **derivado** | Se calcula en la web a partir de campos que existen (iniciales de un nombre, "hace 5 min", "Solo esta hora") |
| **regla** | Texto fijo que repite una regla del backend (`docs/backend-guide.md`), no un dato |
| **navegación** | Llega en el estado de navegación desde otra pantalla; ninguna llamada de esta pantalla lo trae |
| **RB-n** | Requiere backend: ver la sección final |

Todos los instantes se muestran en la zona de la universidad (`timezone` de la respuesta, o
`MyUniversityView.timezone`). "1 crédito = 1 hora" es una regla del contrato.

Datos de demostración: solo personas y cursos de `academic-data/UPC.json` y `USIL.json`. Ana
Torres (U202400001) es la estudiante, Bruno Salas (U202300002) el tutor de las pantallas de tutor,
y en USIL Lucía Paredes y Mateo Ríos. Los tutores de Buscar (Sofía Cornejo, Ana Flores, Paula
Palomino, Carla Lazo, Julián Arce) tienen nota ≥ 15 en el curso que enseñan. Las cifras de
estrellas, horas y créditos son valores de ejemplo de los campos, no datos inventados: el backend
las calcula.

---

## Fundamentos

### Variables de marca · colección `Ayni · Marca` (modos UPC y USIL)

| Variable | UPC | USIL | Origen | CSS |
|---|---|---|---|---|
| `brand/primary` | `#E42322` | `#07256D` | `MyUniversityView.primaryColor` (o `UniversityIdentityView.primaryColor` antes de ingresar) | `--brand-primary` |
| `brand/on-primary` | `#FFFFFF` | `#FFFFFF` | `MyUniversityView.secondaryColor` | `--brand-secondary` |
| `brand/primary-soft` | `#FCE9E9` | `#E6E9F0` | **derivado**: primary al 10 % sobre blanco | `--brand-primary-soft` |
| `brand/primary-pressed` | `#B61C1B` | `#051B4E` | **derivado**: primary oscurecido | `--brand-primary-pressed` |
| `brand/text` | alias de pressed | alias de primary | **derivado** | `--brand-text` |

`brand/text` se agregó a las cuatro pedidas porque el rojo UPC puro da 4,27:1 sobre el fondo gris
(`#F6F6F9`) y 3,94:1 sobre el tinte suave: no llega a 4,5. Con el tono presionado da 6,1 y 5,7. En
USIL el azul pasa en todo.

Se usa la marca solo en: logo de la universidad en la cabecera, pestaña activa, botones primarios,
enlaces, barras de progreso, insignias seleccionadas (Badge `Tone=Brand`), foco de los campos, el
día seleccionado y el contador de la campana. Neutros, tipografía y espaciados no cambian.

El dashboard en los dos modos, lado a lado:
[UPC](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=34-412) ·
[USIL](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=34-597).
Cualquier frame cambia de universidad eligiendo el modo de `Ayni · Marca` en el panel derecho; el
logo es la variante de `UniversityLogo`.

### Componentes (1 a 1 con `shared/ui`)

[Componentes del contrato](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=32-165)
y, en Ayni · Web, `Button`, `Badge` y `NavTab`.

| shared/ui | Figma |
|---|---|
| Button | `Button` · Variant = Primary, Secondary, Danger, Ghost × State = Default, Pressed, Loading, Disabled. Primary es la marca; Danger va con contorno para no confundirse con el rojo UPC |
| TextField, TextArea, Select, Checkbox | mismos nombres, con estados Default, Placeholder, Focus, Error, Disabled |
| Field | `Field` · Default, Error (etiqueta, pista o error, entrada intercambiable) |
| Card, Table, Tabs (`Tab`), Badge, Dialog, Toast, Spinner, EmptyState, ErrorState, Pagination, PageHeader, DateTime, Credits | mismos nombres |

**Faltan en la lista de `shared/ui` y se usan:** `Avatar` (iniciales), `Skeleton` (el contrato lo
nombra como forma de carga), iconos (SVG Lucide), `UniversityLogo`, `AyniLogo` y el marco
(`AppHeader`, `AppNav`, `MobileMenu`, que son de la fundación). También `SessionRow` y `SlotChip`,
que ya existían y son de una feature.

---

## Marco

[Marco (fundación)](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=34-187)

| Dato | Origen |
|---|---|
| Logo de la universidad | `GET /api/v1/university → MyUniversityView.logoUrl` |
| Nombre de la universidad | `MyUniversityView.name` |
| Saldo (solo estudiante) | `GET /api/v1/wallet → WalletBalance.available` |
| Contador de la campana | `GET /api/v1/notifications → NotificationsPage.unreadCount` (cada 60 s con la pestaña visible) |
| Nombre e iniciales | `GET /api/v1/me → MyProfileView.fullName` (iniciales **derivado**) |
| "Ing. de Software · 2026-2" | `MyProfileView.career` · `MyProfileView.currentTerm` |
| Cerrar sesión | `POST /api/v1/access/logout` |
| Pestañas por rol | rutas del contrato. Estudiante: Buscar, Mis tutorías, Créditos, Reconocimiento y, como tutor, Agenda, Disponibilidad, Habilidades, Reputación. Coordinador: Reconocimiento, Regla, Indicadores. Sin "Inicio": no tiene ruta |
| "Coordinación UPC" | `MyProfileView.fullName` del coordinador (los coordinadores no están en academic-data; el texto es el valor de ejemplo del campo) |

---

## Pantallas existentes (Ayni · Web), ya corregidas

### Inicio — Estudiante · referencia visual, sin ruta en el contrato

[Frame](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=6-3)

| Dato | Origen |
|---|---|
| "Hola, Ana", "Viernes 23 de octubre · hora de Lima" | `MyProfileView.fullName`; hoy en `MyUniversityView.timezone` (**derivado**) |
| "3 créditos asignados por la UPC vencen el 31 de octubre" | `WalletBalance.byType[ALLOCATED].groups[].amount`, `.expiresAt` |
| "Al reservar se usan primero los que están por vencer" | **regla** |
| "Tu sesión se cerró. Califícala" y fecha | Notice `SESSION_CLOSED` (`payload.sessionId`) → `GET /sessions/{id} → Session.scheduledStart`; pendiente si `GET /sessions/{id}/rating → RegisteredRating.rated` es false |
| Próxima tutoría: curso, tutor, fecha, hora, horas | `GET /bookings/mine?view=UPCOMING → StudentBooking.catalogItem.name`, `.tutor.name`, `.startsAt`, `.endsAt`, `.hours` |
| "★ 4.8 · 12 calificaciones en este curso" | `GET /tutors/{tutor.id}/standing?catalogItemId → TutorStandingView.averageStars`, `.ratingsCount` |
| "1 crédito" | **derivado** de `hours` |
| "Lo que pediste" | `GET /sessions?bookingId → SessionOfBooking.sessionId` → `GET /sessions/{id} → Session.needDescription` |
| "Entrar a la sala" / "La sala se abre a las 15:45" | `StudentBooking.joinable`, `SessionOfBooking.joinOpensAt` |
| Cancelar tutoría | `DELETE /bookings/{id}` |
| Tus créditos: total, barra y origen | `WalletBalance.available`, `.byType[].available`, `.groups[]`, `.expires` |
| Próximas tutorías "Recibes" / "Enseñas" | `StudentBooking` / `GET /tutor/agenda → TutorAgendaLine(BOOKED).booking.student.name`, `.catalogItem.name` |
| Camino al reconocimiento: 18 de 40 | `GET /recognition/progress → RecognitionProgress.earnedHours`, `.requiredHours` |
| Lo que enseñas | `GET /tutor/skills → TutorSkill.name`, `.status`, `.accreditedGrade`; `GET /tutor/reputation → TutorSkillReputation.averageStars`, `.sessionsTaught`, `.ratingsCount` ("Tutor nuevo" **derivado** de que no haya promedio) |

Quitado: el ciclo de la cabecera y de cada estudiante, "promedio mínimo 4.0", el nombre del tutor en
el aviso de calificación y el género ("tutora").

### Sesión en vivo — Pizarra · base de FE-22

[Frame](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=11-56). Mismo mapa
que FE-22. Quitado: la presencia de la otra persona, el cursor remoto y "Descargar".

### Buscar tutoría — Elegir hora · base de FE-10 escritorio

[Frame](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=14-59). Mismo mapa
que FE-10. Quitado: el filtro de franja y su texto.

---

## FE-01 Ingresar

[desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=35-403) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=35-459) · estados:
inicial, buscando, no afiliada (404), enlace enviado (202).

| Dato | Origen |
|---|---|
| Correo | entrada: `RequestAccessRequest.email` |
| Logo, nombre y código de la universidad; barra y botón de marca | `GET /api/v1/access/university?email= → UniversityIdentityView.logoUrl`, `.name`, `.code`, `.primaryColor`, `.secondaryColor` |
| "Tu universidad todavía no está afiliada a Ayni" | 404 de esa llamada |
| "Te enviamos un enlace a {correo}. Vence en 10 minutos." | `POST /api/v1/access/request` → 202; el correo es el escrito; "10 minutos" es **regla** del contrato |
| "Reenviar en 0:27" | **derivado**: espera de 30 s del contrato |

## FE-03 Onboarding

[desktop (paso 3)](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=36-636) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=36-1028) · estados:
paso 1, paso 2 sin importar, paso 2 importado, curso no habilitado, cargando, error.

| Dato | Origen |
|---|---|
| Paso activo | `MyProfileView.onboardingStep` |
| Nombre, carrera, periodo, código (no editables) | `MyProfileView.fullName`, `.career`, `.currentTerm`, `.studentCode` |
| Foto (URL), Presentación | `UpdateMyProfileRequest.photoUrl`, `.bio` → `PUT /api/v1/me/profile` |
| "Importar mi récord" | `POST /api/v1/me/academic-record/import` (204) |
| Cursos que podrías enseñar con su nota | `GET /api/v1/tutor/skills/suggestions → SuggestedCourse.item.name`, `.item.courseCode`, `.grade` |
| Buscar en el catálogo y su lista | `GET /api/v1/catalog?q= → CatalogPage.items[].name`, `.courseCode` |
| Las dos listas | `OnboardingInterestsRequest.learning`, `.teaching` → `POST /api/v1/me/onboarding/interests` |
| "… no quedó habilitado: su nota no alcanza el mínimo" | curso pedido que no vuelve en `OnboardingInterestsResponse.teaching` (texto del contrato) |

## FE-10 Buscar

[desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=37-816) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=37-1087) · estados:
sin curso, cargando, horas cercanas, vacío, error.

| Dato | Origen |
|---|---|
| Curso y código | `GET /api/v1/catalog → CatalogItem.name`, `.courseCode`, `.scope` |
| Semana o rango | parámetros `from`, `to` |
| "Hora de Lima" | `OfferSearch.timezone` |
| Cada hora libre | `Offer.startsAt`, `.endsAt` agrupadas por día (**derivado**) |
| Tutor, estrellas, calificaciones, sesiones | `Offer.tutorName`, `.averageStars`, `.ratingsCount`, `.sessionsTaught` |
| "Tutor nuevo" / "Sin promedio aún" | `Offer.newTutor` |
| "2 tutores", "También libre 16:00", "Solo esta hora" | **derivado** de las `Offer` de la misma hora o del mismo tutor |
| "Tu tutoría con Bruno" en la rejilla | `GET /bookings/mine?view=UPCOMING` |
| "Ordenados por su calificación…" | **regla** (orden del servidor) |
| "No hay horas en esa franja. Estas son las más cercanas" | `OfferSearch.exactMatch = false` |
| Paginación | `OfferSearch.page`, `.totalPages` |
| "Apartamos la hora 5 minutos" | **regla** |

## FE-11 Reservar

[desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=39-1318) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=39-2228) · estados:
apartando, confirmando, confirmada (201), hora tomada, apartado vencido, tutor no habilitado, sin
créditos, error inesperado.

| Dato | Origen |
|---|---|
| Cuenta atrás 4:32 y su barra | `POST /api/v1/bookings/holds → HeldHours.heldUntil` (**derivado** contra el reloj) |
| Fecha y hora | `HeldHours.startsAt`, `.endsAt`, `.hours` |
| Curso, tutor y su estrella | **navegación** desde FE-10 (`CatalogItem.name`, `Offer.tutorName`, `Offer.averageStars`…) — RB-3 |
| Costo "1 crédito" | **derivado** de `hours` |
| "Tu saldo: 12 créditos" | `WalletBalance.available` |
| ¿En qué necesitas ayuda? | `BookHoursRequest.needDescription` |
| Confirmada: fecha, tutor, "Se descontó 1 crédito" | `Booking.startsAt`, `.endsAt`, `.creditsCharged` (+ el nombre por **navegación**) |
| Rechazos 409 | `BookingApiError.message` traducido a los textos del contrato; "Te falta 1 crédito" = `hours − WalletBalance.available` (**derivado**) |
| Elegir otra hora | `DELETE /api/v1/bookings/holds?tutorId&start&hours` |

## FE-12 Mis tutorías

[desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=40-1966) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=40-2797) · estados:
historial, cancelación tardía (409), cancelada (204), cargando, vacío, error.

| Dato | Origen |
|---|---|
| Fecha, hora, horas | `StudentBooking.startsAt`, `.endsAt`, `.hours` en `StudentBookings.timezone` |
| Curso, tutor | `StudentBooking.catalogItem.name`, `.tutor.name` |
| Estado | `StudentBooking.status` (CONFIRMED Confirmada, COMPLETED Completada, CANCELLED Cancelada, NO_SHOW No asistió) |
| "En curso" y "Entrar" | `StudentBooking.joinable` → `GET /sessions?bookingId → SessionOfBooking.sessionId` |
| "empieza en 4 h" | **derivado** de `startsAt` |
| Diálogo "Faltan menos de 12 horas…" | 409 `LateCancellationConfirmation.late`, `.refundWillBeIssued`; confirmar con `?confirmLate=true` |
| "Te devolvimos 1 crédito" | 204 de `DELETE /bookings/{id}` (fuera de las 12 h) |
| "Repasar" en el historial | lleva a FE-24 |
| Paginación | `StudentBookings.page`, `.total` |

## FE-20 Sesión (detalle y entrar)

[desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=41-2534) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=41-3220) · estados:
antes de abrir, esperando al otro, tutor no llegó, cargando, error, no encontrada (404).

| Dato | Origen |
|---|---|
| Curso del título | RB-1 |
| Fecha y hora | `Session.scheduledStart`, `.scheduledEnd` |
| "con Julián Arce (tutor)" | nombre RB-2; "(tutor)" sale de `Session.role` (la otra parte) |
| Estado | `Session.status` |
| "La sala se abre a las 15:45" | `Session.joinOpensAt` |
| Llegadas | `Session.studentJoinedAt`, `.tutorJoinedAt` |
| "Si no entra antes de las 16:10…" | **regla** (10 minutos) |
| "Tu tutor no llegó…" | `Session.status = ABANDONED`, `.absentParticipantIds` |
| Entrar / Abrir el video | `POST /sessions/{id}/join → JoinedSession.video.url`, `.token` |
| Lo que pediste | `Session.needDescription` |
| Material de apoyo | `Session.supportMaterial.summary`, `.suggestions` |

## FE-21 Código de presencia

[paneles](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=41-3364) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=43-3802) · estados:
por llegar, escribiendo, confirmando, incorrecto (422), sin intentos, reenvío en espera, confirmada.

| Dato | Origen |
|---|---|
| "A las 16:05 te enviaremos…" | `Session.presenceCheckAt` |
| Código | `ConfirmPresenceRequest.code` → `POST /sessions/{id}/presence` |
| "Vence a las 16:20 · 5 intentos" | `Session.presence.expiresAt`, `.attemptsLeft` |
| "Confirmaste a las 16:07" | `PresenceConfirmation.confirmedAt` / `Session.presence.confirmedAt` |
| Reenviar código | `POST /sessions/{id}/presence/resend` (202; 409 si es antes de 60 s o tras 3) |
| "Tú · Pendiente" | `Session.presence.confirmedAt` vacío |

## FE-22 Pizarra en vivo

[desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=43-3040) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=43-3749) · estados:
reconectando, sin video.

| Dato | Origen |
|---|---|
| Contenido de la pizarra, notas, colores | `Whiteboard.elements[]` (`BoardElement.type`, `.data` opaco) y el canal `/whiteboard/live` (`board`, `applied`, `error`) |
| Autor de la nota "— Ana" | lo escribe el cliente en `BoardElement.data` |
| "Guardado · podrás repasarla después" | cada operación se guarda (**regla**); `Whiteboard.updatedAt` |
| "En curso · 07:12" | `Session.status`, **derivado** de `Session.startedAt` |
| Terminar sesión | FE-23 |
| Video y controles | Jitsi con `JoinedSession.video` (los controles son de Jitsi, no de la API de Ayni) |
| "El video no está disponible" | `JoinedSession.video = null` |
| "Reconectando…" | cierre del WebSocket; al volver llega `board` completo |
| Curso y nombre del tutor | RB-1, RB-2 |

## FE-23 Cierre y calificación

[calificar desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=44-3387) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=44-3853) · estados:
confirmar fin, esperando al otro, calificada, ya calificada (409), tutor (tres hechos), sin verificar.

| Dato | Origen |
|---|---|
| Terminar | `POST /sessions/{id}/end → EndedSession` |
| "Se cierra sola a las 17:15" | `EndedSession.closesAt` |
| "Los dos confirmaron su presencia" | `Session.status = COMPLETED` |
| "Duró 58 minutos (de 16:02 a 17:00)" | **derivado** de `Session.startedAt`, `.endedAt` |
| Estrellas, comentario | `RatingRequest.stars`, `.comment` → `POST /sessions/{id}/rating` |
| Etiquetas | `RatingRequest.tags` — catálogo RB-4 |
| Tres hechos del tutor | `RatingRequest.wasPunctual`, `.connectionOk`, `.sessionFlowed` |
| "Gracias… 5 estrellas · …" | `GET /sessions/{id}/rating → RegisteredRating.rated`, `.stars`, `.tags` |
| "Tu tutor no ve quién lo calificó" | **regla** |
| Sin verificar | `Session.status = UNVERIFIED` (no se califica: **regla**) |

## FE-30 Billetera

[desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=46-3504) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=46-4147) · estados:
saldo cero, sin movimientos, cargando, error.

| Dato | Origen |
|---|---|
| Disponible, ganados en total | `WalletBalance.available`, `.earnedTotal` |
| Barra y tarjetas por origen | `WalletBalance.byType[].type`, `.available` |
| Vencimientos | `.byType[].groups[].amount`, `.expiresAt`; "No vencen" si `.expires` es false |
| "Cuenta para el reconocimiento" | `.byType[].countsTowardsRecognition` |
| Aviso de saldo cero | `WalletBalance.guidance` — RB-6 (idioma) |
| Filtros Periodo y Motivo | parámetros `from`, `to`, `reason` |
| Fecha, motivo, origen, movimiento | `Movement.occurredAt`, `.reason` (en español), `.creditType`, `.direction` + `.amount` |
| Ver tutoría / Ver compra | `Movement.referenceType` + `.referenceId` (BOOKING → /mis-tutorias, PURCHASE → /creditos/compras/:id) |
| Paginación | `MovementsPage.page`, `.totalPages` |

## FE-31 Comprar créditos

[desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=47-3870) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=47-4508) · estados:
procesando, confirmada, pendiente, rechazada, vencida, proveedor caído, límite mensual.

| Dato | Origen |
|---|---|
| ¿Cuántos créditos? | `PurchaseRequest.credits`, con la cabecera `Idempotency-Key` |
| "3 créditos · S/ 15.00" (solo tras la respuesta) | `PurchaseResponse.credits`, `.amount`, `.currency` |
| Referencia y fecha | `PurchaseResponse.providerReference`, `.createdAt` |
| Estado | `PurchaseResponse.status` |
| "Las compras están temporalmente no disponibles" | `PurchaseResponse.providerUnavailable` |
| Segunda línea de pendiente | `PurchaseResponse.guidance` — RB-6 |
| Límite mensual | `PaymentsApiError.message` tal cual — RB-6 |

## FE-40 Disponibilidad

[desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=49-4796) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=49-5891) · estados:
rango agregado, rango quitado, en pausa, rango inválido, sin lista.

| Dato | Origen |
|---|---|
| Rangos ya declarados | RB-5 |
| Agregar rango | `DeclareAvailabilityRequest.dayOfWeek`, `.startsAtTime`, `.endsAtTime`, `.validFrom`, `.validUntil` |
| "Se generaron 12 horas libres" | `AvailabilityPattern.generatedHours` |
| Quitar | `DELETE /tutor/availability/{id} → RemovedAvailabilityPattern.withdrawnHours`, `.bookedHoursKept` |
| Excepción | `AvailabilityExceptionRequest.exceptionDate`, `.kind` (Agregar / Quitar horas), horas |
| Pausa | `AvailabilityPauseRequest.startsOn`, `.endsOn`; estado: `GET .../pauses/current → CurrentAvailabilityPause.paused`, `.startsOn`, `.endsOn`; terminar: `DELETE .../pauses/{id}` |
| "Debe ser después de Desde" | 400 `BookingApiError` |

## FE-41 Agenda

[desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=48-4373) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=48-5020) · estados:
cancelación tardía, historial, cargando, vacío, error.

| Dato | Origen |
|---|---|
| Rango | parámetros `from`, `to` (hasta 31 días) |
| Día y hora | `TutorAgendaLine.date`, `.startTime`, `.endTime` en `TutorAgenda.timezone` |
| Reservada / libre | `TutorAgendaLine.kind` |
| Estudiante, curso, necesidad | `TutorBooking.student.name`, `.catalogItem.name`, `.needDescription` |
| "1 inasistencia" | `TutorBooking.student.absenceCount` (> 0) |
| Entrar | `TutorBooking.joinable` → `GET /sessions?bookingId` |
| Diálogo tardío | 409 `LateCancellationConfirmation.incidentWillBeRecorded` |
| Historial | `GET /tutor/agenda/history → TutorBookings.items[]` |

## FE-50 Solicitudes de reconocimiento

[desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=50-5493) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=50-5883) · estados:
historial, vacío, cargando, error.

| Dato | Origen |
|---|---|
| Estudiante y código | `RecognitionQueueItem.student.name`, `.student.studentCode` |
| Horas, sesiones, promedio | `.totalHours`, `.sessionsCount`, `.averageRating` ("Sin calificaciones" si es nulo; hoy el adaptador no tiene calificaciones por sesión) |
| Enviada, estado | `.submittedAt`, `.status` |
| Pestañas | parámetro `status` |

## FE-51 Caso

[desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=50-5952) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=50-6689) · estados:
falta el motivo, aprobando, aprobada, ya decidida (409).

| Dato | Origen |
|---|---|
| Estudiante, cifras, enviada | `RecognitionCase.student`, `.totalHours`, `.sessionsCount`, `.averageRating`, `.submittedAt` |
| "Alertas abiertas: 2" | **derivado**: cuenta de `RecognitionCase.alerts` |
| Cada sesión | `RecognitionCaseSession.startedAt`, `.endedAt`, `.durationMinutes`, `.hours`, `.presenceVerified`, `.stars` |
| Alerta junto a la sesión | `RecognitionCaseSession.alerts[].kind`, `.severity`, `.description` (kind y severidad en español) |
| Evidencia | `GET .../sessions/{sessionId} → RecognitionSessionEvidence.taught.name`, `.taught.courseCode`, `.scheduledStart`, `.scheduledEnd`, `.startedAt`, `.endedAt`, `.attendance[].name`, `.role`, `.presenceVerified`, `.tutorStars` |
| Motivo y decisión | `RecognitionDecisionRequest.reason`, `.decision` |
| Decisión tomada | `RecognitionDecision.status`, `.reviewedAt`, `.reason` (sin el nombre de quien decidió: `reviewedBy` es un id) |

## FE-53 Indicadores

[desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=51-6036) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=51-6464) · estados:
periodo inválido, sin actividad, cargando, error.

| Dato | Origen |
|---|---|
| Periodo | parámetros `from`, `to`; más de 366 días → 400 |
| Totales | `UsageIndicators.totals.sessionsCompleted`, `.hoursExchanged`, `.activeStudents`, `.newStudents`, `.recognitionsGranted` |
| Barras por día | `UsageIndicators.days[].day`, `.figures.hoursExchanged` (tooltip: `.sessionsCompleted`) |
| Cursos buscados sin tutor | `UsageIndicators.uncoveredDemand[].catalogItemId`, `.searches`; nombre y código con `GET /api/v1/catalog` |
| Descargar CSV | `GET /api/v1/coordinator/indicators/export` |

## FE-60 Avisos

[desktop](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=52-6292) ·
[panel](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=52-6956) ·
[mobile](https://www.figma.com/design/RMzuCe7RghVuVZziEl6Zjs/Sin-título?node-id=52-7056) · estados:
sin leer, todo leído, vacío, cargando, error.

| Dato | Origen |
|---|---|
| Texto de cada aviso | `Notice.kind` → tabla del contrato |
| Detalle | `Notice.payload`: `startsAt` (BOOKING_CONFIRMED), `outcome` (SESSION_CLOSED), `amount` + `expiresAt` (CREDITS_EXPIRING), `cancelledBy` (BOOKING_CANCELLED), `approved` (REQUEST_RESOLVED, VALIDATION_RESOLVED) |
| Sin leer | `Notice.readAt` nulo |
| "hace 5 min" | **derivado** de `Notice.createdAt` |
| "3 sin leer" | `NotificationsPage.unreadCount` |
| Pestaña Sin leer | parámetro `unread` |
| Marcar | `POST /notifications/{id}/read`, `POST /notifications/read-all` |

---

## Requiere backend

Lo que está dibujado y todavía no existe en `ayni-openapi.json`. En Figma lleva una anotación.

| # | Qué falta | Dónde se dibujó | Propuesta |
|---|---|---|---|
| RB-1 | El nombre (y código) del curso en `Session` / `JoinedSession` / `SessionReview`: hoy solo traen `catalogItemId`, y no hay `GET /api/v1/catalog/{id}` | FE-20, FE-22, FE-23 y la Sesión en vivo existente | Agregar `catalogItem { id, name, courseCode }` a `Session` |
| RB-2 | El nombre de la otra persona en `Session`: solo trae `tutorId` y `studentId` | Ídem (cabecera, video, avatares) | Agregar `counterpart { id, name }` a `Session` |
| RB-3 | En `/reservar`, el nombre del curso y del tutor y su estrella llegan solo por navegación desde FE-10: al recargar se pierden | FE-11 | Agregar `tutorName` y `catalogItem` a `HeldHours`, o aceptar que al recargar se muestre solo la fecha |
| RB-4 | Un catálogo de etiquetas para la calificación: `RatingRequest.tags` es texto libre | FE-23 | Que el backend publique la lista, o quitar las etiquetas |
| RB-5 | `GET /api/v1/tutor/availability` para listar rangos, excepciones y pausa (ya en el plan del sprint) | FE-40 | Construirlo; mientras tanto se usa el estado "sin lista" |
| RB-6 | Idioma: `WalletBalance.guidance`, `PurchaseResponse.guidance` y el `message` del límite mensual llegan en inglés, y el contrato pide mostrarlos tal cual | FE-30, FE-31 | Que el backend los entregue en español (o como códigos que la web traduzca). **Resuelto:** la web escribe sus propios textos en español a partir del estado y del status, y no muestra `guidance` ni `message` |
| RB-7 | El ciclo del estudiante: `MyProfileView` del snapshot no lo tiene (la rama `feature/academic-system-documents` agrega `currentCycle`) | Se quitó; la cabecera usa `currentTerm` | Si se publica, volver a "Ing. de Software · 4.° ciclo" |

## Lo que se quitó por no tener respaldo

El ciclo de las personas, "promedio mínimo 4.0", el nombre en el aviso de calificar, la presencia
confirmada de la otra persona, el cursor remoto, "Descargar" la pizarra, el filtro de franja, el
género en "tutora" y las personas y cursos que no están en academic-data.

## Decisiones tomadas (aprobadas en la auditoría)

1. El dashboard "Inicio" queda como referencia visual: no tiene id ni ruta, y el marco nuevo no tiene
   pestaña Inicio.
2. Buscar: rejilla semanal en escritorio y lista por día en teléfono.
3. Button: primary = marca; danger con contorno; se agregaron ghost, pressed, loading y disabled.
4. Se corrigieron las pantallas existentes y la marca se aplicó también a los componentes que ya existían.
5. Sin género: "tutor", "Tutor nuevo".

## Pendiente (lo menos prioritario, sin dibujar)

FE-02 Confirmar enlace, FE-04 Perfil, FE-24 Repasar sesión, FE-32 Compras, FE-33 Beneficios y
donaciones, FE-34 Reconocimiento del estudiante, FE-42 Mis habilidades, FE-43 Reputación y FE-52
Regla de reconocimiento. Para FE-23, FE-30 y FE-31 se dibujaron los estados del contrato en
escritorio y los principales en teléfono.
