package pe.ayni.payments.interfaces.rest;

import jakarta.validation.constraints.Min;

public record PurchaseRequest(@Min(1) int credits) {}
