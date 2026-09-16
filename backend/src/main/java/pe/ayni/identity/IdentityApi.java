package pe.ayni.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What identity offers to the other modules. The only way into this module besides its events.
 *
 * <p>Implemented by a class in {@code identity.application}.
 */
public interface IdentityApi {

  /** The university that claims the domain of this email, if any. */
  Optional<TenantView> findTenantByEmailDomain(String email);

  /** @throws java.util.NoSuchElementException when no university has that code */
  TenantView requireTenant(String tenantCode);

  /** @throws java.util.NoSuchElementException when the user does not exist in the current tenant */
  UserView requireUser(UUID userId);

  boolean isActive(UUID userId);

  /** The policy in force of the given kind, if the university has one. */
  Optional<CreditPolicyView> currentPolicy(String tenantCode, PolicyKind kind);

  /**
   * The courses the academic system reports the student approved, with their grade.
   *
   * <p>Skills uses it to enable a course automatically when the grade reaches the university's
   * threshold, and to suggest what a student could teach.
   */
  List<ApprovedCourseView> approvedCourses(UUID userId);

  /** Codes of every active university. Used by scheduled jobs, which run without a request. */
  List<String> activeTenantCodes();
}
