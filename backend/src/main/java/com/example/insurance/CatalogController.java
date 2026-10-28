package com.example.insurance;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.insurance.Domain.*;
import io.swagger.v3.oas.annotations.Operation;
import java.math.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {
  private final Store db;
  private final Access access;
  private final BusinessService service;

  private static boolean present(String s) {
    return s != null && !s.isBlank();
  }

  @GetMapping("/patients")
  @Operation(summary = "查询参保档案", tags = "参保管理")
  public Api<?> patients(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status) {
    access.require("patient:view");
    QueryWrapper<Patient> q = new QueryWrapper<>();
    if (present(keyword))
      q.and(
          w ->
              w.like("name", keyword)
                  .or()
                  .like("card_no", keyword)
                  .or()
                  .like("id_number", keyword)
                  .or()
                  .like("phone", keyword));
    if (present(status))
      q.eq("enabled", "true".equalsIgnoreCase(status) || "ENABLED".equals(status));
    return Api.ok(
        db.patients.selectList(q.orderByDesc("id")).stream().map(service::patientView).toList());
  }

  @GetMapping("/patients/{id}")
  @Operation(summary = "查询参保人详情", tags = "参保管理")
  public Api<?> patient(@PathVariable Long id) {
    access.require("patient:view");
    return Api.ok(service.patientView(Access.found(db.patients.selectById(id), "参保人")));
  }

  @PostMapping("/patients")
  @Operation(summary = "创建参保档案", tags = "参保管理", description = "新账户余额固定为 0；余额通过充值和业务扣款接口维护。")
  @Transactional
  public Api<?> patientCreate(@RequestBody Patient input) {
    access.require("patient:manage");
    validatePatient(input);
    input.setId(null);
    input.setBalance(BusinessService.ZERO);
    db.patients.insert(input);
    access.log("PATIENT_CREATE", input.getCardNo(), "创建参保档案");
    return Api.ok(service.patientView(input));
  }

  @PutMapping("/patients/{id}")
  @Operation(summary = "更新参保档案", tags = "参保管理", description = "保留现有余额，不能通过档案接口修改余额。")
  @Transactional
  public Api<?> patientUpdate(@PathVariable Long id, @RequestBody Patient input) {
    access.require("patient:manage");
    Patient original = Access.found(db.patients.lockById(id), "参保人");
    validatePatient(input);
    input.setId(id);
    input.setBalance(original.getBalance());
    db.patients.updateById(input);
    access.log("PATIENT_UPDATE", input.getCardNo(), "更新参保档案，余额由业务接口维护");
    return Api.ok(service.patientView(input));
  }

  @DeleteMapping("/patients/{id}")
  @Operation(summary = "停用参保档案", tags = "参保管理", description = "保留历史就诊与资金流水。")
  @Transactional
  public Api<?> patientDelete(@PathVariable Long id) {
    access.require("patient:manage");
    Patient p = Access.found(db.patients.lockById(id), "参保人");
    p.setEnabled(false);
    db.patients.updateById(p);
    access.log("PATIENT_DISABLE", p.getCardNo(), "停用参保档案，保留历史记录");
    return Api.ok();
  }

  private void validatePatient(Patient p) {
    Access.text(p.getName(), "姓名", 100);
    Access.text(p.getGender(), "性别", 10);
    if (!Set.of("男", "女", "MALE", "FEMALE", "M", "F").contains(p.getGender()))
      throw Api.bad("性别不正确");
    Access.text(p.getIdNumber(), "证件号", 30);
    Access.text(p.getCardNo(), "医保卡号", 50);
    Access.optional(p.getPhone(), "手机号", 30);
    Access.optional(p.getAddress(), "地址", 300);
    if (p.getBirthDate() != null && p.getBirthDate().isAfter(java.time.LocalDate.now()))
      throw Api.bad("出生日期不能晚于今天");
    if (p.getPlanId() == null) throw Api.bad("请选择保险方案");
    Plan plan = Access.found(db.plans.selectById(p.getPlanId()), "保险方案");
    if (!Boolean.TRUE.equals(plan.getEnabled())) throw Api.bad("保险方案已停用");
    if (p.getEnabled() == null) p.setEnabled(true);
  }

  @GetMapping("/plans")
  @Operation(summary = "查询保险方案", tags = "保险方案")
  public Api<?> plans(@RequestParam(required = false) String keyword) {
    access.require("plan:view");
    QueryWrapper<Plan> q = new QueryWrapper<>();
    if (present(keyword)) q.and(w -> w.like("name", keyword).or().like("code", keyword));
    return Api.ok(db.plans.selectList(q.orderByAsc("id")));
  }

  @PostMapping("/plans")
  @Operation(
      summary = "创建保险方案",
      tags = "保险方案",
      description = "rateA、rateB、rateC 均为 0~1 的费率，最多四位小数。")
  @Transactional
  public Api<?> planCreate(@RequestBody Plan p) {
    access.require("plan:manage");
    validatePlan(p);
    p.setId(null);
    db.plans.insert(p);
    access.log("PLAN_CREATE", p.getCode(), "创建保险方案");
    return Api.ok(p);
  }

  @PutMapping("/plans/{id}")
  @Operation(summary = "更新保险方案", tags = "保险方案", description = "费率变更不影响已经登记的就诊，历史记录使用当时快照。")
  @Transactional
  public Api<?> planUpdate(@PathVariable Long id, @RequestBody Plan p) {
    access.require("plan:manage");
    Access.found(db.plans.selectById(id), "保险方案");
    validatePlan(p);
    p.setId(id);
    db.plans.updateById(p);
    access.log("PLAN_UPDATE", p.getCode(), "修改费率；历史就诊使用快照");
    return Api.ok(p);
  }

  @DeleteMapping("/plans/{id}")
  @Operation(summary = "停用保险方案", tags = "保险方案")
  @Transactional
  public Api<?> planDelete(@PathVariable Long id) {
    access.require("plan:manage");
    Plan p = Access.found(db.plans.selectById(id), "保险方案");
    p.setEnabled(false);
    db.plans.updateById(p);
    access.log("PLAN_DISABLE", p.getCode(), "停用保险方案");
    return Api.ok();
  }

  private void validatePlan(Plan p) {
    Access.text(p.getCode(), "保险编码", 30);
    Access.text(p.getName(), "保险名称", 100);
    Access.optional(p.getDescription(), "说明", 1000);
    rate(p.getRateA());
    rate(p.getRateB());
    rate(p.getRateC());
    if (p.getEnabled() == null) p.setEnabled(true);
  }

  private void rate(BigDecimal n) {
    if (n == null || n.signum() < 0 || n.compareTo(BigDecimal.ONE) > 0 || n.scale() > 4)
      throw Api.bad("报销费率必须为 0~1，最多四位小数");
  }

  @GetMapping("/surgeries")
  @Operation(summary = "查询手术档案", tags = "手术档案")
  public Api<?> surgeries(@RequestParam(required = false) String keyword) {
    access.require("surgery:view");
    QueryWrapper<Surgery> q = new QueryWrapper<>();
    if (present(keyword))
      q.and(w -> w.like("name", keyword).or().like("code", keyword).or().like("category", keyword));
    return Api.ok(db.surgeries.selectList(q.orderByAsc("id")));
  }

  @PostMapping("/surgeries")
  @Operation(summary = "创建手术档案", tags = "手术档案")
  @Transactional
  public Api<?> surgeryCreate(@RequestBody Surgery s) {
    access.require("surgery:manage");
    validateSurgery(s);
    s.setId(null);
    db.surgeries.insert(s);
    access.log("SURGERY_CREATE", s.getCode(), "创建手术档案");
    return Api.ok(s);
  }

  @PutMapping("/surgeries/{id}")
  @Operation(summary = "更新手术档案", tags = "手术档案", description = "价格变更不影响历史就诊的价格快照。")
  @Transactional
  public Api<?> surgeryUpdate(@PathVariable Long id, @RequestBody Surgery s) {
    access.require("surgery:manage");
    Access.found(db.surgeries.selectById(id), "手术");
    validateSurgery(s);
    s.setId(id);
    db.surgeries.updateById(s);
    access.log("SURGERY_UPDATE", s.getCode(), "修改手术；历史就诊使用价格快照");
    return Api.ok(s);
  }

  @DeleteMapping("/surgeries/{id}")
  @Operation(summary = "停用手术档案", tags = "手术档案")
  @Transactional
  public Api<?> surgeryDelete(@PathVariable Long id) {
    access.require("surgery:manage");
    Surgery s = Access.found(db.surgeries.selectById(id), "手术");
    s.setEnabled(false);
    db.surgeries.updateById(s);
    access.log("SURGERY_DISABLE", s.getCode(), "停用手术");
    return Api.ok();
  }

  private void validateSurgery(Surgery s) {
    Access.text(s.getCode(), "手术编码", 30);
    Access.text(s.getName(), "手术名称", 100);
    Access.optional(s.getCategory(), "分类", 100);
    Access.optional(s.getDescription(), "说明", 1000);
    if (s.getPrice() == null
        || s.getPrice().signum() < 0
        || s.getPrice().compareTo(new BigDecimal("99999999.99")) > 0
        || s.getPrice().scale() > 2) throw Api.bad("手术价格必须在 0~99999999.99，最多两位小数");
    if (s.getEnabled() == null) s.setEnabled(true);
  }
}
