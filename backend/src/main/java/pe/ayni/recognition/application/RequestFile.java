package pe.ayni.recognition.application;

import java.util.List;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestedSession;

/** A request with the sessions that back it, as they were when it was submitted. */
public record RequestFile(RecognitionRequest request, List<RequestedSession> sessions) {}
