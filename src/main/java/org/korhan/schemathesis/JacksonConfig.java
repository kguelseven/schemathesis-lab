package org.korhan.schemathesis;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/** Jackson ist von Haus aus nachsichtig: aus {"title": true} wird "true". Das Schema sagt string. */
@Configuration
public class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer strictScalars() {
        return builder -> builder
                .withCoercionConfig(LogicalType.Textual, cfg -> cfg
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Integer, cfg -> cfg
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Float, cfg -> cfg
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail));
    }
}
