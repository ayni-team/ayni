package pe.ayni.identity.application;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;

@Service
@Transactional(readOnly = true)
public class ListUniversitiesQuery {

    private final TenantRepository tenants;
    private final UserRepository users;

    ListUniversitiesQuery(
            TenantRepository tenants,
            UserRepository users) {

        this.tenants = tenants;
        this.users = users;
    }

    public List<UniversityAdminView> execute() {

        return tenants.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(
                                Tenant::getCode))
                .map(this::toView)
                .toList();
    }

    private UniversityAdminView toView(
            Tenant tenant) {

        long students =
                users.countByTenantIdAndRole(
                        tenant.getCode(),
                        UserRole.STUDENT);

        long coordinators =
                users.countByTenantIdAndRole(
                        tenant.getCode(),
                        UserRole.COORDINATOR);

        return new UniversityAdminView(
                tenant.getCode(),
                tenant.getName(),
                tenant.getLogoUrl(),
                tenant.getPrimaryColor(),
                tenant.getSecondaryColor(),
                tenant.getEmailDomains(),
                tenant.getMinimumTeachingGrade(),
                tenant.getTimezone()
                        .getId(),
                tenant.isActive(),
                students,
                coordinators);
    }
}