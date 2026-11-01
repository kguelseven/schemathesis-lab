package org.korhan.schemathesis;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Eingabe für POST und PUT. Die Annotationen landen als Constraints im OpenAPI-Schema. */
public record BookRequest(
        @NotNull @Size(min = 1, max = 200)
        @Schema(example = "Der Process")
        String title,

        @NotNull @Size(min = 1, max = 100)
        @Schema(example = "Franz Kafka")
        String author,

        @NotNull @Pattern(regexp = "^[0-9]{13}$")
        @Schema(description = "ISBN-13 ohne Bindestriche", example = "9783161484100")
        String isbn,

        @NotNull @Min(1450) @Max(2100)
        @Schema(example = "1925")
        Integer year,

        @NotNull @DecimalMin("0") @DecimalMax("10000")
        @Schema(example = "12.90")
        Double price) {
}
