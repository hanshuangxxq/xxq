# xxq API 文档

## 1. 概述

### 1.1 统一响应格式

所有接口返回统一的 `Result<T>` 结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| code | 含义 |
|------|------|
| 200  | 成功 |
| 400  | 请求参数错误 |
| 401  | 未授权（未登录 / token 无效 / token 过期 / 已注销） |
| 404  | 资源不存在 |
| 409  | 冲突（重复注册等） |
| 500  | 服务端错误 |

### 1.2 认证机制

除登录、注册、刷新 token 外，所有接口都需要在请求头中携带 JWT access token：

```
Authorization: Bearer <accessToken>
```

accessToken 由登录接口返回，默认有效期 30 分钟。过期后调用刷新接口获取新 token。

**拦截器路由规则：**

| 路径 | 需要认证 |
|------|----------|
| `POST /api/login` | 否 |
| `POST /api/register` | 否 |
| `POST /api/login/refresh` | 否 |
| 其他 `/api/**` | 是 |

认证失败时返回 401：

| message | 原因 |
|---------|------|
| 请先登录 | 未携带 Authorization 头 |
| token已过期，请刷新 | accessToken 过期 |
| token无效 | token 签名校验失败 |
| token已注销 | 已调用登出接口，session 已删除 |

认证成功后，JWT 中的信息会注入到 request attribute：

| attribute | 来源 |
|-----------|------|
| userId | JWT subject |
| userType | JWT claim |
| role | JWT claim |
| tokenId | JWT claim（指向 Redis 中的 session） |

---

## 2. 接口总览

| 方法 | 路径 | 说明 | 认证 | 章节 |
|------|------|------|------|------|
| POST | `/api/register` | 账号注册 | 否 | 3.1 |
| POST | `/api/login` | 登录（账号 / 微信 / QQ / 支付宝） | 否 | 3.2 |
| POST | `/api/login/refresh` | 刷新 accessToken | 否 | 3.3 |
| POST | `/api/login/logout` | 登出 | 是 | 3.4 |
| GET | `/api/user/profile` | 查询个人信息 | 是 | 4.1 |
| PUT | `/api/user/profile` | 修改个人信息 | 是 | 4.2 |
| POST | `/api/user/avatar/upload` | 上传头像 | 是 | 4.3 |
| GET | `/api/avatar/{filename}` | 获取头像图片 | 是 | 4.4 |
| GET | `/api/courses` | 查询全部课程 | 是 | 5 |
| POST | `/api/courses` | 新增课程 | 是 | 5 |
| PUT | `/api/courses/{id}` | 修改课程 | 是 | 5 |
| DELETE | `/api/courses/{id}` | 删除课程 | 是 | 5 |
| GET | `/api/class-names` | 查询全部班级 | 是 | 5 |
| POST | `/api/class-names` | 新增班级 | 是 | 5 |
| PUT | `/api/class-names/{id}` | 修改班级 | 是 | 5 |
| DELETE | `/api/class-names/{id}` | 删除班级 | 是 | 5 |
| GET | `/api/locals` | 查询全部上课地点 | 是 | 5 |
| POST | `/api/locals` | 新增上课地点 | 是 | 5 |
| PUT | `/api/locals/{id}` | 修改上课地点 | 是 | 5 |
| DELETE | `/api/locals/{id}` | 删除上课地点 | 是 | 5 |
| GET | `/api/teach-info` | 查询教学信息列表（脱敏） | 是 | 5.1 |
| GET | `/api/teach-info/{id}` | 查询单条教学信息（脱敏） | 是 | 5.2 |
| GET | `/api/teach-info/class-courses` | 查询本班课程速览 | 是 | 5.3 |
| POST | `/api/teach-info` | 新增授课安排 | 是 | 5.4 |
| PUT | `/api/teach-info/{id}` | 修改授课安排 | 是 | 5.4 |
| DELETE | `/api/teach-info/{id}` | 删除授课安排 | 是 | 5.4 |
| POST | `/api/teach-info/draft` | 批量提交授课草稿到缓存 | 是 | 5.8 |
| GET | `/api/teach-info/draft` | 查看缓存中的草稿（按角色过滤） | 是 | 5.8 |
| GET | `/api/teach-info/draft/classes` | 查看草稿班级汇总（按角色过滤） | 是 | 5.8 |
| DELETE | `/api/teach-info/draft` | 清空全部草稿 | 是 | 5.8 |
| DELETE | `/api/teach-info/draft/{className}` | 按班级移除草稿 | 是 | 5.8 |
| GET | `/api/time` | 查询全部时间段 | 是 | 6.1 |
| GET | `/api/time/{id}` | 查询单个时间段 | 是 | 6.2 |
| POST | `/api/time` | 新增时间段 | 是 | 6.3 |
| PUT | `/api/time/{id}` | 修改时间段 | 是 | 6.3 |
| DELETE | `/api/time/{id}` | 删除时间段 | 是 | 6.3 |
| GET | `/api/time-restrictions` | 查询全部时段限制 | 是 | 7.2 |
| GET | `/api/time-restrictions/{id}` | 查询单条时段限制 | 是 | 7.3 |
| POST | `/api/time-restrictions` | 新增时段限制 | 是 | 7.4 |
| PUT | `/api/time-restrictions/{id}` | 修改时段限制 | 是 | 7.5 |
| DELETE | `/api/time-restrictions/{id}` | 删除时段限制 | 是 | 7.6 |
| POST | `/api/admin/batch-import` | 批量导入学生和教师 | 是 | 4.5 |
| GET | `/api/majors` | 查询全部专业 | 是 | 4.7 |
| POST | `/api/majors` | 新增专业 | 是 | 4.7 |
| PUT | `/api/majors/{id}` | 修改专业 | 是 | 4.7 |
| DELETE | `/api/majors/{id}` | 删除专业 | 是 | 4.7 |
| GET | `/api/students` | 查询学生列表（支持筛选） | 是 | 4.6 |
| PUT | `/api/students/{id}` | 修改学生信息 | 是 | 4.6 |
| POST | `/api/scheduling/solve` | 触发自动排课 | 是 | 8.1 |
| GET | `/api/scheduling/solution/{scheduleId}` | 查询排课方案 | 是 | 8.2 |
| POST | `/api/scheduling/stop/{scheduleId}` | 停止排课 | 是 | 8.3 |

---

## 3. 认证模块

### 3.1 账号注册

```
POST /api/register
Content-Type: application/json
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| account | String | 是 | 登录账号（用户名） |
| password | String | 是 | 明文密码 |
| userType | String | 是 | `teacher` / `student` / `dean` |
| identifier | String | 否 | 工号 / 学号 / 职工号 |

**请求示例**

```json
{
  "account": "zhangsan",
  "password": "123456",
  "userType": "student",
  "identifier": "2024001"
}
```

**响应示例（成功）**

```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

**错误场景**

| message |
|---------|
| 账号已存在 |
| 不支持的用户类型: xxx |

### 3.2 登录

```
POST /api/login
Content-Type: application/json
```

通过 `type` 字段区分登录渠道，`data` 中携带各渠道所需参数。

#### 3.2.1 账号密码登录 (type=account)

按用户名查找，支持跨表查询（用户名 → 工号 → 学号 → 职工号）。密码使用 PBKDF2 验证。

```json
{
  "type": "account",
  "data": {
    "account": "zhangsan",
    "password": "123456"
  }
}
```

#### 3.2.2 第三方登录 (type=wechat / qq / alipay)

通过第三方 openid / userid 查找绑定关系，要求用户已提前在对应第三方表中绑定。

```json
{
  "type": "wechat",
  "data": {
    "code": "wx_openid_xxx"
  }
}
```

| type | data 字段 | 对应表 | 查询字段 |
|------|-----------|--------|----------|
| wechat | code | wx_user | wx_openid |
| qq | code | qq_user | qq_openid |
| alipay | code | alipay_user | alipay_userid |

#### 3.2.3 登录响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "userType": "student",
    "name": "zhangsan",
    "account": "zhangsan",
    "avatar": null,
    "role": null,
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "a1b2c3d4e5f6...",
    "loginTime": "2026-06-26T10:30:00",
    "lastLoginTime": "2026-06-25T15:20:00"
  }
}
```

**UserSession 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户 ID |
| userType | String | 用户类型 |
| name | String | 用户名 |
| account | String | 登录时使用的账号 |
| avatar | String | 头像文件名 |
| role | String | 角色 |
| accessToken | String | JWT 访问令牌（30 分钟有效，后续请求放入 Authorization 头） |
| refreshToken | String | 刷新令牌（7 天有效，值等于 tokenId） |
| loginTime | LocalDateTime | 本次登录时间 |
| lastLoginTime | LocalDateTime | 上一次登录的时间 |

> **关于 lastLoginTime**：登录时，服务端先将数据库中保存的旧时间写入 session（Redis），再将当前时间更新到数据库。查询个人信息时，优先从 session 中取 `lastLoginTime`（即上一次登录时间），session 不存在时才用数据库中的值。

**错误场景**

| message |
|---------|
| 账号不存在 |
| 密码错误 |
| 登录失败（第三方未绑定） |

### 3.3 刷新令牌

accessToken 过期后，用 refreshToken 获取新的 accessToken。

```
POST /api/login/refresh?refreshToken=<refreshToken>
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| refreshToken | String | 是 | 登录时返回的 refreshToken |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "accessToken": "eyJhbGciOi...（新 token）",
    "refreshToken": "a1b2c3d4e5f6..."
  }
}
```

**错误场景**

| message |
|---------|
| refreshToken 无效或已过期 |

### 3.4 登出

清除 Redis 中的 session，accessToken 随之失效。

```
POST /api/login/logout
Authorization: Bearer <accessToken>
```

> 无需传参，tokenId 由拦截器从 JWT 中解析后自动注入。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

---

## 4. 用户模块

### 4.1 查询个人信息

```
GET /api/user/profile?userId=<userId>&tokenId=<tokenId>
Authorization: Bearer <accessToken>
```

**请求参数（Query）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户 ID |
| tokenId | String | 否 | 会话令牌（传入时用 session 中的 lastLoginTime；不传则用数据库值） |

**调用流程**

1. 从数据库查询用户基础信息
2. 如果传入了 tokenId，从 Redis 获取 session，用 session 中的 `lastLoginTime` 替换数据库查出的值
3. 根据 `userType` 查询子类型表（student / teacher / dean），填充特有字段

**响应示例（student）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "name": "zhangsan",
    "email": "zhangsan@example.com",
    "phone": "13800138000",
    "gender": null,
    "avatar": null,
    "description": null,
    "role": null,
    "userType": "student",
    "lastLoginTime": "2026-06-25T15:20:00",
    "createTime": "2026-06-20T08:00:00",
    "status": 1,
    "identifier": "2024001",
    "grade": "二年级",
    "major": "计算机科学",
    "className": {
      "id": 1,
      "className": "计科2401",
      "college": "计算机学院"
    },
    "enrollmentYear": 2024,
    "title": null,
    "department": null,
    "position": null
  }
}
```

**通用字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户 ID |
| name | String | 用户名 |
| email | String | 邮箱 |
| phone | String | 手机号 |
| gender | GenderEnum | 性别 |
| avatar | String | 头像文件名（通过 `/api/avatar/{filename}` 获取图片） |
| description | String | 个人简介 |
| role | String | 角色 |
| userType | String | `teacher` / `student` / `dean` |
| lastLoginTime | LocalDateTime | 上次登录时间 |
| createTime | LocalDateTime | 注册时间 |
| status | Integer | 状态（1 = 正常） |

**各子类型特有字段**

| 字段 | student | teacher | dean | 说明 |
|------|:------:|:------:|:----:|------|
| identifier | 学号 | 工号 | 职工号 | 唯一标识 |
| grade | ✓ | | | 年级 |
| major | ✓ | | | 专业 |
| className | ✓ | | | 班级信息对象（id, className, college） |
| enrollmentYear | ✓ | | | 入学年份 |
| title | | ✓ | | 职称 |
| department | | ✓ | ✓ | 所属部门 |
| position | | | ✓ | 职位 |

### 4.2 修改个人信息

支持部分更新，未传字段保持原值。

```
PUT /api/user/profile?userId=<userId>
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**请求参数（Query）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户 ID |

**请求体（Body）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | String | 否 | 邮箱 |
| phone | String | 否 | 手机号 |
| gender | GenderEnum | 否 | 性别 |
| avatar | String | 否 | 头像文件名 |
| description | String | 否 | 个人简介 |

**请求示例**

```json
{
  "email": "new_email@example.com",
  "phone": "13900139000",
  "description": "这是我的个人简介"
}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

> 不可修改字段：`name`、`password`、`userType`、`role`、`status`。

### 4.3 头像上传

```
POST /api/user/avatar/upload?userId=<userId>
Authorization: Bearer <accessToken>
Content-Type: multipart/form-data
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户 ID（Query） |
| file | File | 是 | 图片文件（multipart），不超过 5MB |

**允许格式**：png、jpg、jpeg、gif、webp、svg

**请求示例（curl）**

```bash
curl -X POST "http://localhost:8080/api/user/avatar/upload?userId=1" \
  -H "Authorization: Bearer <accessToken>" \
  -F "file=@avatar.png"
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": "a1b2c3d4e5f6.png"
}
```

> 上传成功后自动将文件名写入 `user.avatar` 字段，无需再调用修改接口。

### 4.4 获取头像图片

```
GET /api/avatar/{filename}
Authorization: Bearer <accessToken>
```

直接返回图片二进制流，Content-Type 为对应 MIME 类型。

**前端使用**

```html
<img src="/api/avatar/a1b2c3d4e5f6.png" alt="头像" />
```

```js
const resp = await fetch('/api/avatar/' + profile.avatar, {
  headers: { 'Authorization': `Bearer ${accessToken}` }
});
const blob = await resp.blob();
img.src = URL.createObjectURL(blob);
```

### 4.5 批量导入学生和教师

仅教务管理员可操作。一次性批量导入学生和教师用户，自动将编号、班级、院系等字段写入对应的子类型表。

```
POST /api/admin/batch-import
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**权限要求**：仅 `academic_admin`（教务管理员）可调用，其他角色返回 403。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| users | Array | 是 | 用户数据列表，每项字段见下方 |

**users 数组中每项的字段**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 用户名 |
| password | String | 是 | 明文密码（服务端使用 PBKDF2 哈希存储） |
| userType | String | 是 | 用户类型，仅允许 `student` 或 `teacher`，其他类型拒绝 |
| identifier | String | 否 | 编号（学生→学号 studentNo，教师→教师编号 teacherNo） |
| className | String | 否 | 班级（仅对学生写入 grade 字段） |
| gender | String | 否 | 性别（`男` / `女` / `未知`，默认 `未知`） |
| department | String | 否 | 院系（学生→专业 major，教师→所属部门 department） |

**请求示例**

```json
{
  "users": [
    {
      "username": "张三",
      "password": "123456",
      "userType": "student",
      "identifier": "2024001",
      "className": "计算机科学1班",
      "gender": "男",
      "department": "计算机科学与技术"
    },
    {
      "username": "李四",
      "password": "123456",
      "userType": "teacher",
      "identifier": "T001",
      "department": "计算机学院"
    }
  ]
}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 2,
    "successCount": 2,
    "failCount": 0,
    "details": [
      { "index": 1, "username": "张三", "success": true, "message": "导入成功" },
      { "index": 2, "username": "李四", "success": true, "message": "导入成功" }
    ]
  }
}
```

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| total | Integer | 本次导入的总条数 |
| successCount | Integer | 成功导入的条数 |
| failCount | Integer | 导入失败的条数 |
| details | Array | 逐条导入结果 |

**details 中每项字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| index | Integer | 序号（从 1 开始） |
| username | String | 用户名 |
| success | Boolean | 是否导入成功 |
| message | String | 成功时为 `导入成功`，失败时为具体错误原因 |

**数据写入规则**

| userType | user 表 | 子类型表 | 编号字段 | 班级字段 | 院系字段 |
|----------|---------|----------|----------|----------|----------|
| student | ✓ | student | studentNo | grade | major |
| teacher | ✓ | teacher | teacherNo | — | department |

> 每条记录在事务中独立写入，单条失败不影响其他记录。密码使用 `EncryptUtils.hashWithPbkdf2()` 进行 PBKDF2WithHmacSHA256 哈希（100,000 次迭代，256 bit 密钥）。

**错误场景**

| HTTP 状态码 | message |
|-------------|---------|
| 403 | 仅教务管理员可执行此操作 |
| 400 | 导入数据不能为空 |

**逐条错误示例**（部分失败时的响应）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 3,
    "successCount": 2,
    "failCount": 1,
    "details": [
      { "index": 1, "username": "张三", "success": true, "message": "导入成功" },
      { "index": 2, "username": "李四", "success": true, "message": "导入成功" },
      { "index": 3, "username": "admin", "success": false, "message": "用户类型只允许 student 或 teacher，收到: academic_admin" }
    ]
  }
}
```

### 4.6 学生管理（教务管理员）

仅教务管理员（`academic_admin`）可操作。用于查询和修改全校学生的学号、班级、专业、入学年份等信息。

> **前端对接提示**：
> - 班级下拉列表 → `GET /api/class-names`（见 [5.6 班级 CRUD](#56-班级-crud)），返回 `[{id, className, college}, ...]`
> - 专业下拉列表 → `GET /api/majors`（见 [4.7 专业管理](#47-专业管理)），返回 `[{id, majorName}, ...]`
> - 展示和修改时均传名称字符串即可，后端自动解析为内部 ID

#### 4.6.1 查询学生列表

```
GET /api/students?grade=<grade>&className=<className>&major=<major>&unassigned=<unassigned>&name=<name>
Authorization: Bearer <accessToken>
```

**权限**：仅 `academic_admin`，其他角色返回 403。

**请求参数（Query，均为可选）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| grade | String | 否 | 按班级名称筛选（精确匹配，对应 student.grade 字段） |
| className | String | 否 | 按班级筛选（精确匹配，通过 class_name 表解析为 classId） |
| major | String | 否 | 按专业筛选（精确匹配） |
| unassigned | Boolean | 否 | `true` 时仅返回未分班学生（classId 为空） |
| name | String | 否 | 按姓名模糊查询（LIKE 匹配 user.name） |

> 所有参数可任意组合；不带任何参数时返回全部学生。`name` 模糊查询与其他条件取交集。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "studentId": 1,
      "studentNo": "2024001",
      "grade": "计科2401",
      "majorName": "计算机科学与技术",
      "className": "计科2401",
      "enrollmentYear": 2024,
      "userId": 10,
      "name": "张三",
      "email": "zhangsan@example.com",
      "phone": "13800138000",
      "createTime": "2026-06-20T08:00:00"
    },
    {
      "studentId": 2,
      "studentNo": "2024002",
      "grade": "计科2401",
      "majorName": "计算机科学与技术",
      "className": "计科2401",
      "enrollmentYear": 2024,
      "userId": 11,
      "name": "李四",
      "email": null,
      "phone": null,
      "createTime": "2026-06-20T08:00:00"
    }
  ]
}
```

> 出于隐私保护，以下字段不返回：`gender`（性别）、`avatar`（头像）、`description`（描述）、`role`（权限）、`lastLoginTime`（上次登录时间）、`status`（状态）、`password`（密码）。

**StudentDto 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| studentId | Long | 学生记录主键（student 表 id），修改时用作路径参数 |
| studentNo | String | 学号 |
| grade | String | 班级名称（student 表冗余字段） |
| majorName | String | 专业名称（从 major 表关联，未设置专业时为 null） |
| className | String | 班级名称（从 class_name 表关联，未分班时为 null） |
| enrollmentYear | Integer | 入学年份 |
| userId | Long | 关联的 user 表 ID |
| name | String | 用户姓名 |
| email | String | 邮箱 |
| phone | String | 手机号 |
| createTime | LocalDateTime | 注册时间 |

**调用示例**

```bash
# 查询全部学生
curl -X GET "http://localhost:8080/api/students" \
  -H "Authorization: Bearer <accessToken>"

# 按班级名称筛选（student.grade 字段）
curl -X GET "http://localhost:8080/api/students?grade=计科2401" \
  -H "Authorization: Bearer <accessToken>"

# 按班级筛选（通过 class_name 表解析）
curl -X GET "http://localhost:8080/api/students?className=计科2401" \
  -H "Authorization: Bearer <accessToken>"

# 按专业筛选
curl -X GET "http://localhost:8080/api/students?major=计算机科学与技术" \
  -H "Authorization: Bearer <accessToken>"

# 按姓名模糊查询
curl -X GET "http://localhost:8080/api/students?name=张" \
  -H "Authorization: Bearer <accessToken>"

# 查询未分班学生
curl -X GET "http://localhost:8080/api/students?unassigned=true" \
  -H "Authorization: Bearer <accessToken>"

# 组合筛选：软件工程专业中未分班的学生
curl -X GET "http://localhost:8080/api/students?major=软件工程&unassigned=true" \
  -H "Authorization: Bearer <accessToken>"
```

**错误场景**

| HTTP 状态码 | message |
|-------------|---------|
| 403 | 仅教务管理员可操作学生数据 |

#### 4.6.2 修改学生信息

支持部分更新，未传字段保持原值。

```
PUT /api/students/{id}
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**权限**：仅 `academic_admin`，其他角色返回 403。

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 学生记录主键（student 表 id，非 userId） |

**请求体（Body）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| studentNo | String | 否 | 学号 |
| className | String | 否 | 班级名称（通过 `GET /api/class-names` 获取下拉列表后选择） |
| majorName | String | 否 | 专业名称（通过 `GET /api/majors` 获取下拉列表后选择） |
| enrollmentYear | Integer | 否 | 入学年份 |

> `grade` 和 `className` 均由后端根据 `className` 对应的 `class_name` 记录自动维护。后端将 `majorName` 通过 `major` 表解析为 `majorId` 后写入。

**请求示例**

```json
{
  "studentNo": "2024001",
  "className": "计科2401",
  "majorName": "软件工程",
  "enrollmentYear": 2024
}
```

```json
{
  "majorName": "数据科学与大数据技术"
}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

**错误场景**

| HTTP 状态码 | message |
|-------------|---------|
| 403 | 仅教务管理员可操作学生数据 |
| 404 | 学生不存在 |
| 404 | 班级不存在: xxx |

---

### 4.7 专业管理

专业表（`major`）与学生信息解耦，通过 `majorName` 关联。仅教务管理员可增删改，所有登录用户可查看。

```
GET    /api/majors        # 查询全部专业
POST   /api/majors        # 新增专业（仅 academic_admin）
PUT    /api/majors/{id}   # 修改专业（仅 academic_admin）
DELETE /api/majors/{id}   # 删除专业（仅 academic_admin）
```

**Major 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 专业 ID（自动生成） |
| majorName | String | 专业名称 |

**响应示例（GET）**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "id": 1, "majorName": "计算机科学与技术" },
    { "id": 2, "majorName": "软件工程" },
    { "id": 3, "majorName": "数据科学与大数据技术" }
  ]
}
```

**新增/修改请求体**

```json
{
  "majorName": "人工智能"
}
```

---

### 5.1 查询教学信息列表

```
GET /api/teach-info?teacherId=<teacherId>&courseId=<courseId>
Authorization: Bearer <accessToken>
```

#### 5.1.1 数据范围（按角色自动限定）

| 角色 | 可见范围 | 过滤逻辑 |
|------|---------|---------|
| student | 本班级 | `userId → Student.classId → ClassName.className` |
| teacher | 本人授课 | `userId → Teacher.id → TeachInfo.teacherId` |
| department | 本院系 | `userId → Department.departmentName → ClassName.college` 匹配 → className 列表 |
| academic_admin | 全校 | 无过滤 |

#### 5.1.2 请求参数（Query，均为可选）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| teacherId | Long | 否 | 在角色范围内进一步按教师 ID 筛选 |
| courseId | Long | 否 | 在角色范围内进一步按课程 ID 筛选 |

> 不传参数时返回角色范围内全部教学信息。teacherId 和 courseId 可叠加，均与角色范围取交集。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "courseName": "高等数学",
      "credit": 4,
      "courseHour": 64,
      "courseType": "必修",
      "teacherName": "李老师",
      "department": "数学系",
      "className": "计科2401",
      "college": "计算机学院",
      "dayOfWeek": 1,
      "week": 16,
      "timeId": 1,
      "building": "教学楼A",
      "classroom": "301"
    }
  ]
}
```

> **脱敏说明**：出于安全性考虑，以下字段不会返回给前端：课程/教师/地点的内部 ID、课程编号（courseCode）、教师工号（teacherNo）、教师职称（title）。时间段信息（startPeriod、endPeriod）不直接返回，前端通过 `timeId` 调用 `/api/time/{id}` 接口获取具体时间。

**CourseDto 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| courseName | String | 课程名称 |
| credit | Integer | 学分 |
| courseHour | Integer | 课程学时 |
| courseType | String | 课程类型（必修 / 选修 / 公选 / 实践） |
| teacherName | String | 教师姓名（从 user 表关联） |
| department | String | 所属部门 |
| className | String | 班级名称 |
| college | String | 学院（从 class_name 表关联） |
| dayOfWeek | Integer | 星期几（1=周一 ~ 7=周日） |
| week | Integer | 持续周数 |
| timeId | Long | 时间段 ID，调用 `/api/time/{id}` 获取具体起止时间 |
| building | String | 教学楼 |
| classroom | String | 教室 |

### 5.2 查询单条教学信息

```
GET /api/teach-info/{id}
Authorization: Bearer <accessToken>
```

> 同样受角色数据范围限制——请求不属于自己范围的教学信息 ID 会返回 404。

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 教学信息 ID |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "courseName": "高等数学",
    "credit": 4,
    "courseHour": 64,
    "courseType": "必修",
    "teacherName": "李老师",
    "department": "数学系",
    "className": "计科2401",
    "college": "计算机学院",
    "dayOfWeek": 1,
    "week": 16,
    "timeId": 1,
    "building": "教学楼A",
    "classroom": "301"
  }
}
```

> 字段说明同上 [CourseDto 字段说明](#51-查询教学信息列表)。

**错误场景**

| message |
|---------|
| 教学信息不存在 |

### 5.3 查询本班课程速览

```
GET /api/teach-info/class-courses
Authorization: Bearer <accessToken>
```

根据当前登录用户自动定位所在班级，返回该班级全部课程的精简信息。返回 `timeId`，前端需通过 `/api/time/{id}` 获取具体时间段。包含星期与周数便于按周自动生成课表视图。

> 无需传参，userId 由拦截器从 JWT 中解析后自动注入。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "courseName": "高等数学",
      "teacherName": "李老师",
      "dayOfWeek": 1,
      "week": 16,
      "timeId": 1,
      "building": "教学楼A",
      "classroom": "301"
    },
    {
      "courseName": "大学物理",
      "teacherName": "王老师",
      "dayOfWeek": 3,
      "week": 16,
      "timeId": 2,
      "building": "实验楼B",
      "classroom": "205"
    }
  ]
}
```

**ClassCourseDto 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| courseName | String | 课程名称 |
| teacherName | String | 教师姓名 |
| dayOfWeek | Integer | 星期几（1=周一 ~ 7=周日） |
| week | Integer | 持续周数 |
| timeId | Long | 时间段 ID，调用 `/api/time/{id}` 获取具体起止时间 |
| building | String | 教学楼 |
| classroom | String | 教室 |

### 5.4 新增 / 修改 / 删除授课安排

```
POST   /api/teach-info          # 新增
PUT    /api/teach-info/{id}     # 修改
DELETE /api/teach-info/{id}     # 删除
Authorization: Bearer <accessToken>
```

**请求体（POST/PUT）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| courseId | Long | 是 | 课程 ID |
| teacherId | Long | 是 | 教师 ID |
| className | String | 是 | 班级名称。合班时逗号分隔，如 `"计科2201,计科2101"` |
| timeId | Long | 否 | 时间段 ID（排课前可为空） |
| localId | Long | 否 | 上课地点 ID（排课前可为空） |
| dayOfWeek | Integer | 否 | 星期几（排课前可为空） |
| week | Integer | 否 | 持续周数 |

```json
{
  "courseId": 3,
  "teacherId": 5,
  "className": "计科2201,计科2101",
  "timeId": null,
  "localId": null,
  "dayOfWeek": null,
  "week": 16
}
```

> 排课求解后，`timeId`、`localId`、`dayOfWeek` 由求解器自动写回。

---

### 5.5 课程 CRUD

```
GET    /api/courses             # 查询全部
POST   /api/courses             # 新增
PUT    /api/courses/{id}        # 修改
DELETE /api/courses/{id}        # 删除
Authorization: Bearer <accessToken>
```

**Course 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 课程 ID（自动生成） |
| courseName | String | 课程名称 |
| courseCode | String | 课程代码/编号 |
| credit | Integer | 学分 |
| description | String | 课程描述 |
| courseHour | Integer | 课程学时 |
| courseType | Integer | 1=必修 2=选修 3=公选 4=实践 |

### 5.6 班级 CRUD

```
GET    /api/class-names         # 查询全部
POST   /api/class-names         # 新增
PUT    /api/class-names/{id}    # 修改
DELETE /api/class-names/{id}    # 删除
Authorization: Bearer <accessToken>
```

**ClassName 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 班级 ID（自动生成） |
| className | String | 班级名称 |
| college | String | 所属学院 |

### 5.7 上课地点 CRUD

```
GET    /api/locals              # 查询全部
POST   /api/locals              # 新增
PUT    /api/locals/{id}         # 修改
DELETE /api/locals/{id}         # 删除
Authorization: Bearer <accessToken>
```

**Local 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 地点 ID（自动生成） |
| building | String | 教学楼 |
| classRoom | String | 教室名称 |

---

### 5.8 授课草稿缓存

排课采用**草稿缓存 → 批量入库 → 自动排课**的工作流，减少编排阶段的数据库读写压力。

#### 5.8.1 工作流

```
教务逐班配置课程 → POST /draft (写内存缓存，不写库)
                  → POST /draft (追加下一个班)
                  → GET  /draft (查看已配置的草稿)
                  → DELETE /draft/{className} (如需重配某个班)
全部配好后点排课   → POST /api/scheduling/solve
                  → 后端消费缓存，批量入库，启动 Timefold 求解
                  → 结果写回 teach_info 表
```

#### 5.8.2 批量提交授课草稿

```
POST /api/teach-info/draft
Authorization: Bearer <accessToken>
Content-Type: application/json
```

一次提交一个班的多门课程，可多次调用追加不同班级。

**请求体**（数组，每项一条授课记录）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| courseId | Long | 是 | 课程 ID |
| teacherId | Long | 是 | 教师 ID |
| className | String | 是 | 班级名称。合班时逗号分隔，如 `"计科2201,计科2101"` |
| week | Integer | 否 | 持续周数 |

> `timeId`、`localId`、`dayOfWeek` 不需要填，由排课求解器自动分配。

**请求示例**

```json
[
  { "courseId": 1, "teacherId": 3, "className": "计科2201", "week": 16 },
  { "courseId": 2, "teacherId": 5, "className": "计科2201", "week": 16 },
  { "courseId": 3, "teacherId": 7, "className": "计科2201", "week": 16 }
]
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": 3
}
```

> `data` 返回当前缓存中草稿总数。

#### 5.8.3 查看缓存草稿

```
GET /api/teach-info/draft
Authorization: Bearer <accessToken>
```

按角色返回草稿：

| 角色 | 可见范围 |
|------|---------|
| academic_admin（教务管理员） | 全校全部草稿 |
| department（院系管理者） | 仅本院系的草稿 |
| 其他角色 | 空列表 |

每条草稿携带冗余的课程名、教师名和院系名，前端可直接展示，无需再查库。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": null,
      "courseId": 1,
      "courseName": "高等数学",
      "teacherId": 3,
      "teacherName": "李老师",
      "className": "计科2201,计科2101",
      "college": "计算机学院",
      "timeId": null,
      "localId": null,
      "dayOfWeek": null,
      "week": 16
    }
  ]
}
```

**DraftItem 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 排课前为 null（入库后才有 ID） |
| courseId | Long | 课程 ID |
| courseName | String | 课程名称（冗余） |
| teacherId | Long | 教师 ID |
| teacherName | String | 教师姓名（冗余） |
| className | String | 班级名称，合班时逗号分隔 |
| college | String | 所属院系，多班级时去重后逗号分隔 |
| timeId | Long | 时间段 ID（排课前为 null） |
| localId | Long | 上课地点 ID（排课前为 null） |
| dayOfWeek | Integer | 星期几（排课前为 null） |
| week | Integer | 持续周数 |

#### 5.8.4 清空全部草稿

```
DELETE /api/teach-info/draft
Authorization: Bearer <accessToken>
```

#### 5.8.5 按班级移除草稿

```
DELETE /api/teach-info/draft/{className}
Authorization: Bearer <accessToken>
```

用于重新配置某个班级的课程——先移除旧草稿，再重新 POST。

#### 5.8.6 查看草稿班级汇总

```
GET /api/teach-info/draft/classes
Authorization: Bearer <accessToken>
```

按角色返回草稿中涉及的班级汇总（去重）及每个班的课程数，用于前端展示草稿概览。

> 同样受角色数据范围限制：教务管理员看全校，院系管理者仅看本院系。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "classes": ["计科2201", "计科2101"],
    "countByClass": {
      "计科2201,计科2101": 3,
      "计科2201": 2
    },
    "totalDrafts": 5
  }
}
```

**错误场景**

| message |
|---------|
| 没有待排课的授课草稿，请先通过 POST /api/teach-info/draft 提交授课安排 |

> 触发排课前若缓存为空，求解接口会返回 500 错误并提示先提交草稿。

---

## 6. 时间段模块

时间段表（`time`）与教学信息解耦，仅存储起止时间模板，供前端下拉选择。

### 6.1 查询全部时间段

```
GET /api/time
Authorization: Bearer <accessToken>
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "id": 1, "startPeriod": "08:00:00", "endPeriod": "09:40:00" },
    { "id": 2, "startPeriod": "10:00:00", "endPeriod": "11:40:00" },
    { "id": 3, "startPeriod": "14:00:00", "endPeriod": "15:40:00" }
  ]
}
```

### 6.2 查询单个时间段

```
GET /api/time/{id}
Authorization: Bearer <accessToken>
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "startPeriod": "08:00:00",
    "endPeriod": "09:40:00"
  }
}
```

**Time 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 时间段 ID |
| startPeriod | LocalTime | 开始节次 |
| endPeriod | LocalTime | 结束节次 |

**错误场景**

| message |
|---------|
| 时间段不存在 |

> **设计说明**：时间段（`time` 表）与排课信息（`teach_info` 表）解耦。添加课表时的典型流程：① 从 `/api/time` 列表中选择时间段 → ② 选择星期几 → ③ 设置持续周数 → ④ 创建 teach_info 记录。前端展示时，通过 teach_info 返回的 `timeId` 调用 `/api/time/{id}` 获取具体起止时间。

### 6.3 新增 / 修改 / 删除时间段

```
POST   /api/time                # 新增
PUT    /api/time/{id}           # 修改
DELETE /api/time/{id}           # 删除
Authorization: Bearer <accessToken>
```

**请求体（POST/PUT）**

```json
{
  "startPeriod": "08:00:00",
  "endPeriod": "08:45:00"
}
```

---

## 7. 时段限制模块

教务管理员通过时段限制制定基础排课规则，院系管理者在此基础上做最终排课。

### 7.1 限制类型与角色权限

**限制类型**

| 类型 | 含义 | courseId |
|------|------|----------|
| `BLOCKED` | 该时段完全禁止排课（如全校会议、课外活动） | 不需要 |
| `RESERVED` | 该时段预留给特定课程统一上课（如全校政治课） | 必填 |

**角色权限**

| 角色 | 权限 |
|------|------|
| academic_admin | 可增删改查全部时段限制 |
| department | 可查看，不可修改 |
| 其他角色 | 可在排课结果中看到被限制的时段效果 |

### 7.2 查询全部

```
GET /api/time-restrictions
Authorization: Bearer <accessToken>
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "timeId": 1,
      "dayOfWeek": 3,
      "restrictionType": "RESERVED",
      "courseId": 5,
      "reason": "周三第1-2节全校统一政治课"
    },
    {
      "id": 2,
      "timeId": 2,
      "dayOfWeek": 5,
      "restrictionType": "BLOCKED",
      "courseId": null,
      "reason": "周五下午社团活动时间"
    }
  ]
}
```

### 7.3 查询单条

```
GET /api/time-restrictions/{id}
Authorization: Bearer <accessToken>
```

### 7.4 新增

```
POST /api/time-restrictions
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**请求示例（禁止排课）**

```json
{
  "timeId": 3,
  "dayOfWeek": 5,
  "restrictionType": "BLOCKED",
  "reason": "周五下午全校社团活动"
}
```

**请求示例（预留统考）**

```json
{
  "timeId": 1,
  "dayOfWeek": 1,
  "restrictionType": "RESERVED",
  "courseId": 10,
  "reason": "周一第1节全校大学生心理健康教育"
}
```

### 7.5 修改

```
PUT /api/time-restrictions/{id}
Authorization: Bearer <accessToken>
Content-Type: application/json
```

### 7.6 删除

```
DELETE /api/time-restrictions/{id}
Authorization: Bearer <accessToken>
```

---

## 8. 排课模块

基于 Timefold Solver 约束求解引擎，将授课草稿中的课程自动分配到合适的时间和教室。

**完整流程**：前端逐班提交授课草稿到缓存（不写库）→ 全部配好后触发排课 → 后端消费缓存批量入库 → 求解器分配时间段和教室 → 结果写回 `teach_info` 表。

### 8.1 触发排课

```
POST /api/scheduling/solve
Authorization: Bearer <accessToken>
```

排课在后台异步执行，立即返回方案 ID。

**前置条件**：必须先通过 `POST /api/teach-info/draft` 提交授课草稿到缓存，否则返回错误。

**内部流程**：
1. 消费缓存中的全部授课草稿（清空缓存）
2. 草稿批量入库到 `teach_info` 表（获取自增 ID）
3. 从库中读取时间段、教室、课程、教师等基础数据
4. 组装为 Timefold 排课问题，启动异步求解
5. 每次找到更优解时，将分配结果写回 `teach_info` 表

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "scheduleId": 1719820800000
  }
}
```

### 8.2 查询排课方案

```
GET /api/scheduling/solution/{scheduleId}
Authorization: Bearer <accessToken>
```

轮询此接口可获取求解进度和最终结果。

**求解中响应**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1719820800000,
    "solverStatus": "SOLVING",
    "score": "-3hard/0soft",
    "lessonList": [...]
  }
}
```

**求解完成响应**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1719820800000,
    "solverStatus": "FINISHED",
    "score": "0hard/0soft",
    "timeslotList": [...],
    "roomList": [...],
    "studentGroupList": [...],
    "lessonList": [
      {
        "id": 1,
        "courseId": 3,
        "courseName": "高等数学",
        "teacherId": 5,
        "teacherName": "李老师",
        "studentGroups": [
          { "id": 1, "name": "计科2201", "college": "计算机学院", "studentCount": 35 }
        ],
        "timeslot": {
          "id": 11,
          "dayOfWeek": "MONDAY",
          "startTime": "08:00:00",
          "endTime": "08:45:00",
          "reservedCourseId": null
        },
        "room": {
          "id": 2,
          "building": "教学楼A",
          "roomName": "301"
        }
      }
    ]
  }
}
```

**solverStatus 状态说明**

| 状态 | 含义 |
|------|------|
| NOT_SOLVING | 尚未开始或已结束 |
| SOLVING | 求解进行中 |
| FINISHED | 求解已完成 |

**score 格式说明**

使用 `HardSoftScore` 格式：`"硬约束违规数hard/软约束违规数soft"`。
- `"0hard/0soft"` — 完美解，所有硬约束均满足
- `"-3hard/-10soft"` — 3 个硬约束违规（不可接受），10 个软约束违规

### 8.3 停止排课

```
POST /api/scheduling/stop/{scheduleId}
Authorization: Bearer <accessToken>
```

提前终止正在进行的求解，保留当前最优解。

### 8.4 约束说明

| 约束 | 类型 | 说明 |
|------|------|------|
| Room conflict | 硬 | 同一时间同一教室不能有两节课 |
| Teacher conflict | 硬 | 同一时间一位教师不能上两节课 |
| Class conflict | 硬 | 同一时间任一班级不能有两节课（支持合班交集判断） |
| Time restriction | 硬 | 被 RESERVED 的时段仅供对应课程使用 |

### 8.5 排课配置

```yaml
# application-dev.yaml
timefold:
  solver:
    termination:
      spent-limit: 30s   # 求解最长运行时间
```

> **求解终止条件**：到达 `spent-limit` 后自动停止。若在此之前找到 0hard 解也会提前终止。

### 8.6 合班上课

#### 8.6.1 原理

`teach_info` 表的 `class_name` 字段支持**逗号分隔多个班级名称**，表示多个班级的学生共同上这节课。

排课时，求解器会通过 `StudentGroup` 交集检测班级冲突——只要两个课堂共享任意一个班级，就不能安排在同一时间段。

#### 8.6.2 示例

**单班上课**（常规情况）：

```
teach_info.class_name = "计科2201"
```

排课时该课堂仅与计科2201的其他课程产生冲突约束。

**合班上课**（如重修学生混班）：

```
teach_info.class_name = "计科2201,计科2101"
```

排课时该课堂同时与计科2201和计科2101的课程产生冲突约束，确保重修学生不会在同一时间有两门课。

#### 8.6.3 权限角色

| 角色 | 权限 |
|------|------|
| 教务管理员 (academic_admin) | 查看全校草稿；制定时段限制规则；增删改查班级/课程/地点 |
| 院系管理者 (department) | 仅查看本院系草稿；可设置合班；最终决定上课地点 |

---

## 9. 附录

### 9.1 完整调用流程示例

```bash
# 1. 注册
curl -X POST http://localhost:8080/api/register \
  -H "Content-Type: application/json" \
  -d '{"account":"zhangsan","password":"123456","userType":"student","identifier":"2024001"}'

# 2. 登录
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"type":"account","data":{"account":"zhangsan","password":"123456"}}'
# → 返回 accessToken、refreshToken、userId

# 3. 查询个人信息
curl -X GET "http://localhost:8080/api/user/profile?userId=1&tokenId=<refreshToken>" \
  -H "Authorization: Bearer <accessToken>"

# 4. 查询全部时间段（供排课时下拉选择）
curl -X GET "http://localhost:8080/api/time" \
  -H "Authorization: Bearer <accessToken>"

# 5. 根据 timeId 查询具体时间段
curl -X GET "http://localhost:8080/api/time/1" \
  -H "Authorization: Bearer <accessToken>"

# 6. 提交授课草稿（计科2201 的课程，不写库）
curl -X POST http://localhost:8080/api/teach-info/draft \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '[{"courseId":1,"teacherId":3,"className":"计科2201","week":16},{"courseId":2,"teacherId":5,"className":"计科2201","week":16}]'

# 7. 提交授课草稿（计科2202 的课程）
curl -X POST http://localhost:8080/api/teach-info/draft \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '[{"courseId":1,"teacherId":4,"className":"计科2202","week":16}]'

# 8. 查看当前全部草稿
curl -X GET http://localhost:8080/api/teach-info/draft \
  -H "Authorization: Bearer <accessToken>"

# 9. 触发自动排课（消费缓存 → 入库 → 求解）
curl -X POST http://localhost:8080/api/scheduling/solve \
  -H "Authorization: Bearer <accessToken>"
# → 返回 { "data": { "scheduleId": 1719820800000 } }

# 10. 轮询排课方案
curl -X GET http://localhost:8080/api/scheduling/solution/1719820800000 \
  -H "Authorization: Bearer <accessToken>"

# 11. 排课完成后查询排好的课表
curl -X GET "http://localhost:8080/api/teach-info" \
  -H "Authorization: Bearer <accessToken>"

# 12. 查询某教师的所有教学信息
curl -X GET "http://localhost:8080/api/teach-info?teacherId=5" \
  -H "Authorization: Bearer <accessToken>"

# 13. 查询某课程的教学信息
curl -X GET "http://localhost:8080/api/teach-info?courseId=101" \
  -H "Authorization: Bearer <accessToken>"

# 14. 查询全部教学信息
curl -X GET "http://localhost:8080/api/teach-info" \
  -H "Authorization: Bearer <accessToken>"

# 15. 查询单条教学信息详情
curl -X GET "http://localhost:8080/api/teach-info/1" \
  -H "Authorization: Bearer <accessToken>"

# 16. 查询本班课程速览
curl -X GET "http://localhost:8080/api/teach-info/class-courses" \
  -H "Authorization: Bearer <accessToken>"

# 17. accessToken 过期后刷新
curl -X POST "http://localhost:8080/api/login/refresh?refreshToken=<refreshToken>"
# → 返回新的 accessToken

# 18. 登出
curl -X POST http://localhost:8080/api/login/logout \
  -H "Authorization: Bearer <accessToken>"
```

### 9.2 token 有效期配置

```yaml
jwt:
  secret: <Base64 密钥>
  access-token-expiration: 30m    # accessToken 有效期
  refresh-token-expiration: 7d    # refreshToken / session 有效期
```

### 9.3 密码安全

- 算法：**PBKDF2WithHmacSHA256**
- 迭代次数：**100,000**
- 密钥长度：**256 bit**
- 存储格式：`iterations:salt:hash`
- 注册时 `EncryptUtils.hashWithPbkdf2()` 生成密文
- 登录时 `EncryptUtils.verifyPbkdf2()` 做恒定时间比对
