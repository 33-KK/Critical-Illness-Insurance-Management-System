package com.example.insurance;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.*;
import lombok.Data;

/** Database records. API views are assembled separately to avoid exposing credentials. */
public final class Domain {
  private Domain() {}

  @Data
  @TableName("app_user")
  public static class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username, displayName, passwordHash, role, phone, email;
    private Long orgId;
    private Boolean enabled;
  }

  @Data
  @TableName("app_role")
  public static class Role {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String code, name, permissions;
  }

  @Data
  @TableName("app_org")
  public static class Org {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name, type;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactPerson, phone;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;

    private Boolean enabled;
  }

  @Data
  @TableName("app_dict")
  public static class Dict {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String type, code, label;
    private Integer sortOrder;
    private Boolean enabled;
  }

  @Data
  @TableName("app_menu")
  public static class Menu {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name, path;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String icon, permission;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;

    private Integer sortOrder;
    private Boolean enabled;
  }

  @Data
  @TableName("insurance_plan")
  public static class Plan {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String code, name;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;

    @Schema(description = "报销费率，0~1，最多四位小数", example = "0.8000", minimum = "0", maximum = "1")
    private BigDecimal rateA, rateB, rateC;
    private Boolean enabled;
  }

  @Data
  @TableName("surgery")
  public static class Surgery {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String code, name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String category, description;

    @Schema(
        description = "手术单价，单位元，最多两位小数",
        example = "2000.00",
        minimum = "0",
        maximum = "99999999.99")
    private BigDecimal price;

    private Boolean enabled;
  }

  @Data
  @TableName("patient")
  public static class Patient {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name, gender, idNumber, cardNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone, address;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate birthDate;

    private Long planId;

    @Schema(description = "医保卡余额，单位元；只能通过业务接口维护，档案写入时忽略", accessMode = Schema.AccessMode.READ_ONLY)
    private BigDecimal balance;

    private Boolean enabled;
  }

  @Data
  @TableName("medical_visit")
  public static class Visit {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String visitNo;
    private Long patientId, doctorId, planId;
    private String patientName, cardNo, doctorName, planName, outpatientType, diagnosis;
    private BigDecimal diagnosisFee,
        surgeryFee,
        totalAmount,
        reimbursementRate,
        selfPayAmount,
        claimAmount;
    private String auditStatus, paymentStatus, rejectReason;
    private BigDecimal outstandingAmount;
    private LocalDateTime createdAt, reviewedAt;
  }

  @Data
  @TableName("visit_item")
  public static class VisitItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long visitId, surgeryId;
    private String surgeryName;
    private BigDecimal unitPrice, subtotal;
    private Integer quantity;
  }

  @Data
  @TableName("audit_record")
  public static class AuditRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long visitId, reviewerId;
    private String decision, reason, reviewerName;
    private LocalDateTime createdAt;
  }

  @Data
  @TableName("card_transaction")
  public static class CardTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long patientId;
    private String patientName, cardNo, type;
    private BigDecimal amount, balanceAfter;
    private String businessNo, note, operatorName;
    private LocalDateTime createdAt;
  }

  @Data
  @TableName("request_record")
  public static class RequestRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId, action, fingerprint;
    private Long userId, businessId;
  }

  @Data
  @TableName("auth_session")
  public static class Session {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String tokenHash;
    private LocalDateTime expiresAt;
  }

  @Data
  @TableName("app_log")
  public static class Log {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username, action, target, detail, ip;
    private LocalDateTime createdAt;
  }

  @Data
  @TableName("app_file")
  public static class StoredFile {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uploaderId;
    private String originalName, storageName, contentType, uploaderName;
    private Long size;
    private LocalDateTime createdAt;
  }
}
