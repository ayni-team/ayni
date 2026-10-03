package pe.ayni.identity.interfaces.rest;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

public record RegisterUniversityRequest(
        @NotBlank
        String code,

        @NotBlank
        String name,

        String logoUrl,

        String primaryColor,

        String secondaryColor,

        @NotEmpty
        List<@NotBlank String> emailDomains,

        @DecimalMin("0.00")
        @DecimalMax("20.00")
        BigDecimal minimumTeachingGrade,

        @NotBlank
        String timezone,

        @Min(1)
        int initialCredits,

        @Min(1)
        int initialCreditValidityDays) {}