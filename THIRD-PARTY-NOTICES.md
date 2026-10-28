# 本轮开源复用与第三方声明

更新：2026-10-18。本文件记录 GitHub 调研后新增的直接依赖及相关图表运行时，说明来源和实际复用范围。它不是完整依赖清单，也不为本项目自有代码指定许可证。

| 软件 | 锁定版本 | 许可 | 使用方式与来源 |
|---|---|---|---|
| Vue-ECharts | 8.3.2 | MIT | 新增前端运行时依赖，使用发布包中的组件；[上游](https://github.com/ecomfe/vue-echarts) / [原始许可](docs/licenses/vue-echarts-MIT.txt) |
| springdoc-openapi | 2.8.17 | Apache-2.0 | 新增后端依赖，生成 OpenAPI 和 Swagger UI；[上游](https://github.com/springdoc/springdoc-openapi) / [v2.8.17 原始许可](docs/licenses/springdoc-Apache-2.0.txt) |
| Vitest | 4.1.12 | MIT 及其附带第三方许可 | 新增开发依赖，仅用于图表回归测试；[上游](https://github.com/vitest-dev/vitest) / [发布包完整许可](docs/licenses/vitest-LICENSE.md) |
| Apache ECharts | 6.1.0 | Apache-2.0 及附带声明 | 原有图表依赖，本轮改为按需导入；[上游](https://github.com/apache/echarts) / [LICENSE](docs/licenses/echarts-LICENSE.txt) / [NOTICE](docs/licenses/echarts-NOTICE.txt) / [附带 d3 许可](docs/licenses/echarts-d3-LICENSE.txt) |
| ZRender | 6.1.0 | BSD-3-Clause | ECharts 依赖；[上游](https://github.com/ecomfe/zrender) / [原始许可](docs/licenses/zrender-BSD-3-Clause.txt) |
| tslib | 2.3.0 | 以原始许可文本为准 | 图表运行时的辅助库；[上游](https://github.com/microsoft/tslib) / [原始许可](docs/licenses/tslib-LICENSE.txt) |

Vue-ECharts 的版权声明为 `Copyright (c) 2016-present GU Yiling & ECOMFE`。完整 MIT 许可已原样保存在上述文件，分发时保留。

Apache ECharts 的 NOTICE 原文为：

```text
Apache ECharts
Copyright 2017-2026 The Apache Software Foundation

This product includes software developed at
The Apache Software Foundation (https://www.apache.org/).
```

前端构建生成 `frontend/dist/third-party-licenses.md`，包含实际打包依赖的许可清单，并复制 `frontend/public/echarts-NOTICE.txt` 到产物根目录。分发构建产物时应一并保留这两个文件。后端依赖作为独立嵌套 JAR 保存在 Spring Boot 可执行包中；分发时应保留各上游附带的许可和声明。

本轮没有修改上游库源码，也没有把课程业务仓库、RuoYi、Vben、Soybean 模板代码或资源复制到本项目。候选比较和不采用整套源码的原因见 [GitHub 调研记录](docs/OPEN-SOURCE-REUSE.md)。

依赖的准确解析结果以 `frontend/package-lock.json` 和 `backend/pom.xml` 为准；以后升级版本时应同步核对和更新声明。
