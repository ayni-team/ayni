package pe.ayni.wallet;

public class WalletAccessDeniedException extends RuntimeException {

  public WalletAccessDeniedException() {
    super("Only a university coordinator can manage campus benefits");
  }
}
