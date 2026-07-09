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
