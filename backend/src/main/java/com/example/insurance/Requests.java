package com.example.insurance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class Requests {
  private Requests() {}

  @Schema(description = "登录请求。先获取一次性验证码，再提交验证码标识和识别结果。")
  public record Login(
      @Schema(description = "登录账号", example = "doctor") @NotBlank @Size(max = 50) String username,
      @Schema(description = "登录密码", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
          @NotBlank
          @Size(max = 100)
          String password,
      @Schema(description = "验证码接口返回的标识") @NotBlank String captchaId,
      @Schema(description = "验证码图片中的四位数字") @NotBlank String captchaCode) {}

  public record Profile(
      @NotBlank @Size(max = 100) String displayName,
      @Size(max = 30) String phone,
      @Email @Size(max = 100) String email) {}

  public record Password(
      @Schema(description = "原密码", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
          @NotBlank
          String oldPassword,
      @Schema(
              description = "新密码，8~72 个字符",
              format = "password",
              accessMode = Schema.AccessMode.WRITE_ONLY)
          @NotBlank
          @Size(min = 8, max = 72)
          String newPassword) {}

  @Schema(description = "模拟充值请求；不接入真实支付。")
  public record Recharge(
      @Schema(description = "充值金额，单位元，0 < amount ≤ 50000，最多两位小数", example = "1000.00")
          @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @DecimalMax("50000")
          @Digits(integer = 5, fraction = 2)
          BigDecimal amount,
      @Schema(
              description = "一次操作一个 UUID；网络重试复用原值和原参数，避免重复充值",
              format = "uuid",
              example = "460f9402-fdfa-47a6-a3c5-f98e6df84a42")
          @NotBlank
          String requestId,
      @Size(max = 1000) String note) {}

  @Schema(description = "手术明细；单价由服务端从手术档案读取，客户端不能指定价格。")
  public record Item(
      @Schema(description = "手术档案 ID", example = "1") @NotNull Long surgeryId,
      @Schema(description = "数量，1~100", example = "1") @NotNull @Min(1) @Max(100)
          Integer quantity) {}

  @Schema(description = "就诊试算与登记请求。诊断费用不包含手术费用，总费用=诊断费用+各手术单价×数量；正式登记必须传 UUID requestId。")
  public record VisitInput(
      @Schema(description = "参保人 ID", example = "1") @NotNull Long patientId,
      @Schema(
              description = "门诊类别，对应保险方案的 rateA、rateB、rateC",
              allowableValues = {"A", "B", "C"},
              example = "A")
          @NotBlank
          @Pattern(regexp = "A|B|C")
          String outpatientType,
      @Schema(description = "诊断费用，单位元，最多两位小数，不包含手术费用", example = "100.00")
          @NotNull
          @DecimalMin("0")
          @DecimalMax("99999999")
          @Digits(integer = 8, fraction = 2)
          BigDecimal diagnosisFee,
      @Schema(description = "诊断说明", example = "术后复诊") @NotBlank @Size(max = 2000) String diagnosis,
      @Schema(description = "手术项目列表，最多 30 项，可为空数组") @NotNull @Size(max = 30)
          List<@NotNull @Valid Item> items,
      @Schema(
              description = "正式提交必须为 UUID；试算可省略。一次操作一个标识，网络重试复用原值和原参数",
              format = "uuid",
              example = "460f9402-fdfa-47a6-a3c5-f98e6df84a42")
          String requestId) {}

  @Schema(description = "最终审核决定。拒绝报销后补扣拟报销金额，余额不足形成欠款。")
  public record Review(
      @Schema(
              description = "APPROVE 通过；REJECT 最终拒绝",
              allowableValues = {"APPROVE", "REJECT"},
              example = "APPROVE")
          @NotBlank
          @Pattern(regexp = "APPROVE|REJECT")
          String decision,
      @Schema(description = "审核意见；decision=REJECT 时必须非空", example = "材料齐全") @Size(max = 1000)
          String reason,
      @Schema(
              description = "一次操作一个 UUID；重试复用原标识，避免重复审核或补扣",
              format = "uuid",
              example = "460f9402-fdfa-47a6-a3c5-f98e6df84a42")
          @NotBlank
          String requestId) {}

  @Schema(description = "补缴请求。实际扣款为医保卡余额和当前欠款的较小值，允许部分结清。")
  public record Arrears(
      @Schema(
              description = "一次操作一个 UUID；重试复用原标识，避免重复扣款",
              format = "uuid",
              example = "460f9402-fdfa-47a6-a3c5-f98e6df84a42")
          @NotBlank
          String requestId) {}

  public record UserWrite(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,50}") String username,
      @NotBlank @Size(max = 100) String displayName,
      @Schema(
              description = "创建时必填，更新时省略则保留原密码",
              format = "password",
              accessMode = Schema.AccessMode.WRITE_ONLY)
          @Size(min = 8, max = 72)
          String password,
      @NotBlank @Pattern(regexp = "ADMIN|DOCTOR|REVIEWER") String role,
      @NotNull Long orgId,
      @Size(max = 30) String phone,
      @Email @Size(max = 100) String email,
      @NotNull Boolean enabled) {}

  public record ResetPassword(
      @Schema(
              description = "新密码；重置后撤销该用户的全部会话",
              format = "password",
              accessMode = Schema.AccessMode.WRITE_ONLY)
          @NotBlank
          @Size(min = 8, max = 72)
          String password) {}

  public record RoleWrite(
      @NotBlank @Size(max = 100) String name, @NotNull List<String> permissions) {}
}
