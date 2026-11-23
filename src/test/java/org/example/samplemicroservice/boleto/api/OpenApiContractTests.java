package org.example.samplemicroservice.boleto.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@SpringBootTest(properties = "BPL_OPENAPI_ENABLED=true")
@AutoConfigureMockMvc
class OpenApiContractTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void shouldServeSwaggerUiWithoutOpeningProtectedRoutes() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/boletos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldGenerateAndVerifyThePublishedContract() throws Exception {
        String yaml = mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8).replace("\r\n", "\n");
        Map<String, Object> contract = new Yaml().load(yaml);
        assertThat(contract.get("openapi")).isEqualTo("3.0.4");
        Map<?, ?> paths = (Map<?, ?>) contract.get("paths");
        var ids = new HashSet<>();
        var operations = Map.ofEntries(
                Map.entry("post /api/v1/boletos", "boletos:issue"),
                Map.entry("get /api/v1/boletos", "boletos:list"),
                Map.entry("get /api/v1/boletos/{id}", "boletos:read"),
                Map.entry("get /api/v1/boletos/{id}/pdf", "boletos:read"),
                Map.entry("post /api/v1/boletos/{id}/payments", "boletos:pay"),
                Map.entry("post /api/v1/boletos/{id}/cancellations", "boletos:cancel"),
                Map.entry("get /api/v1/boletos/{id}/history", "boletos:audit"),
                Map.entry("get /api/v1/boletos/{id}/revisions", "boletos:audit"),
                Map.entry("get /api/v1/boletos/{id}/revisions/{revision}", "boletos:audit"),
                Map.entry("get /api/v1/boletos/{id}/revisions/compare", "boletos:audit"),
                Map.entry("get /actuator/health", ""),
                Map.entry("get /actuator/health/liveness", ""),
                Map.entry("get /actuator/health/readiness", ""),
                Map.entry("get /actuator/metrics", "ops:metrics"),
                Map.entry("get /actuator/metrics/{name}", "ops:metrics"));
        var seen = new HashSet<String>();
        paths.forEach((path, mapping) -> ((Map<?, ?>) mapping).forEach((method, value) -> {
            if (!List.of("get", "post", "put", "patch", "delete", "head", "options", "trace").contains(method)) {
                return;
            }
            String key = method + " " + path;
            seen.add(key);
            var operation = (Map<?, ?>) value;
            assertThat(operation.get("operationId")).as(key).isInstanceOf(String.class);
            assertThat(ids.add(operation.get("operationId"))).as("unique operation ID for %s", key).isTrue();
            String scope = operations.get(key);
            assertThat(scope).as(key).isNotNull();
            if (!scope.isEmpty()) {
                assertThat(operation.get("security")).as(key).isEqualTo(
                        List.of(Map.of("oauth2", List.of(scope)), Map.of("bearerJwt", List.of())));
            }
        }));
        assertThat(seen).containsExactlyInAnyOrderElementsOf(operations.keySet());
        var issue = (Map<?, ?>) ((Map<?, ?>) paths.get("/api/v1/boletos")).get("post");
        var issueResponses = (Map<?, ?>) issue.get("responses");
        assertThat(issueResponses.keySet().stream().map(Object::toString).toList()).contains("200", "201", "409");
        var pdf = (Map<?, ?>) ((Map<?, ?>) paths.get("/api/v1/boletos/{id}/pdf")).get("get");
        var pdfResponse = (Map<?, ?>) ((Map<?, ?>) pdf.get("responses")).get("200");
        assertThat(pdfResponse.get("content").toString()).contains("application/pdf", "format=binary");
        verifyReferences(contract, contract);

        var published = Path.of(System.getProperty("bpl.openapi.published"));
        if (Boolean.getBoolean("bpl.openapi.regenerate")) {
            Files.createDirectories(published.getParent());
            var format = new DumperOptions();
            format.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            format.setIndentWithIndicator(true);
            format.setIndicatorIndent(2);
            Files.writeString(published, new Yaml(format).dump(contract));
        } else {
            Map<String, Object> publishedContract = new Yaml().load(Files.readString(published));
            assertThat(publishedContract).as("published OpenAPI contract").isEqualTo(contract);
        }
    }

    private static void verifyReferences(Object node, Map<String, Object> document) {
        if (node instanceof Map<?, ?> map) {
            assertThat(map.containsKey("example") && map.containsKey("examples"))
                    .as("example and examples are mutually exclusive").isFalse();
            if (map.get("$ref") instanceof String reference) {
                assertThat(reference).startsWith("#/");
                Object target = document;
                for (String segment : reference.substring(2).split("/")) {
                    assertThat(target).as(reference).isInstanceOf(Map.class);
                    target = ((Map<?, ?>) target).get(segment.replace("~1", "/").replace("~0", "~"));
                }
                assertThat(target).as(reference).isNotNull();
            }
            map.values().forEach(value -> verifyReferences(value, document));
        } else if (node instanceof List<?> list) {
            list.forEach(value -> verifyReferences(value, document));
        }
    }
}
