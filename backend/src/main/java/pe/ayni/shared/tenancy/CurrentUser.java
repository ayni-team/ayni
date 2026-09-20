package pe.ayni.shared.tenancy;

import java.util.UUID;

/**
 * Holds the person the request being processed on the current thread is about.
 *
 * <p>The companion of {@link TenantContext}: that one answers which university, this one answers
 * who. Both are bound by a filter in {@code config}, so that a use case can ask for either without
 * receiving it as a parameter through every method call.
 *
 * <p>The value arrives in the {@code X-User-Id} header today, exactly as the university arrives in
 * {@code X-Tenant-Id}. When sign in is added it will come from a claim of the access token and only
 * the filter changes.
 *
 * <p>Until then anyone can claim to be anybody, and that is precisely why an endpoint reads its
 * subject from here rather than taking it as a parameter: an endpoint that has no way to name
 * another student cannot be pointed at one, so the hole stays in one place instead of in every
 * controller.
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

  /** Runs the given work as the given user, restoring whoever was bound before. */
  public static void runAs(UUID userId, Runnable work) {
    UUID previous = CURRENT_USER.get();
    set(userId);
    try {
      work.run();
    } finally {
      if (previous == null) {
        clear();
      } else {
        set(previous);
      }
    }
  }

  /** Clears the user. Called when the request finishes, to avoid leaking across threads. */
  public static void clear() {
    CURRENT_USER.remove();
  }
}
