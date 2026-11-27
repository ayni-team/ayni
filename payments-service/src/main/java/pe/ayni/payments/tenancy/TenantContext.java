package pe.ayni.payments.tenancy;

import java.util.function.Supplier;

/**
 * Holds the university that owns the work being done on the current thread.
 *
 * <p>The monolith authenticates the student and forwards the university in {@code X-Tenant-Id}
 * ({@link InternalCallFilter}); the jobs bind each university with pending work in turn. Every
 * transaction tells PostgreSQL which university it belongs to ({@link TenantTransactionManager}),
 * and the row level security policies of {@code ayni_payments} compare {@code tenant_id} with it, as
 * they did in the monolith (TS01, ADR 0007).
 *
 * <p>The university is bound before a transaction begins and never changes inside one: a job binds
 * a university, runs the transactions of that university, and only then moves to the next.
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
    callAs(
        tenantId,
        () -> {
          work.run();
          return null;
        });
  }

  /** Runs the given work with a tenant bound and returns what it answers, restoring the previous. */
  public static <T> T callAs(String tenantId, Supplier<T> work) {
    String previous = CURRENT_TENANT.get();
    set(tenantId);
    try {
      return work.get();
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
