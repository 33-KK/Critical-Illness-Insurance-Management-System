package com.example.insurance;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.insurance.Domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Financial commands use database transactions and row locks, never JVM-only locks. */
@Service
@RequiredArgsConstructor
public class BusinessService {
  private final Store db;
  private final Access access;
  private final ObjectMapper json;
  public static final BigDecimal ZERO = new BigDecimal("0.00");

  public Map<String, Object> map(Object value) {
    return json.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  public BigDecimal outstanding(Long patientId) {
    return db
        .visits
        .selectList(
            new QueryWrapper<Visit>().eq("patient_id", patientId).gt("outstanding_amount", 0))
        .stream()
        .map(Visit::getOutstandingAmount)
        .reduce(ZERO, BigDecimal::add);
  }

  public Map<String, Object> patientView(Patient patient) {
    Map<String, Object> result = map(patient);
    Plan plan = db.plans.selectById(patient.getPlanId());
    result.put("planName", plan == null ? "" : plan.getName());
    result.put("outstandingAmount", outstanding(patient.getId()));
    return result;
  }

  public void visitScope(Visit visit) {
    if ("DOCTOR".equals(access.user().getRole())
        && !access.user().getId().equals(visit.getDoctorId())) throw Api.forbidden();
  }

  public Map<String, Object> visitView(Visit visit) {
    visitScope(visit);
    Map<String, Object> result = map(visit);
    result.put(
        "items",
        db.items.selectList(
            new QueryWrapper<VisitItem>().eq("visit_id", visit.getId()).orderByAsc("id")));
    result.put(
        "auditRecords",
        db.audits.selectList(
            new QueryWrapper<AuditRecord>().eq("visit_id", visit.getId()).orderByAsc("id")));
    return result;
  }

  public List<Visit> visits(String keyword, String status) {
    QueryWrapper<Visit> q = new QueryWrapper<>();
    if ("DOCTOR".equals(access.user().getRole())) q.eq("doctor_id", access.user().getId());
    if (keyword != null && !keyword.isBlank())
      q.and(
          w ->
              w.like("visit_no", keyword)
                  .or()
                  .like("patient_name", keyword)
                  .or()
                  .like("diagnosis", keyword));
    if (status != null && !status.isBlank())
      q.and(w -> w.eq("audit_status", status).or().eq("payment_status", status));
    return db.visits.selectList(q.orderByDesc("id"));
  }

  private record Calculation(Visit visit, List<VisitItem> items) {}

  private Calculation calculate(Requests.VisitInput input, Patient patient) {
    if (!Boolean.TRUE.equals(patient.getEnabled())) throw Api.bad("参保人已停用");
    Plan plan = Access.found(db.plans.selectById(patient.getPlanId()), "保险方案");
    if (!Boolean.TRUE.equals(plan.getEnabled())) throw Api.bad("当前保险方案已停用，请重新关联保险");
    BigDecimal rate =
        switch (input.outpatientType()) {
          case "A" -> plan.getRateA();
          case "B" -> plan.getRateB();
          case "C" -> plan.getRateC();
          default -> throw Api.bad("门诊类型无效");
        };
    List<VisitItem> items = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    BigDecimal surgeryFee = ZERO;
    for (Requests.Item line : input.items()) {
      if (!seen.add(line.surgeryId())) throw Api.bad("同一手术不能重复选择，请调整数量");
      Surgery surgery = Access.found(db.surgeries.selectById(line.surgeryId()), "手术");
      if (!Boolean.TRUE.equals(surgery.getEnabled()))
        throw Api.bad("手术 " + surgery.getName() + " 已停用");
      VisitItem item = new VisitItem();
      item.setSurgeryId(surgery.getId());
      item.setSurgeryName(surgery.getName());
      item.setUnitPrice(surgery.getPrice());
      item.setQuantity(line.quantity());
      item.setSubtotal(
          surgery
              .getPrice()
              .multiply(BigDecimal.valueOf(line.quantity()))
              .setScale(2, RoundingMode.HALF_UP));
      surgeryFee = surgeryFee.add(item.getSubtotal());
      items.add(item);
    }
    Visit visit = new Visit();
    visit.setPatientId(patient.getId());
    visit.setPatientName(patient.getName());
    visit.setCardNo(patient.getCardNo());
    visit.setPlanId(plan.getId());
    visit.setPlanName(plan.getName());
    visit.setOutpatientType(input.outpatientType());
    visit.setDiagnosis(input.diagnosis());
    visit.setDiagnosisFee(input.diagnosisFee().setScale(2, RoundingMode.HALF_UP));
    visit.setSurgeryFee(surgeryFee);
    visit.setTotalAmount(visit.getDiagnosisFee().add(surgeryFee));
    if (visit.getTotalAmount().signum() <= 0
        || visit.getTotalAmount().compareTo(new BigDecimal("99999999.99")) > 0)
      throw Api.bad("总费用必须大于 0 且不超过 99999999.99 元");
    visit.setReimbursementRate(rate);
    visit.setClaimAmount(visit.getTotalAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP));
    visit.setSelfPayAmount(visit.getTotalAmount().subtract(visit.getClaimAmount()));
    visit.setOutstandingAmount(ZERO);
    return new Calculation(visit, items);
  }

  public Map<String, Object> quote(Requests.VisitInput input) {
    Calculation c =
        calculate(input, Access.found(db.patients.selectById(input.patientId()), "参保人"));
    Map<String, Object> result = map(c.visit());
    result.put("items", c.items());
    return result;
  }

  private String fingerprint(Object payload, Long subject) {
    try {
      return SecurityConfig.hash(subject + ":" + json.writeValueAsString(payload));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private RequestRecord replay(String requestId, String action, String fingerprint) {
    try {
      UUID.fromString(requestId);
    } catch (Exception e) {
      throw Api.bad("requestId 必须为 UUID");
    }
    RequestRecord prior =
        db.requests.selectOne(new QueryWrapper<RequestRecord>().eq("request_id", requestId));
    if (prior != null
        && (!prior.getAction().equals(action)
            || !prior.getUserId().equals(access.user().getId())
            || !prior.getFingerprint().equals(fingerprint)))
      throw Api.conflict("requestId 已用于其他请求，请勿更改同一请求的内容");
    return prior;
  }

  private void remember(String requestId, String action, String fingerprint, Long businessId) {
    RequestRecord record = new RequestRecord();
    record.setRequestId(requestId);
    record.setAction(action);
    record.setFingerprint(fingerprint);
    record.setUserId(access.user().getId());
    record.setBusinessId(businessId);
    db.requests.insert(record);
  }

  public CardTransaction ledger(
      Patient patient, String type, BigDecimal amount, String businessNo, String note) {
    CardTransaction entry = new CardTransaction();
    entry.setPatientId(patient.getId());
    entry.setPatientName(patient.getName());
    entry.setCardNo(patient.getCardNo());
    entry.setType(type);
    entry.setAmount(amount.setScale(2));
    entry.setBalanceAfter(patient.getBalance());
    entry.setBusinessNo(businessNo);
    entry.setNote(note);
    entry.setOperatorName(access.user().getDisplayName());
    entry.setCreatedAt(LocalDateTime.now());
    db.transactions.insert(entry);
    return entry;
  }

  @Transactional
  public CardTransaction recharge(Long patientId, Requests.Recharge input) {
    Patient patient = Access.found(db.patients.lockById(patientId), "参保人");
    String fp = fingerprint(input, patientId);
    RequestRecord prior = replay(input.requestId(), "RECHARGE", fp);
    if (prior != null) return db.transactions.selectById(prior.getBusinessId());
    if (!Boolean.TRUE.equals(patient.getEnabled())) throw Api.bad("参保人已停用");
    BigDecimal newBalance = patient.getBalance().add(input.amount());
    if (newBalance.compareTo(new BigDecimal("999999999999.99")) > 0) throw Api.bad("账户余额超出允许范围");
    patient.setBalance(newBalance.setScale(2));
    db.patients.updateById(patient);
    CardTransaction entry =
        ledger(
            patient,
            "RECHARGE",
            input.amount(),
            "RC" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
            input.note());
    remember(input.requestId(), "RECHARGE", fp, entry.getId());
    access.log("RECHARGE", patient.getCardNo(), "模拟充值 " + input.amount() + " 元");
    return entry;
  }

  @Transactional
  public Map<String, Object> createVisit(Requests.VisitInput input) {
    Patient patient = Access.found(db.patients.lockById(input.patientId()), "参保人");
    String fp = fingerprint(input, input.patientId());
    RequestRecord prior = replay(input.requestId(), "VISIT", fp);
    if (prior != null) return visitView(db.visits.selectById(prior.getBusinessId()));
    if (outstanding(patient.getId()).signum() > 0) throw Api.conflict("参保人存在待补缴费用，请先充值并结清欠款");
    Calculation c = calculate(input, patient);
    Visit visit = c.visit();
    if (patient.getBalance().compareTo(visit.getSelfPayAmount()) < 0)
      throw Api.conflict("医保卡余额不足，自付金额为 " + visit.getSelfPayAmount() + " 元");
    User doctor = access.user();
    visit.setDoctorId(doctor.getId());
    visit.setDoctorName(doctor.getDisplayName());
    visit.setVisitNo(
        "V"
            + LocalDate.now().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    visit.setAuditStatus("PENDING");
    visit.setPaymentStatus("SELF_PAID");
    visit.setCreatedAt(LocalDateTime.now());
    db.visits.insert(visit);
    for (VisitItem item : c.items()) {
      item.setVisitId(visit.getId());
      db.items.insert(item);
    }
    patient.setBalance(patient.getBalance().subtract(visit.getSelfPayAmount()));
    db.patients.updateById(patient);
    ledger(patient, "SELF_PAY", visit.getSelfPayAmount().negate(), visit.getVisitNo(), "就诊自付扣款");
    remember(input.requestId(), "VISIT", fp, visit.getId());
    access.log("VISIT_CREATE", visit.getVisitNo(), "登记就诊，总费用 " + visit.getTotalAmount() + " 元");
    return visitView(visit);
  }

  @Transactional
  public Map<String, Object> review(Long visitId, Requests.Review input) {
    Visit visit = Access.found(db.visits.lockById(visitId), "就诊记录");
    visitScope(visit);
    String fp = fingerprint(input, visitId);
    RequestRecord prior = replay(input.requestId(), "REVIEW", fp);
    if (prior != null) return visitView(visit);
    if (!"PENDING".equals(visit.getAuditStatus())) throw Api.conflict("该就诊已审核，不可重复审核");
    if ("REJECT".equals(input.decision()) && (input.reason() == null || input.reason().isBlank()))
      throw Api.bad("拒绝报销必须填写原因");
    Patient patient = Access.found(db.patients.lockById(visit.getPatientId()), "参保人");
    if ("APPROVE".equals(input.decision())) {
      visit.setAuditStatus("APPROVED");
      visit.setPaymentStatus("SETTLED");
      ledger(
          patient,
          "HOSPITAL_SETTLEMENT",
          ZERO,
          visit.getVisitNo(),
          "模拟医院结算 " + visit.getClaimAmount() + " 元（不变更卡余额）");
    } else {
      BigDecimal paid = patient.getBalance().min(visit.getClaimAmount());
      patient.setBalance(patient.getBalance().subtract(paid));
      db.patients.updateById(patient);
      visit.setAuditStatus("REJECTED");
      visit.setRejectReason(input.reason());
      visit.setOutstandingAmount(visit.getClaimAmount().subtract(paid));
      visit.setPaymentStatus(
          visit.getOutstandingAmount().signum() > 0 ? "PENDING_TOPUP" : "PAID_IN_FULL");
      ledger(
          patient,
          "REJECT_DEDUCTION",
          paid.negate(),
          visit.getVisitNo(),
          "拒绝报销补扣；待补缴 " + visit.getOutstandingAmount() + " 元");
    }
    visit.setReviewedAt(LocalDateTime.now());
    db.visits.updateById(visit);
    AuditRecord audit = new AuditRecord();
    audit.setVisitId(visitId);
    audit.setReviewerId(access.user().getId());
    audit.setReviewerName(access.user().getDisplayName());
    audit.setDecision(input.decision());
    audit.setReason(input.reason());
    audit.setCreatedAt(visit.getReviewedAt());
    db.audits.insert(audit);
    remember(input.requestId(), "REVIEW", fp, visitId);
    access.log(
        "REVIEW",
        visit.getVisitNo(),
        input.decision() + " " + Objects.toString(input.reason(), ""));
    return visitView(visit);
  }

  @Transactional
  public Map<String, Object> settle(Long visitId, Requests.Arrears input) {
    Visit visit = Access.found(db.visits.lockById(visitId), "就诊记录");
    String fp = fingerprint(input, visitId);
    RequestRecord prior = replay(input.requestId(), "SETTLE_ARREARS", fp);
    if (prior != null) return visitView(visit);
    if (visit.getOutstandingAmount().signum() <= 0) throw Api.conflict("此就诊没有待补缴费用");
    Patient patient = Access.found(db.patients.lockById(visit.getPatientId()), "参保人");
    BigDecimal amount = patient.getBalance().min(visit.getOutstandingAmount());
    if (amount.signum() <= 0) throw Api.conflict("医保卡余额为零，请先充值");
    patient.setBalance(patient.getBalance().subtract(amount));
    db.patients.updateById(patient);
    visit.setOutstandingAmount(visit.getOutstandingAmount().subtract(amount));
    visit.setPaymentStatus(
        visit.getOutstandingAmount().signum() == 0 ? "PAID_IN_FULL" : "PENDING_TOPUP");
    db.visits.updateById(visit);
    ledger(
        patient,
        "ARREARS_PAYMENT",
        amount.negate(),
        visit.getVisitNo(),
        "主动补缴，剩余 " + visit.getOutstandingAmount() + " 元");
    remember(input.requestId(), "SETTLE_ARREARS", fp, visitId);
    access.log("ARREARS_PAYMENT", visit.getVisitNo(), "补缴 " + amount + " 元");
    return visitView(visit);
  }

  public Map<String, Object> dashboard() {
    List<Visit> visits = visits(null, null);
    List<Patient> patients = db.patients.selectList(null);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("insuredCount", patients.size());
    result.put("visitCount", visits.size());
    result.put(
        "pendingCount", visits.stream().filter(v -> "PENDING".equals(v.getAuditStatus())).count());
    result.put(
        "reimbursedAmount",
        visits.stream()
            .filter(v -> "APPROVED".equals(v.getAuditStatus()))
            .map(Visit::getClaimAmount)
            .reduce(ZERO, BigDecimal::add));
    result.put(
        "todayVisits",
        visits.stream()
            .filter(v -> v.getCreatedAt().toLocalDate().equals(LocalDate.now()))
            .count());
    result.put(
        "totalBalance", patients.stream().map(Patient::getBalance).reduce(ZERO, BigDecimal::add));
    List<Map<String, Object>> months = new ArrayList<>();
    for (int i = 5; i >= 0; i--) {
      YearMonth month = YearMonth.now().minusMonths(i);
      List<Visit> inMonth =
          visits.stream().filter(v -> YearMonth.from(v.getCreatedAt()).equals(month)).toList();
      months.add(
          Map.of(
              "month",
              month.toString(),
              "visits",
              inMonth.size(),
              "amount",
              inMonth.stream().map(Visit::getTotalAmount).reduce(ZERO, BigDecimal::add)));
    }
    result.put("monthly", months);
    result.put(
        "statusCounts",
        List.of(
            Map.of(
                "name",
                "待审核",
                "value",
                visits.stream().filter(v -> "PENDING".equals(v.getAuditStatus())).count()),
            Map.of(
                "name",
                "已通过",
                "value",
                visits.stream().filter(v -> "APPROVED".equals(v.getAuditStatus())).count()),
            Map.of(
                "name",
                "已拒绝",
                "value",
                visits.stream().filter(v -> "REJECTED".equals(v.getAuditStatus())).count())));
    result.put("recentVisits", visits.stream().limit(6).map(this::visitView).toList());
    return result;
  }
}
