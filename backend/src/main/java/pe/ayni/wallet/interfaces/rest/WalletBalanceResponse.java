package pe.ayni.wallet.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.domain.model.Balance;

/**
 * The balance as US23 asks for it: the total, where it came from, and when each part dies.
 *
 * @param available everything the student can spend right now
 * @param earnedTotal every credit ever earned by teaching, spent or not, which is what a
 *     recognition request is measured against
 * @param guidance how to obtain credits, present only when there is nothing to spend
 */
@Schema(name = "WalletBalance", description = "Credits available, by origin, with their expiry")
public record WalletBalanceResponse(
    @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID userId,
    @Schema(example = "12") int available,
    @Schema(example = "4") int earnedTotal,
    List<TypeBalance> byType,
    @Schema(nullable = true, example = "null") String guidance) {

  /** What the student is told when their wallet is empty. */
  static final String HOW_TO_OBTAIN_CREDITS =
      "You have no credits available. Teach a session to earn credits that never expire, "
          + "or ask your university about its credit policy.";

  /**
   * The credits of one origin.
   *
   * @param expires whether this kind of credit carries an expiry at all
   * @param countsTowardsRecognition true for EARNED and for nothing else: only hours taught back a
   *     recognition request
   */
  @Schema(name = "WalletBalanceByType")
  public record TypeBalance(
      @Schema(example = "SEED") CreditType type,
      @Schema(example = "6") int available,
      @Schema(example = "true") boolean expires,
      @Schema(example = "false") boolean countsTowardsRecognition,
      List<Group> groups) {}

  /**
   * A group of credits that arrived together.
   *
   * @param expiresAt {@code null} when these credits never expire
   */
  @Schema(name = "WalletCreditGroup")
  public record Group(
      @Schema(example = "6") int amount,
      @Schema(nullable = true, example = "2026-12-15T05:00:00Z") Instant expiresAt) {}

  static WalletBalanceResponse of(UUID userId, Balance balance, Credits earnedTotal) {

    List<TypeBalance> byType =
        balance.byType().stream()
            .map(
                breakdown ->
                    new TypeBalance(
                        breakdown.type(),
                        breakdown.available().amount(),
                        breakdown.expires(),
                        breakdown.countsTowardsRecognition(),
                        breakdown.groups().stream()
                            .map(group -> new Group(group.amount().amount(), group.expiresAt()))
                            .toList()))
            .toList();

    return new WalletBalanceResponse(
        userId,
        balance.available().amount(),
        earnedTotal.amount(),
        byType,
        balance.isEmpty() ? HOW_TO_OBTAIN_CREDITS : null);
  }
}
