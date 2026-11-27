package pe.ayni.paymentsfacade.infrastructure;

/** What the payments service answered: its status and its body, untouched. */
public record ServiceAnswer(int status, byte[] body) {

  public boolean successful() {
    return status >= 200 && status < 300;
  }
}
