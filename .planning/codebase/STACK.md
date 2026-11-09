# 技术栈地图

日期：2026-11-08；依据 [pom.xml](../../pom.xml)、本轮工具版本输出与源码。

- 单模块 Maven，group io.devicetruth、artifact devicetruth-core、版本仍 0.1.0；不能从版本号推定产品 V1.0 已完成。
- Java 21；Boot parent 3.5.16；AgentScope Java 2.0.4。
- 直接依赖：starter、starter-web（Servlet/MVC）、starter-actuator、Jackson databind/jsr310、AgentScope core/dashscope/openai、starter-test。
- 模型 SDK 内使用 Reactor 的 stream/collect/block；这不等于项目采用 WebFlux HTTP 栈。
- 测试：JUnit5、AssertJ、SpringBootTest、JDK 本机 HTTP 替身；无 Testcontainers/JDBC/PG/Flyway。
- 前端：static/index.html 内原生 HTML/CSS/JS，无 Vue/TypeScript、package.json、前端构建。
- 打包 resources 同时含 datasets 与 skills；Boot Maven mainClass 显式 CLI，Server 单独 Main。
- 无格式化、静态检查或覆盖率阈值插件；Surefire temp 指向 target。
- .mvn/maven.config 指定项目内 .m2/repository 与 settings；可离线复用当前缓存。

## 本机环境（本轮）

Maven 3.9.9；默认 Java 17.0.16，切换 .tools/jdk-21.0.13.0.2+1 后 Java 21.0.13.0.2。Node 24.14.0、npm 11.9.0；Docker CLI 可发现，但 daemon/Compose/pgvector 镜像运行未验证。PowerShell 限制执行 .ps1，进程内直接设置 JAVA_HOME/PATH 可构建，不需要改全局策略。

新增数据库、PDF 解析、embedding、Vue 工具链均为计划，未安装/下载；具体兼容版本留开发阶段实证，不按“最新”盲升现有基线。

