package pe.ayni.shared.demo;

import java.util.UUID;

/** What the history of the {@code demo} profile asks of recognition. */
public interface DemoRecognition {

  /** The tutor asks for the recognition of the hours they taught; answers the request. */
  UUID request(UUID tutorId);

  /** The coordinator decides a request, with a reason. */
  void resolve(UUID coordinatorId, UUID requestId, boolean approve, String reason);
}
