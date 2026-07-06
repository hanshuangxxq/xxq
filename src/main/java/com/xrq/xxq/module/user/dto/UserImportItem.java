package com.xrq.xxq.module.user.dto;

import lombok.Data;

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
