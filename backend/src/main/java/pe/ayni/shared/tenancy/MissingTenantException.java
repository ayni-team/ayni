package pe.ayni.shared.tenancy;

/** Raised when an operation that requires a university runs without one bound to the request. */
public class MissingTenantException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MissingTenantException() {
    super("No tenant is bound to the current request. Send the X-Tenant-Id header.");
  }
}
