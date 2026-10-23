package pe.ayni.identity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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

  /**
   * The full names of these users of the current tenant, read in one query for the whole batch.
   *
   * <p>For a list that shows the people it mentions: asking {@link #requireUser} for each would
   * read once per person. A user who does not exist in the current tenant is left out of the map
   * rather than refused, so a list is not lost for one person it no longer knows.
   */
  Map<UUID, String> namesOf(Collection<UUID> userIds);

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

  /**
   * The courses of the university's curriculum, as its academic system says today (ADR 0006).
   *
   * <p>Skills synchronises the catalogue of a university with it. It is read from the academic
   * system every time, never from what Ayni already loaded.
   *
   * @throws java.util.NoSuchElementException when the university has no academic system configured
   * @throws IllegalStateException when the academic system cannot be read or contradicts itself: a
   *     mistake of the platform
   */
  List<AcademicCourseView> academicCurriculum(String tenantCode);

  /** Codes of every active university. Used by scheduled jobs, which run without a request. */
  List<String> activeTenantCodes();

  /**
   * Finishes the onboarding of a student of the current tenant (US40): their step becomes
   * COMPLETED.
   *
   * <p>Only from the step of the learning interests, which follows the profile and the academic
   * record. A student already COMPLETED is left as they are, so calling it twice is harmless. Skills
   * calls it inside the transaction that declares the interests, so a refusal leaves nothing
   * declared.
   *
   * @throws java.util.NoSuchElementException when the user does not exist in the current tenant
   * @throws IllegalStateException when the profile or the academic record is still missing, or the
   *     user is a coordinator, who has no onboarding
   */
  void completeOnboarding(UUID userId);
}
