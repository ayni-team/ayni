package pe.ayni.wallet.infrastructure;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.application.ExpireCredits;
import pe.ayni.wallet.application.NotifyExpiringCredits;

@ExtendWith(MockitoExtension.class)
class CreditExpiryJobTest {

  @Mock private ExpireCredits expireCredits;
  @Mock private NotifyExpiringCredits notifyExpiringCredits;

  @InjectMocks private CreditExpiryJob job;

  @Test
  @DisplayName("the job processes each university with due or upcoming expiry once")
  void processesTheUnionOfUniversitiesOnce() {
    when(expireCredits.universitiesWithCreditsToExpire()).thenReturn(List.of("UPC"));
    when(notifyExpiringCredits.universitiesWithCreditsExpiring())
        .thenReturn(List.of("UPC", "UNI"));
    when(expireCredits.forCurrentUniversity()).thenReturn(Credits.ZERO);
    when(notifyExpiringCredits.forCurrentUniversity()).thenReturn(0);

    job.processCreditExpiry();

    verify(expireCredits, times(2)).forCurrentUniversity();
    verify(notifyExpiringCredits, times(2)).forCurrentUniversity();
  }
}
