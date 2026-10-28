package com.example.insurance;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.insurance.Domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DemoData implements ApplicationRunner {
  private final Store db;
  private final PasswordEncoder encoder;
  private final BusinessService business;

  @Value("${app.seed:true}")
  private boolean seed;

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    if (!seed || db.users.selectCount(null) > 0) return;
    try {
      Org platform = org("市医保服务中心", "SYSTEM", null, "周主任"),
          hospital = org("市第一人民医院", "HOSPITAL", platform.getId(), "陈院长"),
          insurer = org("惠民保险服务公司", "INSURER", platform.getId(), "杨经理");
      role(
          "ADMIN",
          "系统管理员",
          "dashboard:view,patient:view,card:recharge,card:ledger,plan:view,surgery:view,visit:view,system:user,system:role,system:org,system:dict,system:menu,system:log,file:manage");
      role(
          "DOCTOR",
          "临床医生",
          "dashboard:view,patient:view,plan:view,surgery:view,visit:view,visit:create,file:manage");
      role(
          "REVIEWER",
          "保险审核员",
          "dashboard:view,patient:view,patient:manage,plan:view,plan:manage,surgery:view,surgery:manage,visit:view,audit:review,card:ledger,file:manage");
      User admin = user("admin", "林管理员", "ADMIN", platform.getId()),
          doctor = user("doctor", "陈医生", "DOCTOR", hospital.getId()),
          reviewer = user("reviewer", "杨审核员", "REVIEWER", insurer.getId());
      Plan plan1 = plan("URBAN", "城镇居民大病保障", "0.80", "0.70", "0.60", "面向城镇居民的分级门诊保障方案"),
          plan2 = plan("RURAL", "城乡普惠大病保障", "0.75", "0.65", "0.55", "城乡居民普惠方案"),
          plan3 = plan("PLUS", "惠民补充大病保障", "0.90", "0.85", "0.75", "模拟补充保障方案");
      List<Plan> plans = List.of(plan1, plan2, plan3);
      List<Surgery> surgeries = new ArrayList<>();
      String[][] surgeryData = {
        {"S001", "甲状腺部分切除术", "普外科", "1500"},
        {"S002", "腹腔镜胆囊切除术", "普外科", "2800"},
        {"S003", "白内障超声乳化术", "眼科", "2200"},
        {"S004", "冠状动脉造影术", "心血管科", "3500"},
        {"S005", "关节镜探查术", "骨科", "1800"},
        {"S006", "内镜下息肉切除术", "消化科", "1200"},
        {"S007", "肿瘤组织活检术", "肿瘤科", "980"},
        {"S008", "经皮肾镜取石术", "泌尿科", "3200"},
        {"S009", "皮肤肿物切除术", "皮肤科", "680"},
        {"S010", "血管介入治疗术", "介入科", "4600"}
      };
      for (String[] row : surgeryData) {
        Surgery s = new Surgery();
        s.setCode(row[0]);
        s.setName(row[1]);
        s.setCategory(row[2]);
        s.setPrice(new BigDecimal(row[3]).setScale(2));
        s.setDescription("演示价格，以业务配置为准");
        s.setEnabled(true);
        db.surgeries.insert(s);
        surgeries.add(s);
      }
      String[] names = {
        "张建国", "李晓梅", "王志远", "刘芳", "陈明", "杨秀兰", "赵海峰", "黄丽", "周德华", "吴静", "徐强", "孙悦", "马文华", "朱琳",
        "胡志明", "郭春燕"
      };
      List<Patient> patients = new ArrayList<>();
      as(admin);
      for (int i = 0; i < names.length; i++) {
        Patient p = new Patient();
        p.setName(names[i]);
        p.setGender(i % 2 == 0 ? "男" : "女");
        p.setBirthDate(LocalDate.of(1955 + i * 2, 1 + i % 12, 5 + i % 20));
        p.setPhone("1380000" + String.format("%04d", i + 1));
        p.setIdNumber(
            "330100"
                + (1955 + i * 2)
                + String.format("%02d", 1 + i % 12)
                + String.format("%02d", 5 + i % 20)
                + String.format("%04d", i + 10));
        p.setCardNo("YB2026" + String.format("%06d", i + 1));
        p.setAddress("杭州市拱墅区惠民街 " + (i + 12) + " 号");
        p.setPlanId(plans.get(i % 3).getId());
        p.setBalance(BusinessService.ZERO);
        p.setEnabled(true);
        db.patients.insert(p);
        patients.add(p);
        business.recharge(
            p.getId(),
            new Requests.Recharge(
                new BigDecimal(i == 15 ? "500" : "12000"), UUID.randomUUID().toString(), "演示初始充值"));
      }
      for (int i = 0; i < 24; i++) {
        Patient p = patients.get(i % 14);
        Surgery surgery = surgeries.get(i % surgeries.size());
        as(doctor);
        var input =
            new Requests.VisitInput(
                p.getId(),
                List.of("A", "B", "C").get(i % 3),
                new BigDecimal("180.00"),
                surgery.getName() + "术前诊断及治疗",
                List.of(new Requests.Item(surgery.getId(), 1)),
                UUID.randomUUID().toString());
        Long id = ((Number) business.createVisit(input).get("id")).longValue();
        if (i % 4 != 0) {
          as(reviewer);
          business.review(
              id,
              new Requests.Review(
                  i % 4 == 3 ? "REJECT" : "APPROVE",
                  i % 4 == 3 ? "提交材料与保障范围不符，最终拒绝报销" : "材料齐全，费用核实通过",
                  UUID.randomUUID().toString()));
        }
        Visit v = db.visits.selectById(id);
        LocalDateTime time =
            LocalDate.now()
                .minusMonths(5 - i / 4)
                .withDayOfMonth(Math.min(3 + i % 4 * 5, LocalDate.now().lengthOfMonth()))
                .atTime(9 + i % 8, 15);
        if (time.isAfter(LocalDateTime.now())) time = LocalDateTime.now().minusHours(i + 1);
        v.setCreatedAt(time);
        if (v.getReviewedAt() != null) v.setReviewedAt(time.plusHours(1));
        db.visits.updateById(v);
        for (CardTransaction t :
            db.transactions.selectList(
                new QueryWrapper<CardTransaction>().eq("business_no", v.getVisitNo()))) {
          t.setCreatedAt(time);
          db.transactions.updateById(t);
        }
        for (AuditRecord a :
            db.audits.selectList(new QueryWrapper<AuditRecord>().eq("visit_id", id))) {
          a.setCreatedAt(time.plusHours(1));
          db.audits.updateById(a);
        }
      }
      // 80% coverage: 500 initial balance - 360 self-pay leaves 140 for partial rejection
      // deduction.
      Patient low = patients.get(15);
      low.setPlanId(plan1.getId());
      low.setBalance(db.patients.selectById(low.getId()).getBalance());
      db.patients.updateById(low);
      as(doctor);
      Long arrearsId =
          ((Number)
                  business
                      .createVisit(
                          new Requests.VisitInput(
                              low.getId(),
                              "A",
                              new BigDecimal("300"),
                              "甲状腺手术材料不完整演示",
                              List.of(new Requests.Item(surgeries.get(0).getId(), 1)),
                              UUID.randomUUID().toString()))
                      .get("id"))
              .longValue();
      as(reviewer);
      business.review(
          arrearsId,
          new Requests.Review("REJECT", "诊疗项目不在本次保障范围，拒绝后需补缴差额", UUID.randomUUID().toString()));
      menus();
      dicts();
    } finally {
      SecurityContextHolder.clearContext();
    }
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

  private Org org(String name, String type, Long parent, String contact) {
    Org o = new Org();
    o.setName(name);
    o.setType(type);
    o.setParentId(parent);
    o.setContactPerson(contact);
    o.setPhone("0571-88880001");
    o.setEnabled(true);
    db.orgs.insert(o);
    return o;
  }

  private void role(String code, String name, String permissions) {
    Role r = new Role();
    r.setCode(code);
    r.setName(name);
    r.setPermissions(permissions);
    db.roles.insert(r);
  }

  private User user(String username, String display, String role, Long org) {
    User u = new User();
    u.setUsername(username);
    u.setDisplayName(display);
    u.setRole(role);
    u.setOrgId(org);
    u.setPasswordHash(encoder.encode("Demo@2026"));
    u.setEnabled(true);
    u.setPhone("13800138000");
    u.setEmail(username + "@example.test");
    db.users.insert(u);
    return u;
  }

  private Plan plan(String code, String name, String a, String b, String c, String desc) {
    Plan p = new Plan();
    p.setCode(code);
    p.setName(name);
    p.setRateA(new BigDecimal(a));
    p.setRateB(new BigDecimal(b));
    p.setRateC(new BigDecimal(c));
    p.setDescription(desc);
    p.setEnabled(true);
    db.plans.insert(p);
    return p;
  }

  private void menus() {
    String[][] data = {
      {"工作台", "/dashboard", "DataAnalysis", "dashboard:view"},
      {"参保档案", "/patients", "User", "patient:view"},
      {"保险方案", "/plans", "DocumentChecked", "plan:view"},
      {"手术档案", "/surgeries", "FirstAidKit", "surgery:view"},
      {"就诊登记", "/visits", "Tickets", "visit:view"},
      {"报销审核", "/audits", "CircleCheck", "audit:review"},
      {"资金流水", "/transactions", "Wallet", "card:ledger"},
      {"文件管理", "/files", "Folder", "file:manage"},
      {"用户管理", "/admin/users", "UserFilled", "system:user"},
      {"角色权限", "/admin/roles", "Lock", "system:role"},
      {"组织管理", "/admin/orgs", "OfficeBuilding", "system:org"},
      {"数据字典", "/admin/dicts", "Collection", "system:dict"},
      {"菜单管理", "/admin/menus", "Menu", "system:menu"},
      {"系统日志", "/admin/logs", "List", "system:log"}
    };
    int i = 0;
    for (String[] row : data) {
      Menu m = new Menu();
      m.setName(row[0]);
      m.setPath(row[1]);
      m.setIcon(row[2]);
      m.setPermission(row[3]);
      m.setSortOrder(++i);
      m.setEnabled(true);
      db.menus.insert(m);
    }
  }

  private void dicts() {
    String[][] data = {
      {"OUTPATIENT_TYPE", "A", "A 类门诊"},
      {"OUTPATIENT_TYPE", "B", "B 类门诊"},
      {"OUTPATIENT_TYPE", "C", "C 类门诊"},
      {"GENDER", "男", "男"},
      {"GENDER", "女", "女"},
      {"AUDIT_STATUS", "PENDING", "待审核"},
      {"AUDIT_STATUS", "APPROVED", "已通过"},
      {"AUDIT_STATUS", "REJECTED", "已拒绝"},
      {"PAYMENT_STATUS", "PENDING_TOPUP", "待补缴"}
    };
    int i = 0;
    for (String[] row : data) {
      Dict d = new Dict();
      d.setType(row[0]);
      d.setCode(row[1]);
      d.setLabel(row[2]);
      d.setSortOrder(++i);
      d.setEnabled(true);
      db.dicts.insert(d);
    }
  }
}
