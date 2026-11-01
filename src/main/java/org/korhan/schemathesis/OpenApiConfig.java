package org.korhan.schemathesis;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fehlerantworten, die jede Operation haben kann, einmal zentral dokumentieren. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenApiCustomizer commonErrors() {
        return openApi -> {
            ModelConverters.getInstance().read(Problem.class).forEach(openApi.getComponents()::addSchemas);
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(op -> {
                op.getResponses().putIfAbsent("400", problem("Ungültige Anfrage"));
                if (op.getRequestBody() != null) {
                    op.getResponses().putIfAbsent("415", problem("Content-Type nicht unterstützt"));
                }
            }));
        };
    }

    static ApiResponse problem(String description) {
        Schema<?> ref = new Schema<>().$ref("#/components/schemas/Problem");
        return new ApiResponse().description(description)
                .content(new Content().addMediaType("application/problem+json", new MediaType().schema(ref)));
    }
}
