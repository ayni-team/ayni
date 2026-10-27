package pe.ayni.identity.application;

/**
 * Whether the university still considers a person its student, as its academic system says.
 *
 * <p>Only {@link #ACTIVE} may be activated in Ayni: a graduate or somebody who left the university
 * is known to its academic system but is no longer its student.
 */
public enum AcademicStatus {
    ACTIVE,
    GRADUATED,
    INACTIVE
}
