package org.korhan.schemathesis;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Nur fürs Schema: beschreibt Springs ProblemDetail-Antwort korrekt.
 * springdoc dokumentiert "instance" als format "uri", Spring liefert aber relative Pfade wie "/books/1".
 */
@Schema(name = "Problem")
public record Problem(
        String type,
        String title,
        int status,
        String detail,
        @Schema(format = "uri-reference") String instance) {
}
