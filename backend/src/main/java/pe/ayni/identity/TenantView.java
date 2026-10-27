package pe.ayni.identity;

import java.math.BigDecimal;

/** A university as the other modules see it. */
public record TenantView(
    String code, String name, String timezone, BigDecimal minimumTeachingGrade, boolean active) {}
