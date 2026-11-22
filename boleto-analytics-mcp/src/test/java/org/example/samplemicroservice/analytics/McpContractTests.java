package org.example.samplemicroservice.analytics;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class McpContractTests {
    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize",
             "params":{"protocolVersion":"2025-03-26","capabilities":{},
             "clientInfo":{"name":"analytics-test","version":"1.0"}}}
            """;

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AnalyticsPostgresFixture.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> AnalyticsPostgresFixture.READER);
        registry.add("spring.datasource.password", () -> AnalyticsPostgresFixture.READER_PASSWORD);
        registry.add("mcp.analytics.auth-issuer", () -> "https://issuer.example.test");
        registry.add("mcp.analytics.auth-jwks", () -> "https://issuer.example.test/jwks");
        registry.add("mcp.analytics.auth-audience", () -> "boleto-analytics-mcp");
    }

    @Test
    void shouldRejectAnonymousMcpRequests() throws Exception {
        mvc.perform(post("/mcp").contentType("application/json").content(INITIALIZE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectTokensWithoutTheAnalyticsScope() throws Exception {
        mvc.perform(post("/mcp").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_boletos:read")))
                .contentType("application/json").content(INITIALIZE))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAcceptTheAnalyticsScopeForMcpInitialization() throws Exception {
        mvc.perform(post("/mcp").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_analytics:read")))
                .contentType("application/json").accept("application/json, text/event-stream").content(INITIALIZE))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDiscoverOnlyTheReadOnlyAnalyticsTools() throws Exception {
        var authority = jwt().authorities(new SimpleGrantedAuthority("SCOPE_analytics:read"));
        var initialization = mvc.perform(post("/mcp").with(authority).contentType("application/json")
                .accept("application/json, text/event-stream").content(INITIALIZE)).andExpect(status().isOk())
                .andReturn();
        var sessionId = initialization.getResponse().getHeader("Mcp-Session-Id");

        mvc.perform(post("/mcp").with(authority).header("Mcp-Session-Id", sessionId)
                .contentType("application/json").accept("application/json, text/event-stream")
                .content("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("get_boleto_summary")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("execute_sql"))));
    }
}
