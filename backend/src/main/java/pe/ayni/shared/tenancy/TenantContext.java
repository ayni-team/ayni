package pe.ayni.shared.tenancy;

/**
 * Holds the university that owns the request being processed on the current thread.
 *
 * <p>Every university is a separate tenant. {@code TenantFilter} reads it from the request and
 * pushes it here, so that a use case can ask for it without receiving it as a parameter through
 * every method call.
 *
 * <p>Queries filter by this value today. Later the database will enforce the same thing by itself,
 * and when that happens this class stays as it is.
 */
public final class TenantContext {

  private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

  private TenantContext() {}

  /** Binds a tenant to the current thread. */
  public static void set(String tenantId) {
    CURRENT_TENANT.set(tenantId);
  }

  /** Returns the tenant bound to the current thread, or {@code null} when none is bound. */
  public static String get() {
    return CURRENT_TENANT.get();
  }

  /**
   * Returns the tenant bound to the current thread.
   *
   * @throws MissingTenantException when no tenant is bound
   */
  public static String require() {
    String tenantId = CURRENT_TENANT.get();
    if (tenantId == null || tenantId.isBlank()) {
      throw new MissingTenantException();
    }
    return tenantId;
  }

  /** Runs the given work with a tenant bound, restoring whatever was bound before. */
  public static void runAs(String tenantId, Runnable work) {
    String previous = CURRENT_TENANT.get();
    set(tenantId);
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

  /** Clears the tenant. Called when the request finishes, to avoid leaking across threads. */
  public static void clear() {
    CURRENT_TENANT.remove();
  }
}
