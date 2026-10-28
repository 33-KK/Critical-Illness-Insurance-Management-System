# GitHub 项目调研与复用记录

调研日期：2026-10-18。目标是为当前 Spring Boot 3.5 / Java 17 / Vue 3 / TypeScript 工程选择可维护、可解释的复用内容，并保留已经验证的保险账务规则。

## 选择结论

| 项目与来源 | 核对结果 | 在本项目中的使用方式 |
|---|---|---|
| [yyzwz/medicine-system](https://github.com/yyzwz/medicine-system) | 与蓝桥课程对应的大病保险项目，Vue 2.6 / View UI / Boot 2.7，包含三角色和就诊报销流程；[LICENSE](https://github.com/yyzwz/medicine-system/blob/master/LICENSE) 为 GPL-3.0 | 对照业务流程，未复制源码、SQL、图片或页面；保留现有 Vue 3 与服务端账务实现 |
| [RuoYi-Vue3](https://github.com/yangzongzhuan/RuoYi-Vue3) | 官方 Vue 3 / Vite / Element Plus 前端；同仓含 `typescript` 分支；[MIT](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/LICENSE) | 参考管理系统的组织方式；未引入整套模板 |
| [RuoYi-Vue / springboot3](https://github.com/yangzongzhuan/RuoYi-Vue/tree/springboot3)、[RuoYi-Vue-Plus / 5.X](https://github.com/dromara/RuoYi-Vue-Plus/tree/5.X) | 两个仓库均有 MIT 许可；上述分支适合研究 Boot 3 管理系统，不应直接用已转向 Boot 4 的默认分支替换当前工程 | 参考基础模块和接口文档组织；未移入另一套认证、权限、数据源和业务基类 |
| [vue-vben-admin](https://github.com/vbenjs/vue-vben-admin) | 多包工作区和独立组件、偏好设置体系；[MIT](https://github.com/vbenjs/vue-vben-admin/blob/main/LICENSE) | 参考模块划分；整套接入会扩大改造范围，本轮未采用 |
| [soybean-admin](https://github.com/soybeanjs/soybean-admin) | Vue 3 / TypeScript 管理模板，另有 [Element Plus 变体](https://github.com/soybeanjs/soybean-admin-element-plus)；[MIT](https://github.com/soybeanjs/soybean-admin/blob/main/LICENSE) | 可供后续比较交互设计；本轮未复制模板代码 |
| [Vue-ECharts](https://github.com/ecomfe/vue-echarts) | Vue 的 ECharts 组件封装；8.3.2 对应 Vue 3.3+ / ECharts 6；[MIT](https://github.com/ecomfe/vue-echarts/blob/main/LICENSE) | 作为 npm 依赖实际接入，管理首页图表的更新、尺寸适配与销毁 |
| [springdoc-openapi](https://github.com/springdoc/springdoc-openapi) | 从 Spring MVC 和模型生成 OpenAPI，提供 Swagger UI；[Apache-2.0](https://github.com/springdoc/springdoc-openapi/blob/v2.8.17/LICENSE)；[兼容说明](https://springdoc.org/v2/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot) | 作为 Maven 依赖实际接入，生成可调试接口文档；使用与 Boot 3.5 对应的 2.8.17 |

许可证按本次读取到的文件记录。上表的框架与业务项目是调研参考，不代表代码已经合入；实际新增依赖和许可文本见根目录 `THIRD-PARTY-NOTICES.md`。

## 为什么保留当前业务实现

课程仓库可以帮助理解“就诊扣自付→提交审核→通过结算或拒赔补扣”的流程。它的技术栈早于当前工程，不能把 Vue 2 页面和 Boot 2 接口直接拼入 Vue 3 / Boot 3 工程。

本次检查了原仓库 `a1f9cde` 版本的就诊与拒赔控制器。[SeeDoctorController.insert](https://github.com/yyzwz/medicine-system/blob/a1f9cdeac5692a50244a622a3c454b24da15f369/back/src/main/java/cn/zwz/insurance/controller/SeeDoctorController.java#L74-L95) 将请求中的新余额写到账户，并使用请求中的总费用建立审核金额；[ExamineController.notPass](https://github.com/yyzwz/medicine-system/blob/a1f9cdeac5692a50244a622a3c454b24da15f369/back/src/main/java/cn/zwz/insurance/controller/ExamineController.java#L119-L141) 只检查单据存在便执行拒赔扣款，缺少待审核状态检查，且将金额转为 `double` 计算。这是静态代码路径判断，本轮未运行原仓库复现，也未审计整个项目。两个控制器有事务注解；事务本身不能替代核算与幂等规则。

当前工程保留：

- 服务端读取价格和费率并计算金额，提交后保存费用快照。
- 十进制金额、事务、账户行锁和非负余额约束。
- 请求标识与参数绑定、审核状态检查、防重复扣款。
- 拒赔后的部分扣款、待补缴与后续结清闭环。

原仓库的 GPL 文件和 README 中另行声明的转发/商用要求需要在分发衍生版本前核清。GPL 本身并不禁止商用。本次仅参考业务说明，没有复制该仓库的受许可代码。

同时检查的其他业务候选：

- [mlinyun/insurance](https://github.com/mlinyun/insurance)：同类业务，仍是 Vue 2 / Boot 2.7；本次读取默认分支未找到明确许可证，未复制。
- [SKRaua/practice2506](https://github.com/SKRaua/practice2506)：Vue 3 / Element Plus，业务偏药品报销，后端为 Boot 2.7 / Java 8；本次读取默认分支未找到明确许可证，未复制。
- [GroupFive-CQUPT/medical-insurance-system](https://github.com/GroupFive-CQUPT/medical-insurance-system)：Apache-2.0，但本次读取的默认分支主要是启动类、配置与测试，缺少可接入的业务系统。

结论限于本次检查的候选和分支，不代表已遍历 GitHub 的所有相关项目。上游会继续变化，依赖接入以本工程锁文件和 POM 为准。

## 本轮实际改进

### 图表组件与按需导入

此前首页从 `echarts` 完整入口导入，Vite 的 `manualChunks.charts = ['echarts']` 又强制将完整入口放入图表分包。只改页面 import，仍可能留下完整包。

现在采用 Vue-ECharts，通过 `echarts/core` 注册实际使用的柱图、折线图、饼图、坐标网格、提示、图例、中心文字和 Canvas 渲染器；分包按模块路径归类。图表选项从页面中提取为有类型的函数，页面提供响应式数据，组件负责更新和销毁。

按需导入依据 [ECharts 官方说明](https://echarts.apache.org/handbook/en/basics/import/)，组件接入依据 [Vue-ECharts 官方用法](https://github.com/ecomfe/vue-echarts#installation--usage)。这两者解决不同问题：体积优化主要来自按需导入；组件封装减少页面生命周期代码。

构建体积与测试结果记录在 [VERIFICATION.md](./VERIFICATION.md)。它是同一构建方式下的分包大小比较，不代表网络加载时间或服务端性能压测。

### 自动生成接口文档

接入 springdoc 后，后端提供 OpenAPI JSON 与 Swagger UI。文档描述三角色权限、服务端计费、金额快照、请求标识和审核/补缴规则，便于学习接口和面试演示。

文档使用 HTTP Bearer 描述当前随机会话令牌，不能将其讲成 JWT。验证码和登录公开，业务接口仍由原有 Spring Security 与权限检查保护。`API_DOCS_ENABLED=false` 可关闭文档 JSON 和 UI。

## 建议的学习与讲解方式

1. 先读课程仓库 README，解释一笔就诊的参与角色和资金去向。
2. 用本项目的 Swagger UI 对照 Controller、请求模型和服务层，观察请求怎样变成数据库变化。
3. 阅读图表模块，解释按需导入、组件生命周期与响应式更新；用构建产物验证体积变化。
4. 复现重复充值、重复审核与拒赔待补缴，用测试证据解释为什么需要这些规则。

简历应准确区分课程业务来源、开源依赖、参考模板和本项目实现。可以讲自己完成并理解的改造、设计与验证，避免将上游框架宣称为原创。
