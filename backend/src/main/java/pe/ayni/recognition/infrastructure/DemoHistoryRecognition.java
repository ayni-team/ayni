package pe.ayni.recognition.infrastructure;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.ayni.recognition.application.ResolveRecognitionRequestUseCase;
import pe.ayni.recognition.application.ResolveRecognitionRequestUseCase.Decision;
import pe.ayni.recognition.application.SubmitRecognitionRequestUseCase;
import pe.ayni.shared.demo.DemoRecognition;

/**
 * Recognition's part of the history of the {@code demo} profile: the use cases of asking and of
 * deciding.
 */
@Component
@Profile("demo")
class DemoHistoryRecognition implements DemoRecognition {

  private final SubmitRecognitionRequestUseCase submit;
  private final ResolveRecognitionRequestUseCase resolve;

  DemoHistoryRecognition(
      SubmitRecognitionRequestUseCase submit, ResolveRecognitionRequestUseCase resolve) {
    this.submit = submit;
    this.resolve = resolve;
  }

  @Override
  public UUID request(UUID tutorId) {
    return submit.execute(tutorId).request().getId();
  }

  @Override
  public void resolve(UUID coordinatorId, UUID requestId, boolean approve, String reason) {
    resolve.execute(coordinatorId, requestId, approve ? Decision.APPROVE : Decision.REJECT, reason);
  }
}
