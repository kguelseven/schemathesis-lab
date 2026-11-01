package org.korhan.schemathesis;

import io.swagger.v3.oas.annotations.media.Schema;

/** Antwort-Modell. Was hier steht, verspricht das Schema dem Client. */
@Schema(requiredProperties = {"id", "title", "author", "authorShort", "isbn", "year", "price"})
public record Book(
        long id,
        String title,
        String author,
        @Schema(description = "Kurzform für Listen, z.B. 'F. Kafka'")
        String authorShort,
        @Schema(pattern = "^[0-9]{13}$")
        String isbn,
        int year,
        double price) {
}
