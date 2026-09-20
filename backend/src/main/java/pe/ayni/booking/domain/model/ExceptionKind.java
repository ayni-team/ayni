package pe.ayni.booking.domain.model;

/** What a date specific exception does to the availability the weekly patterns describe. */
public enum ExceptionKind {

  /** Extraordinary availability outside the recurring hours of that date. */
  ADD,

  /** Availability the patterns would otherwise give, taken away for that date. */
  REMOVE
}
