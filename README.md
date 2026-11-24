# XXQ 后端 · 高校教务管理系统

XXQ（高校教务管理系统）的服务端：基于 Spring Boot 的单体应用，提供 REST API 与 WebSocket 能力，面向 **学生、教师、院系管理员、教务管理员** 四类角色，支撑选课排课、成绩考试、课程课业、毕业设计与实践教学等教务业务。

代码按**领域模块 + 分层**组织：每个业务模块独立成包，模块内遵循 `controller | dto | entity | mapper | service[/impl]` 结构；鉴权、限流、日志、文件等横切能力由 `common` / `config` / `util` 与独立模块提供。账号由院校统一创建或批量导入，系统不开放自助注册入口。

> 本文档介绍项目功能、技术栈、架构、运行与部署，**不包含接口契约**。
> 完整的接口说明见 [`README-API.md`](./README-API.md)。

## 功能模块

| 模块 | 职责 |
| --- | --- |
| `user` | 用户基表与四类子表；账号登录与微信 / QQ / 支付宝登录路由；注册 / 改密接口、头像（WebSocket 推送）；学生 / 教师批量导入与 Excel 解析 |
| `college` / `clazz` / `mojor` / `course` / `local` / `semester` / `time` / `teachinfo` | 学院、班级、专业、课程、教学楼与教室、学期、时间段与时段限制、教学信息与课表查询（`mojor` 为专业模块沿用的历史包名） |
| `selection` | 选课活动、选课班与分班、选课记录、选课组；Redis 原子计数防超卖 |
| `scheduling` | 基于 Timefold 的自动排课：异步求解、结果轮询、手动停止 |
| `score` / `exam` | 成绩录入与统计导出、学生成绩复核；考试安排、补考管理、学生考试查询 |
| `analysis` | 学业预警、教学评价（模板 / 指标）、教师教学质量与学情分析 |
| `coursework` | 课程课业：作业发布与批改、教学视频流式播放、课程资料、课程公告、平时分合成 |
| `practice` | 实践教学：毕业设计与论文全流程、实习与培训、竞赛、社会实践 |
| `notification` | 站内消息：单点消息 + 广播，注解化触发，事务提交后发送 |
| `file` | 通用文件传输：分片上传 / 断点续传 / 秒传、整传、下载与在线预览、过期文件回收 |
| `preference` | 用户个性化偏好（语言、主题等）的存取 |
| `frontlog` | 前端日志接收与落盘 |
| `common` / `config` / `util` | 统一响应、全局异常、AOP（访问日志 / 限流）、鉴权、JWT、加解密、Web 与 WebSocket 配置、学籍范围解析、分布式锁 |

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 25 |
| 框架 | Spring Boot 4.0.6（WebMVC、WebSocket、AOP、Scheduling） |
| 构建 | Maven 3.9.16（使用 Maven Wrapper，无需本机安装 Maven） |
| ORM | MyBatis Plus 3.5.17（`BaseMapper` / `IService` / `ServiceImpl`，分页插件） |
| 数据库 | MySQL（`mysql-connector-j`，utf8mb4） |
| 缓存 / 会话 | Redis（会话、限流计数、选课计数、上传会话、分布式锁） |
| 鉴权 | JJWT 0.13.0（access token + refresh token 双 Token） |
| 排课求解 | Timefold Solver 2.6.0 |
| 导出 | Apache POI 5.5.1（Excel 导入 / 导出）、OpenPDF 3.0.5（PDF 导出） |
| 其他 | Lombok、Spring AOT（构建期生成 Bean 元数据） |

## 环境要求

- **JDK 25**
- **MySQL**（建议 8.x，库 / 表使用 `utf8mb4`）
- **Redis**
- 无需安装 Maven：仓库自带 Wrapper（Windows 用 `mvnw.cmd`，Linux / macOS 用 `./mvnw`）
- 默认监听 **8080** 端口；需保证 `uploads/`、`logs/` 所在目录可写

## 快速开始

### 1. 初始化数据库

```sh
# 建库（utf8mb4）
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS xxq DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 导入全量建表脚本（合并版 DDL）
mysql --default-character-set=utf8mb4 -u root -p xxq < src/main/resources/sql/init-all-tables.sql
```

说明：

- `src/main/resources/sql/init-all-tables.sql` 是合并后的全量 DDL，只含 `CREATE TABLE`，不含种子数据。
- 如需默认的学业预警阈值与评教模板，另行执行 `init-analysis-tables.sql`、`init-evaluation-template-tables.sql` 中的 `INSERT` 种子数据段。
- 按仓库约定，`src/main/resources/sql/` **不纳入版本库**，建表 / 迁移脚本随部署环境分发；改动需手工同步到其他环境。
- 全库不在数据库层建外键与唯一约束，唯一性、归属与级联由应用层保证；逻辑删除字段为 `deleted`。

### 2. 配置本地环境

编辑 `src/main/resources/application-dev.yaml`，填写本地 MySQL 与 Redis 连接信息。`application.yaml` 中还包含 JWT 密钥与有效期、限流阈值、文件存储路径、Timefold 终止条件等公共配置。

生产配置 `application-prd.yaml` 已被 `.gitignore` 排除，需要在部署环境单独提供；不要把生产密钥与数据库口令提交到仓库。

### 3. 启动

```sh
# Linux / macOS
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run

# 指定 profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=prd
```

默认激活 `dev` profile，服务地址为 `http://localhost:8080`。前端的 `/api`、`/ws` 默认代理到这里。

> **AOT 提示**：打包产物中的 `XxqApplication` 会自动启用 AOT；本地热重启或新增 Bean 时，若遇到旧 Bean 定义未刷新，可用 `-Dspring.aot.enabled=false` 关闭 AOT 后再启动。

## 构建与测试命令

```sh
./mvnw clean package -DskipTests   # 构建，产物固定为 target/xxq.jar
./mvnw test                        # 运行全部测试
./mvnw test -Dtest=XxqApplicationTests  # 运行单个测试类
./mvnw spring-boot:run             # 开发模式启动
```

## 目录结构

```
src/main/java/com/xrq/xxq/
├── XxqApplication.java       # 启动入口（启用定时任务 + 自动检测 AOT）
├── common/                   # Result、BusinessException、GlobalExceptionHandler、AOP
│   └── aop/                  # 访问日志、限流切面
├── config/                   # JWT、鉴权拦截器、WebMvc、CORS、WebSocket 配置
├── util/                     # JWT、PBKDF2 加密、鉴权上下文、文件响应、学籍范围、分布式锁
└── module/                   # 业务模块（见下方模块列表）
    ├── user/ college/ clazz/ mojor/ course/ local/ semester/ time/ teachinfo/
    ├── selection/ scheduling/ score/ exam/ analysis/
    ├── coursework/ practice/ notification/ file/ preference/ frontlog/
    └── ...

src/main/resources/
├── application.yaml          # 公共配置（JWT、限流、存储、Timefold 等）
├── application-dev.yaml      # 本地开发配置
├── application-prd.yaml      # 生产配置（不纳入版本库）
├── logback-spring.xml        # 分级日志、按天滚动、慢请求日志
└── sql/                      # 建表 / 迁移脚本（不纳入版本库）
```

## 核心设计

### 鉴权与会话

- 登录成功后签发 **access token（默认 30 分钟）** 与 **refresh token（默认 7 天）**；会话写入 Redis（`session:{tokenId}`，TTL 7 天），登出即失效。
- `AuthInterceptor` 拦截 `/api/**`，校验 JWT 签名、有效期与 Redis 会话存活状态，并把当前用户上下文交给 `AuthFacade` 读取。
- 接口鉴权采用 **注解声明 + 拦截器统一执行**：`@RequireAuth` 为基元注解，日常使用 `@RequireLogin` / `@RequireStudent` / `@RequireTeacher` / `@RequireAcademicAdmin` / `@RequireDepartment` / `@RequireManagement` 组合注解；**未声明注解的端点默认拒绝（403）**。构建期 `RequireAuthCompletenessTest` 会遍历所有 `/api/**` 端点，漏标注会直接测试失败。
- 密码使用 PBKDF2 加盐哈希存储（`iterations:salt:hash`），兼容旧 MD5 密文；登录支持账号密码与微信 / QQ / 支付宝路由。
- 登录接口按「公网 IP + 前端上报的内网 IP」复合维度限流，连续失败会触发临时锁定；IP 头由反向代理写入（Nginx `X-Forwarded-For`），客户端自行伪造的头不可信。

### 统一响应与异常

- 所有 JSON 接口返回 `Result<T>`（`code` / `message` / `data`），成功为 200。
- 业务层抛 `BusinessException(code, msg)`，由 `GlobalExceptionHandler` 统一转换；参数错误转 400，未知异常转 500，受保护接口未登录 / 过期返回 401。

### 限流与访问日志

- `RateLimitAspect` 优先级最高，先于访问日志执行；`RateLimitService` 用 Redis Lua 实现固定窗口计数。
- 分档限流（可在 `application.yaml` 调整）：全局账号维度、选课接口宽容档、文件传输档、前端日志上报档、登录独立档；登录另有连续失败锁定。
- 访问日志记录请求方法 / 路径、账号、客户端 IP、参数摘要与耗时；密码类参数自动打码，文件上传只记文件名与大小，超长参数截断。耗时超过 `api-log.slow-threshold-ms`（默认 3 分钟）的成功请求额外写入慢日志文件。

### 自动排课

- 规划模型：`CourseSchedule`（方案）→ `Lesson`（规划实体，时间 / 教室为规划变量）→ `Timeslot` / `Room` / `StudentGroup`（问题事实）。
- 5 条硬约束：教室冲突、教师冲突、班级冲突（支持合班 / 重修的班级交集判断）、时段预留限制、教室容量上限；均要求同一学期且周次重叠。
- `solve(semesterId)` 异步启动求解并返回 `scheduleId`；`getSolution(id)` 轮询状态与结果；`stopSolving(id)` 手动终止。终止条件默认 5 分钟总时长 / 30 秒无改进。

### 数据归属与一致性

- 学籍归属为严格四层链：**学院 ← 专业 ← 班级 ← 学生**（`college ← major.college_id ← class_name.major_id ← student.class_id`）。院系可见性统一经 `StudentScopeResolver` 解析，链上任一跳缺失按不可见处理（fail-closed）。
- 容量类业务（选课、实习报名、竞赛 / 社会实践）使用条件更新或 Redis 原子计数防止超卖；高并发唯一性校验使用 Redis 分布式锁。
- 关联数据清理在应用层显式完成；删除教学任务、班级、专业前会校验下游引用，避免产生孤儿数据。

### 文件传输

- 统一入口 `module/file`：分片上传（SHA-256 完整性校验、断点续传、秒传）、整传、通用下载、在线预览。
- 上传会话与进度存 Redis（带 TTL），分片与成品存本地文件系统；成品采用**内容寻址**（路径即摘要），业务删除不即时删文件，由定时任务扫描无引用产物后回收。
- 业务目录、扩展名白名单与整传大小上限由 `FileBizEnum` 约束；单文件分片上传上限默认 2GB。

### 通知

- 业务服务不直接依赖通知模块，而是调用 `notification/notice/` 下场景类的空体方法；消息类型、接收者、标题与文案通过 `@NotifyUser` / `@NotifyBroadcast` 注解 + SpEL 声明。
- `NotifyAspect` 在事务提交后发送：提交成功才发、回滚不发、无事务立即发；发送失败仅告警，不影响业务返回。
- 单点消息按用户落库；广播消息只存一行，已读状态走回执表。

### 课程课业与实践教学

- 课业内容表统一挂到「学期 + 课程 / 活动 + 教师 + 班级」授课组的**最小 id 锚点行**，入参 `teachInfoId` 接受组内任意行并归一化。
- 教学视频通过 WebSocket 自定义二进制协议流式传输，服务端不解析视频，仅做分段读取、限速与并发控制；视频元数据由客户端解析后回传。
- 毕业设计覆盖活动配置、两级选题审批、师生匹配、开题 / 中期 / 指导记录、论文版本与查重、答辩与成绩合成；关键操作写 JSONL 操作日志，不落库。

### 日志

- 日志按级别分文件（`info` / `warn` / `error`）与慢请求文件，按天滚动，目录为 `logs/yyyy/MM/dd/`，可用 `logging.file.path` 覆盖根目录。
- 前端通过 `/api/front-logs` 上报 warn / error 日志与行为面包屑，该接口免登录但单独限流。

### 配置项概览

| 配置前缀 | 说明 |
| --- | --- |
| `spring.datasource.*` / `spring.data.redis.*` | MySQL 与 Redis 连接（分 profile） |
| `jwt.*` | 签名密钥、access / refresh 有效期 |
| `timefold.solver.termination.*` | 排课求解终止条件 |
| `rate-limit.*` | 全局限流、各业务档位、登录失败锁定 |
| `file.*` | 文件存储根目录、单文件上限、分片 / 上传会话 / 孤儿回收 / 导出暂存 |
| `practice.*` | 实践模块存储路径、单文件上限、毕业设计操作日志目录 |
| `avatar.storage-path` | 头像存储目录 |
| `coursework.video.*` | 视频流单次读取大小、在途窗口、限速、连接数、头尾缓存 |
| `api-log.slow-threshold-ms` | 慢请求阈值（默认 180000 ms） |
| `logging.file.path` | 日志根目录（默认 `./logs`） |

## 数据库

- 新库直接执行 `sql/init-all-tables.sql`（合并版 DDL）；按领域拆分的建表脚本位于同目录。
- 存量库升级按脚本注释中的顺序执行迁移脚本（如 `migration-class-major.sql`、`migration-schema-sync.sql`），均要求 `--default-character-set=utf8mb4`。
- 表使用 `utf8mb4`；主键以自增 `BIGINT` 为主；外键、唯一约束与级联删除由应用层负责；`deleted` 字段为逻辑删除标记。
- SQL 目录不纳入版本库，部署与升级时需要单独携带。

## 部署

```sh
./mvnw clean package -DskipTests
java -jar target/xxq.jar --spring.profiles.active=prd
```

部署要点：

- **profile**：生产环境使用 `prd`；`application-prd.yaml` 不随仓库分发，需在部署目录单独提供。
- **反向代理**：把 `/api` 与 `/ws` 转发到本服务；WebSocket 需透传 `Upgrade` / `Connection` 头；同时写入可信的 `X-Forwarded-For`，限流与访问日志依赖该头。
- **可写目录**：`uploads/`（头像、实践文件、通用文件）与 `logs/` 必须可写；如自定义路径，确认进程用户有权限。
- **AOT**：构建产物已包含 AOT 元数据，启动时自动启用；仅本地开发排障时用 `-Dspring.aot.enabled=false` 关闭。
- **时区 / 编码**：JDBC 连接串使用 `Asia/Shanghai` 与 UTF-8，部署时请保持 JVM 时区与之一致。
- **健康检查**：确认端口 8080（或自定义 `server.port`）可达，Redis 与 MySQL 连接正常。
- 前端站点、HTTPS、CSP、静态缓存与落地页分发的完整 Nginx 配置见前端仓库 `deploy/nginx.conf`。

## 测试

测试覆盖工具类、业务纯函数、文件上传 / 存储、通知切面、选课流程与构建期完整性守卫（鉴权注解、文件引用、通知场景等）：

```sh
./mvnw test
./mvnw test -Dtest=RequireAuthCompletenessTest
```

新增 `/api/**` 端点但未声明鉴权注解、或新增带文件字段的业务表但未登记文件引用时，对应完整性测试会失败。

## 相关文档

- 接口契约：[`README-API.md`](./README-API.md)
- 前端应用：配套 Vue 3 仓库（`xxq-frontend`）
- 问题反馈：gangziwo620@163.com
