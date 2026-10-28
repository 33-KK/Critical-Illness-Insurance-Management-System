package com.example.insurance;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.insurance.Domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BusinessController {
  private final Store db;
  private final Access access;
  private final BusinessService service;

  @GetMapping("/dashboard")
  @Operation(summary = "获取角色工作台", tags = "工作台")
  public Api<?> dashboard() {
    access.require("dashboard:view");
    return Api.ok(service.dashboard());
  }

  @GetMapping("/visits")
  @Operation(summary = "查询就诊记录", tags = "就诊登记", description = "医生只可查询自己登记的记录；支持审核或支付状态筛选。")
  public Api<?> visits(
      @RequestParam(required = false) String keyword,
      @Parameter(
              description = "审核状态或支付状态",
              schema =
                  @Schema(
                      allowableValues = {
                        "PENDING",
                        "APPROVED",
                        "REJECTED",
                        "SELF_PAID",
                        "SETTLED",
                        "PENDING_TOPUP",
                        "PAID_IN_FULL"
                      }))
          @RequestParam(required = false)
          String status) {
    access.require("visit:view");
    return Api.ok(service.visits(keyword, status).stream().map(service::visitView).toList());
  }

  @GetMapping("/visits/{id}")
  @Operation(summary = "查询就诊详情", tags = "就诊登记", description = "包含费用与费率快照、手术明细和审核轨迹；医生只可查看本人登记的记录。")
  public Api<?> visit(@PathVariable Long id) {
    access.require("visit:view");
    return Api.ok(service.visitView(Access.found(db.visits.selectById(id), "就诊记录")));
  }

  @PostMapping("/visits/quote")
  @Operation(
      summary = "试算就诊费用",
      tags = "就诊登记",
      description = "使用当前保险费率和手术价格计算总费用、自付金额和拟报销金额；不扣款、不写入就诊记录，requestId 可省略。")
  public Api<?> quote(@Valid @RequestBody Requests.VisitInput input) {
    access.require("visit:create");
    return Api.ok(service.quote(input));
  }

  @PostMapping("/visits")
  @Operation(
      summary = "登记就诊并扣除自付金额",
      tags = "就诊登记",
      description =
          "需要 UUID requestId。事务内重新核算、保存价格与费率快照并扣款；余额不足或存在欠款时返回 409。成功后"
              + " auditStatus=PENDING、paymentStatus=SELF_PAID。")
  public Api<?> create(@Valid @RequestBody Requests.VisitInput input) {
    access.require("visit:create");
    return Api.ok(service.createVisit(input));
  }

  @PostMapping("/visits/{id}/review")
  @Operation(
      summary = "审核保险报销",
      tags = "保险审核",
      description =
          "APPROVE 记录模拟医院结算（不改变卡余额）；REJECT 是最终拒绝，必须填写原因，补扣原拟报销金额，余额不足部分记为欠款。仅 PENDING 可审核；原"
              + " requestId 重试不会重复扣款。")
  public Api<?> review(@PathVariable Long id, @Valid @RequestBody Requests.Review input) {
    access.require("audit:review");
    return Api.ok(service.review(id, input));
  }

  @PostMapping("/visits/{id}/settle-arrears")
  @Operation(
      summary = "使用医保卡余额补缴欠款",
      tags = "医保卡账务",
      description =
          "仅管理员可执行。按余额与欠款的较小值扣款，允许部分补缴；全部结清后 paymentStatus=PAID_IN_FULL，仍有欠款则保持 PENDING_TOPUP。")
  public Api<?> settle(@PathVariable Long id, @Valid @RequestBody Requests.Arrears input) {
    access.requireRole("ADMIN");
    access.require("card:recharge");
    return Api.ok(service.settle(id, input));
  }

  @PostMapping("/patients/{id}/recharges")
  @Operation(
      summary = "医保卡模拟充值",
      tags = "医保卡账务",
      description = "单次充值大于 0 且不超过 50000 元，最多两位小数；需要 UUID requestId。充值只增加余额，欠款由补缴接口结清。")
  public Api<?> recharge(@PathVariable Long id, @Valid @RequestBody Requests.Recharge input) {
    access.require("card:recharge");
    return Api.ok(service.recharge(id, input));
  }

  @GetMapping("/patients/{id}/transactions")
  @Operation(
      summary = "查询参保人资金流水",
      tags = "医保卡账务",
      description = "amount 正值为充值、负值为扣款；模拟医院结算流水为 0，不改变医保卡余额。")
  public Api<?> ledger(@PathVariable Long id) {
    access.require("card:ledger");
    Access.found(db.patients.selectById(id), "参保人");
    return Api.ok(
        db.transactions.selectList(
            new QueryWrapper<CardTransaction>().eq("patient_id", id).orderByDesc("id")));
  }

  @GetMapping("/transactions")
  @Operation(summary = "查询资金流水", tags = "医保卡账务")
  public Api<?> ledger(
      @RequestParam(required = false) Long patientId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String type) {
    access.require("card:ledger");
    QueryWrapper<CardTransaction> q = new QueryWrapper<>();
    if (patientId != null) q.eq("patient_id", patientId);
    if (type != null && !type.isBlank()) q.eq("type", type);
    if (keyword != null && !keyword.isBlank())
      q.and(
          w ->
              w.like("patient_name", keyword)
                  .or()
                  .like("card_no", keyword)
                  .or()
                  .like("business_no", keyword));
    return Api.ok(db.transactions.selectList(q.orderByDesc("id")));
  }
}
