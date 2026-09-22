package pe.ayni.identity.application;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.CreditPolicyView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.PolicyKind;
import pe.ayni.identity.TenantView;
import pe.ayni.identity.UserView;
import pe.ayni.identity.domain.model.CreditPolicy;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.infrastructure.AcademicRecordRepository;
import pe.ayni.identity.infrastructure.CreditPolicyRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;

@Service
@Transactional(readOnly = true)
public class IdentityApiImpl implements IdentityApi {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final AcademicRecordRepository academicRecords;
    private final CreditPolicyRepository creditPolicies;

    public IdentityApiImpl(
            TenantRepository tenants,
            UserRepository users,
            AcademicRecordRepository academicRecords,
            CreditPolicyRepository creditPolicies) {

        this.tenants = tenants;
        this.users = users;
        this.academicRecords = academicRecords;
        this.creditPolicies = creditPolicies;
    }

    @Override
    public Optional<TenantView> findTenantByEmailDomain(String email) {
        return tenants.findByStatus(TenantStatus.ACTIVE).stream()
                .filter(tenant -> tenant.claims(email))
                .findFirst()
                .map(this::toTenantView);
    }

    @Override
    public TenantView requireTenant(String tenantCode) {
        Tenant tenant =
                tenants.findByCode(tenantCode)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Tenant not found: " + tenantCode));

        return toTenantView(tenant);
    }

    @Override
    public UserView requireUser(UUID userId) {
        String tenantId = TenantContext.require();

        User user =
                users.findByTenantIdAndId(tenantId, userId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "User not found: " + userId));

        return toUserView(user);
    }

    @Override
    public boolean isActive(UUID userId) {
        String tenantId = TenantContext.require();

        return users.findByTenantIdAndId(tenantId, userId)
                .map(User::isActive)
                .orElse(false);
    }

    @Override
    public Optional<CreditPolicyView> currentPolicy(
            String tenantCode,
            PolicyKind kind) {

        return creditPolicies
                .findByTenantIdAndKindAndSupersededAtIsNull(
                        tenantCode,
                        kind)
                .map(this::toCreditPolicyView);
    }

    @Override
    public List<ApprovedCourseView> approvedCourses(UUID userId) {
        String tenantId = TenantContext.require();

        return academicRecords
                .findByTenantIdAndUserId(tenantId, userId)
                .stream()
                .map(
                        record ->
                                new ApprovedCourseView(
                                        record.getCourseCode(),
                                        record.getCourseName(),
                                        record.getGrade(),
                                        record.getTerm()))
                .toList();
    }

    @Override
    public List<String> activeTenantCodes() {
        return tenants.findByStatus(TenantStatus.ACTIVE)
                .stream()
                .map(Tenant::getCode)
                .toList();
    }

    private TenantView toTenantView(Tenant tenant) {
        return new TenantView(
                tenant.getCode(),
                tenant.getName(),
                tenant.getTimezone().getId(),
                tenant.getMinimumTeachingGrade(),
                tenant.isActive());
    }

    private UserView toUserView(User user) {
        return new UserView(
                user.getId(),
                user.getTenantId(),
                user.getRole(),
                user.getEmail(),
                user.getStudentCode(),
                user.getFullName(),
                user.getCareer(),
                user.getCurrentTerm(),
                user.getPhotoUrl());
    }

    private CreditPolicyView toCreditPolicyView(
            CreditPolicy policy) {

        return new CreditPolicyView(
                policy.getId(),
                policy.getKind(),
                Credits.of(policy.getCreditsAmount()),
                policy.getValidityDays());
    }
}