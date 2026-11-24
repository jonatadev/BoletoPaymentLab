package org.example.samplemicroservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "BPL_OPENAPI_ENABLED=false")
@AutoConfigureMockMvc
class BoletoPaymentLabApplicationTests {
    @Autowired private MockMvc mvc;

    @Test
    void contextLoads() {}

    @Test
    void shouldKeepDocumentationRoutesClosedByDefault() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }
}
