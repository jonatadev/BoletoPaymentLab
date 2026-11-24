package org.example.samplemicroservice.boleto.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
class BoletoOpenApi {
    private static final String BOLETOS = "/api/v1/boletos";
    private static final String JSON = "application/json";
    private static final String PROBLEM = "application/problem+json";

    @Bean
    OpenApiCustomizer boletoContract() {
        return BoletoOpenApi::describe;
    }

    private static void describe(OpenAPI api) {
        api.setOpenapi("3.0.4");
        api.setInfo(
                new Info()
                        .title("Boleto Payment Lab API")
                        .version("v1")
                        .description(
                                "Educational REST API for synthetic boleto records. No real bank payment is supported."));
        api.setServers(List.of(new Server().url("/").description("Current API origin")));
        var scopes =
                new Scopes()
                        .addString("boletos:issue", "Issue boletos")
                        .addString("boletos:read", "Read boletos and download PDFs")
                        .addString("boletos:list", "List boletos")
                        .addString("boletos:pay", "Simulate payment")
                        .addString("boletos:cancel", "Cancel an issued boleto")
                        .addString("boletos:audit", "Read audit records and revisions")
                        .addString("ops:metrics", "Read operational metrics");
        if (api.getComponents() == null) {
            api.setComponents(new Components());
        }
        api.getComponents()
                .addSecuritySchemes(
                        "oauth2",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description(
                                        "Acquire a service token outside the browser. Configure the token URL for your realm.")
                                .flows(
                                        new OAuthFlows()
                                                .clientCredentials(
                                                        new OAuthFlow()
                                                                .tokenUrl(
                                                                        "http://127.0.0.1:8180/realms/boleto-lab/protocol/openid-connect/token")
                                                                .scopes(scopes))));
        api.getComponents()
                .addSecuritySchemes(
                        "bearerJwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description(
                                        "Paste an externally acquired access token with the operation's required scope."));
        api.getComponents()
                .addSchemas(
                        "Problem",
                        new ObjectSchema()
                                .addProperty("type", new StringSchema().example("about:blank"))
                                .addProperty("title", new StringSchema())
                                .addProperty("status", new IntegerSchema())
                                .addProperty("detail", new StringSchema())
                                .addProperty("instance", new StringSchema())
                                .addProperty("code", new StringSchema())
                                .required(List.of("type", "title", "status", "code")));

        operation(
                api,
                "",
                PathItem.HttpMethod.POST,
                "issueBoleto",
                "Issuance",
                "boletos:issue",
                "Issue a synthetic boleto or replay a completed request.",
                201,
                200,
                400,
                409,
                503);
        operation(
                api,
                "",
                PathItem.HttpMethod.GET,
                "listBoletos",
                "Queries",
                "boletos:list",
                "List boletos by customer, effective status and inclusive issuance-date range.",
                200,
                400,
                503);
        operation(
                api,
                "/{id}",
                PathItem.HttpMethod.GET,
                "getBoleto",
                "Queries",
                "boletos:read",
                "Retrieve a boleto by UUID.",
                200,
                400,
                404,
                503);
        operation(
                api,
                "/{id}/payments",
                PathItem.HttpMethod.POST,
                "simulatePayment",
                "Lifecycle",
                "boletos:pay",
                "Simulate payment of an issued, unexpired boleto.",
                200,
                400,
                404,
                409,
                503);
        operation(
                api,
                "/{id}/cancellations",
                PathItem.HttpMethod.POST,
                "cancelBoleto",
                "Lifecycle",
                "boletos:cancel",
                "Cancel an issued, unexpired boleto.",
                200,
                400,
                404,
                409,
                503);
        operation(
                api,
                "/{id}/pdf",
                PathItem.HttpMethod.GET,
                "downloadBoletoPdf",
                "Documents",
                "boletos:read",
                "Download a synthetic PDF. It is not valid for bank payment.",
                200,
                400,
                404,
                500,
                503);
        operation(
                api,
                "/{id}/history",
                PathItem.HttpMethod.GET,
                "getBoletoHistory",
                "Audit",
                "boletos:audit",
                "Read the ordered business audit history.",
                200,
                400,
                404,
                503);
        operation(
                api,
                "/{id}/revisions",
                PathItem.HttpMethod.GET,
                "listBoletoRevisions",
                "Audit",
                "boletos:audit",
                "List technical revisions with a backward cursor.",
                200,
                400,
                404,
                503);
        operation(
                api,
                "/{id}/revisions/{revision}",
                PathItem.HttpMethod.GET,
                "getBoletoRevision",
                "Audit",
                "boletos:audit",
                "Read a technical revision and its recorded state.",
                200,
                400,
                404,
                503);
        operation(
                api,
                "/{id}/revisions/compare",
                PathItem.HttpMethod.GET,
                "compareBoletoRevisions",
                "Audit",
                "boletos:audit",
                "Compare the snapshots of two technical revisions.",
                200,
                400,
                404,
                503);
        describeIssuance(api.getPaths().get(BOLETOS).getPost());
        var listing = api.getPaths().get(BOLETOS).getGet();
        for (var parameter : listing.getParameters()) {
            switch (parameter.getName()) {
                case "page" ->
                        parameter
                                .description("Zero-based page. The page multiplied by size must not exceed 100000.")
                                .schema(new IntegerSchema().minimum(BigDecimal.ZERO)._default(0));
                case "size" ->
                        parameter.schema(
                                new IntegerSchema()
                                        .minimum(BigDecimal.ONE)
                                        .maximum(BigDecimal.valueOf(100))
                                        ._default(20));
                case "sort" ->
                        parameter
                                .description("Field and direction. UUID breaks ties for non-ID fields.")
                                .schema(
                                        new StringSchema()
                                                .pattern("^(issuedAt|dueDate|amount|id),(asc|desc)$")
                                                ._default("issuedAt,desc"));
                case "from", "to" -> parameter.description("Inclusive issuance date in America/Sao_Paulo.");
                default -> {}
            }
        }
        var pdf = api.getPaths().get(BOLETOS + "/{id}/pdf").getGet().getResponses().get("200");
        pdf.setContent(content("application/pdf", new StringSchema().format("binary")));
        pdf.addHeaderObject(
                "Content-Disposition",
                new Header().schema(new StringSchema()).description("Attachment filename: boleto-{id}.pdf"));

        health(api, "/actuator/health", "getHealth");
        health(api, "/actuator/health/liveness", "getLiveness");
        health(api, "/actuator/health/readiness", "getReadiness");
        metrics(api, "/actuator/metrics", "listMetrics", false);
        metrics(api, "/actuator/metrics/{name}", "getMetric", true);
    }

    private static void operation(
            OpenAPI api,
            String suffix,
            PathItem.HttpMethod method,
            String id,
            String tag,
            String scope,
            String description,
            int... codes) {
        var operation = api.getPaths().get(BOLETOS + suffix).readOperationsMap().get(method);
        var original = operation.getResponses().get("200");
        var success = original == null ? null : original.getContent();
        if (success != null && success.containsKey("*/*")) {
            success = new Content().addMediaType(JSON, success.get("*/*"));
        }
        operation.setOperationId(id);
        operation.setTags(List.of(tag));
        operation.setSummary(description);
        protect(operation, scope);
        if (operation.getParameters() != null) {
            operation.getParameters().stream()
                    .filter(p -> "path".equals(p.getIn()) && "id".equals(p.getName()))
                    .forEach(p -> p.setSchema(new StringSchema().format("uuid")));
        }
        var responses = new ApiResponses();
        for (int code : codes) {
            responses.addApiResponse(
                    Integer.toString(code),
                    switch (code) {
                        case 200 -> new ApiResponse().description("Successful response").content(success);
                        case 201 -> new ApiResponse().description("New boleto issued").content(success);
                        case 400 -> problem("Invalid input", "INVALID_REQUEST", 400, "The request is invalid.");
                        case 404 ->
                                problem(
                                        "Record not found",
                                        id.contains("Revision") && !id.equals("listBoletoRevisions")
                                                ? "REVISION_NOT_FOUND"
                                                : "BOLETO_NOT_FOUND",
                                        404,
                                        null);
                        case 409 ->
                                problem(
                                        "Conflict with the current state or idempotency key",
                                        "INVALID_STATE_TRANSITION",
                                        409,
                                        "The requested transition is not valid for the current state.");
                        case 500 ->
                                problem(
                                        "PDF rendering failed",
                                        "PDF_RENDER_FAILED",
                                        500,
                                        "Could not render the document.");
                        case 503 ->
                                problem(
                                        "Database temporarily unavailable",
                                        "DATABASE_UNAVAILABLE",
                                        503,
                                        "Database is temporarily unavailable.");
                        default -> throw new IllegalArgumentException("Unexpected status " + code);
                    });
        }
        responses.addApiResponse("401", authenticationFailure(401, "Unauthorized", "UNAUTHORIZED", "Bearer"));
        responses.addApiResponse(
                "403", authenticationFailure(403, "Forbidden", "FORBIDDEN", "Bearer error=\"insufficient_scope\""));
        operation.setResponses(responses);
    }

    private static void describeIssuance(Operation operation) {
        var input =
                new ObjectSchema()
                        .additionalProperties(false)
                        .addProperty(
                                "customerId",
                                new StringSchema()
                                        .pattern("^[A-Za-z0-9._-]{1,120}$")
                                        .minLength(1)
                                        .maxLength(120)
                                        .example("customer-demo-001"))
                        .addProperty(
                                "amount",
                                new NumberSchema()
                                        .minimum(new BigDecimal("0.01"))
                                        .maximum(new BigDecimal("999999.99"))
                                        .multipleOf(new BigDecimal("0.01"))
                                        .example(new BigDecimal("10.00")))
                        .addProperty(
                                "dueDate",
                                new StringSchema()
                                        .format("date")
                                        .example("2099-11-30")
                                        .description("Future date in America/Sao_Paulo on first issuance."));
        input.setRequired(List.of("customerId", "amount", "dueDate"));
        operation.setRequestBody(new RequestBody().required(true).content(content(JSON, input)));
        operation.addParametersItem(
                new HeaderParameter()
                        .name("Idempotency-Key")
                        .required(true)
                        .description(
                                "Single printable ASCII key without spaces or commas, scoped to the customer and issuance operation.")
                        .schema(new StringSchema().minLength(1).maxLength(160).pattern("^(?!.*,)[!-~]{1,160}$")));
        operation
                .getResponses()
                .get("201")
                .addHeaderObject(
                        "Location",
                        new Header().description("Relative URI of the issued boleto").schema(new StringSchema()));
        operation.getResponses().get("200").setDescription("Matching request replayed with the original boleto UUID");
        var snapshot =
                new TreeMap<String, Object>(
                        Map.of(
                                "id",
                                "6a96c74d-1094-4ddd-b96a-a27145096e54",
                                "customerId",
                                "customer-demo-001",
                                "amount",
                                new BigDecimal("10.00"),
                                "currency",
                                "BRL",
                                "dueDate",
                                "2099-11-30",
                                "status",
                                "EMITIDO",
                                "issuedAt",
                                "2026-11-23T12:00:00Z"));
        operation.getResponses().get("201").getContent().get(JSON).example(snapshot);
        var body = content(PROBLEM, new Schema<>().$ref("#/components/schemas/Problem"));
        var conflict = new ApiResponse().description("Key conflict or issuance still in progress").content(body);
        var media = body.get(PROBLEM);
        media.addExamples(
                "differentPayload",
                new Example()
                        .value(
                                example(
                                        "IDEMPOTENCY_CONFLICT",
                                        409,
                                        "Conflict",
                                        "The key is associated with a different request.")));
        media.addExamples(
                "inProgress",
                new Example()
                        .value(
                                example(
                                        "OPERATION_IN_PROGRESS",
                                        409,
                                        "Conflict",
                                        "Issuance is in progress. Retry after the indicated delay.")));
        conflict.addHeaderObject(
                "Retry-After",
                new Header().description("Present for OPERATION_IN_PROGRESS").schema(new StringSchema().example("1")));
        operation.getResponses().addApiResponse("409", conflict);
    }

    private static void health(OpenAPI api, String path, String id) {
        var schema = new ObjectSchema().addProperty("status", new StringSchema());
        var operation =
                new Operation()
                        .operationId(id)
                        .summary("Read the Actuator probe status")
                        .tags(List.of("Operations"))
                        .security(List.of())
                        .responses(
                                new ApiResponses()
                                        .addApiResponse("200", probe("Probe is available", "UP", schema))
                                        .addApiResponse("503", probe("Probe is unavailable", "DOWN", schema)));
        api.path(path, new PathItem().get(operation));
    }

    private static ApiResponse probe(String description, String status, Schema<?> schema) {
        return new ApiResponse()
                .description(description)
                .content(
                        new Content()
                                .addMediaType(JSON, new MediaType().schema(schema).example(Map.of("status", status))));
    }

    private static void metrics(OpenAPI api, String path, String id, boolean detail) {
        var schema =
                detail
                        ? new ObjectSchema()
                                .addProperty("name", new StringSchema())
                                .addProperty("description", new StringSchema().nullable(true))
                                .addProperty("baseUnit", new StringSchema().nullable(true))
                                .addProperty(
                                        "measurements",
                                        new ArraySchema()
                                                .items(
                                                        new ObjectSchema()
                                                                .addProperty("statistic", new StringSchema())
                                                                .addProperty("value", new NumberSchema())))
                                .addProperty(
                                        "availableTags",
                                        new ArraySchema()
                                                .items(
                                                        new ObjectSchema()
                                                                .addProperty("tag", new StringSchema())
                                                                .addProperty(
                                                                        "values",
                                                                        new ArraySchema().items(new StringSchema()))))
                        : new ObjectSchema().addProperty("names", new ArraySchema().items(new StringSchema()));
        var operation =
                new Operation()
                        .operationId(id)
                        .summary(detail ? "Read measurements for a registered metric" : "List registered metric names")
                        .tags(List.of("Operations"))
                        .responses(
                                new ApiResponses()
                                        .addApiResponse(
                                                "200",
                                                new ApiResponse()
                                                        .description("Actuator metrics response")
                                                        .content(
                                                                content(
                                                                        "application/vnd.spring-boot.actuator.v3+json",
                                                                        schema)))
                                        .addApiResponse(
                                                "401",
                                                authenticationFailure(401, "Unauthorized", "UNAUTHORIZED", "Bearer"))
                                        .addApiResponse(
                                                "403",
                                                authenticationFailure(
                                                        403,
                                                        "Forbidden",
                                                        "FORBIDDEN",
                                                        "Bearer error=\"insufficient_scope\"")));
        if (detail) {
            operation.addParametersItem(new PathParameter().name("name").required(true).schema(new StringSchema()));
            operation.addParametersItem(
                    new QueryParameter()
                            .name("tag")
                            .description("Repeat tag=key:value to filter measurements.")
                            .schema(new ArraySchema().items(new StringSchema())));
            operation.getResponses().addApiResponse("404", new ApiResponse().description("Unknown metric"));
        }
        protect(operation, "ops:metrics");
        api.path(path, new PathItem().get(operation));
    }

    private static void protect(Operation operation, String scope) {
        operation.setDescription("Requires the `" + scope + "` scope with either authentication option.");
        operation.setSecurity(
                List.of(
                        new SecurityRequirement().addList("oauth2", scope),
                        new SecurityRequirement().addList("bearerJwt")));
    }

    private static Content content(String type, Schema<?> schema) {
        return new Content().addMediaType(type, new MediaType().schema(schema));
    }

    private static ApiResponse problem(String label, String code, int status, String detail) {
        var body = content(PROBLEM, new Schema<>().$ref("#/components/schemas/Problem"));
        body.get(PROBLEM).example(example(code, status, HttpStatus.valueOf(status).getReasonPhrase(), detail));
        return new ApiResponse().description(label).content(body);
    }

    private static ApiResponse authenticationFailure(int status, String title, String code, String challenge) {
        return problem(title, code, status, null)
                .addHeaderObject("WWW-Authenticate", new Header().schema(new StringSchema().example(challenge)));
    }

    private static Map<String, Object> example(String code, int status, String title, String detail) {
        var value =
                new TreeMap<String, Object>(
                        Map.of("type", "about:blank", "status", status, "title", title, "code", code));
        if (detail != null) {
            value.put("detail", detail);
        }
        return value;
    }
}
