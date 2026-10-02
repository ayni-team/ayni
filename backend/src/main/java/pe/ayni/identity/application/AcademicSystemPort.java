package pe.ayni.identity.application;

public interface AcademicSystemPort {

    AcademicProfile fetchProfile(
            String tenantId,
            String studentCode);
}