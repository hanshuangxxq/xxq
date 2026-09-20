package com.xrq.xxq.module.user.dto;

import lombok.Data;

/**
 * 用户导入具体接口类
 * 后续方便存入数据库
 */
@Data
public class UserImportItem {
    private String username;
    private String password;
    private String userType;
    private String identifier;
    private String className;   // 模板第 5 列「年级」的值（历史命名，实为年级名，非班级名）
    private String gender;
    private String department;  // 模板第 7 列「班级/院系」：学生填班级名、教师填院系名
}
