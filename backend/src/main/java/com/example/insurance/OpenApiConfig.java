package com.example.insurance;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API metadata for the existing opaque-token authentication and simulated insurance workflow. */
@Configuration
public class OpenApiConfig {
  public static final String BEARER_AUTH = "bearerAuth";

  @Bean
  OpenAPI insuranceOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("大病保险管理系统 API")
                .version("1.0.0")
                .description(
                    "用于本地学习和演示的保险报销模拟系统。先获取验证码并登录，再在 Authorize 中填入登录返回的 token。"
                        + "该 token 是随机生成的不透明会话令牌（opaque token），不是 JWT；数据库只保存其 SHA-256 摘要。\n\n"
                        + "金额单位为人民币元，金额使用 BigDecimal，业务金额保留两位小数；报销费率使用 0~1 的小数。"
                        + "前端试算仅供预览，最终金额由服务端依据当前价格和费率计算，并保存历史快照。\n\n"
                        + "充值、就诊提交、审核和补缴使用 requestId 做幂等校验：一次业务操作生成一个唯一标识，"
                        + "网络失败重试必须复用原标识和原参数；同一标识绑定的用户、操作或参数不同会返回 409。"
                        + "就诊试算不落库，无需 requestId；正式提交必须填写。\n\n"
                        + "审核状态：PENDING 待审核、APPROVED 审核通过、REJECTED 最终拒绝报销。"
                        + "支付状态：SELF_PAID 已扣自付部分、SETTLED 审核通过并记录模拟结算、"
                        + "PENDING_TOPUP 拒绝报销后余额不足待补缴、PAID_IN_FULL 拒绝报销后已全额支付。"
                        + "拒绝报销时系统补扣原拟报销金额，余额不足部分记为欠款；充值后由管理员执行补缴。\n\n"
                        + "接口使用统一 code/message/data 响应；权限由后端再次验证。"
                        + "可通过 API_DOCS_ENABLED=false 同时关闭 OpenAPI 和 Swagger UI。"))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("opaque")
                        .description(
                            "登录返回的随机会话 token，不是 JWT。在 Authorize 中只填 token，系统自动加 Bearer 前缀。")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
        .tags(
            List.of(
                tag("身份认证", "验证码、登录和会话管理；只有验证码和登录允许匿名访问。"),
                tag("个人中心", "当前登录用户的资料与密码。"),
                tag("工作台", "根据当前用户角色和数据范围展示业务概况。"),
                tag("参保管理", "参保档案与保险方案关联。"),
                tag("保险方案", "保险费率配置；历史就诊使用价格与费率快照。"),
                tag("手术档案", "手术目录和价格管理。"),
                tag("就诊登记", "医生试算、登记及查询；医生仅可查询自己登记的就诊。"),
                tag("保险审核", "审核员执行最终报销决定，提交后不能重复审核。"),
                tag("医保卡账务", "管理员模拟充值、欠款补缴和资金流水查询。"),
                tag("用户管理", "用户与密码管理。"),
                tag("角色权限", "角色权限配置。"),
                tag("组织管理", "组织维护。"),
                tag("数据字典", "基础枚举配置。"),
                tag("菜单管理", "菜单配置及当前用户可见菜单。"),
                tag("系统日志", "查询业务操作审计日志。"),
                tag("文件管理", "文件上传和下载；普通用户仅可操作自己上传的文件。")));
  }

  private Tag tag(String name, String description) {
    return new Tag().name(name).description(description);
  }
}
