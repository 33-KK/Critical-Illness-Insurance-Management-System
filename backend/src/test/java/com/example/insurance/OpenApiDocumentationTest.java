package com.example.insurance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises generated documentation and real security filters without external services. */
@SpringBootTest(
    properties = {
      "app.seed=false",
      "spring.datasource.url=jdbc:h2:mem:insurance-openapi;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private JsonNode specification() throws Exception {
    return json.readTree(
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8));
  }

  private JsonNode requestSchema(JsonNode doc, String path, String verb) {
    String ref =
        doc.path("paths")
            .path(path)
            .path(verb)
            .path("requestBody")
            .path("content")
            .path("application/json")
            .path("schema")
            .path("$ref")
            .asText();
    assertThat(ref).startsWith("#/components/schemas/");
    JsonNode schema = doc.at(ref.substring(1));
    assertThat(schema.isObject()).isTrue();
    return schema;
  }

  @Test
  void specificationDocumentsOpaqueAuthenticationAndPublicOperations() throws Exception {
    JsonNode doc = specification();
    assertThat(doc.path("info").path("title").asText()).isEqualTo("大病保险管理系统 API");
    assertThat(doc.path("info").path("description").asText())
        .contains("PENDING_TOPUP", "requestId", "不是 JWT");
    JsonNode bearer = doc.path("components").path("securitySchemes").path("bearerAuth");
    assertThat(bearer.path("type").asText()).isEqualTo("http");
    assertThat(bearer.path("scheme").asText()).isEqualTo("bearer");
    assertThat(bearer.path("bearerFormat").asText()).isEqualTo("opaque");
    assertThat(doc.path("security").get(0).has("bearerAuth")).isTrue();
    for (String path : List.of("/api/auth/captcha", "/api/auth/login")) {
      JsonNode operation =
          doc.path("paths").path(path).path(path.endsWith("login") ? "post" : "get");
      assertThat(operation.path("security").isArray()).isTrue();
      assertThat(operation.path("security").size()).isZero();
    }
    assertThat(
            doc.path("paths")
                .path("/api/visits/{id}/review")
                .path("post")
                .path("tags")
                .get(0)
                .asText())
        .isEqualTo("保险审核");
    assertThat(doc.path("paths").has("/v3/api-docs")).isFalse();
  }

  @Test
  void requestBodiesDescribeMoneyIdempotencyAndWriteOnlySecrets() throws Exception {
    JsonNode doc = specification();
    JsonNode recharge = requestSchema(doc, "/api/patients/{id}/recharges", "post");
    assertThat(recharge.path("properties").path("amount").path("maximum").decimalValue())
        .isEqualByComparingTo("50000");
    assertThat(recharge.path("properties").path("amount").path("description").asText())
        .contains("元", "两位小数");
    assertThat(recharge.path("properties").path("requestId").path("format").asText())
        .isEqualTo("uuid");
    JsonNode visit = requestSchema(doc, "/api/visits", "post");
    assertThat(visit.path("properties").path("items").has("items")).isTrue();
    assertThat(visit.path("properties").path("diagnosisFee").path("description").asText())
        .contains("不包含手术费用");
    JsonNode review = requestSchema(doc, "/api/visits/{id}/review", "post");
    assertThat(review.path("properties").path("decision").path("enum").toString())
        .contains("APPROVE", "REJECT");
    JsonNode login = requestSchema(doc, "/api/auth/login", "post");
    assertThat(login.path("properties").path("password").path("writeOnly").asBoolean()).isTrue();
    JsonNode reset = requestSchema(doc, "/api/admin/users/{id}/password", "put");
    assertThat(reset.path("properties").path("password").path("writeOnly").asBoolean()).isTrue();
    assertThat(doc.toString()).doesNotContain("passwordHash", "tokenHash", "storageName");
  }

  @Test
  void swaggerUiAndConfigurationArePublicWithoutWeakeningApiSecurity() throws Exception {
    mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    JsonNode config =
        json.readTree(
            mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(config.path("url").asText()).isEqualTo("/v3/api-docs");
    assertThat(config.path("persistAuthorization").asBoolean()).isFalse();
    assertThat(config.path("displayRequestDuration").asBoolean()).isTrue();
    for (String path :
        List.of(
            "/api/patients",
            "/api/visits",
            "/api/admin/users",
            "/api/auth/me",
            "/swagger-ui-other",
            "/v3/api-docs-other")) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
  }
}
