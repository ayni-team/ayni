package pe.ayni.payments.tenancy;

import java.util.UUID;

/**
 * Holds the person the request being processed on the current thread is about.
 *
 * <p>The companion of {@link TenantContext}. The monolith resolves the person from the session of
 * the request's token and forwards it in {@code X-User-Id}; {@link InternalCallFilter} binds it only
 * when the call proves it comes from the monolith. A controller reads its subject from here, never
 * from the body, so a request cannot name another student.
 */
public final class CurrentUser {

  private static final ThreadLocal<UUID> CURRENT_USER = new ThreadLocal<>();

  private CurrentUser() {}

  /** Binds a user to the current thread. */
  public static void set(UUID userId) {
    CURRENT_USER.set(userId);
  }

  /** Returns the user bound to the current thread, or {@code null} when none is bound. */
  public static UUID get() {
    return CURRENT_USER.get();
  }

  /**
   * Returns the user bound to the current thread.
   *
   * @throws MissingUserException when no user is bound
   */
  public static UUID require() {
    UUID userId = CURRENT_USER.get();
    if (userId == null) {
      throw new MissingUserException();
    }
    return userId;
  }

  /** Clears the user. Called when the request finishes, to avoid leaking across threads. */
  public static void clear() {
    CURRENT_USER.remove();
  }
}
