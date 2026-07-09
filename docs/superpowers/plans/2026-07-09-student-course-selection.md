# 学生选课功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `module/selection` 模块，实现选课活动管理、学生选课/退选（Redis 容量控制）、活动结束后自动分班，产出选课班数据供后续排课模块消费。

**Architecture:** 独立选课活动模式。教务创建活动（DRAFT）→ 配置可选课程 → 开启（OPEN）→ 学生在窗口内选课/退课（Redis 原子计数器控制容量）→ 关闭（CLOSED）→ 分班（FINALIZED，按选课顺序+容量切分）。5 张表：`selection_campaign` / `selection_course` / `selection_record` / `selection_class` / `selection_class_member`。

**Tech Stack:** Java 25、Spring Boot 4.0.6、MyBatis Plus 3.5.17（`com.baomidou.mybatisplus.spring.service.impl.ServiceImpl`）、Redis（`StringRedisTemplate`）、JUnit 5、Lombok。

**设计文档:** `docs/superpowers/specs/2026-07-09-student-course-selection-design.md`

---

## 文件结构

```
src/main/resources/sql/
└── init-selection-tables.sql                              [新建] 5 张表 DDL

src/main/java/com/xrq/xxq/module/selection/
├── entity/
│   ├── CampaignStatusEnum.java                            [新建] DRAFT/OPEN/CLOSED/FINALIZED
│   ├── RecordStatusEnum.java                              [新建] SELECTED/DROPPED
│   ├── SelectionCampaign.java                             [新建] 活动实体
│   ├── SelectionCourse.java                               [新建] 活动可选课程
│   ├── SelectionRecord.java                               [新建] 选课记录
│   ├── SelectionClass.java                                [新建] 选课班
│   └── SelectionClassMember.java                          [新建] 选课班成员
├── mapper/
│   ├── SelectionCampaignMapper.java                       [新建] BaseMapper
│   ├── SelectionCourseMapper.java                         [新建]
│   ├── SelectionRecordMapper.java                         [新建]
│   ├── SelectionClassMapper.java                          [新建]
│   └── SelectionClassMemberMapper.java                    [新建]
├── dto/
│   ├── CampaignCreateRequest.java                         [新建]
│   ├── CampaignUpdateRequest.java                         [新建]
│   ├── CampaignResponse.java                              [新建]
│   ├── SelectionCourseAddRequest.java                     [新建]
│   ├── SelectionCourseResponse.java                       [新建]
│   ├── SelectionRecordRequest.java                        [新建]
│   ├── SelectionRecordResponse.java                       [新建]
│   ├── SelectionClassResponse.java                        [新建]
│   └── StudentSelectionDto.java                           [新建]
├── service/
│   ├── SelectionCampaignService.java                      [新建] IService + 状态流转方法
│   ├── SelectionRecordService.java                        [新建] 选课/退选/查询
│   ├── SelectionClassService.java                         [新建] 分班 + 查询
│   └── impl/
│       ├── SelectionCampaignServiceImpl.java              [新建]
│       ├── SelectionRecordServiceImpl.java                [新建] Redis 容量控制
│       └── SelectionClassServiceImpl.java                 [新建] 分班算法
└── controller/
    ├── SelectionCampaignController.java                   [新建] 活动管理 + 状态流转
    ├── SelectionCourseController.java                     [新建] 可选课程管理
    └── SelectionRecordController.java                     [新建] 学生选课/退选 + 查询

src/test/java/com/xrq/xxq/module/selection/
└── SelectionClassServiceImplTest.java                     [新建] 分班算法纯单元测试
```

**依赖关系：** Task 1（SQL）→ Task 2（枚举）→ Task 3（实体）→ Task 4（Mapper）→ Task 5（DTO）→ Task 6/7/8/9（Service，可并行）→ Task 10（Controller）→ Task 11（验证）。

**注意事项：**
- MyBatis Plus 的 `IService` 在 `com.baomidou.mybatisplus.spring.service.IService`，`ServiceImpl` 在 `com.baomidou.mybatisplus.spring.service.impl.ServiceImpl`（不是 `core` 包，这是 mybatis-plus-spring-boot4-starter 3.5.17 的路径）。
- 枚举持久化用 `@EnumValue` 标注 `name` 字段，JSON 序列化用 `@JsonValue`（参考 `CurseEnum`）。
- 所有文件用 CRLF 行尾（项目约定）。
- 构造器注入用 `@RequiredArgsConstructor`（Lombok）。
- 鉴权用 `request.getAttribute("userId")` / `getAttribute("userType")`（由 `AuthInterceptor` 注入）。

---

## Task 1: 创建选课表 SQL 脚本

**Files:**
- Create: `src/main/resources/sql/init-selection-tables.sql`

- [ ] **Step 1: 创建 SQL 文件**

```sql
-- ============================================
-- 教务系统 - 选课功能表初始化 DDL
-- 数据库: xxq
-- ============================================

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

- [ ] **Step 2: 在本地 MySQL 执行建表**

连接 `localhost:3306/xxq`（dev profile），执行上述 SQL。验证：

```bash
mysql -u root -p xxq -e "SHOW TABLES LIKE 'selection_%';"
```

期望输出 5 张表：`selection_campaign` / `selection_course` / `selection_record` / `selection_class` / `selection_class_member`。

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/sql/init-selection-tables.sql
git -c user.name="寒霜" -c user.email="gangziwo620@163.com" commit -m "feat(selection): 新增选课功能5张表DDL"
```

---

## Task 2: 创建枚举类

**Files:**
- Create: `src/main/java/com/xrq/xxq/module/selection/entity/CampaignStatusEnum.java`
- Create: `src/main/java/com/xrq/xxq/module/selection/entity/RecordStatusEnum.java`

- [ ] **Step 1: 创建 CampaignStatusEnum**

```java
package com.xrq.xxq.module.selection.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 选课活动状态：DRAFT 草稿 / OPEN 开放选课 / CLOSED 关闭 / FINALIZED 已分班。
 */
@Getter
public enum CampaignStatusEnum {
    DRAFT("DRAFT"),
    OPEN("OPEN"),
    CLOSED("CLOSED"),
    FINALIZED("FINALIZED");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    CampaignStatusEnum(String code) {
        this.code = code;
        this.description = code;
    }
}
```

- [ ] **Step 2: 创建 RecordStatusEnum**

```java
package com.xrq.xxq.module.selection.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 选课记录状态：SELECTED 已选 / DROPPED 已退。
 */
@Getter
public enum RecordStatusEnum {
    SELECTED("SELECTED"),
    DROPPED("DROPPED");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    RecordStatusEnum(String code) {
        this.code = code;
        this.description = code;
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw compile -q
```

期望：BUILD SUCCESS。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/xrq/xxq/module/selection/entity/CampaignStatusEnum.java src/main/java/com/xrq/xxq/module/selection/entity/RecordStatusEnum.java
git -c user.name="寒霜" -c user.email="gangziwo620@163.com" commit -m "feat(selection): 新增活动状态与选课记录状态枚举"
```

---

## Task 3: 创建实体类

**Files:**
- Create: `src/main/java/com/xrq/xxq/module/selection/entity/SelectionCampaign.java`
- Create: `src/main/java/com/xrq/xxq/module/selection/entity/SelectionCourse.java`
- Create: `src/main/java/com/xrq/xxq/module/selection/entity/SelectionRecord.java`
- Create: `src/main/java/com/xrq/xxq/module/selection/entity/SelectionClass.java`
- Create: `src/main/java/com/xrq/xxq/module/selection/entity/SelectionClassMember.java`

- [ ] **Step 1: 创建 SelectionCampaign**

```java
package com.xrq.xxq.module.selection.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 选课活动实体。
 */
@Data
@TableName("selection_campaign")
public class SelectionCampaign {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long semesterId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxCoursesPerStudent;
    private CampaignStatusEnum status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 2: 创建 SelectionCourse**

```java
package com.xrq.xxq.module.selection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 选课活动可选课程实体。
 */
@Data
@TableName("selection_course")
public class SelectionCourse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campaignId;
    private Long courseId;
    private Integer capacity;
}
```

- [ ] **Step 3: 创建 SelectionRecord**

```java
package com.xrq.xxq.module.selection.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 选课记录实体。
 */
@Data
@TableName("selection_record")
public class SelectionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campaignId;
    private Long studentId;
    private Long courseId;
    private RecordStatusEnum status;
    private LocalDateTime selectTime;
    private LocalDateTime dropTime;
}
```

- [ ] **Step 4: 创建 SelectionClass**

```java
package com.xrq.xxq.module.selection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 选课班实体（分班结果）。
 */
@Data
@TableName("selection_class")
public class SelectionClass {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campaignId;
    private Long courseId;
    private Integer classNo;
    private Integer studentCount;
}
```

- [ ] **Step 5: 创建 SelectionClassMember**

```java
package com.xrq.xxq.module.selection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 选课班成员实体。
 */
@Data
@TableName("selection_class_member")
public class SelectionClassMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long classId;
    private Long studentId;
    private Long recordId;
}
```

- [ ] **Step 6: 编译验证**

```bash
./mvnw compile -q
```

期望：BUILD SUCCESS。

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/xrq/xxq/module/selection/entity/
git -c user.name="寒霜" -c user.email="gangziwo620@163.com" commit -m "feat(selection): 新增5个选课实体类"
```

---

## Task 4: 创建 Mapper 接口

**Files:**
- Create: 5 个 Mapper 接口于 `src/main/java/com/xrq/xxq/module/selection/mapper/`

- [ ] **Step 1: 创建 SelectionCampaignMapper**

```java
package com.xrq.xxq.module.selection.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;

@Mapper
public interface SelectionCampaignMapper extends BaseMapper<SelectionCampaign> {
}
```

- [ ] **Step 2: 创建 SelectionCourseMapper**

```java
package com.xrq.xxq.module.selection.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.selection.entity.SelectionCourse;

@Mapper
public interface SelectionCourseMapper extends BaseMapper<SelectionCourse> {
}
```

- [ ] **Step 3: 创建 SelectionRecordMapper**

```java
package com.xrq.xxq.module.selection.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.selection.entity.SelectionRecord;

@Mapper
public interface SelectionRecordMapper extends BaseMapper<SelectionRecord> {
}
```

- [ ] **Step 4: 创建 SelectionClassMapper**

```java
package com.xrq.xxq.module.selection.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.selection.entity.SelectionClass;

@Mapper
public interface SelectionClassMapper extends BaseMapper<SelectionClass> {
}
```

- [ ] **Step 5: 创建 SelectionClassMemberMapper**

```java
package com.xrq.xxq.module.selection.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.selection.entity.SelectionClassMember;

@Mapper
public interface SelectionClassMemberMapper extends BaseMapper<SelectionClassMember> {
}
```

- [ ] **Step 6: 编译验证**

```bash
./mvnw compile -q
```

期望：BUILD SUCCESS。

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/xrq/xxq/module/selection/mapper/
git -c user.name="寒霜" -c user.email="gangziwo620@163.com" commit -m "feat(selection): 新增5个Mapper接口"
```

---

## Task 5: 创建 DTO 类

**Files:**
- Create: 9 个 DTO 于 `src/main/java/com/xrq/xxq/module/selection/dto/`

- [ ] **Step 1: 创建 CampaignCreateRequest**

```java
package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;

import org.jspecify.annotations.NonNull;

import lombok.Data;

@Data
public class CampaignCreateRequest {
    @NonNull
    private String name;
    @NonNull
    private Long semesterId;
    @NonNull
    private LocalDateTime startTime;
    @NonNull
    private LocalDateTime endTime;
    private Integer maxCoursesPerStudent;
}
```

- [ ] **Step 2: 创建 CampaignUpdateRequest**

```java
package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class CampaignUpdateRequest {
    private String name;
    private Long semesterId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxCoursesPerStudent;
}
```

- [ ] **Step 3: 创建 CampaignResponse**

```java
package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;

import lombok.Data;

@Data
public class CampaignResponse {
    private Long id;
    private String name;
    private Long semesterId;
    private String semesterName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxCoursesPerStudent;
    private CampaignStatusEnum status;
    private LocalDateTime createTime;
    private Integer selectedCourseCount;
}
```

- [ ] **Step 4: 创建 SelectionCourseAddRequest**

```java
package com.xrq.xxq.module.selection.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

@Data
public class SelectionCourseAddRequest {
    @NonNull
    private Long courseId;
    @NonNull
    private Integer capacity;
}
```

- [ ] **Step 5: 创建 SelectionCourseResponse**

```java
package com.xrq.xxq.module.selection.dto;

import lombok.Data;

@Data
public class SelectionCourseResponse {
    private Long id;
    private Long campaignId;
    private Long courseId;
    private String courseName;
    private String courseCode;
    private Integer credit;
    private String courseType;
    private Integer capacity;
    private Integer selectedCount;
    private Integer remaining;
    private Boolean selectedByMe;
}
```

- [ ] **Step 6: 创建 SelectionRecordRequest**

```java
package com.xrq.xxq.module.selection.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

@Data
public class SelectionRecordRequest {
    @NonNull
    private Long campaignId;
    @NonNull
    private Long courseId;
}
```

- [ ] **Step 7: 创建 SelectionRecordResponse**

```java
package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.selection.entity.RecordStatusEnum;

import lombok.Data;

@Data
public class SelectionRecordResponse {
    private Long id;
    private Long campaignId;
    private Long courseId;
    private String courseName;
    private String courseCode;
    private Integer credit;
    private String courseType;
    private RecordStatusEnum status;
    private LocalDateTime selectTime;
    private LocalDateTime dropTime;
}
```

- [ ] **Step 8: 创建 StudentSelectionDto**

```java
package com.xrq.xxq.module.selection.dto;

import lombok.Data;

@Data
public class StudentSelectionDto {
    private Long studentId;
    private String studentName;
    private String studentNo;
    private String className;
}
```

- [ ] **Step 9: 创建 SelectionClassResponse**

```java
package com.xrq.xxq.module.selection.dto;

import java.util.List;

import lombok.Data;

@Data
public class SelectionClassResponse {
    private Long classId;
    private Long courseId;
    private String courseName;
    private Integer classNo;
    private Integer studentCount;
    private List<StudentSelectionDto> members;
}
```

- [ ] **Step 10: 编译验证**

```bash
./mvnw compile -q
```

期望：BUILD SUCCESS。

- [ ] **Step 11: 提交**

```bash
git add src/main/java/com/xrq/xxq/module/selection/dto/
git -c user.name="寒霜" -c user.email="gangziwo620@163.com" commit -m "feat(selection): 新增9个DTO类"
```

---

## Task 6: 实现 SelectionCampaignService（CRUD + 状态流转）

**Files:**
- Create: `src/main/java/com/xrq/xxq/module/selection/service/SelectionCampaignService.java`
- Create: `src/main/java/com/xrq/xxq/module/selection/service/impl/SelectionCampaignServiceImpl.java`

- [ ] **Step 1: 创建 Service 接口**

```java
package com.xrq.xxq.module.selection.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.selection.dto.CampaignCreateRequest;
import com.xrq.xxq.module.selection.dto.CampaignResponse;
import com.xrq.xxq.module.selection.dto.CampaignUpdateRequest;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;

public interface SelectionCampaignService extends IService<SelectionCampaign> {

    CampaignResponse create(CampaignCreateRequest request);

    CampaignResponse update(Long id, CampaignUpdateRequest request);

    CampaignResponse getDetail(Long id);

    List<CampaignResponse> listAll();

    void delete(Long id);

    void open(Long id);

    void close(Long id);

    void finalizeCampaign(Long id);
}
```

- [ ] **Step 2: 创建 Service 实现的骨架（CRUD 部分）**

```java
package com.xrq.xxq.module.selection.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.selection.dto.CampaignCreateRequest;
import com.xrq.xxq.module.selection.dto.CampaignResponse;
import com.xrq.xxq.module.selection.dto.CampaignUpdateRequest;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionCourse;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCourseMapper;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;
import com.xrq.xxq.module.selection.service.SelectionClassService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectionCampaignServiceImpl
        extends ServiceImpl<SelectionCampaignMapper, SelectionCampaign>
        implements SelectionCampaignService {

    private final SelectionCourseMapper selectionCourseMapper;
    private final SemesterService semesterService;
    private final SelectionClassService selectionClassService;

    @Override
    public CampaignResponse create(CampaignCreateRequest request) {
        Semester semester = semesterService.getById(request.getSemesterId());
        if (semester == null) {
            throw new BusinessException(400, "学期不存在");
        }
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new BusinessException(400, "结束时间不能早于开始时间");
        }
        SelectionCampaign campaign = new SelectionCampaign();
        campaign.setName(request.getName());
        campaign.setSemesterId(request.getSemesterId());
        campaign.setStartTime(request.getStartTime());
        campaign.setEndTime(request.getEndTime());
        campaign.setMaxCoursesPerStudent(
                request.getMaxCoursesPerStudent() == null ? 1 : request.getMaxCoursesPerStudent());
        campaign.setStatus(CampaignStatusEnum.DRAFT);
        save(campaign);
        return toResponse(campaign, semester);
    }

    @Override
    public CampaignResponse update(Long id, CampaignUpdateRequest request) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态的活动可修改");
        }
        if (request.getName() != null) campaign.setName(request.getName());
        if (request.getSemesterId() != null) {
            Semester semester = semesterService.getById(request.getSemesterId());
            if (semester == null) {
                throw new BusinessException(400, "学期不存在");
            }
            campaign.setSemesterId(request.getSemesterId());
        }
        if (request.getStartTime() != null) campaign.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) campaign.setEndTime(request.getEndTime());
        if (request.getMaxCoursesPerStudent() != null) {
            campaign.setMaxCoursesPerStudent(request.getMaxCoursesPerStudent());
        }
        if (campaign.getEndTime().isBefore(campaign.getStartTime())) {
            throw new BusinessException(400, "结束时间不能早于开始时间");
        }
        updateById(campaign);
        return toResponse(campaign, semesterService.getById(campaign.getSemesterId()));
    }

    @Override
    public CampaignResponse getDetail(Long id) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        return toResponse(campaign, semesterService.getById(campaign.getSemesterId()));
    }

    @Override
    public List<CampaignResponse> listAll() {
        List<SelectionCampaign> list = list();
        return list.stream()
                .map(c -> toResponse(c, semesterService.getById(c.getSemesterId())))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态的活动可删除");
        }
        selectionCourseMapper.delete(new LambdaQueryWrapper<SelectionCourse>()
                .eq(SelectionCourse::getCampaignId, id));
        removeById(id);
    }

    @Override
    public void open(Long id) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态的活动可开启");
        }
        Long courseCount = selectionCourseMapper.selectCount(
                new LambdaQueryWrapper<SelectionCourse>().eq(SelectionCourse::getCampaignId, id));
        if (courseCount == 0) {
            throw new BusinessException(409, "活动未配置可选课程，无法开启");
        }
        campaign.setStatus(CampaignStatusEnum.OPEN);
        updateById(campaign);
    }

    @Override
    public void close(Long id) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.OPEN) {
            throw new BusinessException(409, "仅开放状态的活动可关闭");
        }
        campaign.setStatus(CampaignStatusEnum.CLOSED);
        updateById(campaign);
    }

    @Override
    public void finalizeCampaign(Long id) {
        SelectionCampaign campaign = getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.CLOSED) {
            throw new BusinessException(409, "仅关闭状态的活动可分班");
        }
        selectionClassService.finalize(id);
        campaign.setStatus(CampaignStatusEnum.FINALIZED);
        updateById(campaign);
    }

    private CampaignResponse toResponse(SelectionCampaign campaign, Semester semester) {
        CampaignResponse resp = new CampaignResponse();
        resp.setId(campaign.getId());
        resp.setName(campaign.getName());
        resp.setSemesterId(campaign.getSemesterId());
        resp.setSemesterName(semester != null ? semester.getName() : null);
        resp.setStartTime(campaign.getStartTime());
        resp.setEndTime(campaign.getEndTime());
        resp.setMaxCoursesPerStudent(campaign.getMaxCoursesPerStudent());
        resp.setStatus(campaign.getStatus());
        resp.setCreateTime(campaign.getCreateTime());
        Long courseCount = selectionCourseMapper.selectCount(
                new LambdaQueryWrapper<SelectionCourse>().eq(SelectionCourse::getCampaignId, campaign.getId()));
        resp.setSelectedCourseCount(courseCount.intValue());
        return resp;
    }
}
```

- [ ] **Step 3: 创建 SelectionClassService 接口占位（Task 9 会补充完整）**

为了让 Task 6 能编译通过，先创建 `SelectionClassService` 接口和空实现的 `SelectionClassServiceImpl`，Task 9 再填充 `finalize` 逻辑。

`src/main/java/com/xrq/xxq/module/selection/service/SelectionClassService.java`:

```java
package com.xrq.xxq.module.selection.service;

import java.util.List;

import com.xrq.xxq.module.selection.dto.SelectionClassResponse;

public interface SelectionClassService {

    /** 分班：按选课顺序 + 容量切分选课班。 */
    void finalize(Long campaignId);

    /** 查询分班结果。 */
    List<SelectionClassResponse> listByCampaign(Long campaignId);
}
```

`src/main/java/com/xrq/xxq/module/selection/service/impl/SelectionClassServiceImpl.java`（占位，Task 9 填充）:

```java
package com.xrq.xxq.module.selection.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.xrq.xxq.module.selection.dto.SelectionClassResponse;
import com.xrq.xxq.module.selection.service.SelectionClassService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectionClassServiceImpl implements SelectionClassService {

    @Override
    public void finalize(Long campaignId) {
        // Task 9 实现
    }

    @Override
    public List<SelectionClassResponse> listByCampaign(Long campaignId) {
        return List.of();
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
./mvnw compile -q
```

期望：BUILD SUCCESS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/xrq/xxq/module/selection/service/
git -c user.name="寒霜" -c user.email="gangziwo620@163.com" commit -m "feat(selection): 实现活动CRUD与状态流转服务"
```

---

## Task 7: 实现 SelectionCourseController（可选课程管理）

**Files:**
- Create: `src/main/java/com/xrq/xxq/module/selection/controller/SelectionCourseController.java`

- [ ] **Step 1: 创建 Controller**

```java
package com.xrq.xxq.module.selection.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.selection.dto.SelectionCourseAddRequest;
import com.xrq.xxq.module.selection.dto.SelectionCourseResponse;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionCourse;
import com.xrq.xxq.module.selection.mapper.SelectionCourseMapper;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 选课活动可选课程管理接口。
 */
@RestController
@RequestMapping("/api/selection/campaigns/{campaignId}/courses")
@RequiredArgsConstructor
public class SelectionCourseController {

    private final SelectionCampaignService campaignService;
    private final SelectionCourseMapper selectionCourseMapper;
    private final CourseMapper courseMapper;

    @PostMapping
    public Result<SelectionCourse> add(@PathVariable Long campaignId,
                                       @RequestBody SelectionCourseAddRequest request) {
        SelectionCampaign campaign = campaignService.getById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态可配置可选课程");
        }
        Course course = courseMapper.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(400, "课程不存在");
        }
        if (request.getCapacity() <= 0) {
            throw new BusinessException(400, "容量必须大于0");
        }
        Long exists = selectionCourseMapper.selectCount(new LambdaQueryWrapper<SelectionCourse>()
                .eq(SelectionCourse::getCampaignId, campaignId)
                .eq(SelectionCourse::getCourseId, request.getCourseId()));
        if (exists > 0) {
            throw new BusinessException(409, "该课程已在活动中");
        }
        SelectionCourse sc = new SelectionCourse();
        sc.setCampaignId(campaignId);
        sc.setCourseId(request.getCourseId());
        sc.setCapacity(request.getCapacity());
        selectionCourseMapper.insert(sc);
        return Result.ok(sc);
    }

    @GetMapping
    public Result<List<SelectionCourseResponse>> list(@PathVariable Long campaignId,
                                                      HttpServletRequest request) {
        SelectionCampaign campaign = campaignService.getById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        List<SelectionCourse> courses = selectionCourseMapper.selectList(
                new LambdaQueryWrapper<SelectionCourse>().eq(SelectionCourse::getCampaignId, campaignId));
        if (courses.isEmpty()) {
            return Result.ok(List.of());
        }
        List<Long> courseIds = courses.stream().map(SelectionCourse::getCourseId).toList();
        Map<Long, Course> courseMap = courseMapper.selectByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));
        return Result.ok(courses.stream().map(sc -> toResponse(sc, courseMap.get(sc.getCourseId()))).toList());
    }

    @DeleteMapping("/{courseId}")
    public Result<Void> remove(@PathVariable Long campaignId, @PathVariable Long courseId) {
        SelectionCampaign campaign = campaignService.getById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态可移除可选课程");
        }
        selectionCourseMapper.delete(new LambdaQueryWrapper<SelectionCourse>()
                .eq(SelectionCourse::getCampaignId, campaignId)
                .eq(SelectionCourse::getCourseId, courseId));
        return Result.ok();
    }

    private SelectionCourseResponse toResponse(SelectionCourse sc, Course course) {
        SelectionCourseResponse resp = new SelectionCourseResponse();
        resp.setId(sc.getId());
        resp.setCampaignId(sc.getCampaignId());
        resp.setCourseId(sc.getCourseId());
        resp.setCapacity(sc.getCapacity());
        if (course != null) {
            resp.setCourseName(course.getCourseName());
            resp.setCourseCode(course.getCourseCode());
            resp.setCredit(course.getCredit());
            resp.setCourseType(course.getCourseType() != null ? course.getCourseType().getDescription() : null);
        }
        resp.setSelectedCount(0);
        resp.setRemaining(sc.getCapacity());
        resp.setSelectedByMe(false);
        return resp;
    }
}
```

说明：`selectedCount` / `remaining` / `selectedByMe` 的实时计算在 Task 8 的 `SelectionRecordService` 中补充（学生查询时调用）。本任务的 `list` 仅返回静态信息，教务管理场景足够。

- [ ] **Step 2: 编译验证**

```bash
./mvnw compile -q
```

期望：BUILD SUCCESS。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/xrq/xxq/module/selection/controller/SelectionCourseController.java
git -c user.name="寒霜" -c user.email="gangziwo620@163.com" commit -m "feat(selection): 实现可选课程管理接口"
```

---

## Task 8: 实现 SelectionRecordService（选课/退选 + Redis 容量控制）

**Files:**
- Create: `src/main/java/com/xrq/xxq/module/selection/service/SelectionRecordService.java`
- Create: `src/main/java/com/xrq/xxq/module/selection/service/impl/SelectionRecordServiceImpl.java`

- [ ] **Step 1: 创建 Service 接口**

```java
package com.xrq.xxq.module.selection.service;

import java.util.List;

import com.xrq.xxq.module.selection.dto.SelectionCourseResponse;
import com.xrq.xxq.module.selection.dto.SelectionRecordRequest;
import com.xrq.xxq.module.selection.dto.SelectionRecordResponse;

public interface SelectionRecordService {

    SelectionRecordResponse select(Long studentUserId, SelectionRecordRequest request);

    void drop(Long studentUserId, Long recordId);

    List<SelectionRecordResponse> listMy(Long studentUserId, Long campaignId);

    List<SelectionCourseResponse> listCampaignCoursesForStudent(Long campaignId, Long studentUserId);
}
```

- [ ] **Step 2: 创建 Service 实现**

```java
package com.xrq.xxq.module.selection.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.selection.dto.SelectionCourseResponse;
import com.xrq.xxq.module.selection.dto.SelectionRecordRequest;
import com.xrq.xxq.module.selection.dto.SelectionRecordResponse;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.RecordStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionCourse;
import com.xrq.xxq.module.selection.entity.SelectionRecord;
import com.xrq.xxq.module.selection.mapper.SelectionCourseMapper;
import com.xrq.xxq.module.selection.mapper.SelectionRecordMapper;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;
import com.xrq.xxq.module.selection.service.SelectionRecordService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectionRecordServiceImpl implements SelectionRecordService {

    private static final String COUNT_KEY_PREFIX = "selection:count:";
    private static final String INCR_LUA =
            "local cur = redis.call('INCR', KEYS[1]) " +
            "if cur > tonumber(ARGV[1]) then " +
            "  redis.call('DECR', KEYS[1]) " +
            "  return -1 " +
            "end " +
            "return cur";

    private final SelectionRecordMapper selectionRecordMapper;
    private final SelectionCourseMapper selectionCourseMapper;
    private final CourseMapper courseMapper;
    private final SelectionCampaignService campaignService;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public SelectionRecordResponse select(Long studentUserId, SelectionRecordRequest request) {
        SelectionCampaign campaign = campaignService.getById(request.getCampaignId());
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.OPEN) {
            throw new BusinessException(409, "活动未开放");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(campaign.getStartTime()) || now.isAfter(campaign.getEndTime())) {
            throw new BusinessException(409, "不在选课时间窗口内");
        }

        SelectionCourse sc = selectionCourseMapper.selectOne(new LambdaQueryWrapper<SelectionCourse>()
                .eq(SelectionCourse::getCampaignId, request.getCampaignId())
                .eq(SelectionCourse::getCourseId, request.getCourseId()));
        if (sc == null) {
            throw new BusinessException(404, "该课程不在可选列表中");
        }

        Long selectedCount = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                .eq(SelectionRecord::getCampaignId, request.getCampaignId())
                .eq(SelectionRecord::getStudentId, studentUserId)
                .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
        if (selectedCount >= campaign.getMaxCoursesPerStudent()) {
            throw new BusinessException(409, "超过每人选课上限 " + campaign.getMaxCoursesPerStudent() + " 门");
        }

        Long dupCount = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                .eq(SelectionRecord::getCampaignId, request.getCampaignId())
                .eq(SelectionRecord::getStudentId, studentUserId)
                .eq(SelectionRecord::getCourseId, request.getCourseId())
                .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
        if (dupCount > 0) {
            throw new BusinessException(409, "已选该课程");
        }

        String countKey = COUNT_KEY_PREFIX + request.getCampaignId() + ":" + request.getCourseId();
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCR_LUA, Long.class);
        Long result = redisTemplate.execute(script, List.of(countKey), String.valueOf(sc.getCapacity()));
        if (result == null || result == -1) {
            throw new BusinessException(409, "课程已满");
        }

        try {
            SelectionRecord record = new SelectionRecord();
            record.setCampaignId(request.getCampaignId());
            record.setStudentId(studentUserId);
            record.setCourseId(request.getCourseId());
            record.setStatus(RecordStatusEnum.SELECTED);
            record.setSelectTime(LocalDateTime.now());
            selectionRecordMapper.insert(record);
            return toResponse(record, courseMapper.selectById(request.getCourseId()));
        } catch (Exception e) {
            redisTemplate.opsForValue().decrement(countKey);
            throw e;
        }
    }

    @Override
    @Transactional
    public void drop(Long studentUserId, Long recordId) {
        SelectionRecord record = selectionRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(404, "选课记录不存在");
        }
        if (!record.getStudentId().equals(studentUserId)) {
            throw new BusinessException(403, "无权操作他人选课记录");
        }
        if (record.getStatus() != RecordStatusEnum.SELECTED) {
            throw new BusinessException(409, "该记录已退选");
        }
        SelectionCampaign campaign = campaignService.getById(record.getCampaignId());
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.OPEN) {
            throw new BusinessException(409, "活动未开放，不可退选");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(campaign.getStartTime()) || now.isAfter(campaign.getEndTime())) {
            throw new BusinessException(409, "不在选课时间窗口内");
        }

        record.setStatus(RecordStatusEnum.DROPPED);
        record.setDropTime(LocalDateTime.now());
        selectionRecordMapper.updateById(record);

        String countKey = COUNT_KEY_PREFIX + record.getCampaignId() + ":" + record.getCourseId();
        redisTemplate.opsForValue().decrement(countKey);
    }

    @Override
    public List<SelectionRecordResponse> listMy(Long studentUserId, Long campaignId) {
        List<SelectionRecord> records = selectionRecordMapper.selectList(
                new LambdaQueryWrapper<SelectionRecord>()
                        .eq(SelectionRecord::getStudentId, studentUserId)
                        .eq(SelectionRecord::getCampaignId, campaignId)
                        .orderByDesc(SelectionRecord::getSelectTime));
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = records.stream().map(SelectionRecord::getCourseId).distinct().toList();
        Map<Long, Course> courseMap = courseMapper.selectByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));
        return records.stream()
                .map(r -> toResponse(r, courseMap.get(r.getCourseId())))
                .toList();
    }

    @Override
    public List<SelectionCourseResponse> listCampaignCoursesForStudent(Long campaignId, Long studentUserId) {
        SelectionCampaign campaign = campaignService.getById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        List<SelectionCourse> courses = selectionCourseMapper.selectList(
                new LambdaQueryWrapper<SelectionCourse>().eq(SelectionCourse::getCampaignId, campaignId));
        if (courses.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = courses.stream().map(SelectionCourse::getCourseId).toList();
        Map<Long, Course> courseMap = courseMapper.selectByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

        List<SelectionRecord> myRecords = selectionRecordMapper.selectList(
                new LambdaQueryWrapper<SelectionRecord>()
                        .eq(SelectionRecord::getCampaignId, campaignId)
                        .eq(SelectionRecord::getStudentId, studentUserId)
                        .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
        Map<Long, Long> mySelectedCourseIds = myRecords.stream()
                .collect(Collectors.toMap(SelectionRecord::getCourseId, SelectionRecord::getId, (a, b) -> a));

        return courses.stream().map(sc -> {
            Course course = courseMap.get(sc.getCourseId());
            SelectionCourseResponse resp = new SelectionCourseResponse();
            resp.setId(sc.getId());
            resp.setCampaignId(sc.getCampaignId());
            resp.setCourseId(sc.getCourseId());
            resp.setCapacity(sc.getCapacity());
            if (course != null) {
                resp.setCourseName(course.getCourseName());
                resp.setCourseCode(course.getCourseCode());
                resp.setCredit(course.getCredit());
                resp.setCourseType(course.getCourseType() != null ? course.getCourseType().getDescription() : null);
            }
            Long selectedCount = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                    .eq(SelectionRecord::getCampaignId, campaignId)
                    .eq(SelectionRecord::getCourseId, sc.getCourseId())
                    .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
            resp.setSelectedCount(selectedCount.intValue());
            resp.setRemaining(Math.max(0, sc.getCapacity() - selectedCount.intValue()));
            resp.setSelectedByMe(mySelectedCourseIds.containsKey(sc.getCourseId()));
            return resp;
        }).toList();
    }

    private SelectionRecordResponse toResponse(SelectionRecord record, Course course) {
        SelectionRecordResponse resp = new SelectionRecordResponse();
        resp.setId(record.getId());
        resp.setCampaignId(record.getCampaignId());
        resp.setCourseId(record.getCourseId());
        resp.setStatus(record.getStatus());
        resp.setSelectTime(record.getSelectTime());
        resp.setDropTime(record.getDropTime());
        if (course != null) {
            resp.setCourseName(course.getCourseName());
            resp.setCourseCode(course.getCourseCode());
            resp.setCredit(course.getCredit());
            resp.setCourseType(course.getCourseType() != null ? course.getCourseType().getDescription() : null);
        }
        return resp;
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw compile -q
```

期望：BUILD SUCCESS。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/xrq/xxq/module/selection/service/SelectionRecordService.java src/main/java/com/xrq/xxq/module/selection/service/impl/SelectionRecordServiceImpl.java
git -c user.name="寒霜" -c user.email="gangziwo620@163.com" commit -m "feat(selection): 实现选课退选与Redis容量控制"
```

---

## Task 9: 实现 SelectionClassService 分班逻辑（TDD）

**Files:**
- Modify: `src/main/java/com/xrq/xxq/module/selection/service/impl/SelectionClassServiceImpl.java`
- Modify: `src/main/java/com/xrq/xxq/module/selection/service/impl/SelectionCampaignServiceImpl.java`（去掉对旧占位 finalize 的依赖变更，本任务不变更此文件）
- Test: `src/test/java/com/xrq/xxq/module/selection/SelectionClassServiceImplTest.java`

分班的核心是"按选课顺序 + 容量切分"。把这个切分逻辑抽成纯函数 `partitionByCapacity`，方便单元测试。

- [ ] **Step 1: 写失败测试 - 分班切分算法**

创建测试文件 `src/test/java/com/xrq/xxq/module/selection/SelectionClassServiceImplTest.java`：

```java
package com.xrq.xxq.module.selection;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.xrq.xxq.module.selection.entity.SelectionRecord;
import com.xrq.xxq.module.selection.service.impl.SelectionClassServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionClassServiceImplTest {

    private final SelectionClassServiceImpl service = new SelectionClassServiceImpl(
            null, null, null, null, null, null, null, null);

    @Test
    void partitionByCapacity_exactDivision() {
        List<SelectionRecord> records = buildRecords(120);
        List<List<SelectionRecord>> partitions = service.partitionByCapacity(records, 60);
        assertEquals(2, partitions.size());
        assertEquals(60, partitions.get(0).size());
        assertEquals(60, partitions.get(1).size());
    }

    @Test
    void partitionByCapacity_withRemainder() {
        List<SelectionRecord> records = buildRecords(150);
        List<List<SelectionRecord>> partitions = service.partitionByCapacity(records, 60);
        assertEquals(3, partitions.size());
        assertEquals(60, partitions.get(0).size());
        assertEquals(60, partitions.get(1).size());
        assertEquals(30, partitions.get(2).size());
    }

    @Test
    void partitionByCapacity_singleRecord() {
        List<SelectionRecord> records = buildRecords(1);
        List<List<SelectionRecord>> partitions = service.partitionByCapacity(records, 60);
        assertEquals(1, partitions.size());
        assertEquals(1, partitions.get(0).size());
    }

    @Test
    void partitionByCapacity_emptyInput() {
        List<List<SelectionRecord>> partitions = service.partitionByCapacity(List.of(), 60);
        assertTrue(partitions.isEmpty());
    }

    @Test
    void partitionByCapacity_preservesOrder() {
        List<SelectionRecord> records = buildRecords(3);
        List<List<SelectionRecord>> partitions = service.partitionByCapacity(records, 2);
        assertEquals(2, partitions.size());
        assertEquals(1L, partitions.get(0).get(0).getStudentId());
        assertEquals(2L, partitions.get(0).get(1).getStudentId());
        assertEquals(3L, partitions.get(1).get(0).getStudentId());
    }

    private List<SelectionRecord> buildRecords(int count) {
        List<SelectionRecord> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            SelectionRecord r = new SelectionRecord();
            r.setId((long) i);
            r.setStudentId((long) i);
            r.setCourseId(100L);
            r.setSelectTime(LocalDateTime.now().plusNanos(i));
            list.add(r);
        }
        return list;
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./mvnw test -Dtest=SelectionClassServiceImplTest -q
```

期望：编译失败（`partitionByCapacity` 方法不存在，构造函数签名不匹配）。

- [ ] **Step 3: 实现 SelectionClassServiceImpl 完整版**

替换 `src/main/java/com/xrq/xxq/module/selection/service/impl/SelectionClassServiceImpl.java` 全部内容：

```java
package com.xrq.xxq.module.selection.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.selection.dto.SelectionClassResponse;
import com.xrq.xxq.module.selection.dto.StudentSelectionDto;
import com.xrq.xxq.module.selection.entity.RecordStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionClass;
import com.xrq.xxq.module.selection.entity.SelectionClassMember;
import com.xrq.xxq.module.selection.entity.SelectionCourse;
import com.xrq.xxq.module.selection.entity.SelectionRecord;
import com.xrq.xxq.module.selection.mapper.SelectionClassMapper;
import com.xrq.xxq.module.selection.mapper.SelectionClassMemberMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCourseMapper;
import com.xrq.xxq.module.selection.mapper.SelectionRecordMapper;
import com.xrq.xxq.module.selection.service.SelectionClassService;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectionClassServiceImpl implements SelectionClassService {

    private final SelectionClassMapper selectionClassMapper;
    private final SelectionClassMemberMapper selectionClassMemberMapper;
    private final SelectionRecordMapper selectionRecordMapper;
    private final SelectionCourseMapper selectionCourseMapper;
    private final CourseMapper courseMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ClassNameMapper classNameMapper;

    @Override
    @Transactional
    public void finalize(Long campaignId) {
        List<SelectionCourse> courses = selectionCourseMapper.selectList(
                new LambdaQueryWrapper<SelectionCourse>().eq(SelectionCourse::getCampaignId, campaignId));
        if (courses.isEmpty()) {
            throw new BusinessException(409, "活动未配置可选课程");
        }

        List<SelectionRecord> allRecords = selectionRecordMapper.selectList(
                new LambdaQueryWrapper<SelectionRecord>()
                        .eq(SelectionRecord::getCampaignId, campaignId)
                        .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED)
                        .orderByAsc(SelectionRecord::getSelectTime));
        if (allRecords.isEmpty()) {
            return;
        }

        Map<Long, List<SelectionRecord>> recordsByCourse = allRecords.stream()
                .collect(Collectors.groupingBy(SelectionRecord::getCourseId, LinkedHashMap::new, Collectors.toList()));

        for (SelectionCourse sc : courses) {
            List<SelectionRecord> courseRecords = recordsByCourse.getOrDefault(sc.getCourseId(), List.of());
            if (courseRecords.isEmpty()) {
                continue;
            }
            List<List<SelectionRecord>> partitions = partitionByCapacity(courseRecords, sc.getCapacity());
            for (int i = 0; i < partitions.size(); i++) {
                List<SelectionRecord> partition = partitions.get(i);
                SelectionClass selClass = new SelectionClass();
                selClass.setCampaignId(campaignId);
                selClass.setCourseId(sc.getCourseId());
                selClass.setClassNo(i + 1);
                selClass.setStudentCount(partition.size());
                selectionClassMapper.insert(selClass);

                List<SelectionClassMember> members = partition.stream().map(r -> {
                    SelectionClassMember m = new SelectionClassMember();
                    m.setClassId(selClass.getId());
                    m.setStudentId(r.getStudentId());
                    m.setRecordId(r.getId());
                    return m;
                }).toList();
                for (SelectionClassMember m : members) {
                    selectionClassMemberMapper.insert(m);
                }
            }
        }
    }

    /**
     * 按顺序 + 容量切分记录列表。纯函数，便于单元测试。
     */
    List<List<SelectionRecord>> partitionByCapacity(List<SelectionRecord> records, int capacity) {
        List<List<SelectionRecord>> partitions = new ArrayList<>();
        if (records.isEmpty() || capacity <= 0) {
            return partitions;
        }
        for (int i = 0; i < records.size(); i += capacity) {
            int end = Math.min(i + capacity, records.size());
            partitions.add(new ArrayList<>(records.subList(i, end)));
        }
        return partitions;
    }

    @Override
    public List<SelectionClassResponse> listByCampaign(Long campaignId) {
        List<SelectionClass> classes = selectionClassMapper.selectList(
                new LambdaQueryWrapper<SelectionClass>().eq(SelectionClass::getCampaignId, campaignId));
        if (classes.isEmpty()) {
            return List.of();
        }

        List<Long> courseIds = classes.stream().map(SelectionClass::getCourseId).distinct().toList();
        Map<Long, Course> courseMap = courseMapper.selectByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

        List<Long> classIds = classes.stream().map(SelectionClass::getId).toList();
        List<SelectionClassMember> allMembers = selectionClassMemberMapper.selectList(
                new LambdaQueryWrapper<SelectionClassMember>().in(SelectionClassMember::getClassId, classIds));
        Map<Long, List<SelectionClassMember>> membersByClass = allMembers.stream()
                .collect(Collectors.groupingBy(SelectionClassMember::getClassId));

        List<Long> studentIds = allMembers.stream().map(SelectionClassMember::getStudentId).distinct().toList();
        Map<Long, Student> studentMap = studentIds.isEmpty() ? Map.of()
                : studentMapper.selectList(new LambdaQueryWrapper<Student>().in(Student::getUserId, studentIds))
                        .stream().collect(Collectors.toMap(Student::getUserId, s -> s));

        List<Long> userIds = studentMap.values().stream().map(Student::getUserId).toList();
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Long> classIdsOfStudents = studentMap.values().stream().map(Student::getClassId).distinct().toList();
        Map<Long, ClassName> classMap = classIdsOfStudents.isEmpty() ? Map.of()
                : classNameMapper.selectBatchIds(classIdsOfStudents).stream()
                        .collect(Collectors.toMap(ClassName::getId, c -> c));

        return classes.stream().map(c -> {
            SelectionClassResponse resp = new SelectionClassResponse();
            resp.setClassId(c.getId());
            resp.setCourseId(c.getCourseId());
            Course course = courseMap.get(c.getCourseId());
            resp.setCourseName(course != null ? course.getCourseName() : null);
            resp.setClassNo(c.getClassNo());
            resp.setStudentCount(c.getStudentCount());
            List<SelectionClassMember> members = membersByClass.getOrDefault(c.getId(), List.of());
            resp.setMembers(members.stream().map(m -> {
                StudentSelectionDto dto = new StudentSelectionDto();
                dto.setStudentId(m.getStudentId());
                User user = userMap.get(m.getStudentId());
                if (user != null) dto.setStudentName(user.getName());
                Student student = studentMap.get(m.getStudentId());
                if (student != null) {
                    dto.setStudentNo(student.getStudentNo());
                    ClassName cls = classMap.get(student.getClassId());
                    if (cls != null) dto.setClassName(cls.getClassName());
                }
                return dto;
            }).toList());
            return resp;
        }).toList();
    }
}
```

注意：构造函数参数顺序为 `selectionClassMapper, selectionClassMemberMapper, selectionRecordMapper, selectionCourseMapper, courseMapper, studentMapper, userMapper, classNameMapper`，测试中用 `null` 填充。

- [ ] **Step 4: 运行测试验证通过**

```bash
./mvnw test -Dtest=SelectionClassServiceImplTest -q
```

期望：BUILD SUCCESS，5 个测试全部 PASS。

- [ ] **Step 5: 编译整个项目验证**

```bash
./mvnw compile -q
```

期望：BUILD SUCCESS（`SelectionCampaignServiceImpl` 中的 `selectionClassService.finalize(id)` 调用已能链接到真实实现）。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/xrq/xxq/module/selection/service/impl/SelectionClassServiceImpl.java src/test/java/com/xrq/xxq/module/selection/SelectionClassServiceImplTest.java
git -c user.name="寒霜" -c user.email="gangziwo620@163.com" commit -m "feat(selection): 实现自动分班算法与单元测试"
```

---

## Task 10: 实现 SelectionCampaignController 与 SelectionRecordController

**Files:**
- Create: `src/main/java/com/xrq/xxq/module/selection/controller/SelectionCampaignController.java`
- Create: `src/main/java/com/xrq/xxq/module/selection/controller/SelectionRecordController.java`

- [ ] **Step 1: 创建 SelectionCampaignController**

```java
package com.xrq.xxq.module.selection.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.selection.dto.CampaignCreateRequest;
import com.xrq.xxq.module.selection.dto.CampaignResponse;
import com.xrq.xxq.module.selection.dto.CampaignUpdateRequest;
import com.xrq.xxq.module.selection.dto.SelectionClassResponse;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;
import com.xrq.xxq.module.selection.service.SelectionClassService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 选课活动管理接口。
 */
@RestController
@RequestMapping("/api/selection/campaigns")
@RequiredArgsConstructor
public class SelectionCampaignController {

    private final SelectionCampaignService campaignService;
    private final SelectionClassService selectionClassService;

    @PostMapping
    public Result<CampaignResponse> create(HttpServletRequest request,
                                           @RequestBody CampaignCreateRequest body) {
        checkAdmin(request);
        return Result.ok(campaignService.create(body));
    }

    @GetMapping
    public Result<List<CampaignResponse>> list(HttpServletRequest request) {
        checkAdmin(request);
        return Result.ok(campaignService.listAll());
    }

    @GetMapping("/{id}")
    public Result<CampaignResponse> getById(HttpServletRequest request, @PathVariable Long id) {
        checkAdmin(request);
        return Result.ok(campaignService.getDetail(id));
    }

    @PutMapping("/{id}")
    public Result<CampaignResponse> update(HttpServletRequest request, @PathVariable Long id,
                                           @RequestBody CampaignUpdateRequest body) {
        checkAdmin(request);
        return Result.ok(campaignService.update(id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        checkAdmin(request);
        campaignService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/open")
    public Result<Void> open(HttpServletRequest request, @PathVariable Long id) {
        checkAdmin(request);
        campaignService.open(id);
        return Result.ok();
    }

    @PostMapping("/{id}/close")
    public Result<Void> close(HttpServletRequest request, @PathVariable Long id) {
        checkAdmin(request);
        campaignService.close(id);
        return Result.ok();
    }

    @PostMapping("/{id}/finalize")
    public Result<Void> finalize(HttpServletRequest request, @PathVariable Long id) {
        checkAdmin(request);
        campaignService.finalizeCampaign(id);
        return Result.ok();
    }

    @GetMapping("/{id}/classes")
    public Result<List<SelectionClassResponse>> listClasses(HttpServletRequest request,
                                                           @PathVariable Long id) {
        checkAdmin(request);
        SelectionCampaign campaign = campaignService.getById(id);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.FINALIZED) {
            throw new BusinessException(409, "活动尚未分班");
        }
        return Result.ok(selectionClassService.listByCampaign(id));
    }

    private void checkAdmin(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");
        if (!"academic_admin".equals(userType) && !"department".equals(userType)) {
            throw new BusinessException(403, "无权操作选课活动");
        }
    }
}
```

- [ ] **Step 2: 创建 SelectionRecordController**

```java
package com.xrq.xxq.module.selection.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.selection.dto.CampaignResponse;
import com.xrq.xxq.module.selection.dto.SelectionCourseResponse;
import com.xrq.xxq.module.selection.dto.SelectionRecordRequest;
import com.xrq.xxq.module.selection.dto.SelectionRecordResponse;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;
import com.xrq.xxq.module.selection.service.SelectionRecordService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 学生选课接口。
 */
@RestController
@RequestMapping("/api/selection")
@RequiredArgsConstructor
public class SelectionRecordController {

    private final SelectionRecordService selectionRecordService;
    private final SelectionCampaignService campaignService;

    @GetMapping("/campaigns/current")
    public Result<CampaignResponse> currentCampaign(HttpServletRequest request) {
        checkStudent(request);
        SelectionCampaign campaign = campaignService.lambdaQuery()
                .eq(SelectionCampaign::getStatus, CampaignStatusEnum.OPEN)
                .last("LIMIT 1")
                .one();
        if (campaign == null) {
            return Result.ok(null);
        }
        return Result.ok(campaignService.getDetail(campaign.getId()));
    }

    @GetMapping("/campaigns/{id}/courses")
    public Result<List<SelectionCourseResponse>> listCoursesForStudent(HttpServletRequest request,
                                                                       @PathVariable Long id) {
        checkStudent(request);
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(selectionRecordService.listCampaignCoursesForStudent(id, userId));
    }

    @PostMapping("/records")
    public Result<SelectionRecordResponse> select(HttpServletRequest request,
                                                  @RequestBody SelectionRecordRequest body) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(selectionRecordService.select(userId, body));
    }

    @DeleteMapping("/records/{id}")
    public Result<Void> drop(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        selectionRecordService.drop(userId, id);
        return Result.ok();
    }

    @GetMapping("/records/my")
    public Result<List<SelectionRecordResponse>> myRecords(HttpServletRequest request,
                                                           @RequestParam Long campaignId) {
        checkStudent(request);
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(selectionRecordService.listMy(userId, campaignId));
    }

    private void checkStudent(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");
        if (!"student".equals(userType)) {
            throw new BusinessException(403, "仅学生可访问");
        }
    }
}
```

注意：`POST /records` 和 `DELETE /records/{id}` 不强制 `checkStudent`，因为 `SelectionRecordServiceImpl` 已通过 `studentUserId` 参数绑定当前用户，非学生调用会因找不到匹配记录而失败。但建议保持一致，可根据团队偏好决定是否加 `checkStudent`。本实现保持宽松，依赖 Service 层校验。

- [ ] **Step 3: 编译验证**

```bash
./mvnw compile -q
```

期望：BUILD SUCCESS。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/xrq/xxq/module/selection/controller/SelectionCampaignController.java src/main/java/com/xrq/xxq/module/selection/controller/SelectionRecordController.java
git -c user.name="寒霜" -c user.email="gangziwo620@163.com" commit -m "feat(selection): 实现活动管理与学生选课接口"
```

---

## Task 11: 编译验证与端到端手动测试

**Files:**
- 无新建文件，仅运行验证

- [ ] **Step 1: 完整编译 + 单元测试**

```bash
./mvnw clean test -Dtest=SelectionClassServiceImplTest -q
```

期望：BUILD SUCCESS，5 个分班测试 PASS。

- [ ] **Step 2: 完整打包验证（跳过集成测试）**

```bash
./mvnw clean package -DskipTests -q
```

期望：BUILD SUCCESS，生成 `target/xxq-0.0.1-SNAPSHOT.jar`。

- [ ] **Step 3: 启动应用**

```bash
./mvnw spring-boot:run
```

期望：应用正常启动，日志中无 Mapper/Bean 注入失败。

- [ ] **Step 4: 端到端手动测试 - 教务创建活动**

用 `curl` 或 Postman（需先登录获取 access token，假设 token 为 `$TOKEN`，userId 对应 academic_admin）：

```bash
# 1. 创建活动
curl -X POST http://localhost:8080/api/selection/campaigns \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"2026秋季公选课","semesterId":1,"startTime":"2026-07-10T00:00:00","endTime":"2026-07-20T23:59:59","maxCoursesPerStudent":3}'
# 期望：200，返回活动 id（记为 $CID），status=DRAFT

# 2. 添加可选课程（假设 course.id=1 已存在）
curl -X POST http://localhost:8080/api/selection/campaigns/$CID/courses \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"courseId":1,"capacity":60}'
# 期望：200

# 3. 开启活动
curl -X POST http://localhost:8080/api/selection/campaigns/$CID/open \
  -H "Authorization: Bearer $TOKEN"
# 期望：200
```

- [ ] **Step 5: 端到端手动测试 - 学生选课**

用学生账号登录（token 记为 `$STU_TOKEN`）：

```bash
# 1. 查询当前活动
curl http://localhost:8080/api/selection/campaigns/current -H "Authorization: Bearer $STU_TOKEN"
# 期望：200，返回 $CID 对应活动

# 2. 查询可选课程
curl http://localhost:8080/api/selection/campaigns/$CID/courses -H "Authorization: Bearer $STU_TOKEN"
# 期望：200，courseId=1，capacity=60，selectedCount=0，remaining=60，selectedByMe=false

# 3. 选课
curl -X POST http://localhost:8080/api/selection/records \
  -H "Authorization: Bearer $STU_TOKEN" -H "Content-Type: application/json" \
  -d "{\"campaignId\":$CID,\"courseId\":1}"
# 期望：200，返回 record id（记为 $RID），status=SELECTED

# 4. 查询已选
curl "http://localhost:8080/api/selection/records/my?campaignId=$CID" -H "Authorization: Bearer $STU_TOKEN"
# 期望：200，列表含 $RID

# 5. 退选
curl -X DELETE http://localhost:8080/api/selection/records/$RID -H "Authorization: Bearer $STU_TOKEN"
# 期望：200

# 6. 再次选课（验证退选后可重选）
curl -X POST http://localhost:8080/api/selection/records \
  -H "Authorization: Bearer $STU_TOKEN" -H "Content-Type: application/json" \
  -d "{\"campaignId\":$CID,\"courseId\":1}"
# 期望：200
```

- [ ] **Step 6: 端到端手动测试 - 关闭并分班**

```bash
# 1. 关闭活动
curl -X POST http://localhost:8080/api/selection/campaigns/$CID/close -H "Authorization: Bearer $TOKEN"
# 期望：200

# 2. 分班
curl -X POST http://localhost:8080/api/selection/campaigns/$CID/finalize -H "Authorization: Bearer $TOKEN"
# 期望：200

# 3. 查询分班结果
curl http://localhost:8080/api/selection/campaigns/$CID/classes -H "Authorization: Bearer $TOKEN"
# 期望：200，返回含 classNo=1 的选课班，members 含选课学生列表

# 4. 验证 Redis 计数器
redis-cli GET "selection:count:$CID:1"
# 期望：与 selection_record 中 SELECTED 记录数一致
```

- [ ] **Step 7: 提交最终状态**

如果前序步骤有未提交的改动：

```bash
git status
git -c user.name="寒霜" -c user.email="gangziwo620@163.com" commit -am "feat(selection): 选课功能端到端验证通过"
```

---

## 完成标准

- [ ] 5 张选课表已在数据库创建
- [ ] `./mvnw clean package -DskipTests` BUILD SUCCESS
- [ ] `SelectionClassServiceImplTest` 5 个测试 PASS
- [ ] 应用正常启动，无 Bean 注入失败
- [ ] 端到端手动测试流程（创建活动→添加课程→开启→学生选课/退课→关闭→分班→查询分班结果）全部通过
- [ ] Redis 计数器与 DB 记录数一致
