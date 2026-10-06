package pe.ayni.wallet.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.CampusBenefitNotFoundException;
import pe.ayni.wallet.CreditUseConfirmationException;
import pe.ayni.wallet.CreditUseIdempotencyConflict;
import pe.ayni.wallet.InsufficientCreditsException;
import pe.ayni.wallet.InsufficientEarnedCreditsException;
import pe.ayni.wallet.domain.model.CampusBenefit;
import pe.ayni.wallet.domain.model.CreditAccount;
import pe.ayni.wallet.domain.model.CreditLot;
import pe.ayni.wallet.domain.model.CreditRuleViolation;
import pe.ayni.wallet.domain.model.CreditUse;
import pe.ayni.wallet.domain.model.CreditUseConfirmation;
import pe.ayni.wallet.domain.model.CreditUseKind;
import pe.ayni.wallet.domain.model.LedgerReason;
import pe.ayni.wallet.domain.model.Movement;
import pe.ayni.wallet.domain.model.ReferenceType;
import pe.ayni.wallet.domain.services.SpendingPlan;
import pe.ayni.wallet.infrastructure.CampusBenefitRepository;
import pe.ayni.wallet.infrastructure.CreditLotRepository;
import pe.ayni.wallet.infrastructure.CreditUseConfirmationRepository;
import pe.ayni.wallet.infrastructure.CreditUseRepository;

@Service
public class CreditUseService {

  private final Accounts accounts;
  private final CampusBenefitRepository benefits;
  private final CreditLotRepository lots;
  private final CreditUseRepository uses;
  private final CreditUseConfirmationRepository confirmations;
  private final LedgerWriter ledger;
  private final Clock clock;
  private final Duration confirmationValidity;

  CreditUseService(
      Accounts accounts,
      CampusBenefitRepository benefits,
      CreditLotRepository lots,
      CreditUseRepository uses,
      CreditUseConfirmationRepository confirmations,
      LedgerWriter ledger,
      Clock clock,
      @Value("${ayni.wallet.credit-use-confirmation-validity:PT10M}")
          Duration confirmationValidity) {
    this.accounts = accounts;
    this.benefits = benefits;
    this.lots = lots;
    this.uses = uses;
    this.confirmations = confirmations;
    this.ledger = ledger;
    this.clock = clock;
    this.confirmationValidity = confirmationValidity;
  }

  @Transactional
  public CreditUseOutcome redeemBenefit(
      UUID studentId,
      UUID benefitId,
      String idempotencyKey,
      UUID confirmationId,
      boolean confirmed) {
    String tenantId = TenantContext.require();
    validateIdempotencyKey(idempotencyKey);
    var previous =
        uses.findByTenantIdAndStudentIdAndIdempotencyKey(tenantId, studentId, idempotencyKey);
    if (previous.isPresent()) {
      return completedIfSame(
          previous.get(), CreditUseKind.CAMPUS_BENEFIT_REDEMPTION, benefitId, null);
    }

    if (!confirmed) {
      CampusBenefit benefit = requireActiveBenefit(tenantId, benefitId);
      return createConfirmation(
          tenantId,
          studentId,
          CreditUseKind.CAMPUS_BENEFIT_REDEMPTION,
          benefit.id(),
          benefit.name(),
          Credits.of(benefit.creditsCost()));
    }

    CreditUseConfirmation confirmation =
        requireConfirmation(
            tenantId,
            studentId,
            confirmationId,
            CreditUseKind.CAMPUS_BENEFIT_REDEMPTION,
            benefitId);
    CampusBenefit benefit = requireActiveBenefit(tenantId, benefitId);
    previous =
        uses.findByTenantIdAndStudentIdAndIdempotencyKey(tenantId, studentId, idempotencyKey);
    if (previous.isPresent()) {
      return completedIfSame(
          previous.get(), CreditUseKind.CAMPUS_BENEFIT_REDEMPTION, benefitId, null);
    }
    requireUnusedConfirmation(confirmation);

    return spendConfirmed(
        tenantId,
        studentId,
        idempotencyKey,
        confirmation,
        benefit.id(),
        confirmation.benefitName());
  }

  @Transactional
  public CreditUseOutcome donate(
      UUID studentId,
      Credits amount,
      String idempotencyKey,
      UUID confirmationId,
      boolean confirmed) {
    String tenantId = TenantContext.require();
    validateIdempotencyKey(idempotencyKey);
    if (amount == null || amount.isZero()) {
      throw new CreditRuleViolation("A donation must be greater than zero credits");
    }

    var previous =
        uses.findByTenantIdAndStudentIdAndIdempotencyKey(tenantId, studentId, idempotencyKey);
    if (previous.isPresent()) {
      return completedIfSame(
          previous.get(), CreditUseKind.INCOMING_STUDENT_DONATION, null, amount);
    }

    if (!confirmed) {
      return createConfirmation(
          tenantId, studentId, CreditUseKind.INCOMING_STUDENT_DONATION, null, null, amount);
    }

    CreditUseConfirmation confirmation =
        requireConfirmation(
            tenantId,
            studentId,
            confirmationId,
            CreditUseKind.INCOMING_STUDENT_DONATION,
            null);
    if (!confirmation.credits().equals(amount)) {
      throw new CreditUseConfirmationException(
          "The donation amount does not match the confirmed amount");
    }
    previous =
        uses.findByTenantIdAndStudentIdAndIdempotencyKey(tenantId, studentId, idempotencyKey);
    if (previous.isPresent()) {
      return completedIfSame(
          previous.get(), CreditUseKind.INCOMING_STUDENT_DONATION, null, amount);
    }
    requireUnusedConfirmation(confirmation);

    return spendConfirmed(tenantId, studentId, idempotencyKey, confirmation, null, null);
  }

  @Transactional(readOnly = true)
  public DonationPoolView donationPool() {
    String tenantId = TenantContext.require();
    return new DonationPoolView(totalDonated(tenantId), uses.donationCount(tenantId));
  }

  @Transactional(readOnly = true)
  public List<CreditUseOutcome> myDonations(UUID studentId) {
    String tenantId = TenantContext.require();
    long poolBalance = totalDonated(tenantId);
    return uses
        .findByTenantIdAndStudentIdAndKindOrderByCreatedAtDesc(
            tenantId, studentId, CreditUseKind.INCOMING_STUDENT_DONATION)
        .stream()
        .map(use -> CreditUseOutcome.completed(use, poolBalance))
        .toList();
  }

  private CreditUseOutcome createConfirmation(
      String tenantId,
      UUID studentId,
      CreditUseKind kind,
      UUID benefitId,
      String benefitName,
      Credits amount) {
    Instant now = clock.instant();
    CreditUseConfirmation confirmation =
        confirmations.save(
            CreditUseConfirmation.create(
                UUID.randomUUID(),
                tenantId,
                studentId,
                kind,
                benefitId,
                benefitName,
                amount,
                now.plus(confirmationValidity)));
    return CreditUseOutcome.confirmation(
        confirmation.id(), kind, benefitName, amount, totalDonated(tenantId));
  }

  private CreditUseConfirmation requireConfirmation(
      String tenantId,
      UUID studentId,
      UUID confirmationId,
      CreditUseKind kind,
      UUID benefitId) {
    if (confirmationId == null) {
      throw new CreditUseConfirmationException(
          "Request a confirmation warning before completing this irreversible operation");
    }
    CreditUseConfirmation confirmation =
        confirmations
            .lockByTenantStudentAndId(tenantId, studentId, confirmationId)
            .orElseThrow(
                () -> new CreditUseConfirmationException("The confirmation is invalid or expired"));
    if (confirmation.isExpiredAt(clock.instant())) {
      throw new CreditUseConfirmationException("The confirmation has expired; request a new one");
    }
    if (confirmation.kind() != kind
        || !java.util.Objects.equals(confirmation.benefitId(), benefitId)) {
      throw new CreditUseConfirmationException(
          "The confirmation does not match this credit-use operation");
    }
    return confirmation;
  }

  private static void requireUnusedConfirmation(CreditUseConfirmation confirmation) {
    if (confirmation.confirmedAt() != null) {
      throw new CreditUseConfirmationException("This confirmation has already been used");
    }
  }

  private CampusBenefit requireActiveBenefit(String tenantId, UUID benefitId) {
    return benefits
        .lockByTenantIdAndId(tenantId, benefitId)
        .filter(CampusBenefit::active)
        .orElseThrow(() -> new CampusBenefitNotFoundException(benefitId));
  }

  private CreditUseOutcome spendConfirmed(
      String tenantId,
      UUID studentId,
      String idempotencyKey,
      CreditUseConfirmation confirmation,
      UUID benefitId,
      String benefitName) {
    CreditAccount account =
        accounts
            .find(studentId)
            .orElseThrow(
                () -> new InsufficientEarnedCreditsException(confirmation.credits()));
    List<CreditLot> earnedLots =
        lots.lockSpendableOfType(tenantId, account.id(), CreditType.EARNED);

    var previous =
        uses.findByTenantIdAndStudentIdAndIdempotencyKey(tenantId, studentId, idempotencyKey);
    if (previous.isPresent()) {
      return completedIfSame(
          previous.get(), confirmation.kind(), confirmation.benefitId(), confirmation.credits());
    }

    Instant now = clock.instant();
    SpendingPlan plan;
    try {
      plan = SpendingPlan.of(earnedLots, confirmation.credits(), now);
    } catch (InsufficientCreditsException insufficient) {
      throw new InsufficientEarnedCreditsException(insufficient.missing());
    }

    CreditUse use =
        confirmation.kind() == CreditUseKind.CAMPUS_BENEFIT_REDEMPTION
            ? CreditUse.benefitRedemption(
                UUID.randomUUID(),
                tenantId,
                studentId,
                benefitId,
                benefitName,
                confirmation.credits(),
                idempotencyKey,
                now)
            : CreditUse.incomingStudentDonation(
                UUID.randomUUID(),
                tenantId,
                studentId,
                confirmation.credits(),
                idempotencyKey,
                now);
    confirmation.confirm(now);

    LedgerReason reason =
        confirmation.kind() == CreditUseKind.CAMPUS_BENEFIT_REDEMPTION
            ? LedgerReason.CAMPUS_BENEFIT_REDEMPTION
            : LedgerReason.INCOMING_STUDENT_DONATION;
    ReferenceType reference =
        confirmation.kind() == CreditUseKind.CAMPUS_BENEFIT_REDEMPTION
            ? ReferenceType.CAMPUS_BENEFIT_REDEMPTION
            : ReferenceType.INCOMING_STUDENT_DONATION;
    List<Movement> movements =
        plan.allocations().stream()
            .map(
                allocation -> {
                  allocation.lot().consume(allocation.amount());
                  return Movement.debit(
                      allocation.lot(), allocation.amount(), reason, reference, use.id());
                })
            .toList();

    uses.save(use);
    ledger.append(movements);
    return CreditUseOutcome.completed(use, totalDonated(tenantId));
  }

  private CreditUseOutcome completedIfSame(
      CreditUse previous, CreditUseKind kind, UUID benefitId, Credits amount) {
    boolean sameOperation =
        previous.kind() == kind
            && (kind == CreditUseKind.INCOMING_STUDENT_DONATION
                ? previous.credits().equals(amount)
                : java.util.Objects.equals(previous.benefitId(), benefitId));
    if (!sameOperation) {
      throw new CreditUseIdempotencyConflict();
    }
    return CreditUseOutcome.completed(previous, totalDonated(TenantContext.require()));
  }

  private long totalDonated(String tenantId) {
    Long total = uses.totalDonated(tenantId);
    return total == null ? 0 : total;
  }

  private static void validateIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
      throw new CreditRuleViolation("An idempotency key must contain 1 to 64 characters");
    }
  }
}
