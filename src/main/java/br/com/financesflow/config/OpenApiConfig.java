package br.com.financesflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String API_PROBLEM_REF = "#/components/schemas/ApiProblem";

    @Bean
    GlobalOpenApiCustomizer problemResponsesCustomizer() {
        return openApi -> {
            addProblemSchema(openApi);
            openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperationsMap()
                    .forEach((method, operation) -> addDocumentedErrors(path, method, operation)));
        };
    }

    private void addProblemSchema(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }

        var fieldError = new ObjectSchema()
                .addProperty("field", new StringSchema().example("creditLimit"))
                .addProperty("message", new StringSchema().example("must be greater than or equal to 0.01"));
        var schema = new ObjectSchema()
                .addProperty("type", new StringSchema().format("uri").example("about:blank"))
                .addProperty("title", new StringSchema().example("Bad Request"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("detail", new StringSchema().example("Um ou mais campos são inválidos."))
                .addProperty("instance", new StringSchema().format("uri-reference").example("/api/cards"))
                .addProperty("errors", new ArraySchema().items(fieldError));
        openApi.getComponents().addSchemas("ApiProblem", schema);
    }

    private void addDocumentedErrors(String path, PathItem.HttpMethod method, Operation operation) {
        if (!path.startsWith("/api/")) {
            return;
        }

        addResponse(operation, "400", "Requisição inválida ou campos fora das regras de validação.");

        if (!path.startsWith("/api/auth/")) {
            addResponse(operation, "401", "Token ausente, inválido ou expirado.");
        }

        if (path.contains("{")) {
            addResponse(operation, "404", "Recurso não encontrado para o usuário autenticado.");
        }

        if (method == PathItem.HttpMethod.POST && isConflictRoute(path)) {
            addResponse(operation, "409", "Conflito com o estado atual do recurso ou regra de negócio.");
        }
    }

    private boolean isConflictRoute(String path) {
        return path.equals("/api/auth/register")
                || path.equals("/api/budgets")
                || path.equals("/api/cards")
                || path.endsWith("/purchases")
                || path.endsWith("/invoices/pay");
    }

    private void addResponse(Operation operation, String status, String description) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        if (responses.containsKey(status)) {
            return;
        }

        var schema = new Schema<>().$ref(API_PROBLEM_REF);
        var content = new Content().addMediaType("application/problem+json",
                new MediaType().schema(schema));
        responses.addApiResponse(status, new ApiResponse().description(description).content(content));
    }
}
