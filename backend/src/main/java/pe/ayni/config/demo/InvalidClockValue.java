package pe.ayni.config.demo;

/** A value the demo clock cannot be moved by or to: answered with a 400. */
public class InvalidClockValue extends RuntimeException {

  public InvalidClockValue(String message) {
    super(message);
  }
}
