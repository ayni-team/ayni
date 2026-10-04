package pe.ayni.sessions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.ayni.booking.domain.model.events.BookingConfirmedEvent;
import pe.ayni.sessions.application.internal.eventhandlers.SessionSupportMaterialEventHandler;
import pe.ayni.sessions.domain.model.SupportMaterial;
import pe.ayni.sessions.domain.model.SupportMaterialRepository; // IMPORT CORREGIDO
import pe.ayni.sessions.domain.model.SupportMaterialStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionSupportMaterialEventHandlerTest {

    @Mock
    private SupportMaterialRepository supportMaterialRepository;

    @InjectMocks
    private SessionSupportMaterialEventHandler eventHandler;

    @Test
    void shouldApplyFallbackWhenExternalServiceFails() {
        BookingConfirmedEvent event = new BookingConfirmedEvent(100L);

        // Forzar excepcion al intentar guardar la primera vez para activar el fallback
        doThrow(new RuntimeException("External service down"))
                .doAnswer(invocation -> invocation.getArgument(0))
                .when(supportMaterialRepository).save(any());

        eventHandler.handleBookingConfirmed(event);

        ArgumentCaptor<SupportMaterial> captor = ArgumentCaptor.forClass(SupportMaterial.class);
        verify(supportMaterialRepository, times(2)).save(captor.capture());

        SupportMaterial fallbackSaved = captor.getAllValues().get(1);
        assertEquals(SupportMaterialStatus.PENDING_TEACHER_UPLOAD, fallbackSaved.getStatus());
        assertEquals(100L, fallbackSaved.getSessionId());
    }
}