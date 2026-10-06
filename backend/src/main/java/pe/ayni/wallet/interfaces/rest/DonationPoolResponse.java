package pe.ayni.wallet.interfaces.rest;

import pe.ayni.wallet.application.DonationPoolView;

public record DonationPoolResponse(long credits, long contributions) {

  static DonationPoolResponse of(DonationPoolView pool) {
    return new DonationPoolResponse(pool.credits(), pool.contributions());
  }
}
