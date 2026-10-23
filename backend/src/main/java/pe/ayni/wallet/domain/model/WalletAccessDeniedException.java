package pe.ayni.wallet.domain.model;

public class WalletAccessDeniedException extends RuntimeException {

  public WalletAccessDeniedException() {
    super("Only a university coordinator can manage campus benefits");
  }

  public WalletAccessDeniedException(String message) {
    super(message);
  }
}
