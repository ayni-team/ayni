package pe.ayni.identity.application;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.PolicyKind;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.CoordinatorRequired;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.infrastructure.CreditPolicyRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US52: a coordinator reads the baseline credit policy their university applies today.
 *
 * <p>It is the version {@link UpdateCreditPolicyUseCase} would supersede: the one students are
 * granted when they are activated. The superseded versions are history and are not listed here.
 */
@Service
public class GetCreditPolicyQuery {

    private final CreditPolicyRepository creditPolicies;
    private final UserRepository users;

    GetCreditPolicyQuery(CreditPolicyRepository creditPolicies, UserRepository users) {
        this.creditPolicies = creditPolicies;
        this.users = users;
    }

    /**
     * @throws CoordinatorRequired when the person is not an active coordinator of this university
     * @throws NoSuchElementException when the university has no baseline policy
     */
    @Transactional(readOnly = true)
    public CreditPolicyConfigurationView execute(UUID currentUserId) {
        String tenantId = TenantContext.require();
        User coordinator =
                users.findByTenantIdAndId(tenantId, currentUserId)
                        .orElseThrow(
                                () ->
                                        new CoordinatorRequired(
                                                "Only an active coordinator can read the credit policy"));
        if (coordinator.getRole() != UserRole.COORDINATOR || !coordinator.isActive()) {
            throw new CoordinatorRequired("Only an active coordinator can read the credit policy");
        }
        return creditPolicies
                .findByTenantIdAndKindAndSupersededAtIsNull(tenantId, PolicyKind.BASELINE)
                .map(CreditPolicyConfigurationView::from)
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "The university does not have a baseline credit policy"));
    }
}
