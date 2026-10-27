package pe.ayni.skills.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.application.LoadAcademicCatalogUseCase.CourseToLoad;
import pe.ayni.skills.application.LoadAcademicCatalogUseCase.LoadedCourse;

/**
 * US51 from the academic system (ADR 0006, amended 2026-10-23): the courses of the university are
 * the ones its academic system says, and a coordinator brings the catalogue up to date with them
 * instead of typing the list.
 *
 * <p>The list is asked of identity ({@link IdentityApi#academicCurriculum}) and loaded the way
 * {@link LoadAcademicCatalogUseCase} loads a typed one, with the course code as the key: new
 * courses are added, names are updated, retired ones are brought back, nothing is removed, and it is
 * all or nothing. Two differences, both because the academic system says nothing about them: a
 * course keeps the description a coordinator gave it, and the minimum teaching grade is not touched
 * (the registration set it and the coordinator changes it).
 */
@Service
public class SyncAcademicCatalogUseCase {

  private final IdentityApi identity;
  private final CoordinatorGuard coordinators;
  private final LoadAcademicCatalogUseCase loadAcademicCatalog;

  SyncAcademicCatalogUseCase(
      IdentityApi identity,
      CoordinatorGuard coordinators,
      LoadAcademicCatalogUseCase loadAcademicCatalog) {
    this.identity = identity;
    this.coordinators = coordinators;
    this.loadAcademicCatalog = loadAcademicCatalog;
  }

  /**
   * Synchronises the catalogue of the coordinator's university.
   *
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person is not an active coordinator
   * @throws java.util.NoSuchElementException when the person is not of the university, or the
   *     university has no academic system configured
   * @throws pe.ayni.skills.domain.model.SkillsRuleViolation when the curriculum cannot be loaded
   */
  @Transactional
  public List<LoadedCourse> execute(UUID coordinatorId) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");
    coordinators.requireActive(coordinatorId);
    return synchronise();
  }

  /**
   * The same synchronisation for the current university, with nobody asking: the demonstration data
   * loads the curriculum of each university with it, before any coordinator exists. Every request
   * goes through {@link #execute}.
   */
  @Transactional
  public List<LoadedCourse> withoutCoordinator() {
    return synchronise();
  }

  private List<LoadedCourse> synchronise() {
    String tenantId = TenantContext.require();
    List<CourseToLoad> curriculum =
        identity.academicCurriculum(tenantId).stream()
            .map(course -> new CourseToLoad(course.code(), course.name(), null))
            .toList();
    return loadAcademicCatalog.load(LoadAcademicCatalogUseCase.tidy(curriculum), true);
  }
}
