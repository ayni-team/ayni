package pe.ayni.skills.application;

/** What the student says about a file they attach, before it is stored. */
public record EvidenceUpload(String fileName, String contentType, long sizeBytes) {}
