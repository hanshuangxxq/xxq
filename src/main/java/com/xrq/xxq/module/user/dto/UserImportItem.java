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
    private String className;
    private String gender;
    private String department;
}
