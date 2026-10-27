package pe.ayni.shared.tenancy;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Holds the university that owns the request being processed on the current thread.
 *
 * <p>Every university is a separate tenant. Identity's {@code AccessControlFilter} reads it from
 * the session of the request's token (TS03) and pushes it here, so that a use case can ask for it
 * without receiving it as a parameter through every method call. A platform administrator's
 * session has no university, so nothing is bound for them.
 *
 * <p>Queries filter by this value, and the database enforces the same thing by itself (TS01): each
 * transaction sets {@code app.tenant_id} to it, which is what the row level security policies
 * compare {@code tenant_id} with. A change made while a transaction is open is told to whoever
 * registered with {@link #onChange}, so that a listener that binds the university of its event
 * inside its own transaction is answered with that university's rows. The class does not know
 * about the database: {@code config} registers the one listener there is.
 */
public final class TenantContext {

  private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

  private static volatile Consumer<String> listener = tenantId -> {};

  private TenantContext() {}

  /**
   * Registers what must happen every time the tenant of a thread changes, with the new value or
   * {@code null}. Replaces the previous registration: there is one, the one that tells the open
   * transaction.
   */
  public static void onChange(Consumer<String> changeListener) {
    listener = Objects.requireNonNull(changeListener, "changeListener must not be null");
  }

  /** Binds a tenant to the current thread. */
  public static void set(String tenantId) {
    CURRENT_TENANT.set(tenantId);
    listener.accept(tenantId);
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
    listener.accept(null);
  }
}
