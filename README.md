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
| POST | `/api/password/change` | 修改密码 | 是 | 3.5 |
| GET | `/api/user/profile` | 查询个人信息 | 是 | 4.1 |
| PUT | `/api/user/profile` | 修改个人信息 | 是 | 4.2 |
| POST | `/api/user/avatar/upload` | 上传头像 | 是 | 4.3 |
| GET | `/api/avatar/{filename}` | 获取头像图片 | 是 | 4.4 |
| GET | `/api/teachers` | 查询全部教师 | 是 | 4.8 |
| GET | `/api/courses` | 查询全部课程 | 是 | 5.5 |
| GET | `/api/courses/{id}` | 查询单个课程 | 是 | 5.5 |
| POST | `/api/courses` | 新增课程 | 是 | 5.5 |
| PUT | `/api/courses/{id}` | 修改课程 | 是 | 5.5 |
| DELETE | `/api/courses/{id}` | 删除课程 | 是 | 5.5 |
| GET | `/api/class-names` | 查询全部班级 | 是 | 5.6 |
| GET | `/api/class-names/{id}` | 查询单个班级 | 是 | 5.6 |
| GET | `/api/class-names/department` | 查询本院系班级 | 是 | 5.6 |
| POST | `/api/class-names` | 新增班级 | 是 | 5.6 |
| PUT | `/api/class-names/{id}` | 修改班级 | 是 | 5.6 |
| DELETE | `/api/class-names/{id}` | 删除班级 | 是 | 5.6 |
| GET | `/api/locals` | 查询全部上课地点 | 是 | 5.7 |
| GET | `/api/locals/{id}` | 查询单个上课地点 | 是 | 5.7 |
| POST | `/api/locals` | 新增上课地点 | 是 | 5.7 |
| PUT | `/api/locals/{id}` | 修改上课地点 | 是 | 5.7 |
| DELETE | `/api/locals/{id}` | 删除上课地点 | 是 | 5.7 |
| GET | `/api/teach-info` | 查询教学信息列表（脱敏） | 是 | 5.1 |
| GET | `/api/teach-info/{id}` | 查询单条教学信息（脱敏） | 是 | 5.2 |
| GET | `/api/teach-info/class-courses` | 查询本班课程速览 | 是 | 5.3 |
| GET | `/api/teach-info/week-schedule` | 学生查询指定周次课表（Redis缓存） | 是 | 5.9 |
| POST | `/api/teach-info` | 新增授课安排 | 是 | 5.4 |
| PUT | `/api/teach-info/{id}` | 修改授课安排 | 是 | 5.4 |
| DELETE | `/api/teach-info/{id}` | 删除授课安排 | 是 | 5.4 |
| POST | `/api/teach-info/draft` | 批量提交授课草稿到缓存 | 是 | 5.8 |
| GET | `/api/teach-info/draft` | 查看缓存中的草稿（按角色过滤） | 是 | 5.8 |
| GET | `/api/teach-info/draft/classes` | 查看草稿班级汇总（按角色过滤） | 是 | 5.8 |
| DELETE | `/api/teach-info/draft` | 清空全部草稿 | 是 | 5.8 |
| DELETE | `/api/teach-info/draft/item` | 按唯一键删除单条草稿 | 是 | 5.8 |
| DELETE | `/api/teach-info/draft/{className}` | 按班级移除草稿 | 是 | 5.8 |
| GET | `/api/time` | 查询全部时间段 | 是 | 6.1 |
| GET | `/api/time/{id}` | 查询单个时间段 | 是 | 6.2 |
| GET | `/api/time-restrictions` | 查询全部时段限制 | 是 | 7.2 |
| GET | `/api/time-restrictions/{id}` | 查询单条时段限制 | 是 | 7.3 |
| POST | `/api/time-restrictions` | 新增时段限制 | 是 | 7.4 |
| PUT | `/api/time-restrictions/{id}` | 修改时段限制 | 是 | 7.5 |
| DELETE | `/api/time-restrictions/{id}` | 删除时段限制 | 是 | 7.6 |
| POST | `/api/aAdmin/batch-import` | 批量导入学生和教师 | 是 | 4.5 |
| GET | `/api/majors` | 查询全部专业 | 是 | 4.7 |
| POST | `/api/majors` | 新增专业 | 是 | 4.7 |
| PUT | `/api/majors/{id}` | 修改专业 | 是 | 4.7 |
| DELETE | `/api/majors/{id}` | 删除专业 | 是 | 4.7 |
| GET | `/api/students` | 查询学生列表（支持筛选） | 是 | 4.6 |
| PUT | `/api/students/{id}` | 修改学生信息 | 是 | 4.6 |
| POST | `/api/scheduling/solve` | 触发自动排课 | 是 | 8.1 |
| GET | `/api/scheduling/solution/{scheduleId}` | 查询排课方案 | 是 | 8.2 |
| POST | `/api/scheduling/stop/{scheduleId}` | 停止排课 | 是 | 8.3 |
| GET | `/api/semester` | 查询全部学期 | 是 | 9 |
| GET | `/api/semester/current` | 查询当前学期 | 是 | 9 |
| POST | `/api/semester` | 新增学期 | 是 | 9 |
| PUT | `/api/semester/{id}` | 修改学期 | 是 | 9 |
| DELETE | `/api/semester/{id}` | 删除学期 | 是 | 9 |
| POST | `/api/selection/campaigns` | 新建选课活动 | 是 | 10.2.1 |
| GET | `/api/selection/campaigns` | 查询全部选课活动 | 是 | 10.2.2 |
| GET | `/api/selection/campaigns/{id}` | 查询选课活动详情 | 是 | 10.2.3 |
| PUT | `/api/selection/campaigns/{id}` | 修改选课活动 | 是 | 10.2.4 |
| DELETE | `/api/selection/campaigns/{id}` | 删除选课活动 | 是 | 10.2.5 |
| POST | `/api/selection/campaigns/{id}/open` | 开启选课 | 是 | 10.2.6 |
| POST | `/api/selection/campaigns/{id}/close` | 关闭选课 | 是 | 10.2.7 |
| POST | `/api/selection/campaigns/{id}/finalize` | 分班 | 是 | 10.2.8 |
| POST | `/api/selection/campaigns/{campaignId}/courses` | 添加可选课程 | 是 | 10.3.1 |
| GET | `/api/selection/campaigns/{campaignId}/courses` | 查询可选课程 | 是 | 10.3.2 |
| DELETE | `/api/selection/campaigns/{campaignId}/courses/{courseId}` | 移除可选课程 | 是 | 10.3.3 |
| GET | `/api/selection/campaigns/{campaignId}/classes` | 查询分班结果 | 是 | 10.4 |
| GET | `/api/selection/student/campaigns` | 学生查询可选课活动 | 是 | 10.5.1 |
| GET | `/api/selection/student/campaigns/{campaignId}/courses` | 学生查询可选课程 | 是 | 10.5.2 |
| POST | `/api/selection/student/records` | 选课 | 是 | 10.5.3 |
| DELETE | `/api/selection/student/records/{recordId}` | 退选 | 是 | 10.5.4 |
| GET | `/api/selection/student/records` | 查询我的选课记录 | 是 | 10.5.5 |

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

### 3.5 修改密码

已登录用户修改自己的密码。

```
POST /api/password/change
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**请求体（Body）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| account | String | 是 | 用户账号 |
| oldPassword | String | 是 | 旧密码（明文） |
| newPassword | String | 是 | 新密码（明文，服务端使用 PBKDF2 哈希存储） |

**请求示例**

```json
{
  "account": "zhangsan",
  "oldPassword": "123456",
  "newPassword": "654321"
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

| message |
|---------|
| 账号不存在 |
| 原密码错误 |

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
POST /api/aAdmin/batch-import
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

### 4.8 教师查询

查询全部教师列表，用于排课时下拉选择教师。

```
GET /api/teachers
Authorization: Bearer <accessToken>
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "id": 1, "name": "李老师", "teacherNo": "T001", "title": "教授", "department": "计算机学院" },
    { "id": 2, "name": "王老师", "teacherNo": "T002", "title": "副教授", "department": "数学系" }
  ]
}
```

**TeacherDto 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 教师记录主键（teacher 表 id） |
| name | String | 教师姓名 |
| teacherNo | String | 教师工号 |
| title | String | 职称 |
| department | String | 所属部门 |

---

### 5.1 查询教学信息列表

```
GET /api/teach-info?teacherId=<teacherId>&courseId=<courseId>&week=<week>
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
| week | Integer | 否 | 按教学周筛选（startWeek ≤ week ≤ endWeek） |

> 不传参数时返回角色范围内全部教学信息。所有参数可叠加，均与角色范围取交集。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "mondayDate": "2026-09-07",
    "courses": [
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
        "startWeek": 1,
        "endWeek": 16,
        "timeId": 1,
        "building": "教学楼A",
        "classroom": "301"
      }
    ]
  }
}
```

> **脱敏说明**：出于安全性考虑，以下字段不会返回给前端：课程/教师/地点的内部 ID、课程编号（courseCode）、教师工号（teacherNo）、教师职称（title）。时间段信息（startPeriod、endPeriod）不直接返回，前端通过 `timeId` 调用 `/api/time/{id}` 接口获取具体时间。

**UserCourseDto 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| mondayDate | LocalDate | 当前教学周周一对应的日期，供前端渲染日历视图 |
| courses | List\<CourseDto\> | 教学信息列表，每项为 CourseDto |

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
| startWeek | Integer | 起始教学周 |
| endWeek | Integer | 结束教学周 |
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
    "startWeek": 1,
    "endWeek": 16,
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
      "startWeek": 1,
      "endWeek": 16,
      "timeId": 1,
      "building": "教学楼A",
      "classroom": "301"
    },
    {
      "courseName": "大学物理",
      "teacherName": "王老师",
      "dayOfWeek": 3,
      "startWeek": 1,
      "endWeek": 16,
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
| startWeek | Integer | 起始教学周 |
| endWeek | Integer | 结束教学周 |
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
| startWeek | Integer | 否 | 起始教学周 |
| endWeek | Integer | 否 | 结束教学周 |
| semesterId | Long | 否 | 学期 ID（排课时自动设置为当前学期） |

```json
{
  "courseId": 3,
  "teacherId": 5,
  "className": "计科2201,计科2101",
  "timeId": null,
  "localId": null,
  "dayOfWeek": null,
  "startWeek": 1,
  "endWeek": 16
}
```

> 排课求解后，`timeId`、`localId`、`dayOfWeek` 由求解器自动写回。

---

### 5.5 课程 CRUD

```
GET    /api/courses             # 查询全部
GET    /api/courses/{id}        # 查询单个
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

> GET /api/courses/{id} 查询单个课程，课程不存在时返回 404："课程不存在"。

### 5.6 班级 CRUD

```
GET    /api/class-names              # 查询全部
GET    /api/class-names/{id}         # 查询单个
GET    /api/class-names/department   # 查询本院系班级（仅 department 角色）
POST   /api/class-names              # 新增
PUT    /api/class-names/{id}         # 修改
DELETE /api/class-names/{id}         # 删除
Authorization: Bearer <accessToken>
```

**ClassName 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 班级 ID（自动生成） |
| className | String | 班级名称 |
| college | String | 所属学院 |

> GET /api/class-names/{id} 查询单个班级，班级不存在时返回 404："班级不存在"。院系管理者仅可查看本院系的班级。
>
> GET /api/class-names/department 仅院系管理者（department）可调用，返回本院系的班级列表。其他角色返回 403。

### 5.7 上课地点 CRUD

```
GET    /api/locals              # 查询全部
GET    /api/locals/{id}         # 查询单个
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
| max | Integer | 教室最大容纳人数 |

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
| startWeek | Integer | 否 | 起始教学周 |
| endWeek | Integer | 否 | 结束教学周 |

> `timeId`、`localId`、`dayOfWeek` 不需要填，由排课求解器自动分配。

**请求示例**

```json
[
  { "courseId": 1, "teacherId": 3, "className": "计科2201", "startWeek": 1, "endWeek": 16 },
  { "courseId": 2, "teacherId": 5, "className": "计科2201", "startWeek": 1, "endWeek": 8 },
  { "courseId": 3, "teacherId": 7, "className": "计科2201", "startWeek": 9, "endWeek": 16 }
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
      "startWeek": 1,
      "endWeek": 16,
      "semesterId": null
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
| startWeek | Integer | 起始教学周 |
| endWeek | Integer | 结束教学周 |
| semesterId | Long | 学期 ID（排课时自动设为当前学期） |

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

#### 5.8.6 按唯一键删除单条草稿

```
DELETE /api/teach-info/draft/item?courseId=<courseId>&teacherId=<teacherId>&className=<className>
Authorization: Bearer <accessToken>
```

按 courseId + teacherId + className 三元组精确定位并删除单条草稿记录。

**请求参数（Query，均为必填）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| courseId | Long | 是 | 课程 ID |
| teacherId | Long | 是 | 教师 ID |
| className | String | 是 | 班级名称 |

**权限**：教务管理员可删除任意草稿记录；院系管理者仅可删除本院系范围内的草稿。

**错误场景**

| message |
|---------|
| 无权删除其他院系的草稿 |
| 草稿记录不存在 |

#### 5.8.7 查看草稿班级汇总

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

### 5.9 学生周课表查询（Redis 缓存）

学生查询指定周次的班级课表。首次查询走数据库并写入 Redis 缓存，同班其他学生再查同一周直接命中缓存，无需访问数据库。

```
GET /api/teach-info/week-schedule?week=5
Authorization: Bearer <accessToken>
```

**权限**：仅 `student` 角色。后端自动根据 `userId` 解析学生所属班级。

**请求参数（Query）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| week | Integer | 是 | 教学周编号 |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "weekNumber": 5,
    "scheduleByDay": {
      "周一": [
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
          "startWeek": 1,
          "endWeek": 16,
          "timeId": 1,
          "building": "教学楼A",
          "classroom": "301"
        }
      ],
      "周三": [
        {
          "courseName": "大学物理",
          "credit": 3,
          "courseHour": 48,
          "courseType": "必修",
          "teacherName": "王老师",
          "department": "物理系",
          "className": "计科2401",
          "college": "计算机学院",
          "dayOfWeek": 3,
          "startWeek": 1,
          "endWeek": 16,
          "timeId": 2,
          "building": "实验楼B",
          "classroom": "205"
        }
      ]
    }
  }
}
```

**WeekScheduleDto 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| weekNumber | Integer | 查询的教学周编号 |
| mondayDate | LocalDate | 该周周一对应的日期，用于前端展示日期标注 |
| scheduleByDay | Map<String, List<CourseDto>> | 按 "周一"~"周日" 分组的课表，key 为中文星期名，value 为当天课程列表。仅包含有课的日期 |

**缓存策略**

| 项目 | 说明 |
|------|------|
| Redis Key | `schedule:class:{className}:week:{weekNumber}` |
| TTL | 1 小时 |
| 更新时机 | 排课完成后需清除对应班级的缓存 |
| 缓存内容 | 序列化的 `List<CourseDto>` JSON |

**错误场景**

| HTTP 状态码 | message |
|-------------|---------|
| 403 | 仅学生可查询周课表 |
| 404 | 未找到您的班级信息 |

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

> **设计说明**：时间段（`time` 表）与排课信息（`teach_info` 表）解耦。添加课表时的典型流程：① 从 `/api/time` 列表中选择时间段 → ② 选择星期几 → ③ 设置起止教学周（startWeek/endWeek） → ④ 创建 teach_info 记录。前端展示时，通过 teach_info 返回的 `timeId` 调用 `/api/time/{id}` 获取具体起止时间。

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
POST /api/scheduling/solve?semesterId=<semesterId>
Authorization: Bearer <accessToken>
```

排课在后台异步执行，立即返回方案 ID。

**请求参数（Query）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semesterId | Long | 否 | 学期 ID。不传时使用当前学期（status=CURRENT） |

**前置条件**：
1. 必须先通过 `POST /api/teach-info/draft` 提交授课草稿到缓存，否则返回错误。
2. 目标学期必须存在，所有授课草稿的周次范围必须在学期范围内。

**内部流程**：
1. 校验当前学期存在，草稿的 startWeek/endWeek 在学期范围内
2. 未指定 semesterId 的草稿自动关联到当前学期
3. 消费缓存中的全部授课草稿（清空缓存）
4. 草稿批量入库到 `teach_info` 表（获取自增 ID）
5. 从库中读取时间段、教室、课程、教师等基础数据
6. 组装为 Timefold 排课问题，启动异步求解
7. 每次找到更优解时，将分配结果（timeslot、room、startWeek、endWeek）写回 `teach_info` 表

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
        "startWeek": 1,
        "endWeek": 16,
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
| Room conflict | 硬 | 同学期 + 同一时间 + 同一教室 + 周次重叠 → 不能有两节课 |
| Teacher conflict | 硬 | 同学期 + 同一时间 + 周次重叠 → 一位教师不能上两节课 |
| Class conflict | 硬 | 同学期 + 同一时间 + 周次重叠 → 任一班级不能有两节课（支持合班交集判断） |
| Time restriction | 硬 | 被 RESERVED 的时段仅供对应课程使用 |
| Room capacity | 硬 | 上课学生总人数不能超过教室最大容量 |

> **学期隔离**：不同学期（semesterId 不同）的课程互不冲突，即使时间、教室、班级完全相同。
> **周次重叠判断**：`a.startWeek ≤ b.endWeek && b.startWeek ≤ a.endWeek`。两门课的周次区间无交集时，即使同一时段也互不冲突。

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

## 9. 学期模块

学期定义排课和课表查询的时间边界。所有课程的 startWeek/endWeek 范围必须在学期周次范围内。

### 9.1 日期自动计算规则

前端可自由传入以下三项中的任意两项，后端自动计算第三项：

| 参数 | 字段 | 说明 |
|------|------|------|
| 周数 | startWeek + endWeek | 教学周范围，startWeek 不传时默认为 1 |
| 开始日期 | startDate | 学期第一天 |
| 结束日期 | endDate | 学期最后一天 |

**三种传入组合：**

| 前端传入 | 后端行为 |
|----------|----------|
| 周数 + 开始日期 | 自动算出 `endDate = startDate + (总周数 × 7 - 1) 天` |
| 周数 + 结束日期 | 自动算出 `startDate = endDate - (总周数 × 7 - 1) 天` |
| 开始日期 + 结束日期 | 自动算出 `总周数 = 日期间隔天数 / 7 + 1`，写入 endWeek |

**三项全传时**：校验 `endDate` 是否等于 `startDate + (总周数 × 7 - 1)` 天，不匹配则返回 400 错误提示前端。只传一项或零项时不校验日期一致性。

### 9.2 查询全部学期

```
GET /api/semester
Authorization: Bearer <accessToken>
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "id": 1, "name": "2026-2027学年第一学期", "startWeek": 1, "endWeek": 18, "startDate": "2026-09-01", "endDate": "2027-01-03", "status": "CURRENT" },
    { "id": 2, "name": "2025-2026学年第二学期", "startWeek": 1, "endWeek": 18, "startDate": "2026-02-23", "endDate": "2026-06-28", "status": "HISTORICAL" }
  ]
}
```

### 9.3 查询当前学期

```
GET /api/semester/current
Authorization: Bearer <accessToken>
```

> 排课前必须调用此接口确认当前学期存在。

### 9.4 新增学期

```
POST /api/semester
Authorization: Bearer <accessToken>
Content-Type: application/json
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 学期名称 |
| startWeek | Integer | 否 | 起始周，不传时默认为 1 |
| endWeek | Integer | 否 | 结束周，可通过 开始日期+结束日期 自动推导 |
| startDate | LocalDate | 否 | 学期开始日期，可通过 周数+结束日期 自动推导 |
| endDate | LocalDate | 否 | 学期结束日期，可通过 周数+开始日期 自动推导 |
| status | String | 是 | CURRENT / HISTORICAL / FUTURE |

> 若 status 设为 `CURRENT`，后端自动将其他学期标记为 `HISTORICAL`，确保同时只有一个当前学期。

**请求示例（周数 + 开始日期 → 自动算结束日期）**

```json
{
  "name": "2026-2027学年第一学期",
  "startWeek": 1,
  "endWeek": 18,
  "startDate": "2026-09-01",
  "status": "CURRENT"
}
```
→ 后端自动算出 `endDate = 2027-01-03`

**请求示例（开始日期 + 结束日期 → 自动算周数）**

```json
{
  "name": "2026-2027学年第一学期",
  "startDate": "2026-09-01",
  "endDate": "2027-01-03",
  "status": "CURRENT"
}
```
→ 后端自动算出 `startWeek=1, endWeek=18`
```

### 9.5 修改 / 删除学期

```
PUT    /api/semester/{id}   # 修改
DELETE /api/semester/{id}   # 删除
Authorization: Bearer <accessToken>
```

**Semester 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 学期 ID（自动生成） |
| name | String | 学期名称 |
| startWeek | Integer | 学期起始周（不传默认为 1） |
| endWeek | Integer | 学期结束周 |
| startDate | LocalDate | 学期开始日期 |
| endDate | LocalDate | 学期结束日期 |
| status | String | CURRENT=当前学期, HISTORICAL=历史学期, FUTURE=未来学期 |

---

## 10. 选课模块

学生选课采用"活动驱动"模式：教务管理员创建选课活动 -> 配置可选课程 -> 开放选课 -> 学生抢选 -> 关闭 -> 分班。选课容量通过 Redis + Lua 脚本原子控制，避免超选。

### 10.1 状态流转与角色权限

**活动状态机**

```
DRAFT --open()--> OPEN --close()--> CLOSED --finalize()--> FINALIZED
```

| 状态 | 含义 | 允许的操作 |
|------|------|-----------|
| DRAFT | 草稿 | 修改活动、配置/移除可选课程、删除活动、开启 |
| OPEN | 开放选课 | 学生选课/退选、关闭 |
| CLOSED | 关闭 | 分班 |
| FINALIZED | 已分班 | 终态，仅可查询分班结果 |

> 状态只能顺序流转，不可回退。仅 DRAFT 状态可修改/删除，仅 OPEN 状态可选课/退选。

**角色权限**

| 角色 | 权限 |
|------|------|
| academic_admin | 增删改查活动、配置可选课程、开启/关闭/分班、查看分班结果 |
| student | 查询 OPEN 活动、查询可选课程、选课/退选、查询本人选课记录 |
| teacher / department | 无任何选课接口权限 |

> 管理端接口（10.2 / 10.3 / 10.4）仅 `academic_admin` 可调用，其他角色返回 `403 仅教务管理员可...`；学生端接口（10.5）仅 `student` 可调用，其他角色返回 `403 仅学生可操作选课记录`。教师与院系管理者无法调用任何选课接口。

**Redis 容量控制**

选课期间使用 Redis Lua 脚本原子 INCR + 上限校验，避免并发超选：

| 项目 | 说明 |
|------|------|
| Redis Key | `selection:count:{campaignId}:{courseId}` |
| 原子逻辑 | `INCR` 后若超过 capacity 则 `DECR` 并返回 -1；选课记录插入失败时回滚 `DECR` |
| 生命周期 | 活动进入 CLOSED/FINALIZED 后键不再使用，未自动清理 |

### 10.2 选课活动管理（教务管理员）

```
POST   /api/selection/campaigns               # 新建活动
GET    /api/selection/campaigns               # 查询全部活动
GET    /api/selection/campaigns/{id}          # 查询活动详情
PUT    /api/selection/campaigns/{id}          # 修改活动（仅 DRAFT）
DELETE /api/selection/campaigns/{id}          # 删除活动（仅 DRAFT）
POST   /api/selection/campaigns/{id}/open     # 开启选课（DRAFT -> OPEN）
POST   /api/selection/campaigns/{id}/close    # 关闭选课（OPEN -> CLOSED）
POST   /api/selection/campaigns/{id}/finalize # 分班（CLOSED -> FINALIZED）
Authorization: Bearer <accessToken>
```

**权限**：全部接口仅 `academic_admin` 可调用，其他角色返回 403。

#### 10.2.1 新建选课活动

```
POST /api/selection/campaigns
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 活动名称 |
| semesterId | Long | 是 | 关联学期 ID |
| startTime | LocalDateTime | 是 | 选课开始时间 |
| endTime | LocalDateTime | 是 | 选课结束时间（需晚于 startTime） |
| maxCoursesPerStudent | Integer | 否 | 每人最多选课门数，默认 1 |

**请求示例**

```json
{
  "name": "2026秋季选课",
  "semesterId": 1,
  "startTime": "2026-09-01T08:00:00",
  "endTime": "2026-09-07T22:00:00",
  "maxCoursesPerStudent": 3
}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "2026秋季选课",
    "semesterId": 1,
    "semesterName": "2026-2027学年第一学期",
    "startTime": "2026-09-01T08:00:00",
    "endTime": "2026-09-07T22:00:00",
    "maxCoursesPerStudent": 3,
    "status": "DRAFT",
    "createTime": "2026-08-20T10:00:00",
    "selectedCourseCount": 0
  }
}
```

**CampaignResponse 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 活动 ID |
| name | String | 活动名称 |
| semesterId | Long | 学期 ID |
| semesterName | String | 学期名称（冗余） |
| startTime | LocalDateTime | 选课开始时间 |
| endTime | LocalDateTime | 选课结束时间 |
| maxCoursesPerStudent | Integer | 每人最多选课门数 |
| status | String | DRAFT / OPEN / CLOSED / FINALIZED |
| createTime | LocalDateTime | 创建时间 |
| selectedCourseCount | Integer | 已配置可选课程数 |

**错误场景**

| message |
|---------|
| 学期不存在 |
| 结束时间不能早于开始时间 |

#### 10.2.2 查询全部活动

```
GET /api/selection/campaigns
Authorization: Bearer <accessToken>
```

返回全部活动列表，每项字段同 10.2.1 响应。

#### 10.2.3 查询活动详情

```
GET /api/selection/campaigns/{id}
Authorization: Bearer <accessToken>
```

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 活动 ID |

**错误场景**

| message |
|---------|
| 选课活动不存在 |

#### 10.2.4 修改活动

仅 DRAFT 状态可修改，支持部分更新。

```
PUT /api/selection/campaigns/{id}
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**请求体（均为可选）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 否 | 活动名称 |
| semesterId | Long | 否 | 学期 ID |
| startTime | LocalDateTime | 否 | 选课开始时间 |
| endTime | LocalDateTime | 否 | 选课结束时间 |
| maxCoursesPerStudent | Integer | 否 | 每人最多选课门数 |

**错误场景**

| message |
|---------|
| 选课活动不存在 |
| 仅草稿状态的活动可修改 |
| 学期不存在 |
| 结束时间不能早于开始时间 |

#### 10.2.5 删除活动

仅 DRAFT 状态可删除，级联删除活动下的可选课程记录。

```
DELETE /api/selection/campaigns/{id}
Authorization: Bearer <accessToken>
```

**错误场景**

| message |
|---------|
| 选课活动不存在 |
| 仅草稿状态的活动可删除 |

#### 10.2.6 开启选课

将活动从 DRAFT 切换为 OPEN，学生即可选课。

```
POST /api/selection/campaigns/{id}/open
Authorization: Bearer <accessToken>
```

> 前置条件：活动已配置至少 1 门可选课程，否则返回 409。

**错误场景**

| message |
|---------|
| 选课活动不存在 |
| 仅草稿状态的活动可开启 |
| 活动未配置可选课程，无法开启 |

#### 10.2.7 关闭选课

将活动从 OPEN 切换为 CLOSED，学生无法再选课/退选。

```
POST /api/selection/campaigns/{id}/close
Authorization: Bearer <accessToken>
```

**错误场景**

| message |
|---------|
| 选课活动不存在 |
| 仅开放状态的活动可关闭 |

#### 10.2.8 分班

将活动从 CLOSED 切换为 FINALIZED，按选课顺序和课程容量切分为多个选课班，写入 `selection_class` 与 `selection_class_member` 表。

```
POST /api/selection/campaigns/{id}/finalize
Authorization: Bearer <accessToken>
```

**分班规则**：

- 每门课程独立分班
- 按 `selectTime` 升序排列已选记录
- 每班最多容纳 `selection_course.capacity` 名学生，超出则新建下一个班（classNo 从 1 递增）
- 已有分班数据会先清理后重建（幂等）

**错误场景**

| message |
|---------|
| 选课活动不存在 |
| 仅关闭状态的活动可分班 |

### 10.3 可选课程管理（教务管理员）

```
POST   /api/selection/campaigns/{campaignId}/courses            # 添加可选课程
GET    /api/selection/campaigns/{campaignId}/courses            # 查询可选课程列表
DELETE /api/selection/campaigns/{campaignId}/courses/{courseId} # 移除可选课程
Authorization: Bearer <accessToken>
```

**权限**：仅 `academic_admin` 可调用，其他角色返回 403；且仅 DRAFT 状态可配置。

#### 10.3.1 添加可选课程

```
POST /api/selection/campaigns/{campaignId}/courses
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| campaignId | Long | 是 | 活动 ID |

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| courseId | Long | 是 | 课程 ID |
| capacity | Integer | 是 | 选课容量上限（必须 > 0） |

**请求示例**

```json
{
  "courseId": 3,
  "capacity": 30
}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "campaignId": 1,
    "courseId": 3,
    "capacity": 30
  }
}
```

**错误场景**

| message |
|---------|
| 选课活动不存在 |
| 仅草稿状态可配置可选课程 |
| 课程不存在 |
| 容量必须大于0 |
| 该课程已在活动中 |

#### 10.3.2 查询可选课程列表

```
GET /api/selection/campaigns/{campaignId}/courses
Authorization: Bearer <accessToken>
```

返回活动下配置的全部可选课程。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "campaignId": 1,
      "courseId": 3,
      "courseName": "高等数学",
      "courseCode": "MATH101",
      "credit": 4,
      "courseType": "必修",
      "capacity": 30,
      "selectedCount": 0,
      "remaining": 30,
      "selectedByMe": false
    }
  ]
}
```

> **说明**：管理端列表的 `selectedCount`/`remaining`/`selectedByMe` 为占位值（0/capacity/false），不反映实时选课统计。实时统计请使用学生端接口 [10.5.2](#1052-查询可选课程)。

**SelectionCourseResponse 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | selection_course 记录 ID |
| campaignId | Long | 活动 ID |
| courseId | Long | 课程 ID |
| courseName | String | 课程名称 |
| courseCode | String | 课程编号 |
| credit | Integer | 学分 |
| courseType | String | 课程类型描述（必修/选修/公选/实践） |
| capacity | Integer | 容量上限 |
| selectedCount | Integer | 已选人数（管理端列表为占位 0，学生端为实时值） |
| remaining | Integer | 剩余容量（管理端为 capacity，学生端为实时值） |
| selectedByMe | Boolean | 当前学生是否已选（管理端固定 false） |

#### 10.3.3 移除可选课程

```
DELETE /api/selection/campaigns/{campaignId}/courses/{courseId}
Authorization: Bearer <accessToken>
```

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| campaignId | Long | 是 | 活动 ID |
| courseId | Long | 是 | 课程 ID |

**错误场景**

| message |
|---------|
| 选课活动不存在 |
| 仅草稿状态可移除可选课程 |

### 10.4 分班结果查询（教务管理员）

```
GET /api/selection/campaigns/{campaignId}/classes
Authorization: Bearer <accessToken>
```

**权限**：仅 `academic_admin` 可调用，其他角色返回 403。返回活动下所有选课班及成员明细，供教务核对分班结果。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "classId": 1,
      "courseId": 3,
      "courseName": "高等数学",
      "classNo": 1,
      "studentCount": 30,
      "members": [
        {
          "studentId": 10,
          "studentName": "张三",
          "studentNo": "2024001",
          "className": "计科2401"
        },
        {
          "studentId": 11,
          "studentName": "李四",
          "studentNo": "2024002",
          "className": "计科2401"
        }
      ]
    },
    {
      "classId": 2,
      "courseId": 3,
      "courseName": "高等数学",
      "classNo": 2,
      "studentCount": 5,
      "members": []
    }
  ]
}
```

**SelectionClassResponse 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| classId | Long | 选课班 ID |
| courseId | Long | 课程 ID |
| courseName | String | 课程名称 |
| classNo | Integer | 班号（从 1 开始） |
| studentCount | Integer | 班级人数 |
| members | List\<StudentSelectionDto\> | 成员列表 |

**StudentSelectionDto 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| studentId | Long | 学生 user.id |
| studentName | String | 学生姓名 |
| studentNo | String | 学号 |
| className | String | 班级名称 |

### 10.5 学生选课

```
GET    /api/selection/student/campaigns                        # 查询可选课活动（OPEN）
GET    /api/selection/student/campaigns/{campaignId}/courses   # 查询可选课程（含实时容量/已选标记）
POST   /api/selection/student/records                          # 选课
DELETE /api/selection/student/records/{recordId}               # 退选
GET    /api/selection/student/records?campaignId=<campaignId>  # 查询我的选课记录
Authorization: Bearer <accessToken>
```

**权限**：全部接口仅 `student` 可调用，其他角色（含 academic_admin / teacher / department）返回 403。

#### 10.5.1 查询可选课活动

```
GET /api/selection/student/campaigns
Authorization: Bearer <accessToken>
```

返回当前处于 OPEN 状态的活动列表，每项字段同 [10.2.1](#1021-新建选课活动) 响应。

#### 10.5.2 查询可选课程

```
GET /api/selection/student/campaigns/{campaignId}/courses
Authorization: Bearer <accessToken>
```

返回该活动下学生可选的全部课程，含实时已选人数、剩余容量、本人是否已选。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "campaignId": 1,
      "courseId": 3,
      "courseName": "高等数学",
      "courseCode": "MATH101",
      "credit": 4,
      "courseType": "必修",
      "capacity": 30,
      "selectedCount": 18,
      "remaining": 12,
      "selectedByMe": false
    }
  ]
}
```

> 此接口的 `selectedCount`/`remaining`/`selectedByMe` 为实时值，与管理端列表不同。

#### 10.5.3 选课

```
POST /api/selection/student/records
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| campaignId | Long | 是 | 活动 ID |
| courseId | Long | 是 | 课程 ID |

**请求示例**

```json
{
  "campaignId": 1,
  "courseId": 3
}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 101,
    "campaignId": 1,
    "courseId": 3,
    "courseName": "高等数学",
    "courseCode": "MATH101",
    "credit": 4,
    "courseType": "必修",
    "status": "SELECTED",
    "selectTime": "2026-09-01T09:30:00",
    "dropTime": null
  }
}
```

**SelectionRecordResponse 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 选课记录 ID（退选时用作路径参数） |
| campaignId | Long | 活动 ID |
| courseId | Long | 课程 ID |
| courseName | String | 课程名称 |
| courseCode | String | 课程编号 |
| credit | Integer | 学分 |
| courseType | String | 课程类型描述 |
| status | String | SELECTED=已选 / DROPPED=已退 |
| selectTime | LocalDateTime | 选课时间 |
| dropTime | LocalDateTime | 退选时间（未退选时为 null） |

**选课校验链**

| 校验顺序 | 失败时 message |
|---------|---------------|
| 1. 活动存在 | 选课活动不存在 |
| 2. 活动状态为 OPEN | 活动未开放 |
| 3. 当前时间在选课时间窗口内 | 不在选课时间窗口内 |
| 4. 课程在活动可选列表中 | 该课程不在可选列表中 |
| 5. 未超过每人选课上限 | 超过每人选课上限 N 门 |
| 6. 未重复选同一门课 | 已选该课程 |
| 7. Redis 容量未满 | 课程已满 |

> 选课记录插入失败时（如数据库异常），会回滚 Redis 计数，保证一致性。

#### 10.5.4 退选

```
DELETE /api/selection/student/records/{recordId}
Authorization: Bearer <accessToken>
```

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| recordId | Long | 是 | 选课记录 ID |

> 仅本人记录可退选；活动须处于 OPEN 且在时间窗口内。退选后 Redis 计数 -1。

**错误场景**

| message |
|---------|
| 选课记录不存在 |
| 无权操作他人选课记录 |
| 该记录已退选 |
| 选课活动不存在 |
| 活动未开放，不可退选 |
| 不在选课时间窗口内 |

#### 10.5.5 查询我的选课记录

```
GET /api/selection/student/records?campaignId=<campaignId>
Authorization: Bearer <accessToken>
```

**请求参数（Query）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| campaignId | Long | 是 | 活动 ID |

返回当前学生在指定活动下的全部选课记录（含已退选），按选课时间倒序排列，字段同 [10.5.3](#1053-选课) 响应。

---

## 11. 附录

### 11.1 完整调用流程示例

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

# 6. 创建当前学期（传周数+开始日期，自动算结束日期）
curl -X POST http://localhost:8080/api/semester \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"name":"2026-2027学年第一学期","startWeek":1,"endWeek":18,"startDate":"2026-09-01","status":"CURRENT"}'

# 7. 提交授课草稿（计科2201 的课程，不写库）
curl -X POST http://localhost:8080/api/teach-info/draft \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '[{"courseId":1,"teacherId":3,"className":"计科2201","startWeek":1,"endWeek":16},{"courseId":2,"teacherId":5,"className":"计科2201","startWeek":1,"endWeek":8}]'

# 8. 提交授课草稿（计科2202 的课程，后8周与前不冲突）
curl -X POST http://localhost:8080/api/teach-info/draft \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '[{"courseId":1,"teacherId":4,"className":"计科2202","startWeek":9,"endWeek":16}]'

# 9. 查看当前全部草稿
curl -X GET http://localhost:8080/api/teach-info/draft \
  -H "Authorization: Bearer <accessToken>"

# 10. 触发自动排课（消费缓存 → 入库 → 求解，校验学期和周次）
# 不传 semesterId 时使用当前学期
curl -X POST http://localhost:8080/api/scheduling/solve \
  -H "Authorization: Bearer <accessToken>"
# 或指定学期
curl -X POST "http://localhost:8080/api/scheduling/solve?semesterId=1" \
  -H "Authorization: Bearer <accessToken>"
# → 返回 { "data": { "scheduleId": 1719820800000 } }

# 11. 轮询排课方案
curl -X GET http://localhost:8080/api/scheduling/solution/1719820800000 \
  -H "Authorization: Bearer <accessToken>"

# 12. 排课完成后按周查询课表
curl -X GET "http://localhost:8080/api/teach-info?week=5" \
  -H "Authorization: Bearer <accessToken>"

# 13. 学生查询第5周课表（Redis 缓存，首次查库后续命中缓存）
curl -X GET "http://localhost:8080/api/teach-info/week-schedule?week=5" \
  -H "Authorization: Bearer <accessToken>"

# 14. 查询某教师的所有教学信息
curl -X GET "http://localhost:8080/api/teach-info?teacherId=5" \
  -H "Authorization: Bearer <accessToken>"

# 15. 查询单条教学信息详情
curl -X GET "http://localhost:8080/api/teach-info/1" \
  -H "Authorization: Bearer <accessToken>"

# 16. 查询本班课程速览
curl -X GET "http://localhost:8080/api/teach-info/class-courses" \
  -H "Authorization: Bearer <accessToken>"

# 17. 查询当前学期
curl -X GET "http://localhost:8080/api/semester/current" \
  -H "Authorization: Bearer <accessToken>"

# 18. accessToken 过期后刷新
curl -X POST "http://localhost:8080/api/login/refresh?refreshToken=<refreshToken>"
# → 返回新的 accessToken

# 19. 登出
curl -X POST http://localhost:8080/api/login/logout \
  -H "Authorization: Bearer <accessToken>"
```

### 11.2 token 有效期配置

```yaml
jwt:
  secret: <Base64 密钥>
  access-token-expiration: 30m    # accessToken 有效期
  refresh-token-expiration: 7d    # refreshToken / session 有效期
```

### 11.3 密码安全

- 算法：**PBKDF2WithHmacSHA256**
- 迭代次数：**100,000**
- 密钥长度：**256 bit**
- 存储格式：`iterations:salt:hash`
- 注册时 `EncryptUtils.hashWithPbkdf2()` 生成密文
- 登录时 `EncryptUtils.verifyPbkdf2()` 做恒定时间比对
