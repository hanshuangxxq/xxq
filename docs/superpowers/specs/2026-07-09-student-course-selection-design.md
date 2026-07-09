# 学生选课功能设计文档

- **日期**: 2026-07-09
- **范围**: 新增 `module/selection` 模块，实现独立选课活动管理、学生选课/退选、活动结束后自动分班
- **不含**: 排课模块扩展（仅声明接口契约，作为后续工作）

---

## 1. 背景与目标

当前项目学生课表查询是被动式的：学生通过 `class_id` 反查 `class_name`，再用 `FIND_IN_SET` 匹配 `teach_info.class_name` 获取行政班排课结果。`course.course_type` 已定义选修(2)/公选(3)类型但未被任何业务使用。

本设计新增"独立选课活动"机制：
- 教务管理员创建选课活动，绑定学期，配置可选课程清单与容量上限
- 学生在活动时间窗口内主动选课/退课
- 活动结束后系统按选课顺序 + 容量自动切分为多个"选课班"
- 产出的选课班数据供后续排课模块按学生颗粒度排课（第二阶段排课）

## 2. 设计决策

| 决策点 | 选择 | 理由 |
|---|---|---|
| 选课模式 | 独立选课活动 | 与现有行政班排课解耦，活动可作为期次管理 |
| 选课对象 | `course` 课程 | 与排课模块解耦，排课时再生成 `teach_info` |
| 时间冲突检测 | 不检测 | 由排课模块处理（颗粒度到学生） |
| 状态流转 | 直接生效，窗口内可退选 | 简单，先到先得 |
| 分班时机 | 活动结束后批量分班 | 选课期间容量动态变化，结束后状态稳定 |
| 分班策略 | 系统按选课顺序 + 容量自动分班 | 无需教务手工分班 |
| 容量并发控制 | Redis 原子计数器 | 项目已用 Redis，高并发性能优于行锁 |
| 数据模型 | 5 表（campaign/course/record/class/member） | 分班结果独立存储，排课消费链路短 |

## 3. 数据模型

### 3.1 新建 SQL 文件 `src/main/resources/sql/init-selection-tables.sql`

```sql
-- 选课活动表
CREATE TABLE IF NOT EXISTS `selection_campaign` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`         VARCHAR(128) NOT NULL COMMENT '活动名称',
    `semester_id`  BIGINT       NOT NULL COMMENT '关联 semester.id',
    `start_time`   DATETIME     NOT NULL COMMENT '选课开始时间',
    `end_time`     DATETIME     NOT NULL COMMENT '选课结束时间',
    `max_courses_per_student` INT NOT NULL DEFAULT 1 COMMENT '每人最多选课数',
    `status`       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/OPEN/CLOSED/FINALIZED',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_semester` (`semester_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课活动表';

-- 活动可选课程表
CREATE TABLE IF NOT EXISTS `selection_course` (
    `id`           BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `campaign_id`  BIGINT  NOT NULL COMMENT '关联 selection_campaign.id',
    `course_id`    BIGINT  NOT NULL COMMENT '关联 course.id',
    `capacity`     INT     NOT NULL COMMENT '该课程选课容量上限',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_campaign_course` (`campaign_id`, `course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课活动可选课程表';

-- 选课记录表
CREATE TABLE IF NOT EXISTS `selection_record` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `campaign_id`  BIGINT      NOT NULL COMMENT '关联 selection_campaign.id',
    `student_id`   BIGINT      NOT NULL COMMENT '学生 user.id（userId）',
    `course_id`    BIGINT      NOT NULL COMMENT '关联 course.id',
    `status`       VARCHAR(16) NOT NULL DEFAULT 'SELECTED' COMMENT 'SELECTED/DROPPED',
    `select_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `drop_time`    DATETIME             DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_campaign_student_course_active` (`campaign_id`, `student_id`, `course_id`, `status`),
    KEY `idx_campaign_course` (`campaign_id`, `course_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课记录表';

-- 选课班表（分班结果，FINALIZE 阶段写入）
CREATE TABLE IF NOT EXISTS `selection_class` (
    `id`            BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `campaign_id`   BIGINT  NOT NULL COMMENT '关联 selection_campaign.id',
    `course_id`     BIGINT  NOT NULL COMMENT '关联 course.id',
    `class_no`      INT     NOT NULL COMMENT '班号（1,2,3...）',
    `student_count` INT     NOT NULL DEFAULT 0 COMMENT '班级人数',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_campaign_course_classno` (`campaign_id`, `course_id`, `class_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课班表';

-- 选课班成员表
CREATE TABLE IF NOT EXISTS `selection_class_member` (
    `id`           BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id`     BIGINT  NOT NULL COMMENT '关联 selection_class.id',
    `student_id`   BIGINT  NOT NULL COMMENT '学生 user.id',
    `record_id`    BIGINT  NOT NULL COMMENT '关联 selection_record.id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_class_student` (`class_id`, `student_id`),
    KEY `idx_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课班成员表';
```

### 3.2 实体类（`module/selection/entity/`）

| 类 | 表 | 关键字段 |
|---|---|---|
| `SelectionCampaign` | `selection_campaign` | `id` / `name` / `semesterId` / `startTime` / `endTime` / `maxCoursesPerStudent` / `status: CampaignStatusEnum` |
| `SelectionCourse` | `selection_course` | `id` / `campaignId` / `courseId` / `capacity` |
| `SelectionRecord` | `selection_record` | `id` / `campaignId` / `studentId` / `courseId` / `status: RecordStatusEnum` / `selectTime` / `dropTime` |
| `SelectionClass` | `selection_class` | `id` / `campaignId` / `courseId` / `classNo` / `studentCount` |
| `SelectionClassMember` | `selection_class_member` | `id` / `classId` / `studentId` / `recordId` |
| `CampaignStatusEnum` | - | `DRAFT / OPEN / CLOSED / FINALIZED`（`@EnumValue` 存 name） |
| `RecordStatusEnum` | - | `SELECTED / DROPPED`（`@EnumValue` 存 name） |

**关键设计点：**
- `selection_record` 唯一键含 `status`：允许退选后重选产生新 SELECTED 行，但同一时刻同一 campaign+student+course 只能有一行 SELECTED
- `selection_class` / `selection_class_member` 只在 FINALIZE 阶段写入，是分班的物化结果
- 容量并发控制不依赖 SQL 唯一键，由 Redis 计数器保证（§4.3）

## 4. 接口设计

### 4.1 教务管理活动（`SelectionCampaignController`，`/api/selection/campaigns`）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/selection/campaigns` | 创建活动（DRAFT） | academic_admin / department |
| GET | `/api/selection/campaigns` | 活动列表（分页） | academic_admin / department |
| GET | `/api/selection/campaigns/{id}` | 活动详情 | academic_admin / department |
| PUT | `/api/selection/campaigns/{id}` | 修改（仅 DRAFT） | academic_admin / department |
| DELETE | `/api/selection/campaigns/{id}` | 删除（仅 DRAFT） | academic_admin / department |
| POST | `/api/selection/campaigns/{id}/open` | DRAFT -> OPEN | academic_admin / department |
| POST | `/api/selection/campaigns/{id}/close` | OPEN -> CLOSED | academic_admin / department |
| POST | `/api/selection/campaigns/{id}/finalize` | CLOSED -> FINALIZED（触发分班） | academic_admin |

### 4.2 教务管理可选课程（`SelectionCourseController`，`/api/selection/campaigns/{campaignId}/courses`）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `.../courses` | 添加可选课程（仅 DRAFT） | academic_admin / department |
| GET | `.../courses` | 列出可选课程 | 所有登录用户 |
| DELETE | `.../courses/{courseId}` | 移除可选课程（仅 DRAFT） | academic_admin / department |

### 4.3 学生选课（`SelectionRecordController`，`/api/selection`）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/selection/campaigns/current` | 查询当前 OPEN 的活动 | student |
| GET | `/api/selection/campaigns/{id}/courses` | 可选课程（带剩余容量、已选标记） | student |
| POST | `/api/selection/records` | 选课 `{campaignId, courseId}` | student |
| DELETE | `/api/selection/records/{id}` | 退选 | student |
| GET | `/api/selection/records/my` | 我的已选（按 campaign 过滤） | student |

### 4.4 教务查询

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/selection/campaigns/{id}/records` | 选课记录（支持 courseId / studentId 过滤） |
| GET | `/api/selection/campaigns/{id}/classes` | 分班结果（FINALIZED 后可查） |

### 4.5 DTO 清单

- `CampaignCreateRequest`：`name` / `semesterId` / `startTime` / `endTime` / `maxCoursesPerStudent`
- `CampaignUpdateRequest`：同上（全部可选）
- `CampaignResponse`：完整字段 + `semesterName` + `selectedCourseCount`
- `SelectionCourseAddRequest`：`courseId` / `capacity`
- `SelectionCourseResponse`：`courseId` / `courseName` / `courseCode` / `credit` / `courseType` / `capacity` / `selectedCount` / `remaining` / `selectedByMe`
- `SelectionRecordRequest`：`campaignId` / `courseId`
- `SelectionRecordResponse`：`id` / `campaignId` / `courseId` / `courseName` / `status` / `selectTime` / `dropTime`
- `SelectionClassResponse`：`classId` / `courseId` / `courseName` / `classNo` / `studentCount` / `members: List<StudentSelectionDto>`
- `StudentSelectionDto`：`studentId` / `studentName` / `studentNo` / `className`

## 5. 关键流程

### 5.1 选课流程（`SelectionRecordServiceImpl.select`）

```
1. 校验 campaign.status == OPEN，否则 BusinessException(409, "活动未开放")
2. 校验 now ∈ [start_time, end_time]，否则 BusinessException(409, "不在选课时间窗口内")
3. 查询该学生在该 campaign 的 SELECTED 记录数 N
   - 若 N >= max_courses_per_student，BusinessException(409, "超过每人选课上限")
4. 校验未重复选同一门课（SELECTED 状态），否则 BusinessException(409, "已选该课程")
5. Redis Lua 原子操作：
   key = "selection:count:{campaignId}:{courseId}"
   current = INCR key
   if current > capacity:
       DECR key  // 回滚
       throw BusinessException(409, "课程已满")
6. 插入 selection_record（status=SELECTED）
   - 若插入失败（唯一键冲突等）：DECR key 回滚，throw
7. 返回 SelectionRecordResponse
```

**Redis Lua 脚本**（保证 INCR + 容量判断原子）：

```lua
local current = redis.call('INCR', KEYS[1])
if current > tonumber(ARGV[1]) then
    redis.call('DECR', KEYS[1])
    return -1  -- 容量不足
end
return current
```

### 5.2 退选流程（`SelectionRecordServiceImpl.drop`）

```
1. 校验 campaign.status == OPEN 且在窗口内
2. 查询 record，校验 status == SELECTED
3. 更新 record：status=DROPPED, drop_time=now
4. Redis DECR "selection:count:{campaignId}:{courseId}"
```

### 5.3 分班流程（`SelectionClassServiceImpl.finalize`）

事务内执行：

```
1. 校验 campaign.status == CLOSED
2. 查询所有 SELECTED 记录，按 course_id 分组，每组按 select_time 升序排序
3. 对每门 course：
   a. 按 capacity 切分为多个 selection_class（班号 1, 2, 3...）
   b. 批量插入 selection_class + selection_class_member
   c. 更新 selection_class.student_count
4. 更新 campaign.status = FINALIZED
```

**分班示例**：课程 A 容量 60，选课 150 人 -> 切分为 3 个班（60+60+30）。

### 5.4 状态机

```
DRAFT --open--> OPEN --close--> CLOSED --finalize--> FINALIZED
                  ↑                  |
                  └── (可重开) ──────┘
```

- DRAFT：可改、可删、可加/删可选课程
- OPEN：学生可选/退；不可改活动基础信息；不可删
- CLOSED：停止选课；可选课数据冻结；可重开回 OPEN
- FINALIZED：已分班；不可变更

## 6. 模块结构

```
module/selection/
├── controller/
│   ├── SelectionCampaignController.java      # 活动管理 + 状态流转
│   ├── SelectionCourseController.java        # 可选课程管理
│   └── SelectionRecordController.java        # 学生选课/退选 + 查询
├── dto/
│   ├── CampaignCreateRequest.java
│   ├── CampaignUpdateRequest.java
│   ├── CampaignResponse.java
│   ├── SelectionCourseAddRequest.java
│   ├── SelectionCourseResponse.java
│   ├── SelectionRecordRequest.java
│   ├── SelectionRecordResponse.java
│   ├── SelectionClassResponse.java
│   └── StudentSelectionDto.java
├── entity/
│   ├── SelectionCampaign.java
│   ├── SelectionCourse.java
│   ├── SelectionRecord.java
│   ├── SelectionClass.java
│   ├── SelectionClassMember.java
│   ├── CampaignStatusEnum.java
│   └── RecordStatusEnum.java
├── mapper/
│   ├── SelectionCampaignMapper.java
│   ├── SelectionCourseMapper.java
│   ├── SelectionRecordMapper.java
│   ├── SelectionClassMapper.java
│   └── SelectionClassMemberMapper.java
└── service/
    ├── SelectionCampaignService.java         # IService<SelectionCampaign>
    ├── SelectionRecordService.java           # 选课/退选/查询
    ├── SelectionClassService.java            # 分班 + 查询
    └── impl/
        ├── SelectionCampaignServiceImpl.java
        ├── SelectionRecordServiceImpl.java
        └── SelectionClassServiceImpl.java
```

**依赖：**
- `StringRedisTemplate`（容量计数器）
- `CourseMapper`、`StudentMapper`、`UserMapper`、`ClassNameMapper`（DTO 组装）
- `SemesterService`（活动创建时校验 semester 存在）

## 7. 权限控制

沿用现有 `request.getAttribute("userType")` 机制（由 `AuthInterceptor` 注入）：

- `academic_admin`：所有操作
- `department`：管理本院系活动（可后续扩展院系过滤，本期暂同 academic_admin）
- `student`：仅选课/退选/查询自己记录
- `teacher`：本期无选课相关权限

权限校验在 Controller 层用 `checkAdmin(request)` / `checkStudent(request)` 私有方法，抛 `BusinessException(403, ...)`。

## 8. 缓存策略

- Redis 选课计数器 key：`selection:count:{campaignId}:{courseId}`
- TTL：活动 FINALIZED 后清理（或设 30 天兜底）
- 不缓存可选课程列表（容量动态变化，缓存意义不大）

## 9. 边界与排课契约

### 9.1 本次范围

- ✅ 选课活动 CRUD + 状态流转
- ✅ 可选课程管理
- ✅ 学生选课/退选（Redis 容量控制）
- ✅ 自动分班
- ✅ 教务/学生查询

### 9.2 排课模块扩展契约（后续工作）

排课模块消费选课数据的接口契约（本次不实现，仅声明）：

- **输入**：`campaign_id`（须 FINALIZED）
- **数据来源**：
  ```sql
  SELECT sc.class_no, sc.course_id, scm.student_id
  FROM selection_class sc
  JOIN selection_class_member scm ON scm.class_id = sc.id
  WHERE sc.campaign_id = ?
  ```
- **排课策略**：第二阶段排课
  - 第一阶段：行政班课表（现有 `SchedulingServiceImpl.solve`）
  - 第二阶段：每个 `selection_class` 作为独立 `StudentGroup`，颗粒度到学生
- **`teach_info` 写回**：`class_name` 写虚拟班名，格式 `"公选课{courseId}-{classNo}班"`，避免与真实班级名冲突
- **冲突约束**：第二阶段排课需新增约束——选课班内的学生不能在其行政班已排课时段上课

## 10. 测试策略

- 单元测试：`SelectionClassServiceImpl.finalize` 的分班切分逻辑（边界：刚好整除、有余数、单人单班）
- 集成测试：选课并发场景（多线程同时选一门满容量课程，验证不超过 capacity）
- 状态机测试：非法状态流转抛异常

本期先保证编译通过 + 基础 happy-path 测试，完整测试套件随实现迭代补充。

## 11. 风险与未决项

| 项 | 说明 |
|---|---|
| Redis 计数器与 DB 一致性 | 选课失败时 DECR 回滚；若 Redis 宕机，FINALIZE 阶段以 DB `COUNT(*)` 为准重建计数 |
| 退选后容量刷新 | 退选立即 DECR，其他学生可立即抢占 |
| 分班后学生退选 | FINALIZED 状态不允许退选（已锁定）；如需支持，需扩展为"分班后调整"流程，本期不做 |
| 跨学期选课 | 活动绑定 semester_id，不跨学期；新学期新建活动 |
