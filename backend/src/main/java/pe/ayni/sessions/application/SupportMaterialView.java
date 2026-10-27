package pe.ayni.sessions.application;

import java.time.Instant;
import java.util.List;

/** The support material of a session as its participants read it (US07). */
public record SupportMaterialView(String summary, List<String> suggestions, Instant generatedAt) {}
