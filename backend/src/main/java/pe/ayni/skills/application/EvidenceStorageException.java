package pe.ayni.skills.application;

/**
 * The storage failed: the disk is full, the folder cannot be written, a file cannot be read.
 *
 * <p>Not a refusal. Nothing the student can change would have avoided it, so it is not a {@code
 * SkillsRuleViolation} and it is not answered as a 4xx: somebody has to look at it.
 */
public class EvidenceStorageException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public EvidenceStorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
