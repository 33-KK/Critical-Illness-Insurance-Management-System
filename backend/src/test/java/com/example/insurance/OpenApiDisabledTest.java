package com.example.insurance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "API_DOCS_ENABLED=false",
      "app.seed=false",
      "spring.datasource.url=jdbc:h2:mem:insurance-openapi-off;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDisabledTest {
  @Autowired MockMvc mvc;

  @Test
  void environmentSwitchDisablesTheSpecificationAndUiWhileApiStaysProtected() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isNotFound());
    mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    mvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
  }
}
