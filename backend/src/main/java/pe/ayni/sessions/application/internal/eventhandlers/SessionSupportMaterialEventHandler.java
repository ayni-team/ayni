package pe.ayni.sessions.application.internal.eventhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

// Reemplaza este import con la ubicacion exacta de tu evento de booking si existe,
// o usa el evento de tu modulo de sesiones si corresponde.
import pe.ayni.booking.domain.model.events.BookingConfirmedEvent;
import pe.ayni.sessions.domain.model.SupportMaterial;
import pe.ayni.sessions.domain.model.SupportMaterialRepository;
import pe.ayni.sessions.domain.model.SupportMaterialStatus;

@Service
public class SessionSupportMaterialEventHandler {

    private static final Logger log = LoggerFactory.getLogger(SessionSupportMaterialEventHandler.class);

    private final SupportMaterialRepository supportMaterialRepository;

    public SessionSupportMaterialEventHandler(SupportMaterialRepository supportMaterialRepository) {
        this.supportMaterialRepository = supportMaterialRepository;
    }

    @EventListener
    public void handleBookingConfirmed(BookingConfirmedEvent event) {
        // Si bookingId() da error, cambialo por event.getBookingId() segun tu clase Event
        Long bookingId = event.bookingId();

        log.info("Procesando materiales de apoyo para la sesión: {}", bookingId);

        try {
            // Se agrega 'new' para instanciar la clase
            SupportMaterial material = new SupportMaterial(
                    bookingId,
                    "Material base de tutoría",
                    "https://ayni.pe/materials/default.pdf",
                    SupportMaterialStatus.AVAILABLE
            );

            supportMaterialRepository.save(material);
            log.info("Material de apoyo asignado exitosamente a la sesión {}", bookingId);

        } catch (Exception ex) {
            // FALLBACK US07: En caso de error, persite con estado pendiente de carga
            log.warn("Fallo la asignación automatica del material de apoyo para la sesión {}. Aplicando fallback.", bookingId, ex);

            // Se agrega 'new' para instanciar la clase
            SupportMaterial fallbackMaterial = new SupportMaterial(
                    bookingId,
                    "Pendiente de carga por el tutor",
                    null,
                    SupportMaterialStatus.PENDING_TEACHER_UPLOAD
            );

            supportMaterialRepository.save(fallbackMaterial);
        }
    }
}