package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * The grade a university asks of a student to teach a course.
 *
 * @param minimumGrade on the scale of 0 to 20, with at most two decimals
 */
@Schema(name = "MinimumGradeRequest", description = "Sets the minimum grade needed to teach a course")
public record MinimumGradeRequest(
    @NotNull @DecimalMin("0.00") @DecimalMax("20.00") @Digits(integer = 2, fraction = 2)
        @Schema(example = "14.00")
        BigDecimal minimumGrade) {}
