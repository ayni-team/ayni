package pe.ayni.sessions.domain.model;

/**
 * A whole board was sent to replace a version that is not the current one any more: somebody drew
 * in the meantime. Well formed, refused by the state of the board, so it answers 409.
 */
public class WhiteboardVersionConflict extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final long currentVersion;

  public WhiteboardVersionConflict(long currentVersion) {
    super(
        "The whiteboard changed: it is at version "
            + currentVersion
            + ". Read it again before replacing it");
    this.currentVersion = currentVersion;
  }

  public long currentVersion() {
    return currentVersion;
  }
}
