# 接口约定与第一版业务口径

这是简历展示与学习项目，模拟医保业务，不连接真实医保平台、支付或短信。

## 基础约定

- API 前缀 `/api`；JSON 返回 `{code:"OK",message:"",data:...}`，错误用恰当 HTTP 状态与 `{code,message,data:null}`。
- 登录后通过 `Authorization: Bearer <token>` 访问；上传用 multipart。金额为元，两位小数，最终计算由服务端完成。
- 所有列表返回数组；支持 keyword、status 等筛选；第一版数据量小，界面分页。
- 角色代码 ADMIN、DOCTOR、REVIEWER。医生查看自己的就诊记录，审核员负责全系统业务审核，管理员维护基础信息与账户。
- 费用 = 独立诊断费 + sum(手术单价 × 数量)。报销额 = 总费用 × 保险对应 A/B/C 门诊报销率，HALF_UP 保留两位；自付 = 总费用 - 报销额。
- 提交就诊：保存价格/费率快照、扣自付、生成 PENDING 审核单，同一事务完成。
- 审核 APPROVE：记录医院结算，不再扣卡；REJECT：补扣拟报销额，余额不够时只扣可用部分，差额形成待补缴。退回在第一版明确表示最终拒绝报销。
- 充值为单次 0 < amount <= 50000；充值后可以主动结清待补缴，未结清前不允许新就诊。余额不允许为负。
- requestId 为前端生成的 UUID。充值、就诊提交、审核、补缴必须防重复；并发请求不得导致重复记账或负余额。
- 被引用的保险、手术和参保人员不得物理删除；停用不影响历史单据。

## 身份认证

- GET `/auth/captcha` → `{captchaId,svg,expiresIn}`，svg 可直接作为图片数据源；一次性验证码。
- POST `/auth/login` `{username,password,captchaId,captchaCode}` → `{token,user}`。
- GET `/auth/me` → User；POST `/auth/logout`。
- PUT `/auth/profile` `{displayName,phone,email}`；PUT `/auth/password` `{oldPassword,newPassword}`。
- User: `{id,username,displayName,role,orgId,orgName,phone,email,enabled,permissions:string[]}`。
- 演示账号：admin / doctor / reviewer；初始密码均为 `Demo@2026`，仅本地演示。

## 首页

- GET `/dashboard` → `{insuredCount,visitCount,pendingCount,reimbursedAmount,todayVisits,totalBalance,monthly:[{month,visits,amount}],statusCounts:[{name,value}],recentVisits:Visit[]}`。

## 参保人与医保卡

- GET/POST `/patients`，GET/PUT/DELETE `/patients/{id}`。
- Patient: `{id,name,gender,birthDate,phone,idNumber,cardNo,address,planId,planName,balance,enabled,outstandingAmount}`。
- 新增/修改字段与 Patient 相同，余额仅能通过充值和业务扣款修改。
- POST `/patients/{id}/recharges` `{amount,requestId,note}` → Transaction。
- GET `/patients/{id}/transactions` → Transaction[]。
- GET `/transactions?patientId=&keyword=&type=` → Transaction[]。
- Transaction: `{id,patientId,patientName,cardNo,type,amount,balanceAfter,businessNo,note,operatorName,createdAt}`；amount 是有符号数。

## 医疗保险与手术

- GET/POST `/plans`，PUT/DELETE `/plans/{id}`。
- Plan: `{id,code,name,rateA,rateB,rateC,description,enabled}`；rate 0~1。
- GET/POST `/surgeries`，PUT/DELETE `/surgeries/{id}`。
- Surgery: `{id,code,name,category,price,description,enabled}`。

## 就诊与审核

- GET `/visits?keyword=&status=`；GET `/visits/{id}`。
- POST `/visits/quote`，POST `/visits`：`{patientId,outpatientType:"A"|"B"|"C",diagnosisFee,diagnosis,items:[{surgeryId,quantity}],requestId}`，quote 不要求 requestId。
- Visit: `{id,visitNo,patientId,patientName,cardNo,doctorId,doctorName,planName,outpatientType,diagnosis,diagnosisFee,surgeryFee,totalAmount,reimbursementRate,selfPayAmount,claimAmount,auditStatus:"PENDING"|"APPROVED"|"REJECTED",paymentStatus:"SELF_PAID"|"SETTLED"|"PAID_IN_FULL"|"PENDING_TOPUP",outstandingAmount,rejectReason,createdAt,reviewedAt,items:[{surgeryId,surgeryName,unitPrice,quantity,subtotal}],auditRecords:[{decision,reason,reviewerName,createdAt}]}`。
- quote 返回 Visit 的金额相关字段及 items；未保存不带 id 等。
- POST `/visits/{id}/review` `{decision:"APPROVE"|"REJECT",reason,requestId}` → Visit。
- POST `/visits/{id}/settle-arrears` `{requestId}` → Visit。管理员执行，允许按当前余额部分补缴。

## 基础模块

- GET/POST `/admin/users`，PUT `/admin/users/{id}`，PUT `/admin/users/{id}/password` `{password}`。
- 用户写入：`{username,displayName,password,role,orgId,phone,email,enabled}`，修改时 password 可省略。
- GET `/admin/roles`，PUT `/admin/roles/{id}` `{name,permissions:string[]}`；Role: `{id,code,name,permissions}`。
- GET `/admin/permissions` → `[{code,name,group}]`。
- GET/POST `/admin/orgs`，PUT/DELETE `/admin/orgs/{id}`；Org: `{id,name,type:"SYSTEM"|"HOSPITAL"|"INSURER",parentId,contactPerson,phone,enabled}`。
- GET/POST `/admin/dicts`，PUT/DELETE `/admin/dicts/{id}`；Dict: `{id,type,code,label,sortOrder,enabled}`。
- GET `/admin/logs?keyword=&type=` → `[{id,username,action,target,detail,ip,createdAt}]`。
- GET/POST `/admin/menus`，PUT/DELETE `/admin/menus/{id}`；Menu: `{id,name,path,icon,permission,parentId,sortOrder,enabled}`。
- GET `/menus` → 当前用户可见 Menu[]。
- GET `/files`，POST `/files` multipart 字段 file，GET `/files/{id}/download`，DELETE `/files/{id}`。
- File: `{id,originalName,size,contentType,uploaderName,createdAt}`。下载需要身份认证。文件只对上传者与管理员可见。

## 权限代码

`dashboard:view`、`patient:view`、`patient:manage`、`card:recharge`、`card:ledger`、`plan:view`、`plan:manage`、`surgery:view`、`surgery:manage`、`visit:view`、`visit:create`、`audit:review`、`system:user`、`system:role`、`system:org`、`system:dict`、`system:menu`、`system:log`、`file:manage`。

ADMIN 初始含全部基础管理、参保查看、充值流水、就诊查看；DOCTOR 含首页、参保查看、保险/手术查看、本人就诊查看和创建、文件；REVIEWER 含首页、参保维护、保险手术维护、就诊查看、审核、流水、文件。所有角色均可访问个人中心。

## 工程结构

- `backend/` Spring Boot 3.5.x / Java 17 / MyBatis-Plus / Spring Security / MySQL；demo profile 使用 H2 便于无服务启动和测试；MySQL 配置为正式演示选项。
- `frontend/` Vue 3 + TypeScript + Vite + Element Plus + ECharts，开发代理 `/api` 到 localhost:8080。
- 根目录提供 Windows 启动脚本、Docker Compose MySQL/Redis、README 和设计说明。
- 前端、后端独立目录，避免彼此修改。每阶段说明模型、关键取舍和验证方法。
