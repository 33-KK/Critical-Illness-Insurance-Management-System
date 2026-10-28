package com.example.insurance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.insurance.Domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Uses committed transactions, real authentication and independent threads to test ledger
 * invariants.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InsuranceIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired Store db;
  @Autowired ObjectMapper json;
  @Autowired PasswordEncoder encoder;
  @Autowired AuthController authController;
  @Autowired AdminController adminController;

  private User admin;
  private User doctor;
  private User reviewer;
  private String adminToken;
  private String doctorToken;
  private String reviewerToken;

  @BeforeEach
  void identities() {
    admin = user("admin");
    doctor = user("doctor");
    reviewer = user("reviewer");
    adminToken = token(admin);
    doctorToken = token(doctor);
    reviewerToken = token(reviewer);
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void seedDataSupportsDemoWithNoExternalServices() throws Exception {
    assertThat(db.users.selectCount(null)).isGreaterThanOrEqualTo(3);
    assertThat(db.patients.selectCount(null)).isGreaterThanOrEqualTo(16);
    assertThat(db.visits.selectCount(null)).isGreaterThanOrEqualTo(25);
    assertThat(db.menus.selectCount(null)).isGreaterThanOrEqualTo(14);
    assertThat(db.dicts.selectCount(null)).isGreaterThanOrEqualTo(9);
    assertThat(db.visits.selectCount(new QueryWrapper<Visit>().gt("outstanding_amount", 0)))
        .isPositive();
    for (String path :
        List.of(
            "/dashboard",
            "/patients",
            "/plans",
            "/surgeries",
            "/visits",
            "/transactions",
            "/admin/users",
            "/admin/roles",
            "/admin/permissions",
            "/admin/orgs",
            "/admin/dicts",
            "/admin/menus",
            "/admin/logs",
            "/menus",
            "/files")) {
      request(HttpMethod.GET, path, adminToken, null, 200);
    }
  }

  @Test
  void loginUsesSingleUseCaptchaBcryptAndHashedOpaqueSession() throws Exception {
    JsonNode challenge = request(HttpMethod.GET, "/auth/captcha", null, null, 200);
    String svg =
        new String(
            Base64.getDecoder().decode(challenge.get("svg").asText().split(",", 2)[1]),
            StandardCharsets.UTF_8);
    var matcher = Pattern.compile(">([0-9]{4})</text>").matcher(svg);
    assertThat(matcher.find()).isTrue();
    Map<String, Object> login =
        Map.of(
            "username",
            "doctor",
            "password",
            "Demo@2026",
            "captchaId",
            challenge.get("captchaId").asText(),
            "captchaCode",
            matcher.group(1));
    JsonNode signedIn = request(HttpMethod.POST, "/auth/login", null, login, 200);
    String raw = signedIn.get("token").asText();
    assertThat(raw).hasSize(43);
    Session stored =
        db.sessions.selectOne(
            new QueryWrapper<Session>().eq("token_hash", SecurityConfig.hash(raw)));
    assertThat(stored).isNotNull();
    assertThat(stored.getTokenHash()).isNotEqualTo(raw).hasSize(64);
    assertThat(doctor.getPasswordHash()).startsWith("$2");
    assertThat(signedIn.get("user").has("passwordHash")).isFalse();
    request(HttpMethod.POST, "/auth/login", null, login, 400);
    request(HttpMethod.GET, "/auth/me", raw, null, 200);
    request(HttpMethod.POST, "/auth/logout", raw, null, 200);
    request(HttpMethod.GET, "/auth/me", raw, null, 401);
  }

  @Test
  void authorizationIsEnforcedByBackendAndInvalidInputsAreRejected() throws Exception {
    Patient p = patient("100.00");
    request(HttpMethod.GET, "/patients", null, null, 401);
    request(HttpMethod.GET, "/admin/users", doctorToken, null, 403);
    request(
        HttpMethod.POST,
        "/patients/" + p.getId() + "/recharges",
        doctorToken,
        recharge("100", uuid()),
        403);
    request(
        HttpMethod.POST,
        "/patients/" + p.getId() + "/recharges",
        adminToken,
        recharge("50000.01", uuid()),
        400);
    request(
        HttpMethod.POST,
        "/patients/" + p.getId() + "/recharges",
        adminToken,
        recharge("-0.01", uuid()),
        400);
    request(
        HttpMethod.POST,
        "/patients/" + p.getId() + "/recharges",
        adminToken,
        recharge("0.001", uuid()),
        400);
    request(
        HttpMethod.POST,
        "/patients/" + p.getId() + "/recharges",
        adminToken,
        recharge("10", "not-a-uuid"),
        400);
    var malformed = new java.util.LinkedHashMap<>(visit(p, "10", uuid()));
    malformed.put("items", Arrays.asList((Object) null));
    request(HttpMethod.POST, "/visits", doctorToken, malformed, 400);
    assertMoney(balance(p), "100.00");
  }

  @Test
  void duplicateAndConcurrentRechargesCreditOnlyOnceAndChangedPayloadConflicts() throws Exception {
    Patient p = patient("0");
    String requestId = uuid();
    Map<String, Object> payload = recharge("100.00", requestId);
    List<MvcResult> concurrent =
        together(
            () ->
                perform(
                    HttpMethod.POST, "/patients/" + p.getId() + "/recharges", adminToken, payload),
            () ->
                perform(
                    HttpMethod.POST, "/patients/" + p.getId() + "/recharges", adminToken, payload));
    assertThat(concurrent).allSatisfy(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));
    assertThat(data(concurrent.get(0)).get("id")).isEqualTo(data(concurrent.get(1)).get("id"));
    assertMoney(balance(p), "100.00");
    assertThat(ledgerCount(p)).isEqualTo(1);
    request(
        HttpMethod.POST,
        "/patients/" + p.getId() + "/recharges",
        adminToken,
        recharge("101.00", requestId),
        409);
    assertMoney(balance(p), "100.00");
    List<Callable<MvcResult>> tasks = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      tasks.add(
          () ->
              perform(
                  HttpMethod.POST,
                  "/patients/" + p.getId() + "/recharges",
                  adminToken,
                  recharge("25.00", uuid())));
    }
    assertThat(together(tasks))
        .allSatisfy(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));
    assertMoney(balance(p), "300.00");
    assertThat(ledgerCount(p)).isEqualTo(9);
  }

  @Test
  void concurrentVisitSubmissionCannotSpendSameBalanceTwice() throws Exception {
    Patient p = patient("30.00");
    List<MvcResult> results =
        together(
            () -> perform(HttpMethod.POST, "/visits", doctorToken, visit(p, "100", uuid())),
            () -> perform(HttpMethod.POST, "/visits", doctorToken, visit(p, "100", uuid())));
    assertThat(results.stream().map(r -> r.getResponse().getStatus()).sorted().toList())
        .containsExactly(200, 409);
    assertMoney(balance(p), "10.00");
    assertThat(ledgerCount(p)).isEqualTo(1);
    assertThat(db.visits.selectCount(new QueryWrapper<Visit>().eq("patient_id", p.getId())))
        .isEqualTo(1);
  }

  @Test
  void duplicateVisitIsReplayedAndFailureRollsBackEntireCommand() throws Exception {
    Patient p = patient("10.00");
    String requestId = uuid();
    Map<String, Object> payload = visit(p, "100", requestId);
    request(HttpMethod.POST, "/visits", doctorToken, payload, 409);
    assertThat(ledgerCount(p)).isZero();
    assertThat(
            db.requests.selectCount(new QueryWrapper<RequestRecord>().eq("request_id", requestId)))
        .isZero();
    assertThat(db.visits.selectCount(new QueryWrapper<Visit>().eq("patient_id", p.getId())))
        .isZero();
    request(
        HttpMethod.POST,
        "/patients/" + p.getId() + "/recharges",
        adminToken,
        recharge("100", uuid()),
        200);
    JsonNode saved = request(HttpMethod.POST, "/visits", doctorToken, payload, 200);
    JsonNode replay = request(HttpMethod.POST, "/visits", doctorToken, payload, 200);
    assertThat(replay.get("id")).isEqualTo(saved.get("id"));
    assertMoney(balance(p), "90.00");
    request(HttpMethod.POST, "/visits", doctorToken, visit(p, "101", requestId), 409);
    assertThat(ledgerCount(p)).isEqualTo(2);
  }

  @Test
  void concurrentAuditCommitsOnlyOneDecisionAndOneFinancialEntry() throws Exception {
    Patient p = patient("500.00");
    long id = createVisit(p, "100");
    List<MvcResult> results =
        together(
            () ->
                perform(
                    HttpMethod.POST,
                    "/visits/" + id + "/review",
                    reviewerToken,
                    review("APPROVE", "材料完整", uuid())),
            () ->
                perform(
                    HttpMethod.POST,
                    "/visits/" + id + "/review",
                    reviewerToken,
                    review("REJECT", "不在保障范围", uuid())));
    assertThat(results.stream().map(r -> r.getResponse().getStatus()).sorted().toList())
        .containsExactly(200, 409);
    assertThat(db.audits.selectCount(new QueryWrapper<AuditRecord>().eq("visit_id", id)))
        .isEqualTo(1);
    assertThat(ledgerCount(p)).isEqualTo(2);
    Visit persisted = db.visits.selectById(id);
    assertMoney(balance(p), "APPROVED".equals(persisted.getAuditStatus()) ? "480.00" : "400.00");
  }

  @Test
  void approvedVisitRecordsSettlementWithoutSecondCardDebitAndReplayIsSafe() throws Exception {
    Patient p = patient("500.00");
    long id = createVisit(p, "100");
    String requestId = uuid();
    Map<String, Object> payload = review("APPROVE", "符合保障范围", requestId);
    JsonNode approved =
        request(HttpMethod.POST, "/visits/" + id + "/review", reviewerToken, payload, 200);
    assertThat(approved.get("paymentStatus").asText()).isEqualTo("SETTLED");
    assertMoney(balance(p), "480.00");
    CardTransaction settlement =
        db.transactions.selectOne(
            new QueryWrapper<CardTransaction>()
                .eq("patient_id", p.getId())
                .eq("type", "HOSPITAL_SETTLEMENT"));
    assertMoney(settlement.getAmount(), "0");
    request(HttpMethod.POST, "/visits/" + id + "/review", reviewerToken, payload, 200);
    request(
        HttpMethod.POST,
        "/visits/" + id + "/review",
        reviewerToken,
        review("REJECT", "changed payload", requestId),
        409);
    assertThat(ledgerCount(p)).isEqualTo(2);
  }

  @Test
  void rejectionAndPartialArrearsPaymentsNeverMakeBalanceNegative() throws Exception {
    Patient p = patient("25.00");
    long id = createVisit(p, "100");
    String reviewId = uuid();
    request(
        HttpMethod.POST,
        "/visits/" + id + "/review",
        reviewerToken,
        review("REJECT", "", reviewId),
        400);
    JsonNode rejected =
        request(
            HttpMethod.POST,
            "/visits/" + id + "/review",
            reviewerToken,
            review("REJECT", "超出保障范围", reviewId),
            200);
    assertMoney(rejected.get("outstandingAmount").decimalValue(), "75");
    assertThat(rejected.get("paymentStatus").asText()).isEqualTo("PENDING_TOPUP");
    assertMoney(balance(p), "0");
    request(HttpMethod.POST, "/visits", doctorToken, visit(p, "1", uuid()), 409);
    request(
        HttpMethod.POST,
        "/visits/" + id + "/settle-arrears",
        doctorToken,
        Map.of("requestId", uuid()),
        403);
    request(
        HttpMethod.POST,
        "/visits/" + id + "/settle-arrears",
        adminToken,
        Map.of("requestId", uuid()),
        409);
    request(
        HttpMethod.POST,
        "/patients/" + p.getId() + "/recharges",
        adminToken,
        recharge("30", uuid()),
        200);
    String settleId = uuid();
    JsonNode partial =
        request(
            HttpMethod.POST,
            "/visits/" + id + "/settle-arrears",
            adminToken,
            Map.of("requestId", settleId),
            200);
    assertMoney(partial.get("outstandingAmount").decimalValue(), "45");
    request(
        HttpMethod.POST,
        "/visits/" + id + "/settle-arrears",
        adminToken,
        Map.of("requestId", settleId),
        200);
    request(
        HttpMethod.POST,
        "/patients/" + p.getId() + "/recharges",
        adminToken,
        recharge("45", uuid()),
        200);
    JsonNode finished =
        request(
            HttpMethod.POST,
            "/visits/" + id + "/settle-arrears",
            adminToken,
            Map.of("requestId", uuid()),
            200);
    assertMoney(finished.get("outstandingAmount").decimalValue(), "0");
    assertMoney(balance(p), "0");
    assertThat(finished.get("paymentStatus").asText()).isEqualTo("PAID_IN_FULL");
    assertThat(
            db.transactions.selectList(
                new QueryWrapper<CardTransaction>().eq("patient_id", p.getId())))
        .allSatisfy(t -> assertThat(t.getBalanceAfter()).isNotNegative());
    assertThat(
            db.transactions.selectCount(
                new QueryWrapper<CardTransaction>()
                    .eq("patient_id", p.getId())
                    .eq("type", "ARREARS_PAYMENT")))
        .isEqualTo(2);
  }

  @Test
  void historyKeepsPricesRatesAndNamesWhenCatalogChanges() throws Exception {
    Patient p = patient("500.00");
    Surgery surgery = new Surgery();
    surgery.setCode("T" + uuid().substring(0, 20));
    surgery.setName("测试手术");
    surgery.setPrice(new BigDecimal("100.00"));
    surgery.setEnabled(true);
    db.surgeries.insert(surgery);
    var payload = new java.util.LinkedHashMap<>(visit(p, "10", uuid()));
    payload.put("items", List.of(Map.of("surgeryId", surgery.getId(), "quantity", 2)));
    JsonNode saved = request(HttpMethod.POST, "/visits", doctorToken, payload, 200);
    assertMoney(saved.get("totalAmount").decimalValue(), "210");
    assertMoney(saved.get("claimAmount").decimalValue(), "168");
    Plan originalPlan = db.plans.selectById(p.getPlanId());
    originalPlan.setName("修改后的保险名称");
    originalPlan.setRateA(new BigDecimal("0.2000"));
    request(HttpMethod.PUT, "/plans/" + originalPlan.getId(), reviewerToken, originalPlan, 200);
    surgery.setName("修改后的手术名称");
    surgery.setPrice(new BigDecimal("500"));
    request(HttpMethod.PUT, "/surgeries/" + surgery.getId(), reviewerToken, surgery, 200);
    JsonNode history =
        request(HttpMethod.GET, "/visits/" + saved.get("id").asLong(), doctorToken, null, 200);
    assertMoney(history.get("reimbursementRate").decimalValue(), "0.8");
    assertMoney(history.get("items").get(0).get("unitPrice").decimalValue(), "100");
    assertThat(history.get("items").get(0).get("surgeryName").asText()).isEqualTo("测试手术");
    assertThat(history.get("planName").asText()).isEqualTo("测试保障方案");
    JsonNode quote = request(HttpMethod.POST, "/visits/quote", doctorToken, payload, 200);
    assertMoney(quote.get("totalAmount").decimalValue(), "1010");
    assertMoney(quote.get("claimAmount").decimalValue(), "202");
    request(HttpMethod.DELETE, "/plans/" + originalPlan.getId(), reviewerToken, null, 200);
    request(HttpMethod.DELETE, "/surgeries/" + surgery.getId(), reviewerToken, null, 200);
    assertThat(db.plans.selectById(originalPlan.getId())).isNotNull();
    assertThat(db.surgeries.selectById(surgery.getId())).isNotNull();
    request(HttpMethod.POST, "/visits/quote", doctorToken, payload, 400);
    request(HttpMethod.GET, "/visits/" + saved.get("id").asLong(), doctorToken, null, 200);
  }

  @Test
  void doctorsCanOnlyListReadAndSeeDashboardForTheirOwnVisits() throws Exception {
    Patient p = patient("500");
    long id = createVisit(p, "100");
    User other = extraUser("DOCTOR");
    String otherToken = token(other);
    JsonNode list = request(HttpMethod.GET, "/visits", otherToken, null, 200);
    assertThat(list).isEmpty();
    request(HttpMethod.GET, "/visits/" + id, otherToken, null, 403);
    JsonNode dashboard = request(HttpMethod.GET, "/dashboard", otherToken, null, 200);
    assertThat(dashboard.get("visitCount").asInt()).isZero();
    assertThat(dashboard.get("recentVisits")).isEmpty();
    request(HttpMethod.GET, "/visits/" + id, reviewerToken, null, 200);
  }

  @Test
  void profileAndPasswordChangesCannotRestoreStaleSecurityFields() {
    User stale = extraUser("DOCTOR");
    String changedHash = encoder.encode("Concurrent@2026");
    db.users.update(
        null,
        new LambdaUpdateWrapper<User>()
            .eq(User::getId, stale.getId())
            .set(User::getRole, "REVIEWER")
            .set(User::getEnabled, false)
            .set(User::getPasswordHash, changedHash));
    as(stale);
    authController.profile(new Requests.Profile("新显示名", null, null));
    User afterProfile = db.users.selectById(stale.getId());
    assertThat(afterProfile.getRole()).isEqualTo("REVIEWER");
    assertThat(afterProfile.getEnabled()).isFalse();
    assertThat(afterProfile.getPasswordHash()).isEqualTo(changedHash);
    assertThat(afterProfile.getPhone()).isNull();
    assertThatThrownBy(
            () -> authController.password(new Requests.Password("Demo@2026", "Fresh@2026")))
        .isInstanceOf(Api.Failure.class)
        .hasMessage("原密码不正确");
    authController.password(new Requests.Password("Concurrent@2026", "Fresh@2026"));
    User afterPassword = db.users.selectById(stale.getId());
    assertThat(afterPassword.getRole()).isEqualTo("REVIEWER");
    assertThat(afterPassword.getEnabled()).isFalse();
    assertThat(encoder.matches("Fresh@2026", afterPassword.getPasswordHash())).isTrue();
  }

  @Test
  void passwordChangeRevokesSessionsAndAdminEditWithoutPasswordPreservesNewHash() throws Exception {
    User target = extraUser("DOCTOR");
    String oldSession = token(target);
    request(
        HttpMethod.PUT,
        "/auth/password",
        oldSession,
        Map.of("oldPassword", "Demo@2026", "newPassword", "Changed@2026"),
        200);
    request(HttpMethod.GET, "/auth/me", oldSession, null, 401);
    String hash = db.users.selectById(target.getId()).getPasswordHash();
    request(
        HttpMethod.PUT,
        "/admin/users/" + target.getId(),
        adminToken,
        Map.of(
            "username",
            target.getUsername(),
            "displayName",
            "修改后的名称",
            "role",
            "REVIEWER",
            "orgId",
            target.getOrgId(),
            "enabled",
            true),
        200);
    assertThat(db.users.selectById(target.getId()).getPasswordHash()).isEqualTo(hash);
    request(
        HttpMethod.PUT,
        "/admin/users/" + target.getId() + "/password",
        adminToken,
        Map.of("password", "Reset@2026"),
        200);
    assertThat(encoder.matches("Reset@2026", db.users.selectById(target.getId()).getPasswordHash()))
        .isTrue();
  }

  @Test
  void nullableFieldsCanBeClearedAndPatientBalanceCannotBeEdited() throws Exception {
    Patient p = patient("321.00");
    p.setBirthDate(LocalDate.of(1980, 1, 1));
    p.setPhone("13800138000");
    p.setAddress("测试地址");
    db.patients.updateById(p);
    p.setBirthDate(null);
    p.setPhone(null);
    p.setAddress(null);
    p.setBalance(new BigDecimal("999999.99"));
    request(HttpMethod.PUT, "/patients/" + p.getId(), reviewerToken, p, 200);
    Patient saved = db.patients.selectById(p.getId());
    assertThat(saved.getBirthDate()).isNull();
    assertThat(saved.getPhone()).isNull();
    assertThat(saved.getAddress()).isNull();
    assertMoney(saved.getBalance(), "321");
    JsonNode org =
        request(
            HttpMethod.POST,
            "/admin/orgs",
            adminToken,
            Map.of("name", "测试组织", "type", "HOSPITAL", "parentId", admin.getOrgId()),
            200);
    request(
        HttpMethod.PUT,
        "/admin/orgs/" + org.get("id").asLong(),
        adminToken,
        Map.of("name", "无父级组织", "type", "HOSPITAL"),
        200);
    assertThat(db.orgs.selectById(org.get("id").asLong()).getParentId()).isNull();
    JsonNode parentMenu =
        request(
            HttpMethod.POST,
            "/admin/menus",
            adminToken,
            Map.of("name", "测试父级", "path", "/test-parent"),
            200);
    JsonNode menu =
        request(
            HttpMethod.POST,
            "/admin/menus",
            adminToken,
            Map.of(
                "name", "测试菜单", "path", "/test-child", "parentId", parentMenu.get("id").asLong()),
            200);
    request(
        HttpMethod.PUT,
        "/admin/menus/" + menu.get("id").asLong(),
        adminToken,
        Map.of("name", "无父级菜单", "path", "/test-child"),
        200);
    assertThat(db.menus.selectById(menu.get("id").asLong()).getParentId()).isNull();
    request(HttpMethod.DELETE, "/patients/" + p.getId(), reviewerToken, null, 200);
    assertThat(db.patients.selectById(p.getId()).getEnabled()).isFalse();
    assertMoney(balance(p), "321");
  }

  @Test
  void filesAreValidatedAndOnlyVisibleToOwnerAndAdministrator() throws Exception {
    User owner = extraUser("DOCTOR");
    String ownerToken = token(owner);
    MockMultipartFile upload =
        new MockMultipartFile(
            "file", "材料.txt", "text/plain", "模拟就诊资料".getBytes(StandardCharsets.UTF_8));
    MvcResult uploaded =
        mvc.perform(
                multipart("/api/files")
                    .file(upload)
                    .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andReturn();
    long id = data(uploaded).get("id").asLong();
    assertThat(request(HttpMethod.GET, "/files", ownerToken, null, 200)).hasSize(1);
    request(HttpMethod.GET, "/files/" + id + "/download", reviewerToken, null, 403);
    mvc.perform(
            get("/api/files/" + id + "/download").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
    MockMultipartFile fakePdf =
        new MockMultipartFile(
            "file", "fake.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8));
    mvc.perform(
            multipart("/api/files").file(fakePdf).header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isBadRequest());
    request(HttpMethod.DELETE, "/files/" + id, ownerToken, null, 200);
    assertThat(db.files.selectById(id)).isNull();
  }

  private User user(String username) {
    return db.users.selectOne(new QueryWrapper<User>().eq("username", username));
  }

  private User extraUser(String role) {
    User u = new User();
    u.setUsername("u" + uuid().replace("-", ""));
    u.setDisplayName("测试用户");
    u.setPasswordHash(encoder.encode("Demo@2026"));
    u.setRole(role);
    u.setOrgId(doctor.getOrgId());
    u.setEnabled(true);
    db.users.insert(u);
    return u;
  }

  private String token(User user) {
    String raw = "test-" + uuid();
    Session session = new Session();
    session.setUserId(user.getId());
    session.setTokenHash(SecurityConfig.hash(raw));
    session.setExpiresAt(LocalDateTime.now().plusHours(1));
    db.sessions.insert(session);
    return raw;
  }

  private void as(User user) {
    Role role = db.roles.selectOne(new QueryWrapper<Role>().eq("code", user.getRole()));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                user,
                null,
                Arrays.stream(role.getPermissions().split(","))
                    .map(SimpleGrantedAuthority::new)
                    .toList()));
  }

  private Patient patient(String balance) {
    Plan plan = new Plan();
    plan.setCode("T" + uuid().substring(0, 20));
    plan.setName("测试保障方案");
    plan.setRateA(new BigDecimal("0.8000"));
    plan.setRateB(new BigDecimal("0.7000"));
    plan.setRateC(new BigDecimal("0.6000"));
    plan.setEnabled(true);
    db.plans.insert(plan);
    Patient p = new Patient();
    p.setName("测试参保人");
    p.setGender("女");
    p.setIdNumber("ID" + uuid().replace("-", "").substring(0, 20));
    p.setCardNo("CARD" + uuid());
    p.setPlanId(plan.getId());
    p.setEnabled(true);
    p.setBalance(new BigDecimal(balance).setScale(2));
    db.patients.insert(p);
    return p;
  }

  private Map<String, Object> recharge(String amount, String requestId) {
    return Map.of("amount", new BigDecimal(amount), "requestId", requestId, "note", "测试充值");
  }

  private Map<String, Object> visit(Patient p, String fee, String requestId) {
    return Map.of(
        "patientId",
        p.getId(),
        "outpatientType",
        "A",
        "diagnosisFee",
        new BigDecimal(fee),
        "diagnosis",
        "模拟诊断",
        "items",
        List.of(),
        "requestId",
        requestId);
  }

  private Map<String, Object> review(String decision, String reason, String requestId) {
    return Map.of("decision", decision, "reason", reason, "requestId", requestId);
  }

  private long createVisit(Patient p, String fee) throws Exception {
    return request(HttpMethod.POST, "/visits", doctorToken, visit(p, fee, uuid()), 200)
        .get("id")
        .asLong();
  }

  private BigDecimal balance(Patient p) {
    return db.patients.selectById(p.getId()).getBalance();
  }

  private long ledgerCount(Patient p) {
    return db.transactions.selectCount(
        new QueryWrapper<CardTransaction>().eq("patient_id", p.getId()));
  }

  private static void assertMoney(BigDecimal actual, String expected) {
    assertThat(actual).isEqualByComparingTo(expected);
  }

  private static String uuid() {
    return UUID.randomUUID().toString();
  }

  private JsonNode request(HttpMethod method, String path, String token, Object body, int expected)
      throws Exception {
    MvcResult result = perform(method, path, token, body);
    assertThat(result.getResponse().getStatus())
        .as(
            "%s %s: %s",
            method, path, result.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .isEqualTo(expected);
    return data(result);
  }

  private MvcResult perform(HttpMethod method, String path, String token, Object body)
      throws Exception {
    var builder =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
            method, "/api" + path);
    if (token != null) builder.header("Authorization", "Bearer " + token);
    if (body != null)
      builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
    return mvc.perform(builder).andReturn();
  }

  private JsonNode data(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .get("data");
  }

  @SafeVarargs
  private final List<MvcResult> together(Callable<MvcResult>... tasks) throws Exception {
    return together(Arrays.asList(tasks));
  }

  private List<MvcResult> together(List<Callable<MvcResult>> tasks) throws Exception {
    var executor = Executors.newFixedThreadPool(tasks.size());
    var ready = new CountDownLatch(tasks.size());
    var start = new CountDownLatch(1);
    try {
      var futures =
          tasks.stream()
              .map(
                  task ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            if (!start.await(15, TimeUnit.SECONDS))
                              throw new IllegalStateException(
                                  "Timed out starting concurrent requests");
                            return task.call();
                          }))
              .toList();
      assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      List<MvcResult> results = new ArrayList<>();
      for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
      return results;
    } finally {
      executor.shutdownNow();
    }
  }
}
