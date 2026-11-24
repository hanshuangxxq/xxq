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
| 403  | 权限不足（已登录但用户类型不满足接口要求） |
| 404  | 资源不存在 |
| 409  | 冲突（重复注册等） |
| 500  | 服务端错误 |

### 1.2 认证机制

除登录、刷新 token 与前端日志上报（`/api/front-logs`）外，所有接口都需要在请求头中携带 JWT access token（**注册接口也需要登录**，见 3.1）：

```
Authorization: Bearer <accessToken>
```

accessToken 由登录接口返回，默认有效期 30 分钟。过期后调用刷新接口获取新 token。

**拦截器路由规则：**

| 路径 | 需要认证 |
|------|----------|
| `POST /api/login` | 否 |
| `POST /api/login/refresh` | 否 |
| `/api/front-logs`（前端日志上报） | 否 |
| `POST /api/register` | 是（任意已登录用户） |
| 其他 `/api/**` | 是 |

认证失败时返回 401：

| message | 原因 |
|---------|------|
| 请先登录 | 未携带 Authorization 头 |
| token已过期，请刷新 | accessToken 过期 |
| token无效 | token 签名校验失败 |
| token已注销 | 已调用登出接口，session 已删除 |

**接口鉴权（用户类型）：**

认证通过后，服务端按接口声明的用户类型要求做二次校验（`@RequireAuth` 注解及其组合快捷注解 `@RequireStudent`/`@RequireTeacher`/`@RequireAcademicAdmin`/`@RequireDepartment`/`@RequireManagement`/`@RequireLogin`，未登录错误 401 优先于类型不符 403）：

| 场景 | 结果 |
|------|------|
| 接口允许任意已登录用户 | 直接放行 |
| 接口限定用户类型（student / teacher / academic_admin / department）且当前用户命中 | 放行 |
| 类型不符 | 403 `{"code":403,"message":"权限不足"}` |

各接口的类型要求见后文章节。

认证成功后，JWT 中的信息会注入到 request attribute：

| attribute | 来源 |
|-----------|------|
| userId | JWT subject |
| userType | JWT claim |
| tokenId | JWT claim（指向 Redis 中的 session） |

---

## 2. 接口总览

| 方法 | 路径 | 说明 | 认证 | 章节 |
|------|------|------|------|------|
| POST | `/api/register` | 账号注册（需已登录） | 是 | 3.1 |
| POST | `/api/login` | 登录（账号 / 微信 / QQ / 支付宝） | 否 | 3.2 |
| POST | `/api/login/refresh` | 刷新 accessToken | 否 | 3.3 |
| POST | `/api/login/logout` | 登出 | 是 | 3.4 |
| POST | `/api/password/change` | 修改密码 | 是 | 3.5 |
| GET | `/api/user/profile` | 查询个人信息 | 是 | 4.1 |
| PUT | `/api/user/profile` | 修改个人信息 | 是 | 4.2 |
| POST | `/api/user/avatar/upload` | 上传头像 | 是 | 4.3 |
| GET | `/api/avatar/{filename}` | 获取头像图片 | 是 | 4.4 |
| WS | `/ws/avatar` | 头像 WebSocket 拉取（可选） | 否 | 4.4.1 |
| GET | `/api/teachers` | 查询全部教师 | 是 | 4.8 |
| GET | `/api/colleges` | 查询全部院系 | 是 | 4.9 |
| POST | `/api/colleges` | 新增院系 | 是 | 4.9 |
| PUT | `/api/colleges/{id}` | 修改院系 | 是 | 4.9 |
| DELETE | `/api/colleges/{id}` | 删除院系 | 是 | 4.9 |
| GET | `/api/grades` | 查询全部年级 | 是 | 4.10 |
| GET | `/api/grades/{id}` | 查询单个年级 | 是 | 4.10 |
| POST | `/api/grades` | 新增年级 | 是 | 4.10 |
| PUT | `/api/grades/{id}` | 修改年级 | 是 | 4.10 |
| DELETE | `/api/grades/{id}` | 删除年级 | 是 | 4.10 |
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
| POST | `/api/academic/batch-import` | 批量导入学生和教师 | 是 | 4.5 |
| POST | `/api/academic/batch-import/file` | Excel 批量导入（.xlsx，列序见模板） | 是 | 4.5 |
| GET | `/api/academic/batch-import/template` | 下载导入模板 | 是 | 4.5 |
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
| POST | `/api/selection/campaigns` | 新建选课活动（含课程字段） | 是 | 10.2.1 |
| GET | `/api/selection/campaigns` | 查询全部选课活动 | 是 | 10.2.2 |
| GET | `/api/selection/campaigns/{id}` | 查询选课活动详情 | 是 | 10.2.3 |
| PUT | `/api/selection/campaigns/{id}` | 修改选课活动（含课程字段） | 是 | 10.2.4 |
| DELETE | `/api/selection/campaigns/{id}` | 删除选课活动 | 是 | 10.2.5 |
| POST | `/api/selection/campaigns/{id}/open` | 开启选课 | 是 | 10.2.6 |
| POST | `/api/selection/campaigns/{id}/close` | 关闭选课 | 是 | 10.2.7 |
| POST | `/api/selection/campaigns/{id}/finalize` | 分班 | 是 | 10.2.8 |
| POST | `/api/selection/groups` | 新建选课组 | 是 | 10.3.1 |
| GET | `/api/selection/groups` | 查询全部选课组 | 是 | 10.3.2 |
| GET | `/api/selection/groups/{groupId}` | 查询选课组详情 | 是 | 10.3.3 |
| PUT | `/api/selection/groups/{groupId}` | 修改选课组 | 是 | 10.3.4 |
| DELETE | `/api/selection/groups/{groupId}` | 删除选课组 | 是 | 10.3.5 |
| GET | `/api/selection/groups/{groupId}/bindable-campaigns` | 查询可绑定到本组的活动 | 是 | 10.3.6 |
| GET | `/api/selection/campaigns/{campaignId}/classes` | 查询分班结果 | 是 | 10.4 |
| PUT | `/api/selection/campaigns/{campaignId}/classes/{classId}/teacher` | 为选课班分配 / 取消教师 | 是 | 10.4.1 |
| GET | `/api/selection/student/campaigns` | 学生查询可选课活动 | 是 | 10.5.1 |
| GET | `/api/selection/student/campaigns/{campaignId}` | 学生查询活动详情 | 是 | 10.5.2 |
| POST | `/api/selection/student/records` | 选课 | 是 | 10.5.3 |
| DELETE | `/api/selection/student/records/{recordId}` | 退选 | 是 | 10.5.4 |
| GET | `/api/selection/student/records` | 查询我的选课记录 | 是 | 10.5.5 |
| POST | `/api/file/uploads` | 初始化 / 恢复分片上传会话（幂等，可秒传） | 是 | 11.1 |
| PUT | `/api/file/uploads/{uploadId}/parts/{index}` | 上传一个分片（裸二进制 + X-Chunk-SHA256 头） | 是 | 11.1 |
| POST | `/api/file/uploads/{uploadId}/complete` | 合并分片为成品（幂等） | 是 | 11.1 |
| GET | `/api/file/uploads/{uploadId}` | 查询上传进度 | 是 | 11.1 |
| POST | `/api/file/uploads/{uploadId}/verify` | 校验已收分片，返回缺失 / 损坏清单 | 是 | 11.1 |
| DELETE | `/api/file/uploads/{uploadId}` | 取消上传 | 是 | 11.1 |
| POST | `/api/file/whole` | 小文件整传（multipart：biz + file） | 是 | 11.1 |
| POST | `/api/file/download` | 通用下载（POST 传路径，支持 Range → 206） | 是 | 11.1 |
| GET | `/api/file/preview` | 在线预览（inline + Range 懒加载 → 206，类型白名单） | 是 | 11.6 |
| GET | `/api/file/preview/info` | 预览元信息（大小 / 类型 / 可预览性） | 是 | 11.6 |
| POST | `/api/practice/graduation/theses/duplicate-checks` | 登记查重结果（multipart，可附查重报告） | 是 | 11.5 |
| GET | `/api/practice/graduation/theses/duplicate-checks/{checkId}/download` | 查重报告下载 | 是 | 11.5 |
| POST | `/api/practice/graduation/campaigns/{id}/materials` | 上传活动资料 | 是 | 11.5 |
| GET | `/api/practice/graduation/campaigns/{id}/materials` | 活动资料列表 | 是 | 11.5 |
| GET | `/api/practice/graduation/campaigns/{id}/materials/{mid}/download` | 活动资料下载 | 是 | 11.5 |
| DELETE | `/api/practice/graduation/campaigns/{id}/materials/{mid}` | 删除活动资料 | 是 | 11.5 |
| POST | `/api/practice/graduation/defense/{id}/material` | 上传 / 替换答辩材料 | 是 | 11.5 |
| GET | `/api/practice/graduation/defense/{id}/material/download` | 答辩材料下载 | 是 | 11.5 |
| POST | `/api/practice/competitions/results/{id}/certificate` | 上传 / 替换获奖证书 | 是 | 11.5 |
| GET | `/api/practice/competitions/results/{id}/certificate/download` | 获奖证书下载 | 是 | 11.5 |
| POST | `/api/coursework/question-bank` | 题库录入 | 教师 | 12.2 |
| GET | `/api/coursework/question-bank` | 题库分页（courseId/campaignId/type/keyword 筛选） | 教师 | 12.2 |
| PUT | `/api/coursework/question-bank/{id}` | 题库修改 | 教师 | 12.2 |
| DELETE | `/api/coursework/question-bank/{id}` | 题库软删 | 教师 | 12.2 |
| POST | `/api/coursework/assignments` | 新建作业 | 教师 | 12.2 |
| PUT | `/api/coursework/assignments/{id}` | 修改作业（JSON） | 教师 | 12.2 |
| POST | `/api/coursework/assignments/{id}/attachment` | 替换作业附件 | 教师 | 12.2 |
| POST | `/api/coursework/assignments/{id}/clone` | 克隆到其它授课组 `{teachInfoId, deadline}` | 教师 | 12.2 |
| DELETE | `/api/coursework/assignments/{id}` | 删除作业（仅草稿） | 教师 | 12.2 |
| POST | `/api/coursework/assignments/{id}/publish` | 发布作业 | 教师 | 12.2 |
| POST | `/api/coursework/assignments/{id}/close` | 关闭作业 | 教师 | 12.2 |
| GET | `/api/coursework/assignments` | 作业列表（教师/学生双视角） | 教师/学生 | 12.2 |
| GET | `/api/coursework/assignments/{id}` | 作业详情 | 教师/学生 | 12.2 |
| GET | `/api/coursework/assignments/{id}/submissions` | 提交名单（含未交，五态 state） | 教师 | 12.2 |
| POST | `/api/coursework/assignments/{id}/submit` | 提交 / 重交作业（纯 JSON 逐题作答） | 学生 | 12.2 |
| PUT | `/api/coursework/assignments/{id}/draft` | 暂存作答草稿（仅 Redis） | 学生 | 12.2 |
| GET | `/api/coursework/assignments/{id}/my-submission` | 我的提交 | 学生 | 12.2 |
| GET | `/api/coursework/submissions/{id}` | 单份提交详情（含标准答案） | 教师 | 12.2 |
| POST | `/api/coursework/submissions/{id}/grade` | 逐题批改（仅大题可改判） | 教师 | 12.2 |
| POST | `/api/coursework/chapters` | 建章 | 教师 | 12.3 |
| PUT | `/api/coursework/chapters/{id}` | 改章名 | 教师 | 12.3 |
| DELETE | `/api/coursework/chapters/{id}` | 删章（级联软删节+视频） | 教师 | 12.3 |
| GET | `/api/coursework/chapters/tree` | 章节树（挂节视频） | 教师/学生 | 12.3 |
| POST | `/api/coursework/sections` | 建节（挂章下） | 教师 | 12.3 |
| PUT | `/api/coursework/sections/{id}` | 改节名 | 教师 | 12.3 |
| DELETE | `/api/coursework/sections/{id}` | 删节（级联软删视频） | 教师 | 12.3 |
| POST | `/api/coursework/videos` | 登记教学视频（挂节） | 教师 | 12.3 |
| PUT | `/api/coursework/videos/{id}` | 改视频元数据/改挂节 | 教师 | 12.3 |
| DELETE | `/api/coursework/videos/{id}` | 删除视频 | 教师 | 12.3 |
| GET | `/api/coursework/videos/{id}` | 视频元数据 | 教师/学生 | 12.3 |
| GET | `/api/coursework/videos/common` | 未挂节视频（公共区） | 教师/学生 | 12.3 |
| WS | `/ws/video/{videoId}?token=` | 视频流式播放 | 教师/学生 | 12.3 |
| GET | `/api/coursework/materials/browse` | 资料逐层浏览（文件夹+资料） | 教师/学生 | 12.4 |
| GET | `/api/coursework/material-folders` | 单层子文件夹（移动选择器懒展开） | 教师/学生 | 12.4 |
| POST | `/api/coursework/material-folders` | 建文件夹 | 教师 | 12.4 |
| PUT | `/api/coursework/material-folders/{id}` | 改文件夹名（不可换父级） | 教师 | 12.4 |
| DELETE | `/api/coursework/material-folders/{id}` | 删文件夹（级联软删子树） | 教师 | 12.4 |
| POST | `/api/coursework/materials` | 上传课程资料（可挂 folderId） | 教师 | 12.4 |
| PUT | `/api/coursework/materials/{id}` | 改资料元数据 | 教师 | 12.4 |
| PUT | `/api/coursework/materials/{id}/folder` | 移动资料（null=根目录） | 教师 | 12.4 |
| DELETE | `/api/coursework/materials/{id}` | 删除资料 | 教师 | 12.4 |
| POST | `/api/coursework/announcements` | 发布课程公告 | 教师 | 12.5 |
| PUT | `/api/coursework/announcements/{id}` | 编辑公告（不重复通知） | 教师 | 12.5 |
| DELETE | `/api/coursework/announcements/{id}` | 删除公告 | 教师 | 12.5 |
| GET | `/api/coursework/announcements` | 公告列表 | 教师/学生 | 12.5 |
| POST | `/api/coursework/teach-infos/{id}/regular-score/sync` | 作业成绩合成平时分 | 教师 | 12.6 |
| PUT | `/api/scores/config/{teachInfoId}` | 设置平时分占比 | 是 | 13.4 |
| GET | `/api/scores/config/{teachInfoId}` | 查询平时分占比 | 是 | 13.4 |
| GET | `/api/scores/roster/{teachInfoId}` | 查询成绩录入名单 | 是 | 13.5 |
| POST | `/api/scores` | 批量录入成绩 | 是 | 13.6 |
| PUT | `/api/scores/{id}` | 修改单条成绩 | 是 | 13.6 |
| GET | `/api/scores` | 按授课安排查询成绩 | 是 | 13.7 |
| GET | `/api/scores/my` | 查询我的成绩 | 学生 | 13.7 |
| GET | `/api/scores/my/semesters` | 查询我有成绩的学期 | 学生 | 13.7 |
| GET | `/api/scores/statistics` | 成绩分布统计 | 是 | 13.8 |
| GET | `/api/scores/export` | 导出成绩单（Excel / PDF） | 是 | 13.9 |
| POST | `/api/scores/reviews` | 提交成绩复核申请 | 学生 | 13.10 |
| GET | `/api/scores/reviews/my` | 查询我的复核申请 | 学生 | 13.10 |
| GET | `/api/scores/reviews` | 复核待办列表 | 教师/教务 | 13.10 |
| POST | `/api/scores/reviews/{id}/reply` | 教师回复复核 | 教师 | 13.10 |
| POST | `/api/scores/reviews/{id}/escalate` | 复核升级到教务 | 学生 | 13.10 |
| POST | `/api/scores/reviews/{id}/resolve` | 教务终审复核 | 教务 | 13.10 |
| POST | `/api/exams` | 安排期末 / 期中考试 | 教务 | 14.3 |
| PUT | `/api/exams/{id}` | 修改考试 | 教务 | 14.3 |
| DELETE | `/api/exams/{id}` | 删除考试 | 教务 | 14.3 |
| GET | `/api/exams` | 考试分页列表 | 教务 | 14.4 |
| GET | `/api/exams/class-courses` | 按班级查可排考课程 | 教务 | 14.4 |
| GET | `/api/exams/teacher` | 教师查询相关考试 | 教师 | 14.5 |
| GET | `/api/exams/my` | 学生查询我的考试 | 学生 | 14.5 |
| GET | `/api/exams/makeup/candidates` | 补考候选（不及格）名单 | 教务 | 14.6 |
| POST | `/api/exams/makeup` | 建补考 / 重修考试 | 教务 | 14.6 |
| GET | `/api/exams/makeup` | 补考 / 重修考试列表 | 教务 | 14.6 |
| POST | `/api/exams/{examId}/grades` | 录入补考 / 重修成绩 | 教师/教务 | 14.7 |
| GET | `/api/analysis/scores/distribution` | 成绩分数段分布 | 教师/院系/教务 | 15.1 |
| GET | `/api/analysis/scores/trend` | 课程跨学期成绩趋势 | 教师/院系/教务 | 15.1 |
| GET | `/api/analysis/scores/comparison` | 同课程班级横向对比 | 教师/院系/教务 | 15.1 |
| GET | `/api/analysis/warnings/config` | 查询预警阈值配置 | 教务 | 15.2 |
| PUT | `/api/analysis/warnings/config` | 更新预警阈值配置 | 教务 | 15.2 |
| POST | `/api/analysis/warnings/scan` | 触发学业预警扫描 | 教务 | 15.2 |
| GET | `/api/analysis/warnings` | 学业预警看板 | 教务/院系 | 15.2 |
| GET | `/api/analysis/warnings/me` | 我的学业预警 | 学生 | 15.2 |
| POST | `/api/analysis/evaluation-items` | 新建评教指标 | 教务 | 15.3 |
| GET | `/api/analysis/evaluation-items` | 评教指标列表 | 教务 | 15.3 |
| PUT | `/api/analysis/evaluation-items/{id}` | 修改评教指标 | 教务 | 15.3 |
| DELETE | `/api/analysis/evaluation-items/{id}` | 删除评教指标 | 教务 | 15.3 |
| POST | `/api/analysis/evaluation-templates` | 创建评教模板 | 教务 | 15.4 |
| GET | `/api/analysis/evaluation-templates` | 评教模板列表 | 教务 | 15.4 |
| GET | `/api/analysis/evaluation-templates/{id}` | 评教模板详情 | 教务 | 15.4 |
| PUT | `/api/analysis/evaluation-templates/{id}` | 修改评教模板 | 教务 | 15.4 |
| DELETE | `/api/analysis/evaluation-templates/{id}` | 删除评教模板 | 教务 | 15.4 |
| PUT | `/api/analysis/evaluation-templates/{id}/default` | 设为全局默认模板 | 教务 | 15.4 |
| PUT | `/api/analysis/evaluation-templates/{id}/status` | 启用 / 停用评教模板 | 教务 | 15.4 |
| PUT | `/api/analysis/evaluation-templates/override/{teachInfoId}` | 设置 / 清除课程级模板覆盖 | 教务 | 15.4 |
| GET | `/api/analysis/evaluation-templates/override/{teachInfoId}` | 查询课程级模板覆盖 | 教务 | 15.4 |
| GET | `/api/analysis/evaluations/form` | 获取评教表单 | 是 | 15.5 |
| POST | `/api/analysis/evaluations` | 提交评教 | 学生 | 15.5 |
| GET | `/api/analysis/evaluations/my` | 我的评教 | 学生 | 15.5 |
| POST | `/api/analysis/evaluations/period/open` | 开启评教周期 | 教务 | 15.5 |
| POST | `/api/analysis/evaluations/period/close` | 关闭评教周期 | 教务 | 15.5 |
| GET | `/api/analysis/evaluations/period` | 查询评教周期状态 | 是 | 15.5 |
| GET | `/api/analysis/teacher-quality/me` | 我的教学质量 | 教师 | 15.6 |
| GET | `/api/analysis/teacher-quality/{teacherId}` | 指定教师教学质量 | 教务/院系/教师 | 15.6 |
| GET | `/api/analysis/teacher-quality` | 教师质量列表 / 对比 | 教务/院系 | 15.6 |
| GET | `/api/analysis/progress/me` | 我的学习进度 | 学生 | 15.7 |
| GET | `/api/analysis/progress/{studentUserId}` | 指定学生学习进度 | 教务/院系 | 15.7 |
| GET | `/api/analysis/profile/me` | 我的学生画像 | 学生 | 15.8 |
| GET | `/api/analysis/profile/{studentUserId}` | 指定学生画像 | 教务/院系 | 15.8 |
| GET | `/api/analysis/class-analysis` | 班级 / 专业成绩聚合 | 教务/院系 | 15.8 |
| GET | `/api/analysis/class-analysis/trend` | 单组跨学期趋势 | 教务/院系 | 15.8 |
| GET | `/api/notification/unread-count` | 未读消息数 | 是 | 16.2 |
| GET | `/api/notification/list` | 消息列表 | 是 | 16.3 |
| PUT | `/api/notification/{id}/read` | 标记单点消息已读 | 是 | 16.4 |
| PUT | `/api/notification/broadcast/{id}/read` | 标记广播消息已读 | 是 | 16.5 |
| PUT | `/api/notification/read-all` | 全部已读 | 是 | 16.6 |
| DELETE | `/api/notification/{id}` | 删除单点消息 | 是 | 16.7 |
| POST | `/api/notification/send` | 发送单点消息 | 教务/院系 | 16.8 |
| WS | `/ws/notification?token=` | 站内消息实时推送 | 是 | 16.9 |
| POST | `/api/practice/graduation/campaigns` | 创建毕设活动 | 教务 | 17.2.2 |
| PUT | `/api/practice/graduation/campaigns/{id}` | 更新毕设活动 | 教务 | 17.2.2 |
| PUT | `/api/practice/graduation/campaigns/{id}/status` | 变更毕设活动状态 | 教务 | 17.2.2 |
| GET | `/api/practice/graduation/campaigns` | 毕设活动分页列表 | 教务 | 17.2.2 |
| GET | `/api/practice/graduation/campaigns/{id}` | 毕设活动详情 | 四角色 | 17.2.2 |
| GET | `/api/practice/graduation/campaigns/available` | 我可参与的毕设活动 | 学生 | 17.2.2 |
| GET | `/api/practice/graduation/campaigns/selector` | 毕设活动选择器 | 教师/院系 | 17.2.2 |
| GET | `/api/practice/graduation/campaigns/{id}/detail` | 毕设活动教务详情 | 教务 | 17.2.2 |
| POST | `/api/practice/graduation/campaigns/{id}/materials` | 上传毕设活动资料 | 教务 | 17.2.3 |
| GET | `/api/practice/graduation/campaigns/{id}/materials` | 毕设活动资料列表 | 四角色 | 17.2.3 |
| GET | `/api/practice/graduation/campaigns/{id}/materials/{mid}/download` | 毕设活动资料下载 | 四角色 | 17.2.3 |
| DELETE | `/api/practice/graduation/campaigns/{id}/materials/{mid}` | 删除毕设活动资料 | 教务 | 17.2.3 |
| POST | `/api/practice/graduation/proposals` | 提交 / 重提选题申请 | 学生 | 17.2.4 |
| PUT | `/api/practice/graduation/proposals/{id}/review/dept` | 选题院系初审 | 院系 | 17.2.4 |
| PUT | `/api/practice/graduation/proposals/{id}/review/academic` | 选题教务终审 | 教务 | 17.2.4 |
| GET | `/api/practice/graduation/proposals/my` | 我的选题申请 | 学生 | 17.2.4 |
| GET | `/api/practice/graduation/proposals/pending/dept` | 院系待初审队列 | 院系 | 17.2.4 |
| GET | `/api/practice/graduation/proposals/pending/academic` | 教务待终审队列 | 教务 | 17.2.4 |
| POST | `/api/practice/graduation/assignments/picks` | 教师自选学生 | 教师 | 17.2.5 |
| DELETE | `/api/practice/graduation/assignments/picks/{id}` | 教师放弃自选 | 教师 | 17.2.5 |
| POST | `/api/practice/graduation/assignments/allocations` | 院系指定分配 | 院系 | 17.2.5 |
| POST | `/api/practice/graduation/assignments/reassigns` | 院系改派 | 院系 | 17.2.5 |
| GET | `/api/practice/graduation/assignments/teacher/pool` | 教师自选池 | 教师 | 17.2.5 |
| GET | `/api/practice/graduation/assignments/my` | 我的师生匹配 | 学生/教师 | 17.2.5 |
| GET | `/api/practice/graduation/assignments/overview` | 分配总览 | 教务 | 17.2.5 |
| GET | `/api/practice/graduation/assignments/unassigned` | 未分配学生清单 | 教务/院系 | 17.2.5 |
| POST | `/api/practice/graduation/process/opening-reports` | 提交 / 重提开题报告 | 学生 | 17.2.6 |
| PUT | `/api/practice/graduation/process/opening-reports/{id}/review` | 审核开题报告 | 教师 | 17.2.6 |
| GET | `/api/practice/graduation/process/opening-reports/my` | 我的开题报告 | 学生 | 17.2.6 |
| GET | `/api/practice/graduation/process/opening-reports/teacher` | 名下学生开题报告 | 教师 | 17.2.6 |
| GET | `/api/practice/graduation/process/opening-reports/{id}/download` | 开题报告下载 | 四角色 | 17.2.6 |
| POST | `/api/practice/graduation/process/midterms` | 提交 / 重提中期检查 | 学生 | 17.2.6 |
| PUT | `/api/practice/graduation/process/midterms/{id}/review` | 评审中期检查 | 教师 | 17.2.6 |
| GET | `/api/practice/graduation/process/midterms/my` | 我的中期检查 | 学生 | 17.2.6 |
| GET | `/api/practice/graduation/process/midterms/teacher` | 名下学生中期检查 | 教师 | 17.2.6 |
| GET | `/api/practice/graduation/process/midterms/{id}/download` | 中期检查下载 | 四角色 | 17.2.6 |
| POST | `/api/practice/graduation/process/guidance-logs` | 记录指导日志 | 教师 | 17.2.6 |
| GET | `/api/practice/graduation/process/guidance-logs` | 指导记录查询 | 教师/院系/教务 | 17.2.6 |
| POST | `/api/practice/graduation/theses` | 提交 / 重提论文 | 学生 | 17.2.7 |
| PUT | `/api/practice/graduation/theses/{id}/review` | 论文形式审查 | 教师 | 17.2.7 |
| GET | `/api/practice/graduation/theses/my` | 我的论文（全部版本） | 学生 | 17.2.7 |
| GET | `/api/practice/graduation/theses/teacher` | 名下学生论文 | 教师 | 17.2.7 |
| GET | `/api/practice/graduation/theses/campaign` | 活动内论文 | 教务 | 17.2.7 |
| GET | `/api/practice/graduation/theses/export-package` | 导出查重数据包 | 教务 | 17.2.7 |
| GET | `/api/practice/graduation/theses/{id}/download` | 论文下载 | 四角色 | 17.2.7 |
| POST | `/api/practice/graduation/theses/duplicate-checks` | 登记查重结果 | 教务 | 17.2.8 |
| GET | `/api/practice/graduation/theses/{id}/duplicate-checks` | 论文查重记录 | 四角色 | 17.2.8 |
| GET | `/api/practice/graduation/theses/duplicate-checks/{checkId}/download` | 查重报告下载 | 四角色 | 17.2.8 |
| POST | `/api/practice/graduation/defense/arrange` | 安排 / 更新答辩 | 院系 | 17.2.9 |
| GET | `/api/practice/graduation/defense/list` | 答辩安排列表 | 教务/院系/学生 | 17.2.9 |
| POST | `/api/practice/graduation/defense/{id}/material` | 上传 / 替换答辩材料 | 教务/院系 | 17.2.9 |
| GET | `/api/practice/graduation/defense/{id}/material/download` | 答辩材料下载 | 学生/院系/教务 | 17.2.9 |
| POST | `/api/practice/graduation/defense/scores/advisor` | 录入指导分 | 教师 | 17.2.10 |
| GET | `/api/practice/graduation/defense/scores/advisor` | 指导分录入列表 | 教师 | 17.2.10 |
| POST | `/api/practice/graduation/defense/scores/reviewer` | 录入评阅分 | 教师 | 17.2.10 |
| GET | `/api/practice/graduation/defense/scores/reviewer` | 评阅分录入列表 | 教师 | 17.2.10 |
| POST | `/api/practice/graduation/defense/scores/defense` | 录入答辩分 | 教务/院系 | 17.2.10 |
| POST | `/api/practice/graduation/defense/scores/confirm` | 确认并发布总评 | 院系 | 17.2.10 |
| GET | `/api/practice/graduation/defense/scores` | 毕设成绩列表 | 四角色 | 17.2.10 |
| GET | `/api/practice/graduation/defense/scores/my` | 我的毕设成绩 | 学生 | 17.2.10 |
| GET | `/api/practice/graduation/defense/scores/export` | 导出成绩总表 | 教务 | 17.2.10 |
| GET | `/api/practice/graduation/dashboard/{campaignId}` | 毕设看板 | 教务/院系 | 17.2.11 |
| GET | `/api/practice/graduation/dashboard/{campaignId}/export` | 毕设看板导出 | 教务/院系 | 17.2.11 |
| GET | `/api/practice/graduation/dashboard/{campaignId}/logs` | 操作日志分页 | 教务 | 17.2.11 |
| POST | `/api/practice/internships` | 发布实习项目 | 院系 | 17.3.1 |
| PUT | `/api/practice/internships/{id}` | 更新实习项目 | 院系/教务 | 17.3.1 |
| PUT | `/api/practice/internships/{id}/status` | 变更实习状态 | 院系/教务 | 17.3.1 |
| GET | `/api/practice/internships` | 实习项目分页列表 | 院系/教务 | 17.3.1 |
| GET | `/api/practice/internships/{id}` | 实习项目详情 | 是 | 17.3.1 |
| GET | `/api/practice/internships/available` | 可选实习列表 | 学生 | 17.3.1 |
| DELETE | `/api/practice/internships/{id}` | 删除实习项目 | 院系/教务 | 17.3.1 |
| POST | `/api/practice/internships/applications` | 报名实习 | 学生 | 17.3.2 |
| DELETE | `/api/practice/internships/applications/{id}` | 撤销实习报名 | 学生 | 17.3.2 |
| POST | `/api/practice/internships/applications/{id}/review` | 审核实习报名 | 院系/教务 | 17.3.2 |
| GET | `/api/practice/internships/applications/my` | 我的实习报名 | 学生 | 17.3.2 |
| GET | `/api/practice/internships/{internshipId}/applications` | 某实习的报名列表 | 院系/教务 | 17.3.2 |
| POST | `/api/practice/internship-reports` | 提交 / 重传实习报告 | 学生 | 17.3.3 |
| GET | `/api/practice/internship-reports/my` | 我的实习报告 | 学生 | 17.3.3 |
| GET | `/api/practice/internship-reports` | 实习报告分页列表 | 院系/教务 | 17.3.3 |
| POST | `/api/practice/internship-reports/{id}/review` | 评审实习报告 | 院系/教务 | 17.3.3 |
| DELETE | `/api/practice/internship-reports/{id}` | 删除实习报告 | 学生/院系/教务 | 17.3.3 |
| GET | `/api/practice/internship-reports/{id}/download` | 实习报告下载 | 学生/院系/教务 | 17.3.3 |
| POST | `/api/practice/trainings` | 发布培训课程 | 院系 | 17.3.4 |
| PUT | `/api/practice/trainings/{id}` | 更新培训课程 | 院系/教务 | 17.3.4 |
| PUT | `/api/practice/trainings/{id}/status` | 变更培训状态 | 院系/教务 | 17.3.4 |
| GET | `/api/practice/trainings` | 培训课程分页列表 | 院系/教务 | 17.3.4 |
| GET | `/api/practice/trainings/{id}` | 培训课程详情 | 是 | 17.3.4 |
| GET | `/api/practice/trainings/available` | 可选培训列表 | 学生 | 17.3.4 |
| DELETE | `/api/practice/trainings/{id}` | 删除培训课程 | 院系/教务 | 17.3.4 |
| POST | `/api/practice/trainings/{courseId}/enrollments` | 报名培训 | 学生 | 17.3.5 |
| DELETE | `/api/practice/trainings/enrollments/{id}` | 退课 | 学生 | 17.3.5 |
| GET | `/api/practice/trainings/enrollments/my` | 我的培训报名 | 学生 | 17.3.5 |
| GET | `/api/practice/trainings/{courseId}/enrollments` | 某课程的报名列表 | 院系/教务 | 17.3.5 |
| POST | `/api/practice/competitions` | 发布竞赛 | 教务 | 17.4.1 |
| PUT | `/api/practice/competitions/{id}` | 更新竞赛 | 教务 | 17.4.1 |
| PUT | `/api/practice/competitions/{id}/status` | 变更竞赛状态 | 教务 | 17.4.1 |
| GET | `/api/practice/competitions` | 竞赛分页列表 | 教务 | 17.4.1 |
| GET | `/api/practice/competitions/{id}` | 竞赛详情 | 是 | 17.4.1 |
| GET | `/api/practice/competitions/available` | 可选竞赛列表 | 学生 | 17.4.1 |
| DELETE | `/api/practice/competitions/{id}` | 删除竞赛 | 教务 | 17.4.1 |
| POST | `/api/practice/competitions/registrations` | 报名竞赛（个人 / 团队） | 学生 | 17.4.2 |
| DELETE | `/api/practice/competitions/registrations/{id}` | 撤销竞赛报名 | 学生 | 17.4.2 |
| POST | `/api/practice/competitions/registrations/{id}/review` | 审核竞赛报名 | 教务 | 17.4.2 |
| GET | `/api/practice/competitions/registrations/my` | 我的竞赛报名 | 学生 | 17.4.2 |
| GET | `/api/practice/competitions/{competitionId}/registrations` | 某竞赛的报名列表 | 教务 | 17.4.2 |
| POST | `/api/practice/competitions/results` | 录入 / 更新获奖结果 | 教务 | 17.4.3 |
| DELETE | `/api/practice/competitions/results/{id}` | 删除获奖结果 | 教务 | 17.4.3 |
| POST | `/api/practice/competitions/results/{id}/certificate` | 上传 / 替换获奖证书 | 教务 | 17.4.3 |
| GET | `/api/practice/competitions/results/{id}/certificate/download` | 获奖证书下载 | 教务/学生 | 17.4.3 |
| GET | `/api/practice/competitions/{competitionId}/results` | 某竞赛的结果列表 | 是 | 17.4.3 |
| GET | `/api/practice/competitions/{competitionId}/results/my` | 我的竞赛结果 | 学生 | 17.4.3 |
| POST | `/api/practice/social-practices` | 发布实践项目 | 教务 | 17.5.1 |
| PUT | `/api/practice/social-practices/{id}` | 更新实践项目 | 教务 | 17.5.1 |
| PUT | `/api/practice/social-practices/{id}/status` | 变更实践状态 | 教务 | 17.5.1 |
| GET | `/api/practice/social-practices` | 实践项目分页列表 | 教务 | 17.5.1 |
| GET | `/api/practice/social-practices/{id}` | 实践项目详情 | 是 | 17.5.1 |
| GET | `/api/practice/social-practices/available` | 可选实践项目 | 学生 | 17.5.1 |
| DELETE | `/api/practice/social-practices/{id}` | 删除实践项目 | 教务 | 17.5.1 |
| POST | `/api/practice/social-practices/applications` | 申报实践项目（个人 / 团队） | 学生 | 17.5.2 |
| DELETE | `/api/practice/social-practices/applications/{id}` | 撤销实践申报 | 学生 | 17.5.2 |
| POST | `/api/practice/social-practices/applications/{id}/review` | 审核实践申报 | 教务 | 17.5.2 |
| GET | `/api/practice/social-practices/applications/my` | 我的实践申报 | 学生 | 17.5.2 |
| GET | `/api/practice/social-practices/{practiceId}/applications` | 某项目的申报列表 | 教务 | 17.5.2 |
| POST | `/api/practice/social-practice-reports` | 提交 / 重传实践报告 | 学生 | 17.5.3 |
| GET | `/api/practice/social-practice-reports/my` | 我的实践报告 | 学生 | 17.5.3 |
| GET | `/api/practice/social-practice-reports` | 实践报告分页列表 | 教务 | 17.5.3 |
| POST | `/api/practice/social-practice-reports/{id}/review` | 评审实践报告 | 教务 | 17.5.3 |
| DELETE | `/api/practice/social-practice-reports/{id}` | 删除实践报告 | 学生/教务 | 17.5.3 |
| GET | `/api/practice/social-practice-reports/{id}/download` | 实践报告下载 | 学生/教务 | 17.5.3 |
| GET | `/api/preferences/me` | 读取本人偏好 | 是 | 18.1 |
| PUT | `/api/preferences/me` | 浅合并本人偏好 | 是 | 18.1 |
| DELETE | `/api/preferences/me` | 重置本人偏好 | 是 | 18.1 |
| POST | `/api/front-logs` | 前端日志上报 | 否 | 18.2 |

> **文件上传 / 下载**：通用端点 10 个见第 11 节；业务上传端点（头像 / practice 提交系 5 个 /
> 查重登记 / 活动资料 / 答辩材料 / 获奖证书 / Excel 导入）与业务下载端点（practice 系 5 个 +
> 查重报告 / 活动资料 / 答辩材料 / 获奖证书，均支持断点续传）见各对应章节。
> practice 系的 `file` 部分为**选填** —— 大文件可先走第 11 节分片上传、再以 `data.filePath` 提交。
> 大小上限、错误码、断点续传下载（Range/206）与完整分片契约详见根目录 **《文件上传下载接口文档.md》**。

---

## 3. 认证模块

### 3.1 账号注册

```
POST /api/register
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**权限**：任意已登录用户（`@RequireLogin`）。本系统不开放自助注册，账号由已登录用户（通常为教务管理员）创建。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| account | String | 是 | 登录账号（用户名，全库唯一） |
| password | String | 是 | 明文密码（服务端 PBKDF2 哈希存储） |
| userType | String | 是 | `teacher` / `student` / `academic_admin` / `department` |
| identifier | String | 否 | 编号：教师→工号（`teacher.teacher_no`）、学生→学号（`student.student_no`）、`academic_admin`→部门编号（`academic_admin.department_no`）；`department` 无编号字段，传了也忽略 |
| collegeId | Long | 否 | 所属院系 ID（仅 `department` 类型使用，写入 `department.college_id`） |

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
| 工号已存在 |
| 学号已存在 |
| 部门编号已存在 |
| 不支持的用户类型: xxx |

> `account` 命中任意子表（用户名 / 工号 / 学号 / 部门编号）即视为已存在。
> 注册只写 `user` 基表与对应子表，**不会**补全班级、专业、院系归属——需要完整学籍信息请走 4.5 批量导入或 4.6 学生管理。

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
3. 根据 `userType` 查询子类型表（student / teacher / academic_admin / department），填充特有字段

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
      "majorId": 1
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
| userType | String | `teacher` / `student` / `academic_admin` / `department` |
| lastLoginTime | LocalDateTime | 上次登录时间 |
| createTime | LocalDateTime | 注册时间 |
| status | Integer | 状态（1 = 正常） |

**各子类型特有字段**

| 字段 | student | teacher | academic_admin | department | 说明 |
|------|:-------:|:-------:|:--------------:|:----------:|------|
| identifier | 学号 | 工号 | 部门编号 | | 唯一标识（`department` 无此字段） |
| grade | ✓ | | | | 年级名称（`student.grade_id` → `grade.name`） |
| major | ✓ | | | | 专业名称（经班级 `major_id` 推导） |
| className | ✓ | | | | 班级对象（`id` / `className` / `majorId`） |
| enrollmentYear | ✓ | | | | 入学年份 |
| title | | ✓ | | | 职称 |
| department | | ✓ | | ✓ | 所属院系名称（`college.name`） |
| position | | | ✓ | ✓ | `academic_admin` 固定为「教务管理员」，`department` 固定为「院系管理员」 |

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

> 不可修改字段：`name`、`password`、`userType`、`status`。

### 4.3 头像上传

```
POST /api/user/avatar/upload?userId=<userId>
Authorization: Bearer <accessToken>
Content-Type: multipart/form-data
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 否 | 用户 ID（Query）。传了必须等于当前登录用户（否则 403「权限不足」）；不传时自动使用当前登录用户（旧前端兼容） |
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

> 上传成功后自动将文件名写入**当前登录用户**的 `user.avatar` 字段，无需再调用修改接口。
>
> **归属校验**：`userId` 是历史遗留参数，传了就必须等于当前登录用户，否则返回 body `code=403`「权限不足」——防止任意登录用户覆盖他人头像。

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

#### 4.4.1 头像 WebSocket 拉取（可选）

适合需要一次连接批量取多张头像的场景。**该通道无握手鉴权**（与头像 REST 端点同一暴露面），连接后按 `userId` 请求：

```
ws://<host>/ws/avatar
```

| 方向 | 报文 |
|------|------|
| 客户端发送 | `{"userId": 1}` |
| 服务端回复 | `{"userId":1,"contentType":"image/png","data":"<base64>"}`；用户未设置头像时 `data` 为空串 |
| 缺少 `userId` | `{"error":"缺少 userId 参数"}` |

> 每次请求恰好回一帧，连接可复用；`contentType` 由存储文件扩展名推断（图片类型）。

### 4.5 批量导入学生和教师

仅教务管理员可操作。一次性批量导入学生和教师用户，自动将编号、班级、院系等字段写入对应的子类型表。

```
POST /api/academic/batch-import
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
| className | String | 否 | **字段名有历史歧义：学生写入的是 `gradeId`（年级），不是班级**。填已存在的年级名称 |
| gender | String | 否 | 性别（`男` / `女` / `未知`，默认 `未知`） |
| department | String | 否 | 学生→班级名称（写入 `classId`，专业与院系由班级推导）；教师→院系名称（写入 `collegeId`）。两者均须已存在，否则该行以「班级不存在 / 院系不存在」失败 |

**请求示例**

```json
{
  "users": [
    {
      "username": "张三",
      "password": "123456",
      "userType": "student",
      "identifier": "2024001",
      "className": "2024",
      "gender": "男",
      "department": "计算机科学1班"
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

| userType | user 表 | 子类型表 | 编号字段 | 落库的归属字段 |
|----------|---------|----------|----------|----------------|
| student | ✓ | student | studentNo | `gradeId`（第 5 列「年级」）、`classId`（第 7 列「班级/院系」）。**专业与院系不落库**，由班级推导 |
| teacher | ✓ | teacher | teacherNo | `collegeId`（第 7 列「院系」） |

> 每条记录在事务中独立写入，单条失败不影响其他记录。密码使用 `EncryptUtils.hashWithPbkdf2()` 进行 PBKDF2WithHmacSHA256 哈希（100,000 次迭代，256 bit 密钥）。
>
> 学生行只写班级外键，因此导入时**必须**填一个已挂专业的班级，否则该生专业/院系为空、在院系视图中消失——这种行会被判为失败而不是静默成功。

**错误场景**

| HTTP 状态码 | message |
|-------------|---------|
| 403 | 仅教务管理员可执行此操作 |
| 400 | 导入数据不能为空 |

**常见逐条错误**（出现在 `details[].message`，不影响其它行）

| message |
|---------|
| 用户类型只允许 student 或 teacher，收到: xxx |
| 用户名已存在：xxx |
| 学号已存在：xxx / 工号已存在：xxx |
| 年级不存在：xxx，请先在基础数据中创建 |
| 班级不存在：xxx，请先在基础数据中创建 |
| 班级未挂专业：xxx，请先在班级管理中为该班指定专业 |
| 院系不存在：xxx，请先在基础数据中创建 |

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

#### 4.5.1 Excel 文件导入

与 JSON 导入等价的文件形式：上传 `.xlsx`，服务端解析后**原样复用**同一套逐行独立事务导入逻辑，
响应结构与错误口径完全一致（`BatchImportResponse`）。文件只用于瞬态解析，不落盘。

```
POST /api/academic/batch-import/file
Authorization: Bearer <accessToken>
Content-Type: multipart/form-data
```

**权限要求**：仅 `academic_admin`（教务管理员）。

**请求参数**（multipart）

| 部分 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | `.xlsx` 文件（其它扩展名 400），列序固定见下表 |

**Excel 列序**（首行表头必须保留，首列必须是「用户名」，否则 400「表头不符合模板」；
从第 2 行起为数据行，全空行自动跳过；数值单元格自动去 `.0` 尾巴，学号工号可放心填数字）

| 列 | 表头 | 对应字段 | 说明 |
|----|------|----------|------|
| 1 | 用户名 | username | 必填，全库唯一 |
| 2 | 密码 | password | 必填，PBKDF2 加密存储 |
| 3 | 用户类型 | userType | `student` / `teacher` |
| 4 | 学号/工号 | identifier | 可空 |
| 5 | 年级 | className | 学生用，填已存在的年级名称 |
| 6 | 性别 | gender | `男` / `女`，留空默认男 |
| 7 | 班级/院系 | department | 学生填已存在的**且已挂专业**的班级名称、教师填已存在的院系名称；学生的专业与院系由所填班级推导，无需另填 |

**错误场景**

| HTTP 状态码 | message |
|-------------|---------|
| 400 | 导入文件不能为空 |
| 400 | 仅支持 .xlsx 格式，请下载导入模板 |
| 400 | 表头不符合模板（首列应为「用户名」），请下载导入模板 |
| 400 | 文件中没有数据行 |

#### 4.5.2 导入模板下载

生成并下载导入模板：Sheet1「导入数据」仅表头（防止示例行被误导入），Sheet2「填写说明」
含每列含义与取值规则。

```
GET /api/academic/batch-import/template
Authorization: Bearer <accessToken>
```

**权限要求**：仅 `academic_admin`。响应为 `用户批量导入模板.xlsx`
（`Accept-Ranges: bytes` + 内容 SHA-256 强 ETag）。

### 4.6 学生管理（教务管理员）

仅教务管理员（`academic_admin`）可操作。用于查询和修改全校学生的学号、班级、年级、入学年份等信息。

> **学籍归属链为严格四层**：`院系 → 专业 → 班级 → 学生`。学生**不直存专业**，`majorName` 由所属班级（`class_name.major_id → major.major_name`）推导而来；要改学生的专业，只能改其班级。`gradeId`（当前学业年级）与 `enrollmentYear`（学籍入学年份）并存不可互推——留级学生的年级会高于入学年份。
>
> **前端对接提示**：
> - 班级下拉列表 → `GET /api/class-names`（见 [5.6 班级 CRUD](#56-班级-crud)），返回 `[{id, className, majorId}, ...]`
> - 专业下拉列表 → `GET /api/majors`（见 [4.7 专业管理](#47-专业管理)），返回 `[{id, majorName}, ...]`
> - 学生侧只传班级名称字符串即可，后端自动解析为 `classId`；专业为只读派生值

#### 4.6.1 查询学生列表

```
GET /api/students?grade=<grade>&className=<className>&major=<major>&unassigned=<unassigned>&name=<name>
Authorization: Bearer <accessToken>
```

**权限**：仅 `academic_admin`，其他角色返回 403。

**请求参数（Query，均为可选）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| gradeId | Long | 否 | 按年级 ID 筛选（对应 `student.grade_id`） |
| className | String | 否 | 按班级筛选（模糊匹配，通过 class_name 表解析为 classId） |
| major | String | 否 | 按专业筛选（模糊匹配，经 `class_name.major_id` 反查出班级后再筛学生） |
| unassigned | Boolean | 否 | `true` 时仅返回未分班学生（classId 为空） |
| name | String | 否 | 按姓名模糊查询（LIKE 匹配 user.name） |
| page / pageSize | Integer | 否 | 分页参数 |

> 所有参数可任意组合；不带任何参数时返回全部学生。`name` 模糊查询与其他条件取交集。
>
> `className` 与 `major` 同时传入时取**交集**（先各自解析出班级集合，再求交）；交集为空直接返回空列表，不会退化成「不过滤」。
>
> ⚠ **`major` 与 `unassigned=true` 组合恒为空集**：学生不直存专业，专业由班级推导，
> 未分班（`classId` 为空）就意味着没有专业。这个组合在旧模型下有意义（学生可"有专业但没班级"），
> 新模型下已不成立，前端不要再这样拼参数。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "studentId": 1,
      "studentNo": "2024001",
      "gradeId": 1,
      "gradeName": "2024",
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
      "gradeId": 1,
      "gradeName": "2024",
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

> 出于隐私保护，以下字段不返回：`gender`（性别）、`avatar`（头像）、`description`（描述）、`lastLoginTime`（上次登录时间）、`status`（状态）、`password`（密码）。

**StudentDto 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| studentId | Long | 学生记录主键（student 表 id），修改时用作路径参数 |
| studentNo | String | 学号 |
| gradeId | Long | 年级 ID（FK -> grade.id），未设置时为 null |
| gradeName | String | 年级名称（从 grade 表关联），未设置时为 null |
| majorName | String | 专业名称（**经班级推导**：class_name.major_id -> major.major_name；未分班或班级未挂专业时为 null） |
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

# 按年级 ID 筛选
curl -X GET "http://localhost:8080/api/students?gradeId=1" \
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

# 组合筛选：某年级的未分班学生
curl -X GET "http://localhost:8080/api/students?gradeId=1&unassigned=true" \
  -H "Authorization: Bearer <accessToken>"
```

> 注意不要把 `major` 与 `unassigned=true` 组合：未分班即无专业，该组合恒为空集。

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
| className | String | 否 | 班级名称（通过 `GET /api/class-names` 获取下拉列表后选择；班级不存在返回 404） |
| gradeName | String | 否 | 年级名称（通过班级管理/年级字典获取；年级不存在返回 404） |
| enrollmentYear | Integer | 否 | 入学年份 |

> **无 `majorName`**：专业挂在班级上，单独改专业会让学生的专业与班级所属专业相矛盾。要调整学生的专业，请传一个属于目标专业的 `className`。
>
> `gradeName` 与 `enrollmentYear` 互相独立：留级学生二者不对等，不可由其中一个推导另一个。

**请求示例**

```json
{
  "studentNo": "2024001",
  "className": "计科2401",
  "gradeName": "2024",
  "enrollmentYear": 2024
}
```

```json
{
  "className": "软工2402"
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
| 400 | 班级未挂专业: xxx，请先在班级管理中为该班指定专业 |
| 403 | 仅教务管理员可操作学生数据 |
| 404 | 学生不存在 |
| 404 | 班级不存在: xxx |
| 404 | 年级不存在: xxx |

> 「班级未挂专业」是为防止静默丢失归属：学生的专业与院系全部经班级推导，
> 换到一个没有专业的班级会让这两项变 NULL，该生在院系管理员视图中直接消失。

---

### 4.7 专业管理

专业表（`major`）挂在院系下（`college_id`），班级再挂到专业上（`class_name.major_id`）；学生的专业由其班级推导，不单独存。仅教务管理员可增删改，所有登录用户可查看。

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
| majorName | String | 专业名称（必填） |
| collegeId | Long | 所属院系 ID（FK -> `college.id`，**新增时必填**） |

**约束与错误场景**

| HTTP 状态码 | message | 触发条件 |
|-------------|---------|---------|
| 400 | 专业名称不能为空 | 新增时 `majorName` 为空 |
| 400 | 所属院系不能为空 | 新增时 `collegeId` 为空 |
| 400 | 引用的院系不存在(id=x) | `collegeId` 指向不存在的院系 |
| 404 | 专业不存在 | 修改/删除的 id 不存在 |
| 409 | 该专业下仍有 N 个班级，无法删除 | 删除前须先把这些班级改挂其它专业 |

> `collegeId` 必填是为了保住院系链：学生的院系 = `class_name.major_id → major.college_id → college`，
> 专业不挂院系会让其下所有班级与学生一起失去院系归属。修改时传 `null` 表示保持原院系不变。

**响应示例（GET）**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "id": 1, "majorName": "计算机科学与技术", "collegeId": 1 },
    { "id": 2, "majorName": "软件工程", "collegeId": 1 },
    { "id": 3, "majorName": "数据科学与大数据技术", "collegeId": 1 }
  ]
}
```

**新增/修改请求体**

```json
{
  "majorName": "人工智能",
  "collegeId": 1
}
```

> `collegeId` 是「班级 → 院系」推导链的最后一跳：前端拿到班级的 `majorId` 后，用本接口的 `collegeId` 即可显示班级所属院系。

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

### 4.9 院系管理

院系（`college`）是学籍归属链的顶端标准主实体：`major`（专业）、`teacher`（教师）、`department`（院系管理员）通过 `college_id` 引用本表；班级与学生再经「专业」两跳间接归属（`student → class_name.major_id → major.college_id → college.id`）。`college_name` 的全局唯一性由应用层保证（数据库仅保留普通索引）。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/colleges` | 教务管理员 / 院系管理员 / 教师 | 查询全部院系 |
| POST | `/api/colleges` | 仅教务管理员 | 新建院系 |
| PUT | `/api/colleges/{id}` | 仅教务管理员 | 更新院系（部分更新） |
| DELETE | `/api/colleges/{id}` | 仅教务管理员 | 删除院系 |

**权限说明**

- 查询端点标注 `@RequireAuth({ACADEMIC_ADMIN, DEPARTMENT, TEACHER})`；**学生不在放行列表内**（访问返回 HTTP 403「权限不足」）。
- 三个写端点标注 `@RequireAcademicAdmin`，非教务管理员返回 HTTP 403「权限不足」。

> **错误口径**：鉴权失败（未携带/无效 token、用户类型不符）由 `AuthInterceptor` 直接写出真实 HTTP 401/403；进入 Controller/Service 后的业务错误由 `GlobalExceptionHandler` 包装为 **HTTP 200 + 响应体 `code`**。后文错误表中的 `code` 均指响应体字段，HTTP 状态均为 200（特别标注的鉴权错误除外）。

**College 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 院系 ID（数据库自增） |
| collegeName | String | 院系名称（新增必填、全库唯一；数据库上限 64 字符） |
| collegeCode | String | 院系代码（可空；数据库上限 32 字符） |
| collegeNo | String | 院系编号（可空；数据库上限 32 字符） |
| createTime | LocalDateTime | 创建时间（数据库 `DEFAULT CURRENT_TIMESTAMP`） |
| updateTime | LocalDateTime | 更新时间（数据库 `ON UPDATE CURRENT_TIMESTAMP`） |

#### 4.9.1 查询院系列表

```
GET /api/colleges
Authorization: Bearer <accessToken>
```

无请求参数、无分页、无筛选，服务端未显式排序（按数据库默认顺序返回全部院系）。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "collegeName": "计算机学院",
      "collegeCode": "CS",
      "collegeNo": "01",
      "createTime": "2026-06-20T08:00:00",
      "updateTime": "2026-06-20T08:00:00"
    },
    {
      "id": 2,
      "collegeName": "数学学院",
      "collegeCode": "MATH",
      "collegeNo": "02",
      "createTime": "2026-06-20T08:00:00",
      "updateTime": "2026-06-20T08:00:00"
    }
  ]
}
```

**错误场景**

| code | message |
|------|---------|
| 401 | 请先登录 / token已过期，请刷新 / token无效（真实 HTTP 401） |
| 403 | 权限不足（学生或其它不匹配类型，真实 HTTP 403） |

#### 4.9.2 新建院系

```
POST /api/colleges
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**请求体（Body）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| collegeName | String | 是 | 院系名称，不能为空白，全库唯一 |
| collegeCode | String | 否 | 院系代码 |
| collegeNo | String | 否 | 院系编号 |

**请求示例**

```json
{
  "collegeName": "计算机学院",
  "collegeCode": "CS",
  "collegeNo": "01"
}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "collegeName": "计算机学院",
    "collegeCode": "CS",
    "collegeNo": "01",
    "createTime": null,
    "updateTime": null
  }
}
```

> 新建响应是插入前构造的实体回显（未回查数据库），`createTime` / `updateTime` 恒为 `null`（数据库有默认值，需调用查询接口才能拿到实际时间）。
>
> 名称只做「空白判空」，**不自动 trim**：前后带空格会原样保存，并参与精确匹配的唯一性校验。

**错误场景**

| code | message |
|------|---------|
| 400 | 院系名称不能为空 |
| 409 | 院系名称已存在 |
| 403 | 权限不足（非教务管理员，真实 HTTP 403） |

#### 4.9.3 更新院系

部分更新：请求体中不传或传 `null` 的字段保持原值。

```
PUT /api/colleges/{id}
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 院系 ID |

**请求体（Body，均可选）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| collegeName | String | 否 | 新名称；传了就必须非空白，且不能与其它院系重名 |
| collegeCode | String | 否 | 新代码；传空串表示清空 |
| collegeNo | String | 否 | 新编号；传空串表示清空 |

**请求示例**

```json
{
  "collegeName": "计算机与人工智能学院",
  "collegeCode": "CSAI"
}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "collegeName": "计算机与人工智能学院",
    "collegeCode": "CSAI",
    "collegeNo": "01",
    "createTime": "2026-06-20T08:00:00",
    "updateTime": "2026-06-20T08:00:00"
  }
}
```

> 更新响应中的 `updateTime` 是**更新前读到的旧值**：实现先 `selectById` 再 `updateById`，非空的 `updateTime` 会随 UPDATE 语句一并写回，数据库的 `ON UPDATE CURRENT_TIMESTAMP` 不会生效。需要最新时间请重新调用查询接口。

**错误场景**

| code | message |
|------|---------|
| 400 | 院系名称不能为空 |
| 404 | 院系不存在 |
| 409 | 院系名称已存在（排除自身后仍有重名） |
| 403 | 权限不足（非教务管理员，真实 HTTP 403） |

#### 4.9.4 删除院系

```
DELETE /api/colleges/{id}
Authorization: Bearer <accessToken>
```

物理删除（`College` 实体无 `@TableLogic`），成功返回 `data: null`。

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 院系 ID |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**错误场景**

| code | message |
|------|---------|
| 404 | 院系不存在 |
| 403 | 权限不足（非教务管理员，真实 HTTP 403） |

> **删除不校验下游引用**：`CollegeServiceImpl.delete` 只校验院系存在，然后直接删除，**不会**检查 `major` / `teacher` / `department` 中是否仍有行指向它（本项目全部外键约束在应用层，DB 无 FK）。
>
> 误删的后果是静默的：其下专业变成「无院系的专业」，该专业下所有学生经归属链解析失败（fail-closed），在院系管理员视图中直接消失。删除前请自行确认该院系下已无专业、教师与院系管理员。

---

### 4.10 年级管理

年级（`grade`）是学生「当前学业年级」的字典表，`student.grade_id` 引用本表。`grade_id`（当前学业年级）与 `student.enrollment_year`（学籍入学年份）并存且不可互推——留级场景下二者不对等。

**全部 5 个端点均仅教务管理员（`academic_admin`）可访问**：`GradeController` 类级标注 `@RequireAcademicAdmin`，其它用户类型一律 HTTP 403「权限不足」。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/grades` | 查询全部年级 |
| GET | `/api/grades/{id}` | 查询单个年级 |
| POST | `/api/grades` | 新增年级 |
| PUT | `/api/grades/{id}` | 修改年级（部分更新） |
| DELETE | `/api/grades/{id}` | 删除年级（有学生引用时拒绝） |

**Grade 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 年级 ID（数据库自增） |
| name | String | 年级名称（如 `2024`；数据库上限 32 字符，应用层保证唯一） |
| description | String | 年级描述（可空；数据库上限 128 字符） |
| createTime | LocalDateTime | 创建时间（数据库默认值） |
| updateTime | LocalDateTime | 更新时间（数据库 `ON UPDATE CURRENT_TIMESTAMP`） |

#### 4.10.1 查询年级列表

```
GET /api/grades
Authorization: Bearer <accessToken>
```

无请求参数、无分页、无筛选，服务端未显式排序。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "2023",
      "description": "2023 级",
      "createTime": "2026-06-20T08:00:00",
      "updateTime": "2026-06-20T08:00:00"
    },
    {
      "id": 2,
      "name": "2024",
      "description": null,
      "createTime": "2026-06-20T08:00:00",
      "updateTime": "2026-06-20T08:00:00"
    }
  ]
}
```

**错误场景**

| code | message |
|------|---------|
| 403 | 权限不足（非教务管理员，真实 HTTP 403） |

#### 4.10.2 查询单个年级

```
GET /api/grades/{id}
Authorization: Bearer <accessToken>
```

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 年级 ID |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 2,
    "name": "2024",
    "description": null,
    "createTime": "2026-06-20T08:00:00",
    "updateTime": "2026-06-20T08:00:00"
  }
}
```

**错误场景**

| code | message |
|------|---------|
| 404 | 年级不存在 |
| 403 | 权限不足（非教务管理员，真实 HTTP 403） |

#### 4.10.3 新增年级

```
POST /api/grades
Authorization: Bearer <accessToken>
Content-Type: application/json
```

请求体直接使用 `Grade` 实体绑定。

**请求体（Body）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 年级名称，不能为空白，应用层查重 |
| description | String | 否 | 年级描述 |

> 请求体类型是 `Grade` 实体，`id` / `createTime` / `updateTime` 并非业务字段，正常调用不要传。

**请求示例**

```json
{
  "name": "2025",
  "description": "2025 级"
}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 3,
    "name": "2025",
    "description": "2025 级",
    "createTime": null,
    "updateTime": null
  }
}
```

> 与院系新建同理，响应是插入前实体回显：未在请求体中传 `createTime` / `updateTime` 时二者为 `null`，实际默认时间只在查询接口中可见。

**错误场景**

| code | message |
|------|---------|
| 400 | 年级名称不能为空 |
| 409 | 年级名称已存在 |
| 403 | 权限不足（非教务管理员，真实 HTTP 403） |

#### 4.10.4 修改年级

部分更新：`MyBatis-Plus updateById` 跳过 `null` 字段，未传字段保持原值。

```
PUT /api/grades/{id}
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 年级 ID |

**请求体（Body，均可选）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 否 | 新名称；非空且与原名称不同时才做重名校验 |
| description | String | 否 | 新描述；传空串表示清空 |

**请求示例**

```json
{
  "name": "2024",
  "description": "2024 级本科生"
}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 2,
    "name": "2024",
    "description": "2024 级本科生",
    "createTime": "2026-06-20T08:00:00",
    "updateTime": "2026-11-24T10:30:00"
  }
}
```

> 修改成功后服务端会重新 `getById` 回查，因此响应里的 `updateTime` 是数据库刷新后的最新值（与院系更新响应不同）。
>
> ⚠ **修改时未做非空校验**：传 `"name": ""` 会跳过重名校验并把名称直接改成空串（新增时同样输入会被 400 拒绝）。前端应自行禁止提交空名称。

**错误场景**

| code | message |
|------|---------|
| 404 | 年级不存在 |
| 409 | 年级名称已存在（改成了其它年级的名称） |
| 403 | 权限不足（非教务管理员，真实 HTTP 403） |

#### 4.10.5 删除年级

```
DELETE /api/grades/{id}
Authorization: Bearer <accessToken>
```

物理删除（`Grade` 实体无 `@TableLogic`），成功返回 `data: null`。

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 年级 ID |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**错误场景**

| code | message |
|------|---------|
| 404 | 年级不存在 |
| 409 | 该年级仍有学生引用，无法删除 |
| 403 | 权限不足（非教务管理员，真实 HTTP 403） |

> 删除前**只校验 `student` 表中是否存在 `grade_id = id` 的行**（`student` 表本身无逻辑删除标记，存在即计数）。
>
> 其它模块若以字符串/JSON 形式保存年级 id（例如选课活动的允许年级 `allowedGradeIds`），**不参与**校验，删除后这些白名单会指向不存在的年级 id。删除前请确认该年级已无学生使用。

---

## 5. 教学信息模块

授课安排（`teach_info`）是「教师 + 课程 + 班级 + 时段 + 教室 + 周次」的排课记录。课表查询、授课草稿（5.8）、自动排课（第 8 节）与课程课业（第 12 节）都以它为锚点。

> **同一授课组可能有多行**：同一「学期 + 课程/活动 + 教师 + 班级名」在 `teach_info` 中可能对应多条记录（每行一个上课时段）。课业内容只挂在组内最小 id 的**锚点行**上，所有接收 `teachInfoId` 的接口都接受组内任意一行，服务端自动归一（见 12.1）。

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
| department | 本院系 | `userId → Department.collegeId → major.college_id → class_name.major_id` 解析出院系下全部班级名（任一跳缺失即不可见，fail-closed） |
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
        "id": 12,
        "category": "REQUIRE",
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
        "startPeriod": "08:00:00",
        "endPeriod": "09:40:00",
        "building": "教学楼A",
        "classroom": "301"
      }
    ]
  }
}
```

> **脱敏说明**：出于安全性考虑，不返回课程/教师/地点的内部 ID（`courseId` / `teacherId` / `localId`）、课程编号（`courseCode`）、教师工号（`teacherNo`）与职称（`title`）。**`id` 是 `teach_info.id`**（非课程 ID），成绩录入、考试、课业接口用它定位；时间段起止已随 `startPeriod` / `endPeriod` 返回，`timeId` 仅在需要完整时段模板时使用。

**UserCourseDto 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| mondayDate | LocalDate | 当前教学周周一对应的日期，供前端渲染日历视图 |
| courses | List\<CourseDto\> | 教学信息列表，每项为 CourseDto |

**CourseDto 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 授课安排 ID（`teach_info.id`），成绩/考试/课业接口用它定位 |
| category | String | 多态类型：`REQUIRE` / `ELECTIVE` / `PUBLIC` / `PRACTICE`（与 `courseType` 对应） |
| courseName | String | 课程名称 |
| credit | Integer | 学分 |
| courseHour | Integer | 课程学时 |
| courseType | String | 课程类型中文描述（必修 / 选修 / 公选 / 实践） |
| teacherName | String | 教师姓名（从 user 表关联） |
| department | String | 教师所属院系名称 |
| className | String | 班级名称 |
| college | String | 班级所属院系（班级 → 专业 → 院系推导） |
| dayOfWeek | Integer | 星期几（1=周一 ~ 7=周日） |
| startWeek | Integer | 起始教学周 |
| endWeek | Integer | 结束教学周 |
| timeId | Long | 时间段 ID，可调用 `/api/time/{id}` 获取完整时段模板 |
| startPeriod | LocalTime | 上课开始时间（来自 time 表） |
| endPeriod | LocalTime | 上课结束时间（来自 time 表） |
| building | String | 教学楼 |
| classroom | String | 教室 |

**PublicCourseDto 额外字段**（仅 `category=PUBLIC` 时出现）：

| 字段 | 类型 | 说明 |
|------|------|------|
| campaignId | Long | 选课活动 ID（`selection_campaign.id`） |
| campaignStatus | String | 活动状态：`DRAFT` / `OPEN` / `CLOSED` / `FINALIZED` |
| capacity | Integer | 活动容量上限 |
| selectedCount | Integer | 该选课班已分班人数（`selection_class.student_count`） |
| classNo | Integer | 选课班号（从 1 开始） |

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
    "id": 12,
    "category": "REQUIRE",
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
    "startPeriod": "08:00:00",
    "endPeriod": "09:40:00",
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

按当前登录学生所在班级返回该班开设课程的**名称清单**（按课程 ID 去重，不重复、不含时间地点），用于课程下拉、导航与「我的课程」入口。需要完整课表请用 5.1（列表）/ 5.9（按周）。

> 无需传参，`userId` 由拦截器从 JWT 中解析后自动注入。非学生、学生未分班或班级无排课时返回**空数组**（不报错）。结果按 `userId` 缓存。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "courseName": "高等数学" },
    { "courseName": "大学物理" }
  ]
}
```

**ClassCourseDto 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| courseName | String | 课程名称（该班去重后的全部课程） |

### 5.4 新增 / 修改 / 删除授课安排

```
POST   /api/teach-info          # 新增
PUT    /api/teach-info/{id}     # 修改
DELETE /api/teach-info/{id}     # 删除
Authorization: Bearer <accessToken>
```

**权限**：任意已登录用户（`@RequireLogin`）。

**删除保护**：删除前校验下游引用，命中任一即返回 409，避免内容变成孤儿。

| message |
|---------|
| 该授课安排已关联考试，无法删除 |
| 该授课安排已录入成绩，无法删除 |
| 该授课安排关联选课班，无法删除 |
| 该授课安排已关联课程作业，无法删除 |
| 该授课安排已关联教学视频，无法删除 |
| 该授课安排已关联章节树，无法删除 |
| 该授课安排已关联课程资料，无法删除 |
| 该授课安排已关联课程公告，无法删除 |

**请求体（POST/PUT）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| courseId | Long | 条件 | 常规课课程 ID（`course.id`）。公选课不传，改用 `campaignId` |
| campaignId | Long | 条件 | 公选课活动 ID（`selection_campaign.id`）；与 `courseId` 二选一，公选课行的 `courseId` 为 NULL |
| teacherId | Long | 是 | 教师 ID（`teacher.id`） |
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
GET    /api/courses                 # 分页查询（含公选课合成条目）
GET    /api/courses/{id}            # 查询单个
POST   /api/courses                 # 新增（仅 academic_admin）
PUT    /api/courses/{id}            # 修改（仅 academic_admin）
DELETE /api/courses/{id}            # 删除（仅 academic_admin）
Authorization: Bearer <accessToken>
```

**权限**：查询接口任意已登录用户；增删改仅 `academic_admin`。

**查询参数（GET）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page / pageSize | Integer | 否 | 分页参数（列表接口） |
| source | String | 否 | `MANUAL` = 只返回 `course` 表常规课，不追加公选课合成条目；`SELECTION_CAMPAIGN` = 按 `campaign.id` 查询单个公选课条目（仅单个接口有效） |

**列表语义**：返回 `PageResult<Course>`，默认在常规课之外追加由 `selection_campaign` 合成的**公选课条目**（`id = campaign.id`、`source = SELECTION_CAMPAIGN`，不落库、仅供展示）。`department` 角色不追加这些条目。任何会把 `course.id` 当外键写入的场景（排课草稿、补考/重修考试、时段预留）都应传 `source=MANUAL` 排除合成条目，否则会指向不存在的 `course` 记录。

**Course 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 课程 ID（`course` 表自增；合成条目为 `campaign.id`） |
| courseName | String | 课程名称 |
| courseCode | String | 课程代码/编号 |
| credit | Integer | 学分 |
| description | String | 课程描述 |
| courseHour | Integer | 课程学时 |
| courseType | CurseEnum | 课程类型，**响应输出中文描述**：`必修` / `选修` / `公选` / `实践` |
| source | String | `MANUAL`（常规课）/ `SELECTION_CAMPAIGN`（公选课合成条目） |

> **`courseType` 请求必须传中文描述字符串**（如 `"courseType": "必修"`）。该枚举未加 `@JsonCreator`，Jackson 按 `@JsonValue` 的描述串反序列化；传数字会落到**枚举序号**（0→必修、1→选修……），与「1=必修」的直觉错位，传枚举名 `REQUIRE` 同样失败。响应恒为中文描述串。
>
> `GET /api/courses/{id}` 查询单个课程，课程不存在时返回 404「课程不存在」；`?source=SELECTION_CAMPAIGN` 时 id 为 `campaign.id`。删除同理：`DELETE /api/courses/{id}?source=SELECTION_CAMPAIGN` 删除的是对应**选课活动**（须为 DRAFT），而不是同 id 的常规课。

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
| majorId | Long | 所属专业 ID（FK -> `major.id`，**新增时必填**） |

**约束与错误场景**

| HTTP 状态码 | message | 触发条件 |
|-------------|---------|---------|
| 400 | 所属专业不能为空 | 新增时 `majorId` 为空 |
| 400 | 引用的专业不存在(id=x) | 新增/修改时 `majorId` 指向不存在的专业 |
| 403 | 无权查看其他院系的班级 | 院系管理者访问非本院系的班级 |
| 404 | 班级不存在 | 查询/删除的 id 不存在 |
| 409 | 该班级下仍有 N 名学生，无法删除 | 删除前须先把这些学生转到别的班级 |

> 学籍归属链为严格四层：`院系 → 专业 → 班级 → 学生`。班级**只挂专业**，不存 `collegeId`——院系由 `major.college_id` 推导。前端展示院系需自行用 `GET /api/majors` 把 `majorId` 映射为专业，再经专业的 `collegeId` 得到院系。
>
> `majorId` 必填、且删除班级要先清空学生，都是为了防止静默丢失归属：学生的专业与院系完全靠班级推导，班级没有专业或整个班级被删掉，这些学生在院系管理员视图中会直接消失。
>
> GET /api/class-names/{id} 查询单个班级，班级不存在时返回 404："班级不存在"。院系管理者仅可查看本院系的班级（经专业推导出的院系比对，任一跳缺失即拒绝）。
>
> GET /api/class-names/department 仅院系管理者（department）可调用，返回本院系的班级列表。其他角色返回 403。本院系无班级时返回空数组。
>
> PUT 时 `majorId` 传 `null` 表示保持原专业不变（MyBatis Plus `NOT_NULL` 策略），传了值则必须存在。

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
| type | String | 教室类型，响应输出中文描述（普通教室/实验室/机房/报告厅） |
| managerId | Long | 管理者教师 ID（FK -> teacher.id），实验室/机房必填 |
| managerName | String | 管理者姓名（响应回显，请求时忽略） |

**教室类型（LocalTypeEnum）**

| name | value | description |
|------|-------|-------------|
| CLASSROOM | 1 | 普通教室 |
| LABORATORY | 2 | 实验室 |
| COMPUTER_ROOM | 3 | 机房 |
| LECTURE_HALL | 4 | 报告厅 |

> `type` 请求参数可传 name / value / description（推荐 name），响应输出 description；新增时不传默认普通教室。`GET /api/locals` 支持 `?type=` 筛选。
>
> 实验室/机房必须指定管理者（`managerId`），普通教室/报告厅可选；填写时校验教师是否存在。响应回显 `managerName`。
>
> 自动排课（`POST /api/scheduling/solve`）仅选取普通教室，实验室/机房/报告厅不参与自动排课。详细接口说明见 `docs/教室管理接口文档.md`。

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

**权限**：仅 `academic_admin` / `department`（`@RequireManagement`）。

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

**权限**：仅 `academic_admin`（`@RequireAcademicAdmin`）。

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
    "semesterId": 1,
    "semesterName": "2026-2027学年第一学期",
    "classes": ["计科2201", "计科2101"],
    "countByClass": {
      "计科2201": 5,
      "计科2101": 3
    },
    "totalDrafts": 5
  }
}
```

> 响应字段：`semesterId` / `semesterName` 取自草稿中第一条非空学期（无则整个键不出现）；`classes` 把合班字段按逗号拆开后去重；`countByClass` 同样按拆开后的**单个班级**计数（合班草稿会同时计入每个班）；`totalDrafts` 是草稿**条数**（不是班级数）。

**错误场景**

| message |
|---------|
| 没有待排课的授课草稿，请先通过 POST /api/teach-info/draft 提交授课安排 |

> 触发排课前若缓存为空，求解接口会返回 500 错误并提示先提交草稿。

---

### 5.9 学生周课表查询（Redis 缓存）

学生查询指定周次的**个人**课表（本班必修课 + 本人已分班的选课班课程）。首次查询走数据库并写入 Redis 缓存，该学生再次查询同一周直接命中缓存。个人维度是因为选课班课程因人而异，不能按班级共享缓存。

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
| Redis Key | `schedule:user:student:{userId}:t_:c_:w{week}`（维度：用户 + 教师筛选 + 课程筛选 + 周次） |
| TTL | 15 分钟 |
| 更新时机 | 授课安排增删改、排课写回、分班、为选课班分配/取消教师时，按班级名 / 教师 / 学生维度精准淘汰 |
| 缓存内容 | 序列化的 `List<CourseDto>` JSON（显式声明元素类型，保留 `category` 多态标识） |

> 5.3 的本班课程速览另走 `schedule:class-courses:{userId}`，TTL 1 小时，学生换班或排课变更时按用户维度淘汰。

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
8. 求解按 `application.yaml` 中 `timefold.solver.termination` 配置自动终止（当前：最长 5 分钟，或最优解 30 秒无改进），终止后轮询返回 `FINISHED`；也可随时调用 8.3 手动终止

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

学生选课采用"活动即课程"模式：教务管理员创建选课活动（同时录入课程信息）-> 绑定选课组 -> 开放选课 -> 学生抢选 -> 关闭 -> 分班。选课容量通过 Redis + Lua 脚本原子控制，避免超选。

> **设计要点**：一个选课活动本身就是一门公选课，`selection_campaign` 表直接携带课程字段（courseName/courseCode/credit/courseHour/description/courseType/allowedGradeIds/allowedMajors/capacity）。**活动名称 `name` 同时作为课程名称**（`course_name` 列，单一事实源）。**不在 `course` 表生成衍生记录**：下游表（`teach_info` / `score` / `exam` / `time_restriction` / `teaching_evaluation`）通过可空的 `campaign_id` 引用活动，富化时按 `campaign_id` 查本表。`GET /api/courses` 里出现的公选课条目只是**运行时合成**的展示视图（见 5.5），不落库。不再有独立的 `selection_course` 表。

### 10.1 状态流转与角色权限

**活动状态机**

```
DRAFT --open()--> OPEN --close()--> CLOSED --finalize()--> FINALIZED
```

| 状态 | 含义 | 允许的操作 |
|------|------|-----------|
| DRAFT | 草稿 | 修改活动（含课程字段）、绑定/解绑选课组、删除活动、开启 |
| OPEN | 开放选课 | 学生选课/退选、关闭 |
| CLOSED | 关闭 | 分班 |
| FINALIZED | 已分班 | 终态，仅可查询分班结果 |

> 状态只能顺序流转，不可回退。仅 DRAFT 状态可修改/删除，仅 OPEN 状态可选课/退选。

**角色权限**

| 角色 | 权限 |
|------|------|
| academic_admin | 增删改查选课组、绑定/解绑活动与选课组、增删改查活动（含课程字段）、开启/关闭/分班、查看分班结果 |
| student | 查询 OPEN 活动（含课程信息）、选课/退选、查询本人选课记录 |
| teacher / department | 无任何选课接口权限 |

> 管理端接口（10.2 / 10.3 / 10.4）仅 `academic_admin` 可调用；学生端接口（10.5）仅 `student` 可调用。教师与院系管理者无法调用任何选课接口。

**Redis 容量控制**

选课期间使用 Redis Lua 脚本原子 INCR + 上限校验，避免并发超选：

| 项目 | 说明 |
|------|------|
| Redis Key | `selection:count:{campaignId}` |
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
| startWeek | Integer | 否 | 排课起始周，默认 1 |
| endWeek | Integer | 否 | 排课结束周，默认 16 |
| groupId | Long | 否 | 选课组 ID；非空时在创建活动的同时绑定到该组（直接写入 campaign.group_id） |
| courseCode | String | 是 | 课程编号（学期内唯一） |
| credit | Integer | 是 | 学分（>= 0） |
| courseHour | Integer | 否 | 课程学时 |
| description | String | 否 | 课程描述 |
| courseType | String | 否 | 课程类型（CurseEnum 中文描述）：`必修` / `选修` / `公选` / `实践`。**必须传描述字符串，不要传数字**（Jackson 按枚举序号解析，数字 1 会落到「选修」，见 5.5） |
| allowedGradeIds | List\<Long\> | 否 | 允许的年级 ID 列表；空表示不限 |
| allowedMajors | List\<Long\> | 否 | 允许的专业 ID 列表；空表示不限 |
| timeRestrictionIds | List\<Long\> | 否 | RESERVED 类型时段限制 ID 列表（排课备选时段） |
| capacity | Integer | 是 | 选课容量上限（必须 > 0） |

> **说明**：`name` 字段同时作为活动名称与课程名称，无需额外传 `courseName`。

**请求示例**

```json
{
  "name": "2026秋季选课-高等数学",
  "semesterId": 1,
  "startTime": "2026-09-01T08:00:00",
  "endTime": "2026-09-07T22:00:00",
  "startWeek": 1,
  "endWeek": 16,
  "groupId": 1,
  "courseCode": "MATH101",
  "credit": 4,
  "courseHour": 64,
  "courseType": "必修",
  "allowedGradeIds": [2024, 2025],
  "allowedMajors": [1, 2],
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
    "name": "2026秋季选课-高等数学",
    "semesterId": 1,
    "semesterName": "2026-2027学年第一学期",
    "courseId": null,
    "startWeek": 1,
    "endWeek": 16,
    "startTime": "2026-09-01T08:00:00",
    "endTime": "2026-09-07T22:00:00",
    "status": "DRAFT",
    "courseCode": "MATH101",
    "credit": 4,
    "courseHour": 64,
    "description": null,
    "courseType": "必修",
    "allowedGradeIds": [2024, 2025],
    "allowedMajors": [1, 2],
    "timeRestrictionIds": [],
    "capacity": 30,
    "groupId": 1,
    "groupName": "公共选修课组",
    "createTime": "2026-08-20T10:00:00"
  }
}
```

**CampaignResponse 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 活动 ID |
| name | String | 活动名称（= 课程名称） |
| semesterId | Long | 学期 ID |
| semesterName | String | 学期名称（冗余） |
| courseId | Long | 兼容字段；公选课不再关联 `course` 表，**恒为 null** |
| startWeek | Integer | 排课起始周 |
| endWeek | Integer | 排课结束周 |
| startTime | LocalDateTime | 选课开始时间 |
| endTime | LocalDateTime | 选课结束时间 |
| status | String | DRAFT / OPEN / CLOSED / FINALIZED |
| courseCode | String | 课程编号 |
| credit | Integer | 学分 |
| courseHour | Integer | 课程学时 |
| description | String | 课程描述 |
| courseType | String | 课程类型描述（必修/选修/公选/实践） |
| allowedGradeIds | List\<Long\> | 允许年级 ID 列表 |
| allowedMajors | List\<Long\> | 允许专业 ID 列表 |
| timeRestrictionIds | List\<Long\> | 绑定的时段限制 ID 列表 |
| capacity | Integer | 容量上限 |
| groupId | Long | 绑定的选课组 ID（未绑定为 null） |
| groupName | String | 绑定的选课组名称（未绑定为 null） |
| createTime | LocalDateTime | 创建时间 |
| boundGroupId | Long | 仅 `listBindableForGroup` 场景填充，标识当前活动是否已绑定到该组 |

**错误场景**

| message |
|---------|
| 学期不存在 |
| 结束时间不能早于开始时间 |
| 活动名称不能为空 |
| 课程编号不能为空 |
| 学分必须 >= 0 |
| 容量必须大于0 |
| 该课程编号已在本学期中存在 |
| 选课组不存在 |
| 时段限制 {id} 不存在 |
| 时段限制 {id} 必须为 RESERVED 类型 |
| 时段限制 {id} 已预留给其他课程 |

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
| name | String | 否 | 活动名称（= 课程名称，直接写本表 `course_name`） |
| semesterId | Long | 否 | 学期 ID |
| startTime | LocalDateTime | 否 | 选课开始时间 |
| endTime | LocalDateTime | 否 | 选课结束时间 |
| startWeek | Integer | 否 | 排课起始周 |
| endWeek | Integer | 否 | 排课结束周 |
| groupId | Long | 否 | 选课组 ID；非空且与当前绑定不同时触发换绑（要求 DRAFT 状态） |
| unbindGroup | Boolean | 否 | 是否解绑当前选课组，默认 false。为 true 时清除 campaign.group_id（要求 DRAFT 状态）。与 groupId 互斥 |
| courseCode | String | 否 | 课程编号（学期内唯一，直接写本表） |
| credit | Integer | 否 | 学分 |
| courseHour | Integer | 否 | 课程学时 |
| description | String | 否 | 课程描述 |
| courseType | String | 否 | 课程类型中文描述（`必修` / `选修` / `公选` / `实践`），不要传数字 |
| allowedGradeIds | List\<Long\> | 否 | 允许年级 ID 列表 |
| allowedMajors | List\<Long\> | 否 | 允许专业 ID 列表 |
| timeRestrictionIds | List\<Long\> | 否 | 重绑时段限制（先清理旧关联与本活动的 RESERVED 时段限制，再按新列表绑定） |
| capacity | Integer | 否 | 容量上限（必须 > 0） |

**错误场景**

| message |
|---------|
| 选课活动不存在 |
| 仅草稿状态的活动可修改 |
| 学期不存在 |
| 结束时间不能早于开始时间 |
| 活动名称不能为空 |
| 课程编号不能为空 |
| 学分必须 >= 0 |
| 容量必须大于0 |
| 该课程编号已在本学期中存在 |
| 选课组不存在 |
| groupId 与 unbindGroup=true 不可同时传入 |
| 时段限制 {id} 不存在 |
| 时段限制 {id} 必须为 RESERVED 类型 |
| 时段限制 {id} 已预留给其他课程 |

#### 10.2.5 删除活动

仅 DRAFT 状态可删除，应用层级联清理（DB 无外键）：选课班成员 → 选课班 → 选课记录 → 活动-时段限制关联 → 本活动的 RESERVED 时段限制 → Redis 选课计数器 → 活动主表。公选课没有衍生 `course` 记录，无需联动删课程。

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

> **校验**：开启前会检查 `name` / `courseCode` / `capacity` 必须有值，否则返回 400「活动名称不能为空」等错误。

**错误场景**

| message |
|---------|
| 选课活动不存在 |
| 仅草稿状态的活动可开启 |
| 活动名称不能为空 |
| 课程编号不能为空 |
| 容量必须大于0 |

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

- 一个活动即一门课，按 `selectTime` 升序排列已选记录
- 每班最多容纳 `selection_campaign.capacity` 名学生，超出则新建下一个班（classNo 从 1 递增）
- 每个班对应一条 `teach_info`（`campaign_id` 指向本活动、`course_id` 为 NULL，className = `{活动名}-{classNo}`）
- 已有分班数据会先清理后重建（幂等）

**错误场景**

| message |
|---------|
| 选课活动不存在 |
| 仅关闭状态的活动可分班 |

### 10.3 选课组管理（教务管理员）

选课组是独立实体，可被多个选课活动复用；一个选课活动只能绑定一个选课组（通过 `selection_campaign.group_id` 字段直接存储）。每个选课组定义组内学生选课数上限（`maxCourses`）。

> **组内选课上限的统计范围**：`maxCourses` 是组级别约束，**跨所有绑定该组的活动共用**——即一个学生在该组下、跨所有活动已选课程总数不能超过 `maxCourses`。不同选课组之间独立计数，互不影响。

**典型流程**：教务管理员先通过 `/api/selection/groups` 创建/复用选课组，再通过 `/api/selection/campaigns`（POST 创建或 PUT 修改，携带 `groupId` 字段，见 10.2）把选课组绑定到具体活动。

```
POST   /api/selection/groups                                   # 新建选课组
GET    /api/selection/groups                                   # 查询全部选课组
GET    /api/selection/groups/{groupId}                         # 查询选课组详情
PUT    /api/selection/groups/{groupId}                         # 修改选课组
DELETE /api/selection/groups/{groupId}                         # 删除选课组（未绑定任何活动时）
GET    /api/selection/groups/{groupId}/bindable-campaigns      # 查询可绑定到本组的活动
Authorization: Bearer <accessToken>
```

**权限**：全部接口仅 `academic_admin` 可调用，其他角色返回 403。绑定/换绑通过 [10.2](#102-选课活动管理教务管理员) 的 create/update 接口完成，仅 DRAFT 状态活动可修改绑定。

#### 10.3.1 新建选课组

```
POST /api/selection/groups
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 组名称（全局唯一） |
| maxCourses | Integer | 是 | 本组学生最多选课门数（必须 > 0） |

**请求示例**

```json
{
  "name": "公共选修课组",
  "maxCourses": 2
}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "公共选修课组",
    "maxCourses": 2,
    "campaignCount": 0,
    "createTime": "2026-07-25T20:50:00"
  }
}
```

**错误场景**

| message |
|---------|
| 组名已存在 |
| 本组选课上限必须大于0 |

#### 10.3.2 查询全部选课组

```
GET /api/selection/groups
Authorization: Bearer <accessToken>
```

返回系统中所有独立选课组，按 `id` 升序排列。每项含绑定到该组的选课活动数（一个活动即一门课程，故也是组内课程数）。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "公共选修课组",
      "maxCourses": 2,
      "campaignCount": 3,
      "createTime": "2026-07-25T20:50:00"
    }
  ]
}
```

#### 10.3.3 查询选课组详情

```
GET /api/selection/groups/{groupId}
Authorization: Bearer <accessToken>
```

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| groupId | Long | 是 | 选课组 ID |

**响应字段**：同 [10.3.1](#1031-新建选课组) 响应，`campaignCount` 填充实际绑定活动数。

**错误场景**

| message |
|---------|
| 选课组不存在 |

#### 10.3.4 修改选课组

```
PUT /api/selection/groups/{groupId}
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**请求体**（所有字段可选，仅传需要修改的字段）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 否 | 新组名称（全局唯一，不能与其他组重名） |
| maxCourses | Integer | 否 | 新选课上限（必须 > 0） |

> **约束**：若 `maxCourses` 变更且该组被任何 OPEN 状态活动绑定，返回 409，避免破坏进行中的选课不变量。`name` 修改不受此限制。

**请求示例**

```json
{
  "maxCourses": 3
}
```

**错误场景**

| message |
|---------|
| 选课组不存在 |
| 组名已存在 |
| 本组选课上限必须大于0 |
| 组已被开放中的活动绑定，不可修改选课上限 |

#### 10.3.5 删除选课组

```
DELETE /api/selection/groups/{groupId}
Authorization: Bearer <accessToken>
```

**约束**：仅当该组未被任何活动绑定时可删除。已绑定的组需先在所有绑定该组的活动中清除 `groupId` 绑定（通过 [10.2.4](#1024-修改活动) 修改活动接口，传入 `unbindGroup=true` 解绑，或换绑到其它组）。

**错误场景**

| message |
|---------|
| 选课组不存在 |
| 请先清除绑定到本组的活动后再删除 |

**SelectionGroupResponse 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 选课组 ID |
| name | String | 组名称 |
| maxCourses | Integer | 本组学生最多选课门数 |
| campaignCount | Integer | 绑定到该组的选课活动数（也是组内课程数，因为一个活动即一门课） |
| createTime | LocalDateTime | 选课组创建时间 |

#### 10.3.6 查询可绑定到本组的活动

```
GET /api/selection/groups/{groupId}/bindable-campaigns
Authorization: Bearer <accessToken>
```

**权限**：仅 `academic_admin`。

列出可绑定到指定选课组的活动：`group_id` 为空（未绑定任何组）的活动 + 已绑定到本组的活动；已绑定到**其它**组的活动不在结果中（一个活动只能绑定一个组）。每项为 [CampaignResponse](#1021-新建选课活动)（字段见 10.2.1），其中已绑定到本组的项额外带 `boundGroupId = {groupId}`，前端可据此区分「已在本组」与「可加入」。

**错误场景**

| message |
|---------|
| 选课组不存在 |

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
| courseName | String | 课程名称（来自 campaign.name） |
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

#### 10.4.1 为选课班分配教师

```
PUT /api/selection/campaigns/{campaignId}/classes/{classId}/teacher
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**权限**：仅 `academic_admin`。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| teacherId | Long | 否 | 教师记录主键（`teacher` 表 id）；传 `null` 表示**取消**已分配教师 |

```json
{ "teacherId": 5 }
```

**响应**：`SelectionClassResponse`（同 10.4 列表项），对应 `teach_info.teacher_id` 已写入。

**规则**：
- 仅 `FINALIZED`（已分班）活动可分配教师
- 教师写到该选课班关联的 `teach_info` 行，并同步草稿缓存中的教师字段
- 同步清理该班级与教师的课表缓存（`classScheduleCacheManager`）
- `teacherId=null` 用显式 `set` 写入，能真正清空（MyBatis Plus `updateById` 默认跳过 null 字段）

**错误场景**

| HTTP 状态码 | message |
|-------------|---------|
| 404 | 选课活动不存在 |
| 409 | 仅已分班的活动可分配教师 |
| 404 | 选课班不存在或不属于该活动 |
| 409 | 选课班未关联教学信息 |
| 404 | 教学信息不存在 |
| 400 | 引用的教师不存在(id=x) |

### 10.5 学生选课

```
GET    /api/selection/student/campaigns                        # 查询可选课活动（OPEN，含课程信息+组上下文+选课状态）
GET    /api/selection/student/campaigns/{campaignId}           # 查询活动详情（含课程信息+组上下文+选课状态）
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

返回当前处于 OPEN 状态的活动列表，每项为 `StudentCampaignResponse`（含课程信息、组上下文、实时选课统计、本人是否已选）。

#### 10.5.2 查询活动详情

```
GET /api/selection/student/campaigns/{campaignId}
Authorization: Bearer <accessToken>
```

返回单个活动详情，响应字段同 [10.5.1](#1051-查询可选课活动)。

**响应示例（StudentCampaignResponse）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "2026秋季选课-高等数学",
    "semesterId": 1,
    "semesterName": "2026-2027学年第一学期",
    "startWeek": 1,
    "endWeek": 16,
    "startTime": "2026-09-01T08:00:00",
    "endTime": "2026-09-07T22:00:00",
    "status": "OPEN",
    "courseId": 42,
    "courseCode": "MATH101",
    "credit": 4,
    "courseHour": 64,
    "description": null,
    "courseType": "必修",
    "allowedGradeIds": [2024, 2025],
    "allowedMajors": [1, 2],
    "timeRestrictionIds": [],
    "capacity": 30,
    "groupId": 1,
    "groupName": "公共选修课组",
    "groupMax": 2,
    "selectedInGroup": 1,
    "selectedCount": 18,
    "remaining": 12,
    "selectedByMe": false
  }
}
```

**StudentCampaignResponse 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 活动 ID |
| name | String | 活动名称（= 课程名称） |
| semesterId / semesterName | Long / String | 学期信息 |
| startWeek / endWeek | Integer | 排课周次 |
| startTime / endTime | LocalDateTime | 选课时间窗口 |
| status | String | 活动状态（学生端见到的应为 OPEN） |
| courseId | Long | 兼容字段，公选课恒为 null |
| courseCode / credit / courseHour / description / courseType | - | 课程字段（直接来自 `selection_campaign`） |
| allowedGradeIds / allowedMajors | List\<Long\> | 允许的年级/专业 ID 列表 |
| timeRestrictionIds | List\<Long\> | 绑定的时段限制 ID |
| capacity | Integer | 容量上限 |
| groupId / groupName / groupMax | Long / String / Integer | 所属选课组信息 |
| selectedInGroup | Integer | 学生当前在该组下已选课程数（跨活动合计） |
| selectedCount | Integer | 该活动当前已选人数（实时） |
| remaining | Integer | 剩余容量（实时） |
| selectedByMe | Boolean | 当前学生是否已选该活动 |

#### 10.5.3 选课

```
POST /api/selection/student/records
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| campaignId | Long | 是 | 活动 ID（一个活动即一门课） |

**请求示例**

```json
{
  "campaignId": 1
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
| courseName | String | 课程名称（来自 campaign.name） |
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
| 4. 学生信息存在 | 学生信息不存在 |
| 5. 年级/专业允许 | 本课程不对您的年级或专业开放 |
| 6. 未超过组内选课上限 | 超过本组选课上限 N 门 |
| 7. 未重复选同一活动 | 已选该课程 |
| 8. Redis 容量未满 | 课程已满 |

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

## 11. 文件模块

通用文件传输：分片上传（断点续传 / 秒传 / SHA-256 完整性校验）、小文件整传、可续传下载、
在线预览（Range 懒加载）。

**鉴权**：所有端点只要求「已登录」，**不做归属判权** —— 文件归属由各业务表自行记录，
敏感文件一律走业务自己的下载端点。

**限流**：整组走 `/api/file/**` 独立桶 **600 次/分钟**（2GB ÷ 5MB = 410 个分片请求，
落在全站默认的 120/分钟上必然中途被限流）。

**大小**：整传受 multipart 25MB 约束；分片路径的请求体是**裸二进制**（不经 multipart 解析），
上限由 `file.max-file-size` 决定，当前 **2GB**。

> 字段含义、前端上传器必须遵守的约定、完整错误码，见根目录 **《文件上传下载接口文档.md》**。

### 11.1 端点总览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/file/uploads` | 初始化 / 恢复上传会话（幂等） |
| PUT | `/api/file/uploads/{uploadId}/parts/{index}` | 上传一个分片 |
| POST | `/api/file/uploads/{uploadId}/complete` | 合并分片为成品（幂等） |
| GET | `/api/file/uploads/{uploadId}` | 查询上传进度 |
| POST | `/api/file/uploads/{uploadId}/verify` | 校验已收分片 |
| DELETE | `/api/file/uploads/{uploadId}` | 取消上传 |
| POST | `/api/file/whole` | 小文件整传 |
| POST | `/api/file/download` | 通用下载 |
| GET | `/api/file/preview` | 在线预览（inline + Range 懒加载 → 206） |
| GET | `/api/file/preview/info` | 预览元信息（大小 / 类型 / 可预览性） |

### 11.2 完整分片上传流程

**① 初始化**

```bash
curl -X POST "http://localhost:8080/api/file/uploads"   -H "Authorization: Bearer <accessToken>"   -H "Content-Type: application/json"   -d '{"biz":"graduation-thesis","originalName":"论文.pdf",
       "totalSize":104857600,"totalChunks":21,"sha256":"<整文件 SHA-256>"}'
```

```json
{ "code": 200, "data": {
    "uploadId": "a1b2c3…", "chunkSize": 5242880, "totalChunks": 21,
    "receivedChunks": [], "receivedCount": 0, "completedFile": null } }
```

- `biz` 取值：`graduation-thesis` / `graduation-opening-report` / `graduation-midterm` /
  `internship-report` / `social-practice-report` / `graduation-duplicate-report` /
  `graduation-campaign-material` / `graduation-defense-material` / `competition-certificate`
  （服务端白名单，其它值 400）。扩展名白名单按业务分组：前 8 个为文档类
  （`.doc/.docx/.pdf/.zip/.rar`），`competition-certificate` 为证书类（`.jpg/.jpeg/.png/.pdf`）。
- **`completedFile` 非 null = 秒传命中**，跳过 ②③，直接进业务提交。
- `uploadId` 是**确定性**的：同一用户 + 同一 biz + 同一文件内容，反复 `init` 都得到同一个值。
  刷新页面后**用相同参数重新 `init`** 即可恢复进度，无需本地持久化。

**② 逐片上传**（`index` 从 0 起，请求体是裸二进制，**不是 multipart**）

```bash
curl -X PUT "http://localhost:8080/api/file/uploads/{uploadId}/parts/0"   -H "Authorization: Bearer <accessToken>"   -H "Content-Type: application/octet-stream"   -H "X-Chunk-SHA256: <该片的 SHA-256>"   --data-binary @part0.bin
# → { "code": 200, "data": { "index": 0, "receivedCount": 1 } }
```

片大小必须是 `init` 返回的 `chunkSize`（最后一片为余数）；`X-Chunk-SHA256` **必传**，
否则坏片无法定位、只能全量重传。同序号重传幂等。

**③ 合并**

```bash
curl -X POST "http://localhost:8080/api/file/uploads/{uploadId}/complete"   -H "Authorization: Bearer <accessToken>"
# → { "code": 200, "data": { "storedPath": "objects/graduation-thesis/<sha256>.pdf",
#                            "originalName": "论文.pdf", "size": 104857600,
#                            "sha256": "<sha256>", "biz": "graduation-thesis" } }
```

把 `storedPath` 填进业务请求的 `data.filePath`（建议同时带 `data.fileOriginal`）。
合并是**幂等**的，超时重试安全。

**④ 出错时**：先 `POST /api/file/uploads/{uploadId}/verify` 拿缺失 / 损坏清单，**精准重传**这几片，
再重新 `complete` —— 不必整个文件重来。用户主动放弃时 `DELETE /api/file/uploads/{uploadId}`。

### 11.3 通用下载

```bash
curl -X POST "http://localhost:8080/api/file/download"   -H "Authorization: Bearer <accessToken>"   -H "Content-Type: application/json"   -H "Range: bytes=0-1023"   -d '{"filePath":"objects/graduation-thesis/<sha256>.pdf","originalName":"论文.pdf"}'
```

用 POST 承载路径是为了让**路径不进 URL、浏览器历史与网关日志**（服务端日志也只记展示名）。

> ⚠️ **`Range` 在 POST 下依然返回 206**，但浏览器 / `wget -c` / IDM 的**自动**断点续传只对 GET 发起。
> 用 POST 下载要续传必须前端自研分块循环。需要下载器自动续传时请用业务下载端点。

### 11.4 与业务端点的衔接

practice 的 5 个提交端点（论文 / 开题 / 中期 / 实习报告 / 社会实践报告）支持**二选一**：

- 小文件：照旧传 multipart 的 `file` 部分（≤20MB）
- 大文件：不传 `file`，改在 `data` 里带 `filePath` + `fileOriginal`

两者同时给会返回 400；`filePath` 必须属于该端点对应的业务目录，否则 403。

### 11.5 业务附件端点（毕业设计 / 竞赛）

以下附件点全部复用第 11 节的传输能力：小文件走各自端点的 multipart `file` 部分整传（≤20MB），
大文件先走分片上传、再以 `filePath` + `fileOriginal`（表单字段或 `data` 内字段）提交产物路径，
两者同时给 400。下载端点均为 GET 且支持 **Range 断点续传（206）+ 强 ETag**。

#### 11.5.1 查重报告附件

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/graduation/theses/duplicate-checks` | 教务 | 登记查重结果，可附报告（`data` + 可选 `file`） |
| GET | `/api/practice/graduation/theses/duplicate-checks/{checkId}/download` | 学生本人/指导教师/院系/教务 | 报告下载（无附件 404） |

> ⚠️ **Breaking change**：登记接口由 `application/json` 改为 `multipart/form-data`
> （`@RequestPart("data")` 承载原 JSON，字段不变并新增可选 `filePath`/`fileOriginal`；
> `@RequestPart("file")` 可选承载整传文件）。前端需同步改为 multipart 表单提交。

#### 11.5.2 毕设活动资料（一个活动多份）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/graduation/campaigns/{id}/materials` | 教务 | 上传资料（`file` 或 `filePath`+`fileOriginal` 二选一，必填其一） |
| GET | `/api/practice/graduation/campaigns/{id}/materials` | 四种角色 | 资料列表（可见性与活动详情一致） |
| GET | `/api/practice/graduation/campaigns/{id}/materials/{mid}/download` | 四种角色 | 资料下载 |
| DELETE | `/api/practice/graduation/campaigns/{id}/materials/{mid}` | 教务 | 删除（逻辑删，文件由回收任务对账清理） |

上传/删除动作均记入活动操作日志（JSONL）。

#### 11.5.3 答辩材料附件

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/graduation/defense/{id}/material` | 院系（本院）/教务 | 上传材料，重复上传为**替换** |
| GET | `/api/practice/graduation/defense/{id}/material/download` | 学生本人/院系/教务 | 材料下载（未上传 404） |

#### 11.5.4 竞赛获奖证书附件

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/competitions/results/{id}/certificate` | 教务 | 上传/替换证书（`.jpg/.jpeg/.png/.pdf`） |
| GET | `/api/practice/competitions/results/{id}/certificate/download` | 教务/获奖学生本人 | 证书下载（未上传 404） |

删除竞赛结果时证书引用一并释放。

#### 11.5.5 导出类端点的传输层变化

- `GET /api/practice/graduation/theses/export-package`（查重数据包）：改为**流式生成 zip 落盘**
  （不再整个包驻留内存），响应支持 Range 断点续传；暂存文件由服务端按
  `file.export-expire-hours`（默认 24h）自动清理。
- `GET /api/scores/export`、`GET /api/practice/graduation/dashboard/{campaignId}/export`、
  `GET /api/practice/graduation/defense/scores/export`：响应头统一为
  RFC5987 文件名 + `Accept-Ranges: bytes` + 内容 SHA-256 强 ETag（支持 206）。

### 11.6 在线预览（懒加载）

浏览器内联预览专用端点，与「下载」的核心差异是 `Content-Disposition: inline`（浏览器直接渲染
而非触发下载）+ **GET**（pdf.js / `<img>` / `<video>` / iframe 只能发 GET，无法 POST）。

**为什么懒加载能解决大文件慢**：客户端带 `Range: bytes=a-b` 请求时服务端只回该分段（206），
pdf.js 按页拉取、`<video>` 按拖动位置拉取，**首屏永不整传**。内容寻址产物附带
`Cache-Control: private, max-age=1年, immutable`（路径即内容摘要，字节永不改变），
**二次打开零传输**。

#### 11.6.1 预览元信息（先调，零内容传输）

```bash
curl "http://localhost:8080/api/file/preview/info?filePath=objects/course-material/<sha256>.pdf" \
  -H "Authorization: Bearer <accessToken>"
# → { "code": 200, "data": { "size": 52428800, "contentType": "application/pdf", "previewable": true } }
```

前端据此决定策略：`previewable=false`（docx/xlsx/zip 等浏览器无法原生渲染的类型）→ 直接回退
`POST /api/file/download`；`size` 大 → 提示「将按需加载」；`contentType` → 选 viewer。

#### 11.6.2 预览内容

```bash
curl "http://localhost:8080/api/file/preview?filePath=objects/course-material/<sha256>.pdf&originalName=第一章课件.pdf" \
  -H "Authorization: Bearer <accessToken>" -H "Range: bytes=0-1048575"
# → 206 Partial Content / Content-Range: bytes 0-1048575/52428800
#   响应头：Content-Disposition: inline; filename*=UTF-8''...  +  Accept-Ranges: bytes
#         + 强 ETag  +  Cache-Control: private, max-age=31536000, immutable
#         +  X-Content-Type-Options: nosniff
```

- `filePath` 必填（query）；`originalName` 选填，只影响 viewer 里显示的文件名。
- **可预览类型白名单**：`.pdf` / `.jpg` / `.jpeg` / `.png` / `.mp4`（= 各业务扩展名白名单中
  浏览器能原生渲染的子集）。其余类型返回 body code **415**，前端回退下载。
- 不带 `Range` 时返回 200 完整正文（小图等一次性加载场景）。

#### 11.6.3 前端对接要点

- **鉴权仍是 Bearer 头**（本组端点不做归属判权，与通用下载同一暴露面；敏感文件请走业务判权端点）：
  - pdf.js：`pdfjsLib.getDocument({ url, httpHeaders: { Authorization: 'Bearer ' + token } })`，
    它会自动用 Range 分段拉取 —— **这就是懒加载，无需前端自己分块**。
  - `<img>` / `<video>` 标签无法带请求头：先 `fetch(url, { headers })` 拿 Blob 再
    `URL.createObjectURL`；要保留视频拖动分段能力则用 fetch + MSE 或接受整载。
- **限流**：预览与上传同走 `/api/file/**` 桶（600 次/分钟）。pdf.js 默认 64KB/段偏碎，
  大 PDF 建议把 `rangeChunkSize` 调到 **1~2MB**（50MB PDF ≈ 25~50 段，远低于桶上限）。
- **错误形态**与全站一致：HTTP 200 + body `code`（404 文件不存在 / 400 路径非法 / 415 不可预览）。

---

## 12. 课程课业模块

课程课业（`module/coursework`）为**授课安排**提供作业闭环、教学视频、课程资料、课程公告与平时分合成五项能力。

> **对接前端请优先读《docs/课程课业模块-前端对接文档.md》** —— 那里有按页面组织的完整请求/响应示例、
> 枚举与时间序列化取值、错误码分支（业务错误是 HTTP 200 + body.code）、13 条易踩坑清单与联调自检清单。
> 视频播放的 WebSocket 协议见《docs/课程视频WS协议.md》。本节只列端点与关键约定。

### 12.1 授课组锚点（重要约定）

同一「学期 + 课程/活动 + 教师 + 班级名」在 `teach_info` 里可能有多条行（对应多个上课时段）。
课业内容**只挂其中的锚点行**（组内最小 `id`），服务端在写入/读取时统一归一。

因此所有 `teachInfoId` 入参**接受组内任意一行**，无需前端自己找锚点；返回的 `teachInfoId` 一律是锚点行 id。

### 12.2 作业

**题库（教师个人）**：

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/coursework/question-bank?page=&pageSize=&courseId=&campaignId=&type=&keyword=` | 教师 | 分页（type 为题型枚举，keyword 匹配题干） |
| POST | `/api/coursework/question-bank` | 教师 | 录入 |
| PUT | `/api/coursework/question-bank/{id}` | 教师 | 修改 |
| DELETE | `/api/coursework/question-bank/{id}` | 教师 | 软删 |

**作业与作答**：

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/coursework/assignments` | 教师 | 新建（`data` 必填，可选 `file`；`publish=true` 直接发布） |
| PUT | `/api/coursework/assignments/{id}` | 教师 | 修改（JSON；**PUBLISHED 拒绝题目变更、只允许延长截止**） |
| POST | `/api/coursework/assignments/{id}/attachment` | 教师 | 替换附件（multipart，因 Tomcat 不解析 PUT 的 multipart） |
| POST | `/api/coursework/assignments/{id}/clone` | 教师 | 克隆到其它授课组 `{teachInfoId, deadline}`（题目快照随复制，落 DRAFT） |
| DELETE | `/api/coursework/assignments/{id}` | 教师 | 删除（**仅草稿**；级联软删题目快照） |
| POST | `/api/coursework/assignments/{id}/publish` | 教师 | 发布（DRAFT → PUBLISHED，**要求 ≥1 道 score>0 的题目**，通知授课组学生） |
| POST | `/api/coursework/assignments/{id}/close` | 教师 | 关闭（PUBLISHED → CLOSED，停止提交） |
| GET | `/api/coursework/assignments?teachInfoId=` | 教师/学生 | 分页列表（教师带提交统计，学生带我的提交摘要） |
| GET | `/api/coursework/assignments/{id}` | 教师/学生 | 详情（草稿对学生按 404；学生视图**不含**标准答案/解析，`myAnswer` 仅在未提交时回显草稿） |
| GET | `/api/coursework/assignments/{id}/submissions` | 教师 | 提交名单（花名册 LEFT JOIN，**含未交学生**；五态 `state`：`GRADED`/`SUBMITTED`/`DRAFTING`/`VIEWED`/`NOT_VIEWED` + `autoScore`） |
| POST | `/api/coursework/assignments/{id}/submit` | 学生 | 提交/重交（**纯 JSON** `{answers:[{questionId, answer}]}`，不再是 multipart） |
| PUT | `/api/coursework/assignments/{id}/draft` | 学生 | 暂存草稿（**仅 Redis**，整体替换 `{answers:[...]}`，提交后自动失效） |
| GET | `/api/coursework/assignments/{id}/my-submission` | 学生 | 我的提交（未提交返回 `data: null`；标准答案/解析受 `answerVisible` 门控） |
| GET | `/api/coursework/submissions/{id}` | 教师 | 单份提交详情（**总是**含标准答案/解析，供批改） |
| POST | `/api/coursework/submissions/{id}/grade` | 教师 | 逐题批改 `{items:[{answerId, score, comment}], comment}`；**客观题不可改判**；大题全部批完 → 状态 GRADED、通知学生 |

**状态机**：`DRAFT → PUBLISHED → CLOSED`。仅草稿可删除；已发布可关闭但不能回退。

**出题模型**：作业题目 = `questionIds`（题库引用 `[{questionId, score}]`）+ `newQuestions`（直接内嵌新题，默认同步存入题库），发布时对题目做**完整快照**——之后题库改动不影响已发布作业。`totalScore` 由 Σ题目配分派生，请求传入值被忽略。

**题型与作答 JSON**（`answer` 字段按题型区分形状）：

| 题型 | answer 形状 | 判分 |
|------|------------|------|
| `SINGLE_CHOICE` | `"A"` | 自动（与标准答案精确相等） |
| `MULTI_CHOICE` | `["A","B"]` | 自动；多选计分规则 `ALL_OR_NOTHING` / `HALF_ON_PARTIAL`（默认后者，教师可选） |
| `JUDGE` | `true` / `false` | 自动 |
| `FILL_BLANK` | `["空1","空2"]` | 自动；支持无序匹配、可选大小写敏感 |
| `ESSAY` | `{text, files:[{path, original}]}` | 教师人工批改 |

**附件**：作业附件 `data` 里给 `filePath`（分片产物）或表单给 `file`（整传），二选一，同时给 400；
作业附件 ≤20MB，允许 `.doc/.docx/.pdf/.zip/.rar`。大题附件（≤3 个/题）先走 `/api/file` 上传拿 `path`
（biz=`course-assignment-submission`，放行文档/压缩包/`.jpg`/`.jpeg`/`.png`），`requireFile=true` 的大题必须带附件才可交。

**自动判分**：提交时客观题即时判分出分；**全客观作业提交即 `GRADED`**（`autoScore`/`score` 即时给出，不通知教师）；
含大题的作业提交后状态 `SUBMITTED`，由教师逐题批改，大题全批完自动 `GRADED` 并通知。

**`answerVisible`**（作业级，门控学生提交后可见标准答案/解析的时机）：`SUBMIT`（默认，提交后即可见）/
`DEADLINE`（截止后）/`CLOSED`（作业关闭后）/`NEVER`（不可见）。教师端与批改视图不受限。

**重交语义**：截止前可反复提交，`version` 递增；重交会**清空已批改痕迹**（`score`/`comment`/`gradeTime` 置空、状态回到 `SUBMITTED`），教师需重新批改。
重交为**全量替换**：旧答案行软删、按当次提交重建（附件以 `answer_json.files` 为准）。

**空提交**：`submit` 允许 `answers` 为空/null（视为全部未答，仍可提交占位）；`saveDraft` 则要求 `answers` 非 null（400）。

**存量迁移**：`AssignmentQuestionMigrator` 需关闭 AOT 运行——
`java -Dspring.aot.enabled=false -jar target/xxq.jar --coursework.migration.enabled=true [--coursework.migration.apply=true]`
（AOT 构建期求值 `@ConditionalOnProperty`，常规启动加开关不会装配迁移器）；默认 dry-run，可重入。

**迟交**：截止时间之后仍允许提交，但置 `late=1`；恰好等于截止时间不算迟交。作业 `CLOSED` 后拒绝提交（400）。

**Redis 暂存**：已查看集合 `coursework:viewed:{assignmentId}` 与草稿 `coursework:draft:{assignmentId}:{studentId}`
只存 Redis（TTL = max(截止+7d, 7d)），**提交是唯一的 SQL 写入口**；名单五态中的 `DRAFTING` 表示学生已查看/起稿但未提交。

**视图变化**：`AssignmentView` 增 `answerVisible`/`questionTypes`/`hasFileQuestion`/`questions`（仅详情）；
提交视图去 `content`/`fileName`/`fileOriginal`，增 `autoScore`/`answers`（逐题：标准答案/得分/评语）。

### 12.3 教学视频（章节树 + WS 流式播放）

视频按教科书式两级树组织：**章（CHAPTER）→ 节（SECTION）→ 视频**，章/节挂授课组锚点
（同一授课组一套目录），视频必须挂节；未挂节的视频（存量）归入**公共区**，由
`GET /api/coursework/videos/common` 承载，展示在前端「课程资料 → 教学视频」节点。
章/节/视频均无人工排序，一律按 id 升序（创建顺序）。详细字段与示例见 `docs/课程视频章节树协议.md`。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/coursework/chapters` | 教师 | 建章 `{teachInfoId, title}` |
| PUT | `/api/coursework/chapters/{id}` | 教师 | 改章名 `{title}` |
| DELETE | `/api/coursework/chapters/{id}` | 教师 | 删章：**级联软删**其下节与视频 |
| GET | `/api/coursework/chapters/tree?teachInfoId=` | 教师/学生 | 整棵树（仅挂节视频，不再带 `commonVideos`） |
| POST | `/api/coursework/sections` | 教师 | 建节 `{chapterId, title}`（节不可换父章） |
| PUT | `/api/coursework/sections/{id}` | 教师 | 改节名 `{title}` |
| DELETE | `/api/coursework/sections/{id}` | 教师 | 删节：**级联软删**其下视频 |
| POST | `/api/coursework/videos` | 教师 | 登记视频（`data.nodeId` 必填挂节 + `file` 或 `filePath` 必填其一） |
| PUT | `/api/coursework/videos/{id}` | 教师 | 改元数据（标题/描述/时长/`nodeId` 改挂节，限同授课组；**文件不可换**） |
| DELETE | `/api/coursework/videos/{id}` | 教师 | 软删（产物由回收任务对账清理） |
| GET | `/api/coursework/videos/{id}` | 教师/学生 | 元数据（WS 播放前置：拿 `size`/`sha256`/`durationSec`） |
| GET | `/api/coursework/videos/common?teachInfoId=` | 教师/学生 | 未挂节视频（公共区，id 升序，含孤儿兜底） |

原 `GET /api/coursework/videos?teachInfoId=` 平铺列表已**移除**，由 tree 接口取代；`VideoView` 增 `nodeId`、去 `sortNo`。
tree 响应不再带 `commonVideos`：tree 与公共区**严格互补**——树只挂「父章存活」的节，
`/videos/common` 收其余全部（未挂节，以及挂在已删/不存在节、父章缺失的节上的孤儿视频），两者合成视频全集。

视频本体走**分片上传**（上限取全局 `file.max-file-size`），扩展名仅放行 `.mp4`。
时长 `durationSec` 由**客户端解析 MP4 后回传**（服务端不解析视频，不转码）。
播放走 WebSocket，协议详见 `docs/课程视频WS协议.md`：

- 连接：`ws://host/ws/video/{videoId}?token={accessToken}`
- 握手 401 = 未登录/过期；403 = 无权限（未选课学生 / 非归属教师）
- 指令 `open` / `read{req,offset,length}` / `cancel{req}`；应答为 `meta`/`error` 文本 JSON 与
  `[req:int32 BE][offset:int64 BE]+payload` 二进制数据帧
- 一次 `read` 恰好回一帧；尾部短读的帧长小于请求 `length` 属正常；`offset` 越界回 12 字节空头帧
- 在途 `read` 上限 8，超出回 429 error 帧（减速信号）；单连接限速 32MB/s

### 12.4 课程资料（文件夹树）

资料按**自引用文件夹树**组织：文件夹挂授课组锚点（同一授课组一棵树），`parentId` 空 = 根层，
任意深度，**不可换父级**（只能改名 / 删除 / 建子级）；资料挂在某层文件夹下（`folderId` 空 = 根目录）。
文件夹一律按 id 升序（创建顺序），当前层资料按 `createTime` 倒序。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/coursework/materials/browse?teachInfoId=&folderId=` | 教师/学生 | 逐层浏览：`path` 面包屑 + 当前层 `folders`/`materials`；根层附 `commonVideoCount` |
| GET | `/api/coursework/material-folders?teachInfoId=&parentId=` | 教师/学生 | 仅文件夹单层（`parentId` 缺省 = 根层），移动选择器逐层懒展开用 |
| POST | `/api/coursework/material-folders` | 教师 | 建文件夹 `{teachInfoId, parentId?, title}`（parentId 空 = 根层） |
| PUT | `/api/coursework/material-folders/{id}` | 教师 | 改名 `{title}`（**仅改名，不可换父级**） |
| DELETE | `/api/coursework/material-folders/{id}` | 教师 | **级联软删**整棵子树（后代文件夹 + 其中资料行；文件本体交回收任务） |
| POST | `/api/coursework/materials` | 教师 | 上传资料（`data.folderId?` 缺省/null = 根目录 + `file` 或 `filePath`，必填其一） |
| PUT | `/api/coursework/materials/{id}` | 教师 | 改元数据（标题/描述） |
| PUT | `/api/coursework/materials/{id}/folder` | 教师 | 移动 `{folderId}`（null/缺失 = 移回根目录；目标不存在或不属本组 404） |
| DELETE | `/api/coursework/materials/{id}` | 教师 | 删除（逻辑删） |

原 `GET /api/coursework/materials?teachInfoId=` 平铺列表已**下线**，由 browse 接口取代。

`MaterialBrowseView`：`teachInfoId`（归一后的锚点行 id，前端采纳锚点用）、`path`（根 → 当前层
面包屑链，根层为空表）、`folders`（当前层子文件夹，id 升序）、`materials`（当前层资料，
`createTime` 倒序）、`commonVideoCount`（**仅根层非 null**：未挂节视频数，前端据此决定
「教学视频」虚拟节点显隐，不预拉视频列表）。browse 的 `folderId` 不存在或不属于该授课组 → 404。
`MaterialView` 增 `folderId`（null = 根目录）。

**表结构**：新增 `course_material_folder`（自引用单表，任意深度，所有层级冗余同一锚点行 id；
`@TableLogic` 软删）；`course_material` 增 `folder_id`（NULL = 根目录，
`updateStrategy = ALWAYS`，移动资料允许把文件夹改回 null）。

资料 ≤20MB，放行文档 / PPT / 表格 / PDF / 压缩包 / 图片。**下载走文件模块的
`POST /api/file/download`**，请求体带列表返回的 `fileName` 作为 `filePath`。

### 12.5 课程公告

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/coursework/announcements` | 教师 | 发布公告（**通知授课组学生**） |
| PUT | `/api/coursework/announcements/{id}` | 教师 | 编辑（**不重复通知**） |
| DELETE | `/api/coursework/announcements/{id}` | 教师 | 删除 |
| GET | `/api/coursework/announcements?teachInfoId=` | 教师/学生 | 列表 |

### 12.6 作业成绩合成平时分

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/coursework/teach-infos/{teachInfoId}/regular-score/sync` | 教师 | 将该授课组已批改作业合成为平时分 |

**合成口径**：每份**已批改**作业按自身满分归一化（`得分 / 满分`），再对学生取算术平均，
百分制保留 2 位小数。分母只计已批改份数——未提交/未批改的作业既不拉低也不抬高。

**写入语义**：覆盖式重算，可重复触发（幂等）。仅 `PUBLISHED`/`CLOSED` 状态的作业参与。
无成绩行则按授课安排快照新建（`score_type=REGULAR`，`regularRatio` 取 `score_config`，无配置为 0）；
期末成绩未出时 `totalScore`/`scoreLevel` 保持 `null`，已有期末则按行内占比快照重算总评。

**跳过项**（随响应返回，便于前端提示）：

| 字段 | 含义 |
|------|------|
| `updatedCount` | 成功写入平时分的学生数 |
| `skippedLockedUserIds` | 成绩已锁定（`locked=1`）被跳过的学生 |
| `skippedUngradedUserIds` | 授课组名单中无已批改作业的学生 |

无已发布作业（400「该授课组暂无已发布作业」）或全部未批改（400「暂无已批改的作业提交」）时报错而非静默成功。

---

## 13. 成绩模块

成绩（`module/score`）负责平时分占比配置、成绩录入与修改、按角色查询、学生成绩页、成绩分布统计、Excel/PDF 导出，以及「学生申请 → 教师回复 → 学生升级 → 教务终审」的成绩复核工作流。

> **状态码约定（第 13、14 节通用）**：未登录 / token 过期 / 用户类型不满足，由 `AuthInterceptor` 直接返回**真实 HTTP 401/403**，响应体只有 `{"code":..,"message":..}`（**没有 `data` 字段**）；
> 业务校验失败（`BusinessException`）与参数绑定失败由 `GlobalExceptionHandler` 统一包装为 **HTTP 200 + body `code`**（400/403/404/409/500），body 形如 `{"code":409,"message":"成绩已锁定，不可修改","data":null}`。
> 下文错误表除注明「HTTP 403」外，均为 HTTP 200 + body code，前端必须读 body 的 `code`。

### 13.1 权限与角色

| 能力 | 教师 | 院系管理员 | 教务管理员 | 学生 |
|------|------|-----------|-----------|------|
| 占比配置 读/写（`/config/**`） | 本人授课安排 | — | 全部 | — |
| 录入名单（`/roster/**`） | 本人授课安排 | — | 全部 | — |
| 批量录入 / 单条修改 | 本人授课安排 | — | 全部 | — |
| 按授课安排查成绩（`GET /api/scores`） | 本人授课安排 | 本院（合班任一班属本院即放行） | 全部 | — |
| 导出 | 同「按授课安排查成绩」 | 同左 | 全部 | — |
| 成绩统计（`/statistics`） | — | 本院学生 | 全校 | — |
| 我的成绩 / 我的学期 | — | — | — | 本人 |
| 复核申请 / 升级 | — | — | — | 本人 |
| 复核待办列表 | 本人课程相关 | — | 全部 | — |
| 复核回复 | 该成绩的任课教师 | — | — | — |
| 复核终审 | — | — | 全部 | — |

**「本人授课安排」判定**：`teach_info.teacher_id` 存的是 **teacher 子表主键**，服务端用 `teacherMapper.findByUserId(userId).id` 比较（不是 `user.id`）。教师用户无 teacher 子表记录时按无权限处理。

**「本院」判定**：把 `teach_info.class_name`（合班 CSV）拆开，经「班级 → 专业 → 院系」两跳解析，**任一班级属于该院系的 college 即放行**；拆不出班级或院系未分配时拒绝。

> 两类 403 的形态不同：**注解级**类型不符（如院系管理员调 `PUT /api/scores/config/1`、教师调 `/statistics`）是真实 HTTP 403；**服务层**归属不符（如教师录入他人课程）是 HTTP 200 + body `code=403`。

### 13.2 枚举、派生规则与锁定

**成绩类型 `ScoreTypeEnum`**（`score.score_type`，库内持久化 `code`）：

| code（持久化/查询） | JSON 输出（`@JsonValue`） | 请求入参可接受 |
|---------------------|--------------------------|----------------|
| `REGULAR` | `正常` | `REGULAR` 或 `正常` |
| `MAKEUP` | `补考` | `MAKEUP` 或 `补考` |
| `RETAKE` | `重修` | `RETAKE` 或 `重修` |

请求体中的枚举字段走 Jackson `@JsonCreator`，`code` 与中文描述均可；无法识别的值会让请求体反序列化失败，返回 400 `请求体格式错误`（不会透出「未知成绩类型」原文）。查询参数中的枚举（如考试模块的 `examType`、复核的 `status`）走 Spring 字符串→枚举转换，**请传 code**。

**成绩等级不是枚举**：`score.score_level` 是派生字符串，由 `ScoreStats.levelOf(totalScore)` 计算：

| 总评区间 | 等级 |
|----------|------|
| ≥ 90 | `优` |
| ≥ 80 | `良` |
| ≥ 70 | `中` |
| ≥ 60 | `及格` |
| < 60 | `不及格` |

**总评合成**（`regularRatio` = 平时分占比，期末占比 = `100 - regularRatio`）：

```
totalScore = round2(regularScore × regularRatio / 100) + round2(finalScore × (100 - regularRatio) / 100)
```

两段分别按 `HALF_UP` 保留 2 位小数后相加。`regularRatio` 在**写入成绩行时快照**（存 `score.regular_ratio`），之后修改 `/config` **不会**回溯重算已存在的成绩行。

**锁定 `locked`**：`0` = 可修改，`1` = 已锁定。批量录入 / 单条修改遇到 `locked=1` 均返回 409 `成绩已锁定，不可修改`；`locked=1` 仅由**复核终审**写入，本模块**没有解锁接口**。复核回复/终审的调分（`newTotalScore`）不校验 `locked`。

> 写入成绩前必须先有占比配置：`POST /api/scores` 在无 `score_config` 行时直接 400 `请先设置平时分占比`。

**时间序列化**：`LocalDate` → `"2026-06-20"`；`LocalTime` → `"09:00:00"`；`LocalDateTime` → `"2026-06-01T10:00:00"`（ISO-8601 默认，无自定义格式）。

### 13.3 端点总览

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| PUT | `/api/scores/config/{teachInfoId}` | 教师本人/教务 | 设置平时分占比（upsert） |
| GET | `/api/scores/config/{teachInfoId}` | 教师本人/教务 | 查询占比配置（未设置为 `data: null`） |
| GET | `/api/scores/roster/{teachInfoId}?examId=` | 教师本人/教务 | 录入名单（`examId` 可选，按排考班级过滤合班名单） |
| POST | `/api/scores` | 教师本人/教务 | 批量录入成绩（按学生 upsert，录入即生效） |
| PUT | `/api/scores/{id}` | 教师本人/教务 | 修改单条成绩（未锁定） |
| GET | `/api/scores?teachInfoId=` | 教师本人/院系本院/教务 | 按授课安排查成绩（**仅 REGULAR**） |
| GET | `/api/scores/my?semesterId=` | 学生 | 我的成绩（缺省当前学期） |
| GET | `/api/scores/my/semesters` | 学生 | 我有成绩的学期列表 |
| GET | `/api/scores/statistics?courseId=&source=&className=&semesterId=` | 教务/院系 | 按课程聚合的成绩分布 |
| GET | `/api/scores/export?teachInfoId=&format=` | 教师本人/院系本院/教务 | 导出 Excel / PDF |
| POST | `/api/scores/reviews` | 学生 | 提交成绩复核申请 |
| GET | `/api/scores/reviews/my` | 学生 | 我的复核申请 |
| GET | `/api/scores/reviews?status=&page=&pageSize=` | 教师/教务 | 复核待办列表（分页） |
| POST | `/api/scores/reviews/{id}/reply` | 该成绩任课教师 | 教师回复（可调分） |
| POST | `/api/scores/reviews/{id}/escalate` | 学生 | 升级到教务（无 body） |
| POST | `/api/scores/reviews/{id}/resolve` | 教务 | 终审（可调分并锁定成绩） |

**成绩视图 `ScoreView`**（录入/修改/查询/导出共用）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 成绩记录 id |
| teachInfoId | Long | 授课安排 id |
| courseId | Long | 常规课 `course.id`；**公选课为 `null`** |
| courseName | String | 课程名（公选课按 `campaign_id` 解析） |
| teacherId | Long | **teacher 子表主键**（非 user.id） |
| teacherName | String | 教师姓名 |
| studentUserId | Long | 学生 `user.id` |
| studentName | String | 学生姓名 |
| studentNo | String | 学号 |
| semesterId | Long | 学期 id |
| regularScore | BigDecimal | 平时分 |
| finalScore | BigDecimal | 期末成绩 |
| regularRatio | Integer | 写入时的平时占比快照 |
| totalScore | BigDecimal | 总评（派生） |
| scoreLevel | String | `优/良/中/及格/不及格` |
| scoreType | String | `正常/补考/重修` |
| locked | Integer | `0` 可改 / `1` 锁定 |
| createTime | LocalDateTime | 创建时间 |

> `ScoreView` **不含 `campaignId`**；公选课行只能通过 `courseId == null` + `courseName` 辨识。
>
> **新增类接口的响应是「内存实体直接富化」，不经回查库**：`POST /api/scores` 新建行的 `createTime` 与 `locked`、`POST /api/scores/reviews` 的 `createTime`、新建考试/占比配置的 `createTime` 可能为 `null`（库内 `DEFAULT CURRENT_TIMESTAMP` / `DEFAULT 0` 已正确落库）。需要完整视图请随后调查询接口。

### 13.4 平时分占比配置

#### 13.4.1 设置占比

```
PUT /api/scores/config/{teachInfoId}
Content-Type: application/json
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| regularRatio | Integer | 是 | 平时分占比 0-100（期末占比 = 100 - 该值） |

```json
{ "regularRatio": 30 }
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 7,
    "teachInfoId": 102,
    "regularRatio": 30,
    "createUserId": 15,
    "createTime": "2026-03-01T09:00:00",
    "updateTime": null
  }
}
```

**行为**：按 `teachInfoId` upsert——无配置则新增，有配置则覆盖 `regularRatio`。`createUserId` 存**最后一次设置人**（不是首次创建人），字段名有历史误导。

**错误场景**

| code | message |
|------|---------|
| 400 | 平时分占比不能为空 |
| 400 | 平时分占比必须在 0-100 之间 |
| 400 | 引用的授课安排不存在(id=xxx) |
| 403 | 权限不足（教师非本人授课安排） |
| 404 | 授课安排不存在 |

#### 13.4.2 查询占比

```
GET /api/scores/config/{teachInfoId}
```

`data` 为 `ScoreConfig`（字段同上）；**未设置时 `data: null`**（不报错）。权限与错误同上。

### 13.5 录入名单

```
GET /api/scores/roster/{teachInfoId}?examId=
```

| 参数 | 位置 | 必填 | 说明 |
|------|------|------|------|
| teachInfoId | path | 是 | 授课安排 id |
| examId | query | 否 | 按考试排考班级过滤合班名单；不传返回合班全部学生 |

**名单来源**：该授课安排关联了选课班（公选课）→ 取 `selection_class_member` 成员；否则按 `teach_info.class_name` 的班级 CSV 取在读学生。**公选课忽略 `examId` 的班级过滤**（一条授课安排即一个选课班）。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "studentUserId": 501, "studentName": "张三", "studentNo": "2023001" }
  ]
}
```

无学生时返回 `[]`。名单**不包含**已有成绩，需要成绩请调 `GET /api/scores`。

**错误场景（examId 相关）**

| code | message |
|------|---------|
| 404 | 授课安排不存在 |
| 404 | 考试不存在 |
| 400 | 考试与授课安排不匹配（考试不属于该 teachInfoId） |
| 400 | 考试排考班级不在该授课安排的合班范围内 |

### 13.6 批量录入与单条修改

#### 13.6.1 批量录入

```
POST /api/scores
Content-Type: application/json
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| teachInfoId | Long | 是 | 授课安排 id |
| examId | Long | 否 | 按考试排考班级限定可录入学生（合班时仅允许该考试班级的学生） |
| entries | Array | 是 | 至少 1 条 |
| entries[].studentUserId | Long | 是 | 学生 `user.id` |
| entries[].regularScore | BigDecimal | **是** | 平时分 0-100（**不可为 null**） |
| entries[].finalScore | BigDecimal | **是** | 期末成绩 0-100（**不可为 null**） |

```json
{
  "teachInfoId": 102,
  "examId": 33,
  "entries": [
    { "studentUserId": 501, "regularScore": 85, "finalScore": 78 },
    { "studentUserId": 502, "regularScore": 60, "finalScore": 55 }
  ]
}
```

**处理规则**

1. 按 `(teachInfoId, studentUserId, scoreType=REGULAR)` upsert：无则新增，有则覆盖并重算总评。
2. `regularRatio` 取当前 `score_config` 快照写入该行；响应返回写入后的 `ScoreView` 列表（顺序同 `entries`）。
3. **必须是平时分 + 期末成绩同时给齐**（各 0-100），不存在「只录期末」的入口；教师尚未出期末时可用课业模块的平时分合成（见 12.6，合成行 `totalScore`/`scoreLevel` 保持 `null`）。
4. 同一请求内出现重复 `studentUserId`：首条插入，后续条目按更新处理（不报重复）。
5. **新建且总评 < 60** 的学生发送站内消息（`通知类型 GRADE`，事务提交后发送，文案「您的《{课程名}》总评成绩为 {总评} 分，未及格，请关注后续补考安排。」）；覆盖更新不及格**不重复通知**。
6. 整个批量操作在一个事务内：任一学生失败（如已锁定）全部回滚，不会部分写入。

**校验顺序与错误**

| 顺序 | 条件 | code | message |
|------|------|------|---------|
| 1 | `entries` 为 null/空 | 400 | 成绩列表不能为空 |
| 2 | `teachInfoId` 不存在 | 404 | 授课安排不存在 |
| 3 | 非教务且非该授课安排教师 | 403 | 权限不足 |
| 4 | 授课安排的课程/学期/教师引用失效 | 400 | 引用的课程不存在(id=xxx) / 引用的学期不存在(id=xxx) / 引用的教师不存在(id=xxx) |
| 5 | `examId` 不存在 | 404 | 考试不存在 |
| 6 | 考试不属于该授课安排 | 400 | 考试与授课安排不匹配 |
| 7 | 考试班级不在合班 CSV | 400 | 考试排考班级不在该授课安排的合班范围内 |
| 8 | 无占比配置 | 400 | 请先设置平时分占比 |
| 9 | 批量学生用户存在性（含软删用户） | 400 | 存在无效的学生用户 |
| 10 | 单条 `studentUserId` 为 null | 400 | 学生ID不能为空 |
| 11 | 不在 `examId` 的排考班级名单 | 400 | 存在未参加本次考试的学生，无法录入成绩 |
| 12 | 分数为 null | 400 | 成绩不能为空 |
| 13 | 分数越界 | 400 | 成绩必须在 0-100 之间 |
| 14 | 已有该学生 REGULAR 行且 `locked=1` | 409 | 成绩已锁定，不可修改 |

> 第 14 条在事务中抛出 → 本批全部回滚。教师若只想改个别学生，请走单条修改并逐条确认未锁定。

#### 13.6.2 修改单条

```
PUT /api/scores/{id}
Content-Type: application/json
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| studentUserId | Long | 否 | **被忽略**（不能改学生；传了也不生效） |
| regularScore | BigDecimal | 是 | 平时分 0-100 |
| finalScore | BigDecimal | 是 | 期末成绩 0-100 |

**行为**：用该行**已有的 `regularRatio` 快照**重算总评与等级（`regularRatio` 为 null 时按 0 计）；**不要求**该授课安排存在占比配置；不改变 `regularRatio` / `scoreType` / `locked`；不发通知。

**校验顺序与错误**

| 顺序 | 条件 | code | message |
|------|------|------|---------|
| 1 | 成绩 id 不存在 | 404 | 成绩记录不存在 |
| 2 | 已锁定 | 409 | 成绩已锁定，不可修改 |
| 3 | 所属授课安排缺失 | 404 | 授课安排不存在 |
| 4 | 非教务且非该授课安排教师 | 403 | 权限不足 |
| 5 | 引用失效（课程/学生/学期/教师/原成绩） | 400 | 引用的课程不存在(id=xxx) / 引用的学生不存在(id=xxx) / 引用的学期不存在(id=xxx) / 引用的教师不存在(id=xxx) / 引用的原成绩不存在(id=xxx) |
| 6 | 同 `(teachInfoId, studentUserId, scoreType)` 存在其它行（历史脏数据） | 409 | 成绩记录已存在 |
| 7 | 分数为 null / 越界 | 400 | 成绩不能为空 / 成绩必须在 0-100 之间 |

> 注意顺序：**锁定检查先于归属校验**，非本人教师在已锁定成绩上会收到 409 而不是 403。

### 13.7 成绩查询

#### 13.7.1 按授课安排查询

```
GET /api/scores?teachInfoId=
```

- 权限：教师本人 / 院系本院 / 教务（注解允许这三类；院系归属不符为 **HTTP 200 + code 403**）。
- 只返回 `score_type=REGULAR` 的行，**不含补考/重修**；按 `studentUserId` 升序。
- 返回 `List<ScoreView>`；无成绩返回 `[]`。

| code | message |
|------|---------|
| 400 | 缺少必填参数: teachInfoId |
| 404 | 授课安排不存在 |
| 403 | 权限不足 |

#### 13.7.2 我的成绩

```
GET /api/scores/my?semesterId=
```

| 参数 | 必填 | 说明 |
|------|------|------|
| semesterId | 否 | 缺省时取 `semester.status = 'CURRENT'` 的学期 |

- **响应是平铺列表，不按学期分组**；学期切换由前端先调 `/my/semesters` 取下拉再逐学期查询。
- 返回该学期下该学生的**全部成绩类型**（`REGULAR` + `MAKEUP` + `RETAKE`），按 `courseId` 升序、`createTime` 倒序。
- 当前无 `CURRENT` 学期且未传 `semesterId` → 返回 `[]`。
- 不做额外归属校验，学生 id 取 JWT 上下文。

#### 13.7.3 我的学期

```
GET /api/scores/my/semesters
```

返回该学生**有成绩记录的学期**（去重，按学期 id 倒序），供成绩页学期下拉。学期对象字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 学期 id |
| name | String | 学期名 |
| startWeek / endWeek | Integer | 起止教学周 |
| startDate / endDate | LocalDate | 起止日期 |
| status | String | 状态（自由字符串；`CURRENT` = 当前学期） |

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "id": 3, "name": "2025-2026-1", "startWeek": 1, "endWeek": 20,
      "startDate": "2025-09-01", "endDate": "2026-01-15", "status": "CURRENT" }
  ]
}
```

### 13.8 成绩统计

```
GET /api/scores/statistics?courseId=&source=&className=&semesterId=
```

| 参数 | 必填 | 说明 |
|------|------|------|
| courseId | 否 | 课程 id；`source=SELECTION_CAMPAIGN` 时该值表示 `campaignId` |
| source | 否 | `SELECTION_CAMPAIGN` = 公选课（按 `campaign_id` 过滤），其余/缺省 = 常规课（按 `course_id` 过滤） |
| className | 否 | 单个班级名精确匹配（`class_name.class_name`，非模糊） |
| semesterId | 否 | 学期过滤 |

- 仅统计 `REGULAR` 成绩。
- **数据范围**：教务 = 全校（`className` 非空时收窄到该班）；院系 = 本院学生（可再叠加 `className`）。院系未分配 `college_id`、或班级无人时返回 `[]`（**不是 403**）。
- **按课程聚合**：公选课按 `campaignId` 分组、常规课按 `courseId` 分组；响应中的 `courseId` 字段在公选课行里装的是 **`campaignId`**（字段名沿用，注意不要拿去查 `course` 表）。

**`ScoreStatisticsDto`**

| 字段 | 类型 | 说明 |
|------|------|------|
| courseId | Long | 常规课 `course.id`；**公选课为 `campaignId`** |
| courseName | String | 课程名 |
| totalCount | Integer | 该课程成绩行数（含总评为 null 的行） |
| excellentCount | Integer | 优（≥90）人数 |
| goodCount | Integer | 良（≥80）人数 |
| mediumCount | Integer | 中（≥70）人数 |
| passCount | Integer | 及格（≥60）人数 |
| failCount | Integer | 不及格（<60）人数 |
| avgScore | BigDecimal | 平均分（仅对非 null 总评；2 位小数 HALF_UP） |
| maxScore | BigDecimal | 最高分（无非 null 总评时为 null） |
| minScore | BigDecimal | 最低分（同上） |
| passRate | BigDecimal | 及格率（%）= (优+良+中+及格) / **有总评的行数** × 100，2 位小数 HALF_UP |

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "courseId": 12,
      "courseName": "数据结构",
      "totalCount": 45,
      "excellentCount": 6,
      "goodCount": 14,
      "mediumCount": 15,
      "passCount": 8,
      "failCount": 2,
      "avgScore": 78.42,
      "maxScore": 96.00,
      "minScore": 51.50,
      "passRate": 95.56
    }
  ]
}
```

> `totalCount` 与 `passRate` 的分母口径不同：前者是全部成绩行，后者只算 `totalScore` 非 null 的行（课业合成的平时分在期末未出时总评为 null，会计入 `totalCount` 但不参与均分/及格率/人数分布）。
> 结果在内存聚合，**无分页、顺序不保证**。

### 13.9 成绩导出

```
GET /api/scores/export?teachInfoId=&format=
```

| 参数 | 必填 | 说明 |
|------|------|------|
| teachInfoId | 是 | 授课安排 id |
| format | 否 | `excel`（默认）/ `pdf`，**大小写不敏感**；其它值一律按 excel 处理 |

- 数据源与权限**完全复用** `GET /api/scores`（教师本人 / 院系本院 / 教务；只含 `REGULAR`，按 `studentUserId` 升序）。
- 成功时**不是 `Result` 包装**，而是二进制流（HTTP 200）。

**响应头**

| Header | 值 |
|--------|-----|
| `Content-Type` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`（xlsx）/ `application/pdf`（pdf） |
| `Content-Disposition` | `attachment; filename*=UTF-8''<URLEncoder 文件名>`（RFC5987，空格编码为 `%20`） |
| `Accept-Ranges` | `bytes` |
| `ETag` | 强校验 `"<导出字节的 SHA-256>"` |

> 导出走 `ResumableFileResponse.buildDownload(byte[], fileName)`：字节内容寻址的强 ETag 与 `Range` 语义详见**第 11 节**（命中 `Range` 由 Spring `ResourceHttpRequestConverter` 返回 206）。byte[] 导出**不输出 `Last-Modified`**。

**文件名**：`{课程名}-成绩单.xlsx` / `{课程名}-成绩单.pdf`；课程名取结果集第一行的 `courseName`，结果为空或为 null 时用 `成绩`（即 `成绩-成绩单.xlsx`）。

**内容**（两种格式列一致）：`学号 | 姓名 | 平时分 | 期末成绩 | 平时占比(%) | 总评 | 等级`。

- Excel：第 1 行标题「{课程名} 成绩单」跨 7 列合并居中，第 2 行表头（灰底加粗），冻结前两行；列宽按 CJK 宽度手动估算。
- PDF：标题居中 + 7 列表格，使用 `STSong-Light` 非内嵌中文字体（字体不可用时会**静默回退**默认字体，中文可能显示异常）。
- 数值差异：Excel 中 null 分数写为 `0`；PDF 中 null 分数显示为空串。

**错误场景**

| code | message |
|------|---------|
| 400 | 缺少必填参数: teachInfoId |
| 404 | 授课安排不存在 |
| 403 | 权限不足（HTTP 200 + body） |
| 500 | 导出 Excel 失败 |
| 500 | 导出 PDF 失败 |

### 13.10 成绩复核

**状态机**

```
                教师回复(可重复)                学生升级                 教务终审
PENDING ───────────────────────> TEACHER_REPLIED ─────────> ESCALATED ─────────> RESOLVED
                                     │                                          └──> REJECTED
                                     └──── 教师可再次回复（覆盖回复内容）
```

| 当前状态 | 动作 | 执行人 | 目标状态 |
|----------|------|--------|----------|
| PENDING | reply | 该成绩任课教师 | TEACHER_REPLIED |
| TEACHER_REPLIED | reply | 该成绩任课教师 | TEACHER_REPLIED（覆盖原回复） |
| TEACHER_REPLIED | escalate | 申请人 | ESCALATED |
| ESCALATED | resolve（`resolved=true`） | 教务 | RESOLVED |
| ESCALATED | resolve（`resolved=false` 或 null） | 教务 | REJECTED |

- 其它状态组合一律 409（终态 `RESOLVED`/`REJECTED` 不可再流转）。
- 同一成绩**同时只能有一条进行中**复核（PENDING / TEACHER_REPLIED / ESCALATED）；已解决/已驳回后可以再次申请。
- 申请不限制成绩类型（REGULAR / MAKEUP / RETAKE 均可，只要是本人成绩）。
- 教务**终审必定把成绩置 `locked=1`**（即使驳回），此后该成绩不可再经录入/修改接口改动。

**`ReviewView`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 复核申请 id |
| scoreId | Long | 成绩记录 id |
| studentUserId | Long | 申请人 `user.id` |
| studentName / studentNo | String | 姓名 / 学号 |
| courseId | Long | 课程 id（公选课为 null） |
| courseName | String | 课程名 |
| teacherId | Long | 成绩所属教师 **teacher 子表主键** |
| teacherName | String | 教师姓名 |
| currentTotalScore | BigDecimal | 当前成绩总评 |
| reason | String | 申请理由 |
| status | String | `待教师处理/教师已回复/已升级教务/已解决/已驳回` |
| teacherReply | String | 教师回复 |
| adminReply | String | 教务回复 |
| escalateTime | LocalDateTime | 升级时间 |
| resolvedTime | LocalDateTime | 终审时间 |
| createTime | LocalDateTime | 申请时间 |

#### 13.10.1 学生提交申请

```
POST /api/scores/reviews
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scoreId | Long | 是 | 成绩记录 id |
| reason | String | 是 | 申请理由（非空白） |

```json
{ "scoreId": 9001, "reason": "期末卷面第 3 题给分有误" }
```

**校验顺序与错误**

| 顺序 | 条件 | code | message |
|------|------|------|---------|
| 1 | scoreId 为 null | 400 | 成绩ID不能为空 |
| 2 | reason 为 null/空白 | 400 | 申请理由不能为空 |
| 3 | 成绩不存在 | 404 | 成绩不存在 |
| 4 | 成绩不属于当前学生 | 403 | 权限不足 |
| 5 | 该成绩已有进行中复核 | 409 | 该成绩已有进行中的复核申请 |
| 6 | 用户引用失效 | 400 | 引用的用户不存在(id=xxx) |

返回新建的 `ReviewView`（`status = 待教师处理`）。

#### 13.10.2 我的申请

```
GET /api/scores/reviews/my
```

返回当前学生全部复核申请（`createTime` 倒序），`List<ReviewView>`。

#### 13.10.3 处理人待办列表

```
GET /api/scores/reviews?status=&page=&pageSize=
```

| 参数 | 必填 | 说明 |
|------|------|------|
| status | 否 | 复核状态 code：`PENDING` / `TEACHER_REPLIED` / `ESCALATED` / `RESOLVED` / `REJECTED` |
| page | 否 | 页码，默认 1（<1 按 1） |
| pageSize | 否 | 每页条数，默认 20，上限 100 |

- **教务**：全部申请；**教师**：`score.teacher_id = 本人 teacher.id` 的成绩所关联的申请（无 teacher 子表记录时返回空页）。
- `createTime` 倒序，返回 `PageResult<ReviewView>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "page": 1,
    "pageSize": 20,
    "pages": 0
  }
}
```

> `status` 是查询参数枚举，请传 code（`PENDING` 等）；传中文描述会转换失败，返回 400 `参数类型错误: status`。

#### 13.10.4 教师回复

```
POST /api/scores/reviews/{id}/reply
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| reply | String | 否 | 回复内容（**不校验非空**，可为 null） |
| newTotalScore | BigDecimal | 否 | 调整后的总评 0-100；为空表示不调分 |

**规则**

- 仅**该成绩的任课教师**可回复（教务调本接口会被注解拦成真实 HTTP 403）。
- 状态须为 PENDING 或 TEACHER_REPLIED，否则 409 `当前状态不可回复`。
- 回复后状态置 TEACHER_REPLIED，并发送站内消息「教师已回复您的成绩复核申请，请查看。」。
- 调分只改 `totalScore` 与 `scoreLevel`（`0-100` 校验，越界 400 `成绩必须在 0-100 之间`），**不改** `regularScore`/`finalScore`/`locked`。

| code | message |
|------|---------|
| 404 | 复核申请不存在 |
| 404 | 成绩不存在 |
| 403 | 权限不足（非该成绩教师） |
| 409 | 当前状态不可回复 |
| 400 | 引用的用户不存在(id=xxx) / 引用的教师不存在(id=xxx) |
| 400 | 成绩不能为空 / 成绩必须在 0-100 之间（newTotalScore） |

#### 13.10.5 学生升级到教务

```
POST /api/scores/reviews/{id}/escalate
```

- 无请求体；仅申请人本人（否则 403 `权限不足`）。
- 仅 `TEACHER_REPLIED` 可升级，否则 409 `仅教师已回复后可升级到教务`。
- 置 `ESCALATED` + `escalateTime`；**不发送通知**。
- 成功返回 `{"code":200,"message":"success","data":null}`。

| code | message |
|------|---------|
| 404 | 复核申请不存在 |
| 403 | 权限不足（非申请人） |
| 409 | 仅教师已回复后可升级到教务 |
| 400 | 引用的成绩不存在(id=xxx) / 引用的用户不存在(id=xxx) / 引用的教师不存在(id=xxx) |

#### 13.10.6 教务终审

```
POST /api/scores/reviews/{id}/resolve
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| reply | String | 否 | 教务回复 |
| newTotalScore | BigDecimal | 否 | 调整后的总评 0-100（可选） |
| resolved | Boolean | 否 | `true` → RESOLVED；`false`/null → REJECTED |

**规则**

- 仅 `ESCALATED` 可终审，否则 409 `仅已升级到教务的申请可终审`。
- 终审**无条件锁定成绩**（`locked=1`），无论 `resolved` 真假；有 `newTotalScore` 时先校验 0-100 再写入总评与等级。
- 发送站内消息「您的成绩复核申请已处理完毕，请查看结果。」。
- 返回更新后的 `ReviewView`。

| code | message |
|------|---------|
| 404 | 复核申请不存在 |
| 409 | 仅已升级到教务的申请可终审 |
| 400 | 成绩不能为空 / 成绩必须在 0-100 之间（newTotalScore） |
| 400 | 引用的成绩不存在(id=xxx) / 引用的用户不存在(id=xxx) / 引用的教师不存在(id=xxx) |

---

## 14. 考试模块

考试（`module/exam`）分两条业务线：**期末/期中考试**（教务安排、绑授课安排、全班参加）与**补考/重修**（按不及格成绩自动生成考生名单、单独录入成绩）。状态码约定同第 13 节。

### 14.1 权限与枚举

| 能力 | 教务管理员 | 教师 | 学生 | 院系管理员 |
|------|-----------|------|------|-----------|
| 建/改/删考试、考试列表、按班级可选课程 | ✓ | — | — | — |
| 补考候选名单 / 建补考 / 补考列表 | ✓ | — | — | — |
| 查询自己课程相关考试 | — | ✓ | — | — |
| 查询自己的考试 | — | — | ✓ | — |
| 录入补考/重修成绩 | ✓ | 该课程任课教师 | — | — |

> 考试模块**所有管理端点都只给教务**（`@RequireAcademicAdmin`），院系管理员调用会得到**真实 HTTP 403**。

**考试类型 `ExamTypeEnum`**（`exam.exam_type` 持久化 `code`）：

| code | JSON 输出（`@JsonValue`） | 入参（JSON 体） |
|------|--------------------------|-----------------|
| `FINAL` | `期末考试` | `FINAL` 或 `期末考试` |
| `MIDTERM` | `期中考试` | `MIDTERM` 或 `期中考试` |
| `MAKEUP` | `补考` | `MAKEUP` 或 `补考` |
| `RETAKE` | `重修` | `RETAKE` 或 `重修` |

**考试状态 `ExamStatusEnum`**（`exam.status`）：

| code | JSON 输出 |
|------|-----------|
| `SCHEDULED` | `已安排` |
| `CANCELED` | `已取消` |
| `COMPLETED` | `已完成` |

> 状态**没有状态机约束**：创建固定为 `SCHEDULED`，修改接口可把 `status` 改成任意枚举值，无流转校验。
>
> `GET /api/exams` 的 `examType`、复核列表的 `status` 都是**查询参数**枚举：传 code（`FINAL` / `PENDING`）可被 Spring 转换（大小写不敏感）；传中文描述会转换失败并 400 `参数类型错误: <参数名>`（如 `examType` / `status`）。请求**体**内的枚举字段才接受中文。

**`ExamView`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 考试 id |
| examName | String | 考试名称 |
| courseId | Long | 课程 id（公选课行理论为 null，见 14.1 末注） |
| courseName | String | 课程名 |
| teachInfoId | Long | 授课安排 id（补考/重修恒为 null） |
| className | String | 排考班级（**单班级名**；补考/重修为 null） |
| examType | String | `期末考试/期中考试/补考/重修` |
| semesterId | Long | 学期 id |
| examDate | LocalDate | 考试日期 |
| startTime | LocalTime | 开始时间 |
| endTime | LocalTime | 结束时间（由开始时间 + 时长派生） |
| localId | Long | 考试地点 id |
| localName | String | `教学楼 教室`（两段以空格拼接，缺失段留空） |
| notes | String | 备注 |
| status | String | `已安排/已取消/已完成` |
| createTime | LocalDateTime | 创建时间 |

> **公选课补充（代码现状）**：`exam` / `score` 表都有 `campaign_id` 列，`ExamView` 富化也按 `campaign_id` 非空路由；但考试模块**所有写入路径只写 `course_id`**（`ExamCreateRequest` / `MakeupExamCreateRequest` 都没有 `campaignId` 字段）。
> 因此 `GET /api/exams?source=SELECTION_CAMPAIGN`、公选课的补考自动名单与补考成绩录入在现有代码下不可用（按 `course_id` 匹配不到公选课成绩行）。这是已知实现缺口，不是调用姿势问题。

### 14.2 端点总览

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/exams` | 教务 | 安排期末/期中考试 |
| PUT | `/api/exams/{id}` | 教务 | 修改考试（全量覆盖） |
| DELETE | `/api/exams/{id}` | 教务 | 删除考试（连带清理考生名单） |
| GET | `/api/exams?semesterId=&courseId=&source=&examType=&page=&pageSize=` | 教务 | 考试分页列表 |
| GET | `/api/exams/class-courses?classId=` | 教务 | 按班级查可排考课程（建考选项） |
| GET | `/api/exams/teacher` | 教师 | 自己课程相关的考试 |
| GET | `/api/exams/my` | 学生 | 自己的考试（常规 ∪ 补考/重修） |
| GET | `/api/exams/makeup/candidates?courseId=&source=&semesterId=` | 教务 | 不及格学生名单（补考候选） |
| POST | `/api/exams/makeup` | 教务 | 建补考/重修考试（自动生成考生） |
| GET | `/api/exams/makeup?semesterId=` | 教务 | 补考/重修考试列表 |
| POST | `/api/exams/{examId}/grades` | 教师本人/教务 | 录入补考/重修成绩 |

### 14.3 安排、修改与删除考试

#### 14.3.1 安排考试（期末/期中）

```
POST /api/exams
Content-Type: application/json
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| examName | String | 是 | 考试名称 |
| courseId | Long | 是 | 课程 id |
| teachInfoId | Long | **是**（本接口） | 授课安排 id；缺失时在 validate 全部通过后报 400 |
| className | String | 期末/期中**是** | 排考班级（**单个**班级名，不是合班 CSV） |
| examType | String | 是 | 本接口只允许 `FINAL` / `MIDTERM` |
| semesterId | Long | 是 | 学期 id |
| examDate | LocalDate | 是 | 考试日期 |
| startTime | LocalTime | 是 | 开始时间 |
| durationMinutes | Integer | 是 | 考试时长（分钟，>0）；`endTime` 由后端计算 |
| localId | Long | 否 | 考试地点 `local.id` |
| notes | String | 否 | 备注 |
| status | String | 否 | **创建时被忽略**（固定 `SCHEDULED`） |

```json
{
  "examName": "2025-2026-1 数据结构期末考试",
  "courseId": 12,
  "teachInfoId": 102,
  "className": "计科2301",
  "examType": "FINAL",
  "semesterId": 3,
  "examDate": "2026-01-10",
  "startTime": "09:00:00",
  "durationMinutes": 120,
  "localId": 8,
  "notes": "闭卷"
}
```

**校验顺序与错误**

| 顺序 | 条件 | code | message |
|------|------|------|---------|
| 1 | `examType` 为 MAKEUP/RETAKE | 400 | 补考/重修请使用补考创建接口 |
| 2 | examName 空白 | 400 | 考试名称不能为空 |
| 3 | courseId 为 null | 400 | 课程不能为空 |
| 4 | examType 为 null | 400 | 考试类型不能为空 |
| 5 | FINAL/MIDTERM 且 className 空白 | 400 | 期末/期中考试必须指定排考班级 |
| 6 | semesterId 为 null | 400 | 学期不能为空 |
| 7 | examDate 为 null | 400 | 考试日期不能为空 |
| 8 | startTime 或 durationMinutes 为 null | 400 | 开始时间与考试时长不能为空 |
| 9 | durationMinutes ≤ 0 | 400 | 考试时长必须大于0分钟 |
| 10 | `startTime + duration` 回绕（不晚于开始时间） | 400 | 考试时长超出当日范围 |
| 11 | courseId 不存在 | 400 | 引用的课程不存在(id=xxx) |
| 12 | teachInfoId 传入但不存在 | 400 | 引用的授课安排不存在(id=xxx) |
| 13 | semesterId 不存在 | 400 | 引用的学期不存在(id=xxx) |
| 14 | localId 传入但不存在 | 400 | 引用的考试地点不存在(id=xxx) |
| 15 | teachInfoId 为 null | 400 | 期末/期中考试必须关联授课安排 |

**未做的校验（调用方需自行保证）**：

- 不校验 `teachInfoId` 与 `courseId` 是否同一门课；
- 不校验 `className` 是否在该授课安排的合班 CSV 内（`/class-courses` 只是前端建考选项，服务端不强制）；
- 不校验同 `(teachInfoId, className)` 是否已安排过考试——**重复建考不会被拒绝**（`/class-courses` 会把已排过的从选项里滤掉，但直接 POST 仍可建）。
- `durationMinutes` 用 `LocalTime.plusMinutes` 计算 `endTime`：跨零点会回绕并被第 10 条拦截。

成功返回单个 `ExamView`（`status=已安排`，`createTime` 可能为 null，见 13.3 注）。

#### 14.3.2 修改考试

```
PUT /api/exams/{id}
```

请求体与 14.3.1 完全相同（**全量覆盖**，不是部分更新；未传的字段会被写成 null 并触发对应必填校验）。

行为与注意：

- 考试不存在 → 404 `考试不存在`。
- 复用同一套 `validate`：`examType`、`className`（FINAL/MIDTERM 时）、时长、引用存在性等规则一致。
- **没有「补考/重修请使用补考创建接口」这道守卫**：可以把已有考试改成 `MAKEUP`/`RETAKE`（反之亦然）。
- **不要求 `teachInfoId` 非空**（创建接口才有这道检查）——可把期末考试的 `teachInfoId` 改为 null。
- `status` 非空时覆盖，取值不做流转校验。
- `endTime` 始终由 `startTime + durationMinutes` 重算，不接受客户端直传。
- 返回更新后的 `ExamView`。

#### 14.3.3 删除考试

```
DELETE /api/exams/{id}
```

- 不存在 → 404 `考试不存在`；成功 → `data: null`。
- **硬删除**（`exam` 表无逻辑删除列）：先删该考试的 `exam_student` 考生行，再删考试行。
- **不检查也不清理**关联的成绩（`score` 行保留）、不做任何下游引用校验。

### 14.4 教务查询

#### 14.4.1 考试分页列表

```
GET /api/exams?semesterId=&courseId=&source=&examType=&page=&pageSize=
```

| 参数 | 必填 | 说明 |
|------|------|------|
| semesterId | 否 | 学期过滤 |
| courseId | 否 | 课程过滤；`source=SELECTION_CAMPAIGN` 时按 `campaign_id`，否则按 `course_id` |
| source | 否 | `SELECTION_CAMPAIGN` = 公选课路由 |
| examType | 否 | 考试类型 code：`FINAL`/`MIDTERM`/`MAKEUP`/`RETAKE` |
| page / pageSize | 否 | 默认 1 / 20，`pageSize` 上限 100 |

- 按 `examDate` 倒序；返回 `PageResult<ExamView>`（结构见 13.10.3）。
- 结果包含各类考试（补考/重修同样在列表中，无默认类型过滤）。
- 公选课路由注意见 14.1 末注（`campaign_id` 无写入路径，`source=SELECTION_CAMPAIGN` 实际恒为空）。

#### 14.4.2 按班级查可排考课程

```
GET /api/exams/class-courses?classId=
```

- `classId` 不存在 → 404 `班级不存在`。
- 用班级名 `FIND_IN_SET` 匹配 `teach_info.class_name`（**合班自动命中**），返回**所有学期**的授课安排。
- **排除已排考**：同一 `teachInfoId` 下若已存在 `className=该班` 且状态**不是 `CANCELED`** 的考试，则该课程不再出现在选项中（已取消的考试不占名额；合班课的不同班级可各建一次）。
- 结果**无显式排序**。

**`ClassCourseOptionDto`**

| 字段 | 类型 | 说明 |
|------|------|------|
| teachInfoId | Long | 建考要用的授课安排 id |
| courseId | Long | 课程 id |
| courseName | String | 课程名 |
| teacherName | String | 教师姓名 |
| className | String | `teach_info` 的**合班全名**（如 `计科2301,计科2302`），用于前端提示 |
| semesterId | Long | 学期 id |
| semesterName | String | 学期名 |

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "teachInfoId": 102,
      "courseId": 12,
      "courseName": "数据结构",
      "teacherName": "李老师",
      "className": "计科2301,计科2302",
      "semesterId": 3,
      "semesterName": "2025-2026-1"
    }
  ]
}
```

### 14.5 教师与学生查询

#### 14.5.1 教师查询自己课程的考试

```
GET /api/exams/teacher
```

- 取该教师**全部授课安排**（不限学期）的 `courseId` 与 `campaignId`，返回命中的考试（按 `examDate` 升序）。
- 因此同一门课在其它学期/其它班级的考试也会一并出现；补考/重修考试（`teach_info_id` 为 null）只要 `course_id` 命中也会返回。
- 教师无 teacher 子表记录或无授课安排 → `[]`。

#### 14.5.2 学生查询自己的考试

```
GET /api/exams/my
```

学生可见考试 = **常规考试 ∪ 补考/重修**：

1. **常规考试**：取学生可见授课安排（本班常规课 + 已选公选课班，见 `TeachInfoService.listMyTeachInfoIds`）下的 `FINAL`/`MIDTERM` 考试，再按**排考班级**过滤——`exam.className` 为 null 或等于学生所在班级名才返回（合班课只为本班安排的那场考试可见）。
2. **补考/重修**：`exam_student` 中考生名单命中该学生的考试。

返回顺序：常规考试（`examDate` 升序）在前，补考/重修（`examDate` 升序）在后；**整体不是全局排序**。结果不去重（两条线类型不重叠）。

### 14.6 补考 / 重修

#### 14.6.1 不及格名单（补考候选）

```
GET /api/exams/makeup/candidates?courseId=&source=&semesterId=
```

| 参数 | 必填 | 说明 |
|------|------|------|
| courseId | 是 | 课程 id；`source=SELECTION_CAMPAIGN` 时表示 `campaignId` |
| source | 否 | `SELECTION_CAMPAIGN` = 按 `campaign_id` 过滤，否则按 `course_id` |
| semesterId | 否 | 只看指定学期的成绩 |

- 规则：`score_type=REGULAR` 且 `total_score < 60`（总评为 null 的行不入选）。
- 按 `studentUserId` 升序；不分页。
- **只是实时派生视图，不落库**；不会排除「已经建过补考/已有补考成绩」的学生。

**`MakeupCandidateDto`**

| 字段 | 类型 | 说明 |
|------|------|------|
| studentUserId | Long | 学生 `user.id` |
| studentName / studentNo | String | 姓名 / 学号 |
| scoreId | Long | **原不及格 REGULAR 成绩 id** |
| totalScore | BigDecimal | 原总评 |
| scoreLevel | String | 原等级（`不及格`） |
| semesterId | Long | 原成绩学期 |

#### 14.6.2 建补考/重修考试

```
POST /api/exams/makeup
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| examName | String | 是 | 考试名称 |
| courseId | Long | 是 | 课程 id |
| examType | String | 是 | 仅 `MAKEUP` / `RETAKE` |
| semesterId | Long | 是 | **考试所在**学期 id |
| sourceSemesterId | Long | 否 | 不及格成绩**来源**学期；为空则取 `semesterId` |
| examDate | LocalDate | 是 | 考试日期 |
| startTime | LocalTime | 是 | 开始时间 |
| durationMinutes | Integer | 是 | 时长（分钟，>0），`endTime` 由后端算 |
| localId | Long | 否 | 考试地点 |
| notes | String | 否 | 备注 |

```json
{
  "examName": "数据结构补考",
  "courseId": 12,
  "examType": "MAKEUP",
  "semesterId": 4,
  "sourceSemesterId": 3,
  "examDate": "2026-03-05",
  "startTime": "14:00:00",
  "durationMinutes": 90,
  "localId": 8
}
```

**行为**

1. 建 `exam` 行：`teachInfoId = null`、`className = null`、`campaignId = null`、`status = SCHEDULED`、`createUserId = 当前教务`。
2. 自动生成考生：查 `sourceSemesterId`（或 `semesterId`）学期、该课程 `course_id`、`total_score < 60` 的 `REGULAR` 成绩，按学生去重写入 `exam_student`。
3. **无不及格学生时仍建考成功**（考生名单为空，不报错）。
4. 注意：`exam_student` 的唯一性预检在**新考试 id** 上执行，必然命中 0 行，实际不会触发 `考生名单中存在已分配的学生`；重建同课程补考会再生成一场新考试并各自持有考生。

**校验顺序与错误**

| 顺序 | 条件 | code | message |
|------|------|------|---------|
| 1 | examType 不是 MAKEUP/RETAKE | 400 | 仅支持补考/重修类型 |
| 2 | examName 空白 | 400 | 考试名称不能为空 |
| 3 | courseId 为 null | 400 | 课程不能为空 |
| 4 | semesterId 为 null | 400 | 学期不能为空 |
| 5 | examDate 为 null | 400 | 考试日期不能为空 |
| 6 | startTime 或 durationMinutes 为 null | 400 | 开始时间与考试时长不能为空 |
| 7 | durationMinutes ≤ 0 | 400 | 考试时长必须大于0分钟 |
| 8 | 时长回绕 | 400 | 考试时长超出当日范围 |
| 9 | 命中学生用户不存在/已软删 | 400 | 考生名单中存在无效的学生用户 |

> **与常规建考不同，补考建考只做必填与时长校验，不校验 `course_id` / `semester_id` / `local_id` 是否真实存在**（无 `ReferenceValidator` 调用），传错 id 也能落库。
>
> 补考建考不接受 `source`，考生查询固定按 `course_id` 匹配。公选课（成绩行 `course_id` 为 null）无法生成考生，见 14.1 末注。

成功返回 `ExamView`（`status=已安排`）。

#### 14.6.3 补考/重修考试列表

```
GET /api/exams/makeup?semesterId=
```

返回 `examType ∈ {MAKEUP, RETAKE}` 的考试，按 `examDate` 倒序；`semesterId` 可选。返回 `List<ExamView>`。

**考试学生名单（`exam_student`）语义汇总**

- 只有补考/重修考试有名单行；期末/期中考试不落名单，参加范围由「授课安排班级 + 排考班级」推导。
- 名单在建考时按不及格成绩**一次性生成**，之后**没有增删改查接口**；`ExamView` 也不返回名单或人数。
- 学生 `/api/exams/my` 靠它判断补考/重修可见性。
- **录入补考成绩时不校验名单成员**（见 14.7）。

### 14.7 补考 / 重修成绩录入

```
POST /api/exams/{examId}/grades
Content-Type: application/json
```

请求体是**裸数组**（不是对象包装）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| studentUserId | Long | 是 | 学生 `user.id` |
| score | BigDecimal | 是 | 补考/重修卷面分 0-100 |

```json
[
  { "studentUserId": 501, "score": 72 },
  { "studentUserId": 502, "score": 58.5 }
]
```

**权限**：教务全部；教师需在该考试课程下拥有**任意授课安排**（`exam.campaignId` 非空按 campaign 校验，否则按 `course_id`），否则 body 403 `权限不足`。

**写入语义**（每条）

1. 查该学生在该课程下**最近一条**（`createTime` 倒序）`REGULAR` 成绩作为「原成绩」，**找不到直接 404**。
2. 以 `(原成绩.teachInfoId, studentUserId, scoreType)` upsert 一条补考/重修成绩：
   - `score_type`：`MAKEUP` 考试 → `MAKEUP`；`RETAKE` 考试 → `RETAKE`；
   - `finalScore = score`、`totalScore = score`、`scoreLevel = levelOf(score)`；
   - `regularScore = null`、`regularRatio = 0`；
   - `teacherId` 沿用原成绩、`semesterId = 考试所属学期`、`originalScoreId = 原成绩 id`；
   - `enterUserId = 当前操作人`。
3. **不回写、不锁定原 REGULAR 成绩**，也不发通知；原不及格记录保持原样。

**校验顺序与错误**

| 顺序 | 条件 | code | message |
|------|------|------|---------|
| 1 | 请求体为 null/空数组 | 400 | 成绩列表不能为空 |
| 2 | 考试不存在 | 404 | 考试不存在 |
| 3 | 考试类型不是 MAKEUP/RETAKE | 400 | 仅补考/重修考试可录入补考成绩 |
| 4 | 教师非该课程任课教师 / 非教务 | 403 | 权限不足 |
| 5 | 单条 studentUserId 为 null | 400 | 学生ID不能为空 |
| 6 | 分数为 null / 越界 | 400 | 成绩不能为空 / 成绩必须在 0-100 之间 |
| 7 | 找不到该学生的原 REGULAR 成绩 | 404 | 未找到学生 userId=501 的原成绩记录 |

成功返回写入后的 `List<ScoreView>`（`scoreType` 为 `补考`/`重修`，`regularScore=null`）。

> **不校验 `exam_student` 名单**：只要学生在系统中存在该课程的原成绩，即使不在本次考试的考生名单里也能录入。名单仅用于学生端可见性与建考时的自动生成。
>
> 公选课限制：本接口按 `exam.campaignId`（恒 null）→ `course_id` 分支查原成绩，公选课成绩行 `course_id` 为 null，会落到 404「未找到原成绩」。

---

## 15. 学情分析模块

学情分析模块（`module/analysis`）提供 8 组能力：成绩分析、学业预警、评教指标库、评教模板、评教周期与评教提交、教师教学质量、学习进度、学生画像/班级分析。所有端点前缀 `/api/analysis`，共 **36 个端点**。

```
module/analysis
├── controller/   ClassAnalysis / EvaluationItem / EvaluationTemplate / Progress /
│                 ScoreAnalysis / StudentProfile / TeacherEvaluation / Warning
├── dto/          请求与视图对象（本模块全部接口均返回 DTO，不直接暴露实体）
├── entity/       EvaluationItem / EvaluationTemplate / EvaluationTemplateItem /
│                 EvaluationTemplateOverride / EvaluationPeriod / TeachingEvaluation /
│                 TeachingEvaluationScore / WarningConfig / WarningRecord + 4 个枚举
├── mapper/       9 个 Mapper（MyBatis Plus BaseMapper）
├── service/      8 个接口 + impl/ 8 个实现
└── util/         GpaCalculator / ScoreStats / CreditSource（纯函数，被画像/预警/班级分析复用）
```

### 15.0 通用约定

**认证与错误响应**

- 所有端点都要求 `Authorization: Bearer <accessToken>`。未登录/过期/无效由 `AuthInterceptor` 直接返回 **HTTP 401**（`{"code":401,"message":"请先登录|token已过期，请刷新|token无效"}`），用户类型不符返回 **HTTP 403**（`{"code":403,"message":"权限不足"}`）。
- **业务层错误一律 HTTP 200 + body.code**（`BusinessException` 由 `GlobalExceptionHandler` 转成 `Result`，未设置 `@ResponseStatus`）：400 参数/业务校验、404 资源不存在、409 冲突、429 限流、500 未预期异常。
- 缺少必填 query 参数 → 400 `缺少必填参数: xxx`；query 参数类型不符 → 400 `参数类型错误: xxx`；请求体不是合法 JSON → 400 `请求体格式错误`。
- 本模块 DTO 上的 `@NonNull`（jspecify）**不做运行时校验**，实际保护来自 Service 内的 `ParamValidator`/手工判空。

**权限模型**

| 注解 | 允许的用户类型 | 本模块使用处 |
|------|----------------|--------------|
| `@RequireStudent` | 学生 | 学习进度/画像/预警自查、评教表单提交与我的评教 |
| `@RequireTeacher` | 教师 | 教师查询本人教学质量 |
| `@RequireAcademicAdmin` | 教务管理员 | 评教指标库、评教模板、评教周期开闭、预警配置与扫描 |
| `@RequireManagement` | 教务管理员 + 院系管理员 | 班级/专业分析、指定学生进度/画像、预警看板、教师质量列表 |
| `@RequireLogin` | 任意已登录用户 | 评教表单、评教周期状态查询 |
| `@RequireAuth({...})` | 显式枚举 | 成绩分析（TEACHER/DEPARTMENT/ACADEMIC_ADMIN）、指定教师质量（ACADEMIC_ADMIN/DEPARTMENT/TEACHER） |

**数据范围（StudentScopeResolver）**

- 教务管理员：全校，学生范围返回 `null`（不过滤）；可选 `className` 时收窄到该班级。
- 院系管理员：经 `department.college_id` → `class_name.major_id` → `major.college_id` 两跳圈定本院班级，再取学生。
- **fail-closed**：院系管理员未分配 `college_id`、学生不存在、学生无班级、班级无专业/专业无院系——一律判为「不可见」（`isOutsideDept` 返回 true），列表接口返回空结果，单点接口 403。
- 教师只在**成绩分析**中参与：按 `teach_info.teacher_id` 校验该教师是否教授此课程（`source=SELECTION_CAMPAIGN` 时校验 `campaign_id`，否则 `course_id`），通过后可见该课程全部学生；其余管理侧接口教师不可访问。

**统一统计口径**（成绩分析 / 班级分析 / 画像 / 预警共用）

| 口径 | 规则 |
|------|------|
| 参与统计的成绩类型 | 仅 `score.score_type = REGULAR`（正常成绩）；补考 MAKEUP、重修 RETAKE 不入均分/分布/趋势 |
| 及格线 | `total_score >= 60`（`<60` 为挂科；`total_score` 为 null 视为「未出分」，不计入挂科也不参与均分） |
| 等级（`score_level`） | `>=90` 优 / `>=80` 良 / `>=70` 中 / `>=60` 及格 / 其余 不及格 |
| 单科绩点 | `<60` 或 null 记 0；否则 `(成绩-50)/10`（5 分制：60→1.0、90→4.0、100→5.0），保留 2 位 |
| 加权 GPA | `Σ(单科绩点×学分) / Σ学分`；跳过「无总评」或「无有效学分」的记录；**挂科课程绩点 0 但学分仍计入分母**；无有效记录返回 `null` |
| 学分来源 | 常规课查 `course.credit`、公选课查 `selection_campaign.credit`，按成绩的非空列路由（两表 id 空间重叠，禁止合并成一个 Map） |
| 百分比/均值 | 保留 2 位小数，`HALF_UP` |
| 学期缺省 | 多数接口 `semesterId` 不传时取「当前学期」（`semester.status = CURRENT`）；无当前学期则不按学期过滤（预警看板缺省同样回退为「不过滤」，见 15.2 陷阱） |

**枚举（持久化 / 入参 / 输出）**

| 枚举 | 持久化 code | 响应 JSON | 入参可接受 |
|------|-------------|-----------|------------|
| `WarningLevelEnum` | `YELLOW` / `ORANGE` / `RED` | `黄色预警` / `橙色预警` / `红色预警` | code 或中文描述均可（`fromValue`） |
| `WarningStatusEnum` | `ACTIVE` / `RESOLVED` | `生效中` / `已解除` | 仅输出 |
| `EvaluationStatusEnum` | `OPEN` / `CLOSED` | `已开放` / `已关闭` | 仅输出 |
| `EvaluationTemplateStatusEnum` | `ENABLED` / `DISABLED` | `启用` / `停用` | **query 参数只认枚举名 `ENABLED`/`DISABLED`**（Spring 默认 `Enum.valueOf`，不认中文，见 15.4 陷阱） |
| `ScoreTypeEnum` | `REGULAR` / `MAKEUP` / `RETAKE` | `正常` / `补考` / `重修` | 本模块只消费 `REGULAR` |

---

### 15.1 成绩分析

按课程维度统计分数段分布、跨学期趋势与班级横向对比。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/analysis/scores/distribution` | 教师/院系/教务 | 分数段分布 + 均值/及格率/标准差 |
| GET | `/api/analysis/scores/trend` | 教师/院系/教务 | 课程跨学期趋势 |
| GET | `/api/analysis/scores/comparison` | 教师/院系/教务 | 同课程各班级横向对比 |

**公共参数说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| courseId | Long | 是 | 课程 id。`source=SELECTION_CAMPAIGN` 时按 **campaignId** 解释，否则按 `courseId` 解释 |
| source | String | 否 | 仅 `SELECTION_CAMPAIGN` 有特殊含义（公选课，按 `score.campaign_id` 过滤）；其他任意值/不传均按常规课 `score.course_id` 过滤 |
| className | String | 否 | 班级名精确过滤。**仅教务/院系生效**，教师传了也被忽略 |
| semesterId | Long | 否 | 缺省取当前学期；无当前学期则不过滤 |

**数据范围解析顺序**（`resolveScopeForCourse`）：

1. 教师：先校验课程归属（存在该教师 + 该课程/活动的 `teach_info`），不通过 → 403 `权限不足`；通过后学生范围 `null`（该课程全部学生）。
2. 教务：全校（可被 `className` 收窄）。
3. 院系：本院学生（可被 `className` 与本院班级取交集，交集为空返回空结果）。
4. 其他类型：403（已被类级注解拦住，实际不可达）。

> **`courseId` 与 `source` 必须配对**：传公选课的 campaignId 却漏传 `source=SELECTION_CAMPAIGN`，会当成常规课 `course_id` 查询——结果通常为空；教师会因「未教授该课程」直接 403。趋势/对比接口的 `courseName` 由 `resolveNameByEitherId` 先按 course.id、再按 campaign.id 解析（不参考 `source`），若两表存在相同数值 id 会显示常规课的名称。

#### 15.1.1 分数段分布

```
GET /api/analysis/scores/distribution?courseId=101&source=&className=&semesterId=3
权限：教师 / 院系管理员 / 教务管理员
```

**响应字段 `ScoreDistributionDto`**

| 字段 | 类型 | 说明 |
|------|------|------|
| courseId | Long | 请求的课程/活动 id |
| courseName | String | 课程名（先按 course.id，再按 campaign.id 解析） |
| totalCount | Integer | 有总评成绩的记录数（`total_score` 非空） |
| seg0to59 | Integer | [0,59] 不及格人数 |
| seg60to69 | Integer | [60,69] 人数 |
| seg70to79 | Integer | [70,79] 人数 |
| seg80to89 | Integer | [80,89] 人数 |
| seg90to100 | Integer | [90,100] 人数 |
| avgScore | BigDecimal | 平均分（无有效成绩时为 null） |
| maxScore | BigDecimal | 最高分（无成绩时 null） |
| minScore | BigDecimal | 最低分（无成绩时 null） |
| passRate | BigDecimal | 及格率（百分比数值，如 `75.00` 表示 75%） |
| stddev | BigDecimal | 总体标准差（除以 n；样本 <2 时为 null） |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "courseId": 101,
    "courseName": "高等数学",
    "totalCount": 40,
    "seg0to59": 4,
    "seg60to69": 8,
    "seg70to79": 12,
    "seg80to89": 10,
    "seg90to100": 6,
    "avgScore": 76.35,
    "maxScore": 98.00,
    "minScore": 42.00,
    "passRate": 90.00,
    "stddev": 12.34
  }
}
```

**业务规则**

- 分段边界：`<60` / `[60,70)` / `[70,80)` / `[80,90)` / `>=90`，五段计数之和 = `totalCount`。
- 院系学生范围为空时返回「零值分布」：`totalCount=0`、五段全 0，`avgScore/passRate/stddev/maxScore/minScore` 均为 `null`（不是 0）。
- 只统计 REGULAR 成绩；学员范围过滤基于学生 user.id。

**错误场景**

| code | message |
|------|---------|
| 400 | 缺少必填参数: courseId |
| 400 | 参数类型错误: courseId |
| 400 | 课程ID不能为空（`courseId=` 传空串） |
| 403 | 权限不足（教师未教授该课程） |
| 403 | 拦截器：用户类型不在 TEACHER/DEPARTMENT/ACADEMIC_ADMIN |

#### 15.1.2 课程跨学期趋势

```
GET /api/analysis/scores/trend?courseId=101&source=&className=
权限：教师 / 院系管理员 / 教务管理员
```

注意：**此接口没有 `semesterId` 参数**，固定返回该课程在各学期的全部统计点。

**响应字段 `ScoreTrendDto`**

| 字段 | 类型 | 说明 |
|------|------|------|
| courseId | Long | 课程/活动 id |
| courseName | String | 课程名 |
| points | Array | 学期统计点，按 `semesterId` 升序 |
| points[].semesterId | Long | 学期 id |
| points[].semesterName | String | 学期名 |
| points[].totalCount | Integer | 该学期成绩记录数（**包含 total_score 为 null 的行**，见陷阱） |
| points[].avgScore | BigDecimal | 该学期平均分（只算非 null 总评） |
| points[].passRate | BigDecimal | 该学期及格率（百分比，只算非 null 总评） |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "courseId": 101,
    "courseName": "高等数学",
    "points": [
      { "semesterId": 1, "semesterName": "2025-2026-1", "totalCount": 38, "avgScore": 74.20, "passRate": 86.84 },
      { "semesterId": 2, "semesterName": "2025-2026-2", "totalCount": 40, "avgScore": 76.35, "passRate": 90.00 }
    ]
  }
}
```

> **`totalCount` 与 `avgScore` 分母不一致**：`totalCount` 用 `该学期成绩行数`，而 `avgScore`/`passRate` 的分母是「总评非空」的行数。存在未出分记录时，两处对不上属正常。

- `semesterId` 为 null 的成绩行被丢弃，不出现在任何点中；无任何有效学期时 `points=[]`。
- 学院/院系的 `className` 过滤同样生效；教师忽略该参数。

**错误场景**：同 15.1.1（`课程ID不能为空` / 403）。

#### 15.1.3 同课程班级横向对比

```
GET /api/analysis/scores/comparison?courseId=101&source=&semesterId=3
权限：教师 / 院系管理员 / 教务管理员
```

本接口**不接受** `className`（对比的就是同一课程下的所有班级）。

**响应字段 `ScoreComparisonDto`**

| 字段 | 类型 | 说明 |
|------|------|------|
| courseId | Long | 课程/活动 id |
| courseName | String | 课程名 |
| semesterId | Long | 实际使用的学期 id（缺省为当前学期；无当前学期为 null） |
| semesterName | String | 学期名（无则 null） |
| classes | Array | 各班级统计点 |
| classes[].className | String | 班级名；学生无班级时归入 `未知班级` |
| classes[].totalCount | Integer | 该班成绩记录数（含未出分行） |
| classes[].avgScore | BigDecimal | 班级平均分（只算非 null 总评；无有效成绩 null） |
| classes[].passRate | BigDecimal | 班级及格率（百分比；无有效成绩 null） |
| classes[].failCount | Integer | 挂科人次（总评 <60 的行数） |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "courseId": 101,
    "courseName": "高等数学",
    "semesterId": 3,
    "semesterName": "2025-2026-1",
    "classes": [
      { "className": "计算机2301", "totalCount": 20, "avgScore": 78.10, "passRate": 95.00, "failCount": 1 },
      { "className": "计算机2302", "totalCount": 20, "avgScore": 74.60, "passRate": 85.00, "failCount": 3 }
    ]
  }
}
```

- 班级列表**未显式排序**（按成绩查询结果中班级首次出现的顺序）。
- 班级归属按学生**当前** `student.class_id` 解析；学生无班级 → `未知班级` 桶。
- 院系范围为空时 `classes=[]`，但 `courseId/courseName/semesterId/semesterName` 正常返回。

**错误场景**：同 15.1.1。

---

### 15.2 学业预警

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/analysis/warnings/config` | 教务 | 查询三级阈值配置 |
| PUT | `/api/analysis/warnings/config` | 教务 | 批量更新阈值配置 |
| POST | `/api/analysis/warnings/scan` | 教务 | 手动触发全体学生扫描 |
| GET | `/api/analysis/warnings` | 教务/院系 | 预警看板（分页，默认当前学期生效中） |
| GET | `/api/analysis/warnings/me` | 学生 | 本人当前生效预警 |

**预警模型**

- 三个级别 `YELLOW < ORANGE < RED`（命中多级取最高级）。
- 每级一行 `warning_config`，三个条件**任一命中**即触发该级别：
  - `gpaThreshold`：GPA **严格小于**该值触发（`gpa < threshold`）；
  - `failCountThreshold`：累计挂科门数 **>=** 触发；
  - `semesterFailThreshold`：当前学期挂科门数 **>=** 触发；
  - 某个阈值字段为 null 表示该条件不启用。
- 字段含义（表 `warning_config`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| level | WarningLevelEnum | `YELLOW` / `ORANGE` / `RED` |
| gpaThreshold | BigDecimal(4,2) | GPA 下限（低于触发） |
| failCountThreshold | Integer | 累计挂科门数阈值（>= 触发） |
| semesterFailThreshold | Integer | 单学期挂科门数阈值（>= 触发） |
| enabled | Integer | 0 禁用 / 1 启用（禁用级别不参与匹配） |

- 初始化种子（`init-analysis-tables.sql`，已存在则跳过）：

| 级别 | gpaThreshold | failCountThreshold | semesterFailThreshold |
|------|--------------|--------------------|-----------------------|
| YELLOW | 2.00 | 2 | 2 |
| ORANGE | 1.50 | 4 | 3 |
| RED | 1.00 | 6 | 4 |

- **挂科判定（预警口径）**：按「课程」而非按成绩行——同一门课的所有尝试（REGULAR/MAKEUP/RETAKE）取最高总评，最高分仍 `<60` 才算挂科；分组键用 `K{courseId}` / `C{campaignId}` 前缀区分常规课与公选课，避免两表 id 相同串台。
- **GPA（预警口径）**：全部 REGULAR 成绩的累计加权 GPA（跨学期），规则同 15.0。
- `semesterFailCount`：上述「未通过课程」中，其 REGULAR 成绩所在学期 == 当前学期的门数。

#### 15.2.1 查询预警阈值配置

```
GET /api/analysis/warnings/config
权限：仅教务管理员（@RequireAcademicAdmin）
```

**响应字段**（数组，按 id 升序）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 配置行 id |
| level | String | 输出中文描述：`黄色预警` / `橙色预警` / `红色预警` |
| gpaThreshold | BigDecimal | GPA 下限，可为 null |
| failCountThreshold | Integer | 累计挂科阈值，可为 null |
| semesterFailThreshold | Integer | 单学期挂科阈值，可为 null |
| enabled | Integer | 0/1 |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "id": 1, "level": "黄色预警", "gpaThreshold": 2.00, "failCountThreshold": 2, "semesterFailThreshold": 2, "enabled": 1 },
    { "id": 2, "level": "橙色预警", "gpaThreshold": 1.50, "failCountThreshold": 4, "semesterFailThreshold": 3, "enabled": 1 },
    { "id": 3, "level": "红色预警", "gpaThreshold": 1.00, "failCountThreshold": 6, "semesterFailThreshold": 4, "enabled": 1 }
  ]
}
```

#### 15.2.2 更新预警阈值配置

```
PUT /api/analysis/warnings/config
权限：仅教务管理员
Content-Type: application/json
```

**请求体 `WarningConfigRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| configs | Array | 是 | 配置列表，非空 |
| configs[].level | String | 是 | 按级别定位已有行；可传 `YELLOW` 或 `黄色预警` |
| configs[].gpaThreshold | BigDecimal | 否 | **整体覆盖**：不传即置 null（关闭该条件） |
| configs[].failCountThreshold | Integer | 否 | 同上，不传即置 null |
| configs[].semesterFailThreshold | Integer | 否 | 同上，不传即置 null |
| configs[].enabled | Integer | 否 | 不传默认 1；0 禁用、1 启用 |
| configs[].id | Long | 否 | 忽略（按 level 定位） |

**请求示例**

```json
{
  "configs": [
    { "level": "YELLOW", "gpaThreshold": 2.20, "failCountThreshold": 2, "semesterFailThreshold": 2, "enabled": 1 },
    { "level": "ORANGE", "gpaThreshold": 1.60, "failCountThreshold": 4, "semesterFailThreshold": 3, "enabled": 1 },
    { "level": "RED", "gpaThreshold": 1.00, "failCountThreshold": 6, "semesterFailThreshold": 4, "enabled": 1 }
  ]
}
```

**响应**：`{"code":200,"message":"success","data":null}`

**业务规则**

- 只更新**已存在**的级别行（三个级别由初始化脚本种下，不支持新增/删除级别）。
- 每个级别必须把三个阈值一并传全，否则未传字段被清空——「只想改 GPA 阈值」也会把两个挂科阈值置 null。
- 不做阈值大小/区间校验（允许 GPA 负数、挂科 0 等），也不会校验「黄 > 橙 > 红」的单调性。

**错误场景**

| code | message |
|------|---------|
| 400 | 请求体格式错误 |
| 400 | 配置不能为空（`configs` 为 null/空数组） |
| 400 | 预警级别不能为空（某条 `level` 为 null） |
| 404 | 预警级别 YELLOW 配置不存在（消息中的级别为枚举 `toString`，中文入参也会显示成 code） |
| 409 | 预警级别已存在（库中存在同级别重复行时才可达） |

#### 15.2.3 触发预警扫描

```
POST /api/analysis/warnings/scan
权限：仅教务管理员
```

无请求参数（开启人 `callerUserId` 服务端传入但当前未使用）。

**响应字段 `WarningScanResultDto`**

| 字段 | 类型 | 说明 |
|------|------|------|
| scannedCount | Integer | 本次评估的学生数 = 全库有任意成绩记录的学生去重数 |
| warnedCount | Integer | **本次新激活**的预警人数（此前不是该级别的 ACTIVE 才算） |
| resolvedCount | Integer | 本次因好转被解除的 ACTIVE 记录数（级别变化的旧记录不计入） |
| byLevel | Map | `黄色预警/橙色预警/红色预警` → 本次命中的学生数（固定三键，无命中为 0） |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "scannedCount": 1200,
    "warnedCount": 7,
    "resolvedCount": 3,
    "byLevel": { "黄色预警": 12, "橙色预警": 4, "红色预警": 1 }
  }
}
```

**扫描语义（必须按此理解看板数据）**

1. 取**当前学期**（`status=CURRENT`），预警记录按 `(student_user_id, semester_id=当前学期, level)` upsert。
2. 指标计算使用**全库所有学期**的成绩（累计 GPA、累计挂科），因此一条「本学期」的预警可能由往年成绩触发。
3. 启用配置按严重程度降序（RED → ORANGE → YELLOW）匹配，命中首个即定级；级别变化时旧级别的 ACTIVE 记录置 `RESOLVED`。
4. 无任何级别命中的学生：其**本学期**所有 ACTIVE 记录置 `RESOLVED`（`resolvedCount` 只统计这部分）。
5. 新激活（此前该学生本学期该级别不是 ACTIVE）→ 调用 `WarningNoticeScenes.warningActivated` 发站内通知（`NotificationTypeEnum.WARNING`，事务提交后发送）；已是同级 ACTIVE 的重复扫描不重复通知。
6. `reason` 固定格式：`累计GPA {gpa}，累计挂科 {n} 门，本学期挂科 {m} 门`（无 GPA 时显示「无」）。
7. 只处理「本学期」的记录：**历史学期的 ACTIVE 记录不会被本次扫描解除**，看板显式传历史 `semesterId` 时仍能看到。
8. 全流程单事务：任一学生写入失败整体回滚（通知也随之不发）。

**错误场景**

| code | message |
|------|---------|
| 400 | 无当前学期，无法扫描 |
| 400 | 无启用的预警阈值配置（三级全 enabled=0 或配置表为空） |
| 400 | 引用的用户不存在(id=xxx)（命中学生有成绩但 user 表无此人，且需新建记录时） |
| 403 | 拦截器：非教务管理员 |

#### 15.2.4 预警看板

```
GET /api/analysis/warnings?semesterId=&level=&page=&pageSize=
权限：教务管理员 / 院系管理员（@RequireManagement）
```

**请求参数（query）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semesterId | Long | 否 | 缺省取当前学期；**无当前学期时退化为不过滤**（返回所有学期的 ACTIVE 记录） |
| level | String | 否 | 级别过滤，可传 `RED` 或 `红色预警`；空白视为不过滤 |
| page | Integer | 否 | 默认 1 |
| pageSize | Integer | 否 | 默认 20，上限 100 |

**响应：`PageResult<WarningItemDto>`**

| 字段 | 类型 | 说明 |
|------|------|------|
| records | Array | 当前页数据 |
| total | Long | 总记录数 |
| page | Integer | 当前页 |
| pageSize | Integer | 每页条数 |
| pages | Long | 总页数 |
| records[].id | Long | 预警记录 id |
| records[].studentUserId | Long | 学生 user.id |
| records[].studentName | String | 学生姓名 |
| records[].studentNo | String | 学号 |
| records[].className | String | 班级名（无班级 null） |
| records[].level | String | 中文级别描述 |
| records[].reason | String | 触发原因描述 |
| records[].gpa | BigDecimal | 扫描时 GPA 快照 |
| records[].failCount | Integer | 扫描时累计挂科门数 |
| records[].semesterFailCount | Integer | 扫描时本学期挂科门数 |
| records[].semesterId | Long | 学期 id |
| records[].semesterName | String | 学期名 |
| records[].status | String | 固定 `生效中`（看板只查 ACTIVE） |
| records[].createTime | LocalDateTime | 记录创建时间 |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 15,
        "studentUserId": 1001,
        "studentName": "李四",
        "studentNo": "20230012",
        "className": "计算机2301",
        "level": "橙色预警",
        "reason": "累计GPA 1.42，累计挂科 4 门，本学期挂科 3 门",
        "gpa": 1.42,
        "failCount": 4,
        "semesterFailCount": 3,
        "semesterId": 3,
        "semesterName": "2025-2026-1",
        "status": "生效中",
        "createTime": "2026-11-20T09:30:00"
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20,
    "pages": 1
  }
}
```

**业务规则**

- 只返回 `status=ACTIVE`；排序为 SQL 级 `ORDER BY FIELD(level,'RED','ORANGE','YELLOW'), update_time DESC`（红 > 橙 > 黄，同级最近更新在前），分页全局有序。
- 院系管理员只看到本院学生；院系归属无法判定时返回空页（fail-closed）。教务全校。
- `level` 传中文/英文均可，非法值抛 `IllegalArgumentException` 转 400。

**错误场景**

| code | message |
|------|---------|
| 400 | 未知预警级别: xxx |
| 403 | 拦截器：非教务/院系 |

#### 15.2.5 我的预警（学生）

```
GET /api/analysis/warnings/me
权限：仅学生（@RequireStudent）
```

**响应**：`WarningItemDto` 数组（字段同上），仅含本人 `status=ACTIVE` 的记录，按 `updateTime` 倒序后再按级别降序（同级内保持更新时间倒序）。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 15,
      "studentUserId": 1001,
      "studentName": "李四",
      "studentNo": "20230012",
      "className": "计算机2301",
      "level": "橙色预警",
      "reason": "累计GPA 1.42，累计挂科 4 门，本学期挂科 3 门",
      "gpa": 1.42,
      "failCount": 4,
      "semesterFailCount": 3,
      "semesterId": 3,
      "semesterName": "2025-2026-1",
      "status": "生效中",
      "createTime": "2026-11-20T09:30:00"
    }
  ]
}
```

- **不按学期过滤**：所有学期的 ACTIVE 记录都会返回。
- 无记录返回 `[]`；学生无 `student` 子表行也不报错（学号/班级为 null）。

---

### 15.3 评教指标库

评教指标是教务维护的**共享题库**（`evaluation_item`），模板通过 `item_id` 引用。指标名/满分**不在模板中冗余快照**，模板读取时实时返回指标库当前值；只有**已提交的评教明细**（`teaching_evaluation_score`）保留提交时的名称/满分快照，历史不可变。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/analysis/evaluation-items` | 教务 | 新建指标 |
| GET | `/api/analysis/evaluation-items` | 教务 | 全部指标（按创建时间倒序） |
| PUT | `/api/analysis/evaluation-items/{id}` | 教务 | 修改指标 |
| DELETE | `/api/analysis/evaluation-items/{id}` | 教务 | 删除指标（被模板引用时拒绝） |

#### 15.3.1 新建指标

```
POST /api/analysis/evaluation-items
权限：仅教务管理员（类级 @RequireAcademicAdmin）
Content-Type: application/json
```

**请求体 `ItemCreateRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 指标名称（非空白，库内 `deleted=0` 内唯一） |
| description | String | 否 | 指标说明 |
| maxScore | Integer | 否 | 满分，默认 5，合法区间 1–100 |

**请求示例**

```json
{ "name": "教学态度", "description": "教师授课态度", "maxScore": 5 }
```

**响应字段 `ItemResponse`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 指标 id |
| name | String | 指标名称 |
| description | String | 说明 |
| maxScore | Integer | 满分 |
| usedCount | Integer | 被多少个模板指标关联引用（新建时固定 0） |
| createTime | LocalDateTime | 创建时间 |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "教学态度",
    "description": "教师授课态度",
    "maxScore": 5,
    "usedCount": 0,
    "createTime": "2026-11-24T10:00:00"
  }
}
```

**错误场景**

| code | message |
|------|---------|
| 400 | 指标名称不能为空 |
| 400 | 满分须在 1-100 之间 |
| 409 | 指标名称已存在 |
| 400 | 引用的用户不存在(id=xxx)（JWT 用户被硬删，极少见） |

#### 15.3.2 指标列表

```
GET /api/analysis/evaluation-items
权限：仅教务管理员
```

返回 `ItemResponse` 数组，按 `createTime` 倒序；`usedCount` 为 `evaluation_template_item` 中引用该指标的关联行数（含停用模板的引用）。

#### 15.3.3 修改指标

```
PUT /api/analysis/evaluation-items/{id}
权限：仅教务管理员
Content-Type: application/json
```

**请求体 `ItemUpdateRequest`**（字段全部可选，null 表示不修改）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 否 | 非空白；与其他指标重名 409 |
| description | String | 否 | 传空串可清空 |
| maxScore | Integer | 否 | 1–100 |
| updateTemplates | Boolean | 否 | **历史遗留字段，服务端完全忽略**（见下方陷阱） |

**响应**：`ItemResponse`（`usedCount` 为当前引用数）。

> **`updateTemplates` 是无效字段**：控制器与 DTO 注释仍描述「同步刷新模板快照」，但 `EvaluationItemServiceImpl.updateItem` 从未读取它。原因是模板侧已无快照——名称/满分本来就实时读指标库；已提交明细保留自身快照，不受影响。传 `true`/`false` 行为完全一致。

> **指标改动不做模板一致性校验**：把某指标满分从 5 改成 10，会让引用它的模板出现「各指标满分不一致」（模板只在创建/更新绑定指标时校验），进而使 `avg_score` 量纲失真。修改被引用指标的满分前需人工确认影响面。

- 名称重复校验只在 `name` 有变化时执行（与自身同名不算冲突）。
- 逻辑删除（`deleted=1`）的指标不参与重名判断，可复用已删指标的名称。

**错误场景**

| code | message |
|------|---------|
| 404 | 指标不存在 |
| 400 | 指标名称不能为空 |
| 400 | 满分须在 1-100 之间 |
| 409 | 指标名称已存在 |

#### 15.3.4 删除指标

```
DELETE /api/analysis/evaluation-items/{id}
权限：仅教务管理员
```

- 逻辑删除（`deleted=1`）。
- 若仍被任一模板关联引用 → 409，必须先修改/删除相关模板的指标绑定。

**错误场景**

| code | message |
|------|---------|
| 404 | 指标不存在 |
| 409 | 指标已被模板引用，无法删除 |

---

### 15.4 评教模板

**模型**：`evaluation_template`（模板）— `evaluation_template_item`（模板-指标关联，含 `sortOrder` 与 `required`）— `evaluation_item`（指标库）。模板不复制指标名/满分，响应时实时关联指标库填充 `itemName/maxScore`。

关联行只有**排序**（`sortOrder`）和**必填标记**（`required`），**没有权重**：评教均分 `avg_score` 是各指标原始分的算术平均（不按满分归一化、不加权），因此模板强制要求「各指标满分一致」以保证量纲统一。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/analysis/evaluation-templates` | 教务 | 创建模板 |
| GET | `/api/analysis/evaluation-templates` | 教务 | 模板列表 |
| GET | `/api/analysis/evaluation-templates/{id}` | 教务 | 模板详情 |
| PUT | `/api/analysis/evaluation-templates/{id}` | 教务 | 修改模板（可整体调整指标） |
| DELETE | `/api/analysis/evaluation-templates/{id}` | 教务 | 删除模板 |
| PUT | `/api/analysis/evaluation-templates/{id}/default` | 教务 | 设为全局默认 |
| PUT | `/api/analysis/evaluation-templates/{id}/status?status=` | 教务 | 启用/停用 |
| PUT | `/api/analysis/evaluation-templates/override/{teachInfoId}` | 教务 | 设置/清除课程级覆盖 |
| GET | `/api/analysis/evaluation-templates/override/{teachInfoId}` | 教务 | 查询课程级覆盖 |

**模板状态与默认语义**

- 新建模板固定 `status=ENABLED`、`isDefault=0`。
- `is_default=1` 表示全局默认模板（**全局至多一个**，服务层保证）。
- 学生评教表单解析顺序：**课程覆盖（`evaluation_template_override`）优先 → 否则全局默认模板**。
- 停用（DISABLED）模板不可用于评教（表单返回「当前评教模板不可用」），也不可设为默认/覆盖。
- 默认模板不可停用、不可删除。

> **默认模板一旦存在就无法通过 API 切换**：`setDefault` 在检测到「存在另一个 `is_default=1` 的模板」时直接 409 `已有默认模板`，后面的「原默认置普通」代码实际执行不到；而默认模板又不可停用（409）、不可删除（409）。初始化脚本已种下「默认评教模板」，因此再对其它模板调用设默认必然失败。目前只能通过直接改库切换默认模板。

#### 15.4.1 创建模板

```
POST /api/analysis/evaluation-templates
权限：仅教务管理员
Content-Type: application/json
```

**请求体 `TemplateCreateRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 模板名称（非空白） |
| description | String | 否 | 模板说明 |
| items | Array | 是 | 至少 1 个指标项 |
| items[].itemId | Long | 是 | 指标库 id |
| items[].sortOrder | Integer | 否 | 排序，从小到大；不传按提交顺序 1..n |
| items[].required | Integer | 否 | 0 选填 / 1 必填，默认 1 |
| items[].itemName | String | — | **请求时忽略**（响应返回指标库当前值） |
| items[].maxScore | Integer | — | **请求时忽略** |

**请求示例**

```json
{
  "name": "期末评教模板",
  "description": "四维度评教",
  "items": [
    { "itemId": 1, "sortOrder": 1, "required": 1 },
    { "itemId": 2, "sortOrder": 2, "required": 1 },
    { "itemId": 3, "sortOrder": 3, "required": 0 }
  ]
}
```

**响应字段 `TemplateResponse`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 模板 id |
| name | String | 模板名 |
| description | String | 说明 |
| status | String | `启用` / `停用`（JSON 输出中文描述） |
| isDefault | Integer | 0 普通 / 1 全局默认 |
| items | Array | 指标项，按 `sortOrder` 升序 |
| items[].itemId | Long | 指标 id |
| items[].itemName | String | 指标当前名称（实时取指标库） |
| items[].maxScore | Integer | 指标当前满分（实时取指标库） |
| items[].sortOrder | Integer | 排序 |
| items[].required | Integer | 0/1 |
| createTime | LocalDateTime | 创建时间 |
| updateTime | LocalDateTime | 更新时间 |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 2,
    "name": "期末评教模板",
    "description": "四维度评教",
    "status": "启用",
    "isDefault": 0,
    "items": [
      { "itemId": 1, "itemName": "教学态度", "maxScore": 5, "sortOrder": 1, "required": 1 },
      { "itemId": 2, "itemName": "教学内容", "maxScore": 5, "sortOrder": 2, "required": 1 },
      { "itemId": 3, "itemName": "教学效果", "maxScore": 5, "sortOrder": 3, "required": 0 }
    ],
    "createTime": "2026-11-24T10:00:00",
    "updateTime": "2026-11-24T10:00:00"
  }
}
```

**指标校验规则（create 与 update 共用，按顺序）**

1. `items` 为 null/空 → 400 `至少需要 1 个指标`；
2. 存在重复 `itemId` → 400 `指标不可重复`；
3. 有 itemId 在指标库不存在（含已逻辑删除）→ 400 `存在无效指标`；
4. 这些指标的 `maxScore` 不全相同 → 400 `模板内各指标满分须一致`（保证 `avg_score` 量纲统一）。

**错误场景**

| code | message |
|------|---------|
| 400 | 请求体格式错误 |
| 400 | 模板名称不能为空 |
| 400 | 至少需要 1 个指标 |
| 400 | 指标不可重复 |
| 400 | 存在无效指标 |
| 400 | 模板内各指标满分须一致 |
| 400 | 引用的用户不存在(id=xxx) |
| 409 | 模板指标已存在（新模板无关联行，理论不可达；仅并发脏数据） |

#### 15.4.2 模板列表 / 详情

```
GET /api/analysis/evaluation-templates
GET /api/analysis/evaluation-templates/{id}
权限：仅教务管理员
```

- 列表：`TemplateResponse` 数组，按 `isDefault` 倒序、`createTime` 倒序（默认模板排最前）。
- 详情：`{id}` 不存在 → 404 `模板不存在`。

#### 15.4.3 修改模板

```
PUT /api/analysis/evaluation-templates/{id}
权限：仅教务管理员
Content-Type: application/json
```

**请求体 `TemplateUpdateRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 否 | 非空白 |
| description | String | 否 | 传空串可清空 |
| items | Array | 否 | **传了则整体对齐到该集合**（按 itemId 增量 diff，删除缺失项、新增新项、更新保留项的 sortOrder/required）；不传则只改名称/描述 |

- `items` 传空数组不等于「清空」，而是 400 `至少需要 1 个指标`（模板必须至少保留 1 个指标）。
- 响应为更新后的完整 `TemplateResponse`。

**错误场景**

| code | message |
|------|---------|
| 404 | 模板不存在 |
| 400 | 模板名称不能为空 |
| 400 | 至少需要 1 个指标 / 指标不可重复 / 存在无效指标 / 模板内各指标满分须一致 |

#### 15.4.4 删除模板

```
DELETE /api/analysis/evaluation-templates/{id}
权限：仅教务管理员
```

删除前依次校验：

1. 模板不存在 → 404 `模板不存在`；
2. 是默认模板 → 409 `默认模板不可删除`；
3. 被课程覆盖引用（`evaluation_template_override.template_id`）→ 409 `模板被课程覆盖引用，无法删除`；
4. 已有评教记录（`teaching_evaluation.template_id`）→ 409 `模板已有评教记录，无法删除`。

校验通过后：硬删模板-指标关联行，模板本身逻辑删除。

#### 15.4.5 设为全局默认

```
PUT /api/analysis/evaluation-templates/{id}/default
权限：仅教务管理员
```

无请求体。

**错误场景**

| code | message |
|------|---------|
| 404 | 模板不存在 |
| 409 | 停用模板不可设为默认 |
| 409 | 已有默认模板（已存在另一个默认模板，见 15.4 开头陷阱） |

**响应**：`{"code":200,"message":"success","data":null}`

#### 15.4.6 启用/停用模板

```
PUT /api/analysis/evaluation-templates/{id}/status?status=DISABLED
权限：仅教务管理员
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | Enum | 是 | **query 参数，只接受枚举名** `ENABLED` / `DISABLED`（大小写敏感） |

> **不能传中文**：这里走 Spring 默认的枚举转换（`Enum.valueOf`），`?status=启用` / `?status=enabled` 都会得到 400 `参数类型错误: status`。与请求体/响应体里的 `@JsonValue` 中文描述无关。

- 对默认模板执行 `DISABLED` → 409 `默认模板不可停用`。
- 停用一个已被课程覆盖引用的模板是允许的（引用不校验状态），但该课程学生取表单会得到 400 `当前评教模板不可用`，不会回退到全局默认。

**错误场景**

| code | message |
|------|---------|
| 404 | 模板不存在 |
| 409 | 默认模板不可停用 |
| 400 | 缺少必填参数: status |
| 400 | 参数类型错误: status |

#### 15.4.7 设置/清除课程级覆盖

```
PUT /api/analysis/evaluation-templates/override/{teachInfoId}
权限：仅教务管理员
Content-Type: application/json（body 可省略）
```

**请求体 `TemplateOverrideRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| templateId | Long | 否 | 目标模板 id；**null / body 缺省 = 清除覆盖**，该课程回退到全局默认模板 |

- 清除为幂等操作（无覆盖时不报错）。
- 设置时按 `teachInfoId` upsert（`evaluation_template_override` 应用层保证一课一行）。
- 每次调用都会先校验 `teachInfoId` 对应的授课安排存在。

**错误场景**

| code | message |
|------|---------|
| 400 | 请求体格式错误（仅当 body 是非法 JSON） |
| 400 | 引用的授课安排不存在(id=xxx) |
| 404 | 模板不存在 |
| 409 | 停用模板不可设为覆盖 |

#### 15.4.8 查询课程级覆盖

```
GET /api/analysis/evaluation-templates/override/{teachInfoId}
权限：仅教务管理员
```

- 未设置覆盖 → `data: null`（前端按「使用全局默认」展示）。
- 已设置 → 返回被覆盖的 `TemplateResponse`（完整指标列表）。
- **不校验授课安排是否存在**：传任意不存在的 `teachInfoId` 只会得到 null。

---

### 15.5 评教周期与评教提交

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/analysis/evaluations/form?teachInfoId=` | 任意登录 | 取评教表单（解析该课程所用模板） |
| POST | `/api/analysis/evaluations` | 学生 | 提交评教（一人一授课安排一条，重复为更新） |
| GET | `/api/analysis/evaluations/my` | 学生 | 我的已提交评教 |
| POST | `/api/analysis/evaluations/period/open` | 教务 | 开启当前学期评教周期 |
| POST | `/api/analysis/evaluations/period/close` | 教务 | 关闭当前学期评教周期 |
| GET | `/api/analysis/evaluations/period` | 任意登录 | 当前学期周期状态（学生开放时附可评课程） |

**周期语义**

- 周期按**学期一行**（`evaluation_period`），只针对**当前学期**开启/关闭，无参数。
- 未开启（无记录）或状态为 CLOSED 时：学生**不能提交**（即便只为更新已提交内容），提交前置校验直接 400 `评教暂未开放，请等待教务开启`。
- 开/关分别记录 `openUserId/openTime` 与 `closeTime`；重复开启会把 `openTime` 重置为本次时间并清空 `closeTime`。
- 周期与表单分离：**取表单接口不校验周期**（前端可在未开放时预取题目），只有提交校验。

#### 15.5.1 获取评教表单

```
GET /api/analysis/evaluations/form?teachInfoId=55
权限：任意已登录用户（@RequireLogin）
```

**请求参数（query）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| teachInfoId | Long | 是 | 授课安排 id |

**响应字段 `EvaluationTemplateView`**

| 字段 | 类型 | 说明 |
|------|------|------|
| templateId | Long | 解析所得模板 id（课程覆盖优先，否则全局默认） |
| templateName | String | 模板名 |
| items | Array | 指标项（按 sortOrder 升序） |
| items[].itemId | Long | 指标 id（提交时回传） |
| items[].itemName | String | 指标当前名称 |
| items[].maxScore | Integer | 指标当前满分 |
| items[].sortOrder | Integer | 排序 |
| items[].required | Integer | 0 选填 / 1 必填 |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "templateId": 1,
    "templateName": "默认评教模板",
    "items": [
      { "itemId": 1, "itemName": "教学态度", "maxScore": 5, "sortOrder": 1, "required": 1 },
      { "itemId": 2, "itemName": "教学内容", "maxScore": 5, "sortOrder": 2, "required": 1 },
      { "itemId": 3, "itemName": "教学效果", "maxScore": 5, "sortOrder": 3, "required": 1 },
      { "itemId": 4, "itemName": "课堂互动", "maxScore": 5, "sortOrder": 4, "required": 1 }
    ]
  }
}
```

**业务规则**

- 解析顺序：`evaluation_template_override(teachInfoId)` 命中则用其模板（即使该模板后来被停用）；否则用 `is_default=1` 的模板。
- **不校验 `teachInfoId` 是否存在、不校验学生是否选修该课**——本接口只负责发题目，真正的门禁在 `POST /evaluations`。
- 模板无任何指标时返回空 `items`（不报错）；但提交会报 `评教模板无指标`。

**错误场景**

| code | message |
|------|---------|
| 400 | 暂未配置评教模板，请联系教务（无覆盖且无全局默认，或覆盖指向的模板已删） |
| 400 | 当前评教模板不可用（解析出的模板为停用状态） |
| 400 | 缺少必填参数: teachInfoId |

#### 15.5.2 提交评教

```
POST /api/analysis/evaluations
权限：仅学生（@RequireStudent）
Content-Type: application/json
```

**请求体 `EvaluationSubmitRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| teachInfoId | Long | 是 | 授课安排 id |
| scores | Array | 是 | 各指标评分，至少 1 项 |
| scores[].itemId | Long | 是 | 指标 id，必须属于该课程解析出的模板 |
| scores[].score | Integer | 是 | 评分，`1 ≤ score ≤ 该指标 maxScore` |
| comment | String | 否 | 文字评语（库上限 512 字符） |

**请求示例**

```json
{
  "teachInfoId": 55,
  "scores": [
    { "itemId": 1, "score": 5 },
    { "itemId": 2, "score": 4 },
    { "itemId": 3, "score": 5 },
    { "itemId": 4, "score": 4 }
  ],
  "comment": "老师讲课思路清晰，希望多留答疑时间。"
}
```

**响应字段 `TeachingEvaluationView`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 评教记录 id |
| teachInfoId | Long | 授课安排 id |
| courseId | Long | 常规课 `course.id`；**公选课为 null**（campaignId 未在视图暴露） |
| courseName | String | 课程名（公选课经 campaign 解析） |
| teacherId | Long | 教师 **teacher 表主键**（快照） |
| teacherName | String | 教师姓名 |
| semesterId | Long | 学期 id |
| semesterName | String | 学期名 |
| templateId | Long | 提交时所用模板 id（快照） |
| templateName | String | 模板名 |
| items | Array | 指标得分明细 |
| items[].itemId | Long | 指标 id |
| items[].itemName | String | 提交时的指标名快照 |
| items[].maxScore | Integer | 提交时的满分快照 |
| items[].score | Integer | 学生评分 |
| avgScore | BigDecimal | 已评分项的原始分算术平均（2 位小数） |
| comment | String | 评语 |
| createTime | LocalDateTime | 首次提交时间（重交不改变） |

**校验顺序（决定错误码优先级）**

1. `teachInfoId` 为 null → 400 `授课安排ID不能为空`；
2. `scores` 为 null/空 → 400 `至少需要 1 项评分`；
3. 授课安排不存在 → 404 `授课安排不存在`；
4. 其教师 / 课程（或选课活动）/ 学期 / 当前用户 引用不存在 → 400 `引用的教师不存在(id=…)` / `引用的选课活动不存在(id=…)` / `引用的课程不存在(id=…)` / `引用的学期不存在(id=…)` / `引用的用户不存在(id=…)`；
5. 该授课安排所属学期的评教周期未开启 → 400 `评教暂未开放，请等待教务开启`（`semesterId` 为 null 同样报此错）；
6. 学生不在该授课安排名册（常规班按班级名 CSV，公选课按选课成员）→ 403 `权限不足`；
7. 解析模板失败 / 模板停用 → 400 `暂未配置评教模板，请联系教务` / `当前评教模板不可用`；
8. 模板无指标 → 400 `评教模板无指标`；
9. 逐指标校验：必填项（`required=1` 或 null）缺失 → 400 `指标「{itemName}」未评分`；评分越界 → 400 `指标「{itemName}」评分须为 1-{maxScore}`；
10. 提交了不在模板中的 itemId → 400 `提交了不属于评教模板的指标`；
11. 并发保护：同一 `(teachInfoId, studentUserId)` Redis 锁（`lock:eval:{teachInfoId}:{studentUserId}`）已被占用 → 409 `操作过于频繁，请稍后重试`。

**业务规则**

- **一人一授课安排一条**：已存在则更新（覆盖 `avgScore/comment/templateId`，明细按 itemId 增量 diff）；否则插入。锁保证并发双击不会产生重复行。
- **可反复修改**：评教周期保持 OPEN 期间可重复提交，无次数限制；周期关闭后连更新也被拒。
- `avgScore` = **本次提交的所有条目**原始分均值（选填项未提交则不计）；不同指标满分不一致时该值量纲不具可比性（模板创建时已强制一致，但指标库后续改满分可破坏，见 15.3 陷阱）。
- **匿名固定为 1**：请求体不提供匿名开关，服务端强制 `anonymous=1`；返回视图也不暴露 anonymous 字段。
- 明细快照：`itemName/maxScore` 按**本次提交时**指标库的值写入，后续指标改名/改满分不影响历史评教。
- **重复 itemId 只在校验阶段去重**（保留第一条）：分值校验/越界判断按去重后的 map；但明细写入按请求列表逐条执行——首次提交时重复 itemId 会**插入多条重复明细**。前端必须保证 `scores[].itemId` 不重复。
- 选填项未提交时，重交会删除该指标的旧明细（整体对齐到本次提交集合）。

> **`score` 为 null 会 500**：DTO 上的 `@NonNull` 仅为静态标注，不参与运行时校验；`scores[].score` 为 null 时在汇总均值处空指针，落到全局兜底 500 `服务器内部错误，请联系管理员`。前端必须保证分值为整数。

**错误场景汇总**

| code | message |
|------|---------|
| 400 | 请求体格式错误（body 非合法 JSON） |
| 400 | 授课安排ID不能为空（body 中 `teachInfoId` 缺失/null） |
| 400 | 至少需要 1 项评分 |
| 404 | 授课安排不存在 |
| 400 | 引用的教师不存在(id=xxx) |
| 400 | 引用的选课活动不存在(id=xxx) |
| 400 | 引用的课程不存在(id=xxx) |
| 400 | 引用的学期不存在(id=xxx) |
| 400 | 引用的用户不存在(id=xxx) |
| 400 | 评教暂未开放，请等待教务开启 |
| 403 | 权限不足（未选修该授课安排） |
| 400 | 暂未配置评教模板，请联系教务 |
| 400 | 当前评教模板不可用 |
| 400 | 评教模板无指标 |
| 400 | 指标「教学态度」未评分 |
| 400 | 指标「教学态度」评分须为 1-5 |
| 400 | 提交了不属于评教模板的指标 |
| 409 | 操作过于频繁，请稍后重试 |

#### 15.5.3 我的评教

```
GET /api/analysis/evaluations/my
权限：仅学生
```

- 返回 `TeachingEvaluationView` 数组，本人全部评教（跨学期），按 `createTime` 倒序；无记录 `[]`。
- 富化课程名（公选课经 campaign 解析）/教师名/学期名/模板名，明细见 15.5.2。

> **课程名富化的 id 撞车风险**：`loadNameMaps` 把常规课名与公选课活动名合并进**同一个** `id -> name` Map（公选课后写入），当某条评教的 `courseId` 与另一活动的 `campaignId` 数值相同时，常规课可能显示成公选课名（反之公选课恒按 campaignId 查，不受影响）。本项目 `course` 与 `selection_campaign` 均为自增 id，真实数据极易撞车；对课程名准确性要求高的页面建议以 `courseId=null` 判定公选课并核对展示。

#### 15.5.4 开启评教周期

```
POST /api/analysis/evaluations/period/open
权限：仅教务管理员
```

无请求参数（开启人取自 JWT）。

**前置条件**：必须存在当前学期，且已配置全局默认模板（`is_default=1` 至少一行，**不要求该模板启用**）。

**响应字段 `EvaluationStatusDto`**

| 字段 | 类型 | 说明 |
|------|------|------|
| open | Boolean | 是否开放（本接口固定 true） |
| message | String | 开放时为 null |
| semesterId | Long | 当前学期 id |
| semesterName | String | 当前学期名 |
| openTime | LocalDateTime | 本次开启时间 |
| closeTime | LocalDateTime | null（重新开启会清空） |
| courses | Array | **本接口不填充，固定 null** |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "open": true,
    "message": null,
    "semesterId": 3,
    "semesterName": "2025-2026-1",
    "openTime": "2026-11-24T09:00:00",
    "closeTime": null,
    "courses": null
  }
}
```

**错误场景**

| code | message |
|------|---------|
| 400 | 无当前学期 |
| 400 | 请先配置默认评教模板 |
| 400 | 引用的学期不存在(id=xxx)（学期在查询后被硬删，极少见） |

#### 15.5.5 关闭评教周期

```
POST /api/analysis/evaluations/period/close
权限：仅教务管理员
```

- 有周期行：置 `CLOSED`、写 `closeTime`，返回状态（`open=false`、`message="暂无评教"`）。
- **无周期行时不报错**：返回同样「未开放」的状态 DTO（`openTime/closeTime` 为 null）。
- 同样**不填充 courses**（null）。

**错误场景**

| code | message |
|------|---------|
| 400 | 无当前学期 |

#### 15.5.6 查询评教周期状态

```
GET /api/analysis/evaluations/period
权限：任意已登录用户（@RequireLogin）
```

**响应 `EvaluationStatusDto`**，与 15.5.4 同结构，但：

- `courses` **总是填充**（无课程时为 `[]`，不是 null）；
- **仅当调用者是学生且周期已开放**时，`courses` 才包含可评课程；其他角色/未开放时 `courses=[]`；
- 无当前学期时直接返回 `open=false, message="暂无评教", courses=[]`（学期字段全 null）。

**可评课程 `courses[]`**

| 字段 | 类型 | 说明 |
|------|------|------|
| teachInfoId | Long | 授课安排 id（提交评教时回传） |
| courseId | Long | 课程 id；公选课为 null |
| courseName | String | 课程名（公选课经 campaign 解析） |
| teacherName | String | 教师姓名 |
| evaluated | Boolean | 本人是否已提交过该授课安排的评教 |

**响应示例（学生且已开放）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "open": true,
    "message": null,
    "semesterId": 3,
    "semesterName": "2025-2026-1",
    "openTime": "2026-11-24T09:00:00",
    "closeTime": null,
    "courses": [
      { "teachInfoId": 55, "courseId": 101, "courseName": "高等数学", "teacherName": "王老师", "evaluated": false },
      { "teachInfoId": 56, "courseId": null, "courseName": "摄影鉴赏", "teacherName": "赵老师", "evaluated": true }
    ]
  }
}
```

- 可评课程来源 = 学习进度（15.7）的「当前学期我上的课」（常规班 + 公选课班），因此包含公选课与课程名。
- `evaluated` 按 `(studentUserId, teachInfoId)` 是否已有评教记录计算。

**错误场景**

| code | message |
|------|---------|
| 404 | 学生不存在（学生身份、周期已开放、但无 `student` 子表行：可评课程复用学习进度会先查学生） |

> 其他角色的 `courses` 恒为 `[]`，因此教师/教务调用不会触发上述 404。

---

### 15.6 教师教学质量

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/analysis/teacher-quality/me?semesterId=` | 教师 | 本人质量 |
| GET | `/api/analysis/teacher-quality/{teacherId}?semesterId=` | 教务/院系/教师本人 | 指定教师质量 |
| GET | `/api/analysis/teacher-quality?semesterId=&page=&pageSize=` | 教务/院系 | 质量列表/对比（分页） |

**质量评分计算口径**

- 评教侧（按 `teacher_id` 查 `teaching_evaluation`，可选学期过滤）：
  - `evalCount` = 评教条数；
  - `avgEvaluationScore` = 各条 `avg_score` 的算术平均（分母为评教条数；`avg_score` 在库中非空）；
  - `itemAverages` = 按明细表快照 `itemName` 分组的**原始分**算术平均（不加权、不按满分归一化；同名指标跨模板合并；选填项未提交不产生行）。
- 成绩侧（按 `teacher_id` + `score_type=REGULAR` 查 `score`，可选学期过滤）：
  - `courseCount` = 成绩行中**非空 `courseId`** 的去重数；
  - `courseAvgScore` = 有 `totalScore` 行的平均分；
  - `coursePassRate` = `totalScore >= 60` 占比（百分比）；
  - `studentCount` = 去重学生数（字段名虽叫「人次」，实际是去重人数）。

> **公选课拉低 `courseCount` 准确性**：统计只数 `courseId`，公选课成绩行 `course_id=NULL`、只有 `campaign_id`，因此不增加 `courseCount`。公选课成绩仍计入均分/及格率/学生数。

> **院系可见性存在 fail-open 边界**：`assertCanViewTeacher` 用 `Objects.equals(dept.getCollegeId(), teacher.getCollegeId())` 判定，若院系管理员与教师**双方 `college_id` 都为 null** 会判为同院放行（其他接口的 `StudentScopeResolver` 是 fail-closed）。正常数据下不会出现，但清理脏数据时需注意。

#### 15.6.1 教师查询本人质量

```
GET /api/analysis/teacher-quality/me?semesterId=3
权限：仅教师（@RequireTeacher）
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semesterId | Long | 否 | 不传则统计全部学期 |

- 先按 `teacher.user_id` 解析本人 `teacher.id`；无教师子表行 → 404 `教师信息不存在`。
- 响应 `TeacherQualityDto`。

**错误场景**

| code | message |
|------|---------|
| 404 | 教师信息不存在 |
| 403 | 拦截器：非教师用户 |

#### 15.6.2 查询指定教师质量

```
GET /api/analysis/teacher-quality/{teacherId}?semesterId=3
权限：教务管理员 / 院系管理员 / 教师（@RequireAuth({ACADEMIC_ADMIN, DEPARTMENT, TEACHER})）
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| teacherId | Long | 是 | **teacher 子表主键，不是 user.id** |
| semesterId | Long | 否 | 不传则统计全部学期 |

**权限判定**（`assertCanViewTeacher`）：

- 教务：任意教师；
- 教师：只能查自己（按 `teacher.user_id` 反查本人的 `teacher.id` 比对），否则 403；
- 院系：`department.college_id` 与教师 `college_id` 相等才放行；无 `department` 行 403。

**响应字段 `TeacherQualityDto`**

| 字段 | 类型 | 说明 |
|------|------|------|
| teacherId | Long | 教师 **teacher.id** |
| teacherName | String | 教师姓名（经 teacher.user_id → user.name） |
| department | String | 所属院系名（`college.name`，无则 null） |
| avgEvaluationScore | BigDecimal | 评教均分（2 位；无评教 null） |
| evalCount | Integer | 评教条数（无评教 0） |
| itemAverages | Map<String,BigDecimal> | 指标名（快照）→ 原始均分；无评教时字段为 null |
| courseCount | Integer | 授课课程数（按非空 courseId 去重） |
| courseAvgScore | BigDecimal | 所授课程成绩均分（无成绩 null） |
| coursePassRate | BigDecimal | 所授课程及格率（%，无成绩 null） |
| studentCount | Integer | 授课学生去重人数（无成绩 null） |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "teacherId": 8,
    "teacherName": "王老师",
    "department": "计算机学院",
    "avgEvaluationScore": 4.63,
    "evalCount": 36,
    "itemAverages": {
      "教学态度": 4.81,
      "教学内容": 4.55,
      "教学效果": 4.58,
      "课堂互动": 4.58
    },
    "courseCount": 2,
    "courseAvgScore": 78.42,
    "coursePassRate": 92.31,
    "studentCount": 96
  }
}
```

**错误场景**

| code | message |
|------|---------|
| 404 | 教师不存在（teacher 表无此 id） |
| 403 | 权限不足（教师查他人 / 院系查外院） |
| 403 | 拦截器：用户类型不符 |

#### 15.6.3 教师质量列表/对比

```
GET /api/analysis/teacher-quality?semesterId=&page=&pageSize=
权限：教务管理员 / 院系管理员（@RequireManagement）
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semesterId | Long | 否 | 不传则统计全部学期 |
| page | Integer | 否 | 默认 1 |
| pageSize | Integer | 否 | 默认 20，上限 100 |

**响应：`PageResult<TeacherQualityDto>`**（结构同 15.2.4 的分页包装，`records` 为教师质量条目）

**业务规则**

- 范围：教务全校教师；院系仅本院教师（`teacher.college_id == department.college_id`），院系无 `college_id` 时教师集合为空 → 空页。
- **无任何评教且无任何成绩的教师被跳过**，不出现在列表。
- 排序：`avgEvaluationScore` 倒序，null 排最后（同分不保证稳定次序）。
- 分页在**内存**完成（聚合无法下推 SQL）：先全量组装、全局排序、再按 `PageResult.slice` 切片，故 `total` 为符合条件的教师总数。
- 院系失败关闭：`teachers` 为空时直接返回空页（`records=[]`）。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "teacherId": 8,
        "teacherName": "王老师",
        "department": "计算机学院",
        "avgEvaluationScore": 4.63,
        "evalCount": 36,
        "itemAverages": { "教学态度": 4.81, "教学内容": 4.55 },
        "courseCount": 2,
        "courseAvgScore": 78.42,
        "coursePassRate": 92.31,
        "studentCount": 96
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20,
    "pages": 1
  }
}
```

---

### 15.7 学习进度

课程进度由 `teach_info` 周次 + 考试状态 + 成绩有无**派生计算**，不落表。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/analysis/progress/me` | 学生 | 本人当前学期进度 |
| GET | `/api/analysis/progress/{studentUserId}` | 教务/院系 | 指定学生进度 |

#### 15.7.1 我的学习进度

```
GET /api/analysis/progress/me
权限：仅学生（@RequireStudent）
```

**响应字段 `LearningProgressDto`**

| 字段 | 类型 | 说明 |
|------|------|------|
| studentUserId | Long | 学生 user.id |
| studentName | String | 姓名 |
| semesterName | String | 当前学期名（无当前学期 null） |
| currentWeek | Integer | 当前教学周（学期起始周 + 自 `startDate` 起的整周数；`startDate`/`startWeek` 缺失时 null） |
| courses | Array | 当前学期在上的课 |
| courses[].teachInfoId | Long | 授课安排 id |
| courses[].courseId | Long | 常规课课程 id；**公选课为 null** |
| courses[].courseName | String | 课程名（公选课经 campaign 解析） |
| courses[].teacherName | String | 教师姓名 |
| courses[].startWeek | Integer | 起始周 |
| courses[].endWeek | Integer | 结束周 |
| courses[].progressPercent | Integer | 进度 0–100（算法见下） |
| courses[].status | String | `进行中` / `已结课` |
| courses[].examStatus | String | `无考试` / `已排考` / `已完成` |
| courses[].scoreEntered | Boolean | 是否已出成绩（存在 REGULAR 且 `totalScore` 非空） |
| courses[].totalScore | BigDecimal | 总评成绩（未出分 null） |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "studentUserId": 1001,
    "studentName": "李四",
    "semesterName": "2025-2026-1",
    "currentWeek": 12,
    "courses": [
      {
        "teachInfoId": 55,
        "courseId": 101,
        "courseName": "高等数学",
        "teacherName": "王老师",
        "startWeek": 1,
        "endWeek": 16,
        "progressPercent": 75,
        "status": "进行中",
        "examStatus": "已排考",
        "scoreEntered": false,
        "totalScore": null
      },
      {
        "teachInfoId": 56,
        "courseId": null,
        "courseName": "摄影鉴赏",
        "teacherName": "赵老师",
        "startWeek": 1,
        "endWeek": 8,
        "progressPercent": 100,
        "status": "已结课",
        "examStatus": "无考试",
        "scoreEntered": true,
        "totalScore": 91.00
      }
    ]
  }
}
```

**派生规则**

- 课程来源：`StudentEnrollmentResolver.studentTeachInfos(userId, 当前学期)` —— 常规班按 `FIND_IN_SET(学生班级名, teach_info.class_name)`，公选课班按 `selection_class_member`；两者合并去重。
- 进度百分比：`round((当前周 - startWeek + 1) / (endWeek - startWeek + 1) × 100)`，clamp 到 [0,100]；`startWeek/endWeek` 缺失、`endWeek < startWeek` 或当前周未知 → null。
- 当前周 < startWeek → 0；当前周 > endWeek → 100。
- `status=已结课` 的判定：`当前周 > endWeek` **或** 考试状态为 `COMPLETED`；其余 `进行中`。
- `examStatus`：该授课安排当前学期有考试且 `COMPLETED` → `已完成`，有考试但非完成 → `已排考`（**考试 `CANCELED` 也显示「已排考」**），无考试 → `无考试`。
- 成绩取该授课安排、该学生的 REGULAR 记录（多行时取第一条）。

**错误场景**

| code | message |
|------|---------|
| 404 | 学生不存在（无 student 子表行） |

#### 15.7.2 查询指定学生学习进度

```
GET /api/analysis/progress/{studentUserId}
权限：教务管理员 / 院系管理员（@RequireManagement）
```

- 权限：教务任意学生；院系经 `isOutsideDept` 判定（fail-closed：学生不存在/无班级/班级无专业/专业无院系一律 403）。
- 校验顺序：先鉴权（403），再查学生（404）。
- 响应同 15.7.1。

**错误场景**

| code | message |
|------|---------|
| 403 | 权限不足（院系查外院学生；归属链任一跳缺失同样 403） |
| 404 | 学生不存在 |
| 403 | 拦截器：学生或其他角色访问本端点 |

---

### 15.8 学生画像与班级/专业分析

#### 15.8.1 学生个人画像

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/analysis/profile/me` | 学生 | 本人画像 |
| GET | `/api/analysis/profile/{studentUserId}` | 教务/院系 | 指定学生画像 |

```
GET /api/analysis/profile/me
GET /api/analysis/profile/{studentUserId}
```

**响应字段 `StudentProfileDto`**

| 字段 | 类型 | 说明 |
|------|------|------|
| studentUserId | Long | 学生 user.id |
| studentName | String | 姓名 |
| studentNo | String | 学号 |
| className | String | 班级名（无班级 null） |
| majorName | String | 专业名（经班级 → 专业推导） |
| enrollmentYear | Integer | 入学年份 |
| semesterId | Long | 当前学期 id（无当前学期 null） |
| semesterName | String | 当前学期名 |
| cumulativeGpa | BigDecimal | 累计加权 GPA（无有效成绩 null） |
| semesterGpa | BigDecimal | 本学期加权 GPA（无当前学期/无成绩 null） |
| totalCredits | Integer | 已选课程总学分（按课程去重；含挂科与未出分课程） |
| earnedCredits | Integer | 已通过课程学分（课程去重后、非挂科课程；**未出分课程也会计入**，见陷阱） |
| failCount | Integer | 累计挂科数 |
| semesterFailCount | Integer | 本学期挂科数 |
| levelDistribution | Map<String,Integer> | 固定五键 `优/良/中/及格/不及格` → 成绩**行数** |
| semesterTrend | Array | 学期趋势，按 semesterId 升序 |
| semesterTrend[].semesterId | Long | 学期 id |
| semesterTrend[].semesterName | String | 学期名 |
| semesterTrend[].gpa | BigDecimal | 该学期加权 GPA |
| semesterTrend[].avgScore | BigDecimal | 该学期平均分 |
| semesterTrend[].failCount | Integer | 该学期挂科行数 |
| subjects | Array | **当前学期**各科表现（无当前学期 `[]`） |
| subjects[].courseId | Long | 课程 id；公选课为 null |
| subjects[].courseName | String | 课程名 |
| subjects[].courseType | String | `必修` / `选修` / `公选` / `实践` |
| subjects[].credit | Integer | 学分 |
| subjects[].totalScore | BigDecimal | 总评 |
| subjects[].scoreLevel | String | 等级 |
| subjects[].gradePoint | BigDecimal | 单科绩点 |
| classRank | Integer | 班级累计 GPA 排名（1 起；本人无 GPA 时 null） |
| classSize | Integer | 参与排名的班级人数（含本人） |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "studentUserId": 1001,
    "studentName": "李四",
    "studentNo": "20230012",
    "className": "计算机2301",
    "majorName": "计算机科学与技术",
    "enrollmentYear": 2023,
    "semesterId": 3,
    "semesterName": "2025-2026-1",
    "cumulativeGpa": 3.42,
    "semesterGpa": 3.68,
    "totalCredits": 48,
    "earnedCredits": 44,
    "failCount": 2,
    "semesterFailCount": 1,
    "levelDistribution": { "优": 6, "良": 8, "中": 5, "及格": 3, "不及格": 2 },
    "semesterTrend": [
      { "semesterId": 2, "semesterName": "2024-2025-2", "gpa": 3.20, "avgScore": 78.10, "failCount": 1 },
      { "semesterId": 3, "semesterName": "2025-2026-1", "gpa": 3.68, "avgScore": 82.40, "failCount": 1 }
    ],
    "subjects": [
      { "courseId": 101, "courseName": "高等数学", "courseType": "必修", "credit": 4, "totalScore": 86.00, "scoreLevel": "良", "gradePoint": 3.60 },
      { "courseId": null, "courseName": "摄影鉴赏", "courseType": "公选", "credit": 2, "totalScore": 91.00, "scoreLevel": "优", "gradePoint": 4.10 }
    ],
    "classRank": 5,
    "classSize": 40
  }
}
```

**业务规则与陷阱**

- 鉴权同 15.7.2（学生本人 / 教务任意 / 院系 `isOutsideDept` fail-closed）；先鉴权后 404。
- **`failCount` 按成绩行计数**（REGULAR 且 `<60` 的行数），与预警的「按课程最高分」口径**不同**：同一门课重修/补考通过后，画像的累计挂科仍包含当初的不及格行；预警则视为已通过。跨模块对数字时需注意。
- **`earnedCredits` 把「未出分」课程也算作已通过**：`isFail(null)` 为 false，`totalScore` 为 null 的课程不会被排除，学分照加。总学分与已获学分在出分前可能相同。
- `totalCredits`/`earnedCredits` 按课程去重（`K{courseId}` / `C{campaignId}`），同一课程多条成绩只计一次。
- `levelDistribution` 统计的是 `score.scoreLevel` 字段（服务端派生），不是现算阈值。
- `classRank`：仅与**同班同学**比较，按累计 GPA 降序；名次 = 「GPA 严格高于本人的人数 + 1」（同分并列）；`classSize` = 有 GPA 的同学数 + 本人（本人有 GPA 时）。无班级 → 两字段均为 null；本人无 GPA 但同班有成绩 → `classRank=null`，`classSize` 仍返回有 GPA 的同学数；无同班同学且本人有 GPA → `classRank=1`、`classSize=1`。
- `subjects` 用当前学期成绩行；成绩行按 `campaignId` 非空路由到公选课信息，因此公选课 `courseId=null`。
- 画像不区分成绩来源学期，`cumulativeGpa`/`totalCredits` 为全部 REGULAR 聚合。

**错误场景**

| code | message |
|------|---------|
| 403 | 权限不足（院系查外院 / 学生查他人） |
| 404 | 学生不存在 |

#### 15.8.2 班级/专业成绩聚合

```
GET /api/analysis/class-analysis?groupBy=class&semesterId=3
权限：教务管理员 / 院系管理员（类级 @RequireManagement）
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| groupBy | String | 否 | `class`（默认，按班级）或 `major`（按专业）；**其他值也按班级计算**，但 `groupType` 原样回显 |
| semesterId | Long | 否 | 缺省当前学期；无当前学期则不过滤（跨全部学期聚合） |

**响应字段**（`ClassAnalysisDto` 数组，按 GPA 倒序、null 最后）

| 字段 | 类型 | 说明 |
|------|------|------|
| groupKey | String | 班级名或专业名（名称缺失时 `未知班级` / `未知专业`） |
| groupType | String | 回显请求的 `groupBy` |
| studentCount | Integer | 组内参评学生去重数 |
| scoreCount | Integer | 组内 REGULAR 成绩条数 |
| avgScore | BigDecimal | 组内平均分（只算非 null 总评；无则 null） |
| gpa | BigDecimal | 组内加权 GPA（全部成绩合并后按学分加权） |
| passRate | BigDecimal | 组内及格率（%） |
| failCount | Integer | 组内挂科**人次**（<60 的行数） |
| levelDistribution | Map<String,Integer> | `优/良/中/及格/不及格` → 条数 |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "groupKey": "计算机2301",
      "groupType": "class",
      "studentCount": 40,
      "scoreCount": 200,
      "avgScore": 78.42,
      "gpa": 3.36,
      "passRate": 93.50,
      "failCount": 13,
      "levelDistribution": { "优": 20, "良": 55, "中": 60, "及格": 52, "不及格": 13 }
    }
  ]
}
```

**业务规则**

- 只统计 REGULAR；先按成绩行的 `studentUserId` 归属到**当前**班级/专业（历史成绩也归入学生当前所属组）。
- 无法归属的学生跳过：按班级时无 `class_id`；按专业时班级无专业。
- 院系范围为空 → `[]`（fail-closed）；教务全校。
- 分组由 `LinkedHashMap` 决定初次出现顺序，最终按 GPA 倒序重排。

**错误场景**

| code | message |
|------|---------|
| 403 | 拦截器：非教务/院系 |
| 400 | 参数类型错误: semesterId |

#### 15.8.3 单组跨学期趋势

```
GET /api/analysis/class-analysis/trend?groupBy=class&groupKey=计算机2301
权限：教务管理员 / 院系管理员
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| groupBy | String | 否 | `class`（默认）/ `major`；其他值按班级 |
| groupKey | String | 是 | 班级名或专业名（精确匹配） |

**响应字段 `ClassTrendDto`**

| 字段 | 类型 | 说明 |
|------|------|------|
| groupKey | String | 回显 |
| groupType | String | 回显 `groupBy` |
| points | Array | 学期统计点，按 semesterId 升序；无数据 `[]` |
| points[].semesterId | Long | 学期 id |
| points[].semesterName | String | 学期名 |
| points[].avgScore | BigDecimal | 该学期组内平均分 |
| points[].gpa | BigDecimal | 该学期组内加权 GPA |
| points[].passRate | BigDecimal | 该学期及格率 |
| points[].studentCount | Integer | 该学期组内有成绩的学生数 |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "groupKey": "计算机2301",
    "groupType": "class",
    "points": [
      { "semesterId": 2, "semesterName": "2024-2025-2", "avgScore": 76.10, "gpa": 3.18, "passRate": 90.00, "studentCount": 40 },
      { "semesterId": 3, "semesterName": "2025-2026-1", "avgScore": 78.42, "gpa": 3.36, "passRate": 93.50, "studentCount": 40 }
    ]
  }
}
```

**业务规则**

- **不按学期过滤**：返回该组全部学期的 REGULAR 统计点（无 `semesterId` 参数）。
- 组成员按学生**当前**班级/专业归属解析（先取范围内全部学生 → 计算组名 → 匹配 `groupKey`）。
- `groupKey` 为空白串直接返回空 points；不存在该组同样空 points（不报 404）。
- 院系范围内为空 → 空 points。

**错误场景**

| code | message |
|------|---------|
| 400 | 缺少必填参数: groupKey |
| 403 | 拦截器：非教务/院系 |

---

**本模块端点清单（36 个）**

| # | 方法 | 路径 |
|---|------|------|
| 1 | GET | `/api/analysis/scores/distribution` |
| 2 | GET | `/api/analysis/scores/trend` |
| 3 | GET | `/api/analysis/scores/comparison` |
| 4 | GET | `/api/analysis/warnings/config` |
| 5 | PUT | `/api/analysis/warnings/config` |
| 6 | POST | `/api/analysis/warnings/scan` |
| 7 | GET | `/api/analysis/warnings` |
| 8 | GET | `/api/analysis/warnings/me` |
| 9 | POST | `/api/analysis/evaluation-items` |
| 10 | GET | `/api/analysis/evaluation-items` |
| 11 | PUT | `/api/analysis/evaluation-items/{id}` |
| 12 | DELETE | `/api/analysis/evaluation-items/{id}` |
| 13 | POST | `/api/analysis/evaluation-templates` |
| 14 | GET | `/api/analysis/evaluation-templates` |
| 15 | GET | `/api/analysis/evaluation-templates/{id}` |
| 16 | PUT | `/api/analysis/evaluation-templates/{id}` |
| 17 | DELETE | `/api/analysis/evaluation-templates/{id}` |
| 18 | PUT | `/api/analysis/evaluation-templates/{id}/default` |
| 19 | PUT | `/api/analysis/evaluation-templates/{id}/status` |
| 20 | PUT | `/api/analysis/evaluation-templates/override/{teachInfoId}` |
| 21 | GET | `/api/analysis/evaluation-templates/override/{teachInfoId}` |
| 22 | GET | `/api/analysis/evaluations/form` |
| 23 | POST | `/api/analysis/evaluations` |
| 24 | GET | `/api/analysis/evaluations/my` |
| 25 | POST | `/api/analysis/evaluations/period/open` |
| 26 | POST | `/api/analysis/evaluations/period/close` |
| 27 | GET | `/api/analysis/evaluations/period` |
| 28 | GET | `/api/analysis/teacher-quality/me` |
| 29 | GET | `/api/analysis/teacher-quality/{teacherId}` |
| 30 | GET | `/api/analysis/teacher-quality` |
| 31 | GET | `/api/analysis/progress/me` |
| 32 | GET | `/api/analysis/progress/{studentUserId}` |
| 33 | GET | `/api/analysis/profile/me` |
| 34 | GET | `/api/analysis/profile/{studentUserId}` |
| 35 | GET | `/api/analysis/class-analysis` |
| 36 | GET | `/api/analysis/class-analysis/trend` |

---

## 16. 通知模块

站内消息采用**单点 + 广播双轨**模型：

| 维度 | 单点消息 | 广播消息 |
|------|----------|----------|
| 存储表 | `notification`（每个接收者 1 行） | `notification_broadcast`（全局 1 行） |
| 接收范围 | 指定 `user_id` | 按 `target_type` 命中群体 |
| 已读状态 | 行内 `is_read` 置 1 | 单独存 `notification_read` 已读回执 |
| 未读数 | `is_read = 0` 的行数 | 可见广播数 − 已读回执数 |
| 列表标识 | `broadcast = false` | `broadcast = true`、`userId = null` |
| 删除 | `DELETE /api/notification/{id}`（逻辑删除） | 无删除端点 |

消息来源有两类：业务模块在关键节点通过 `@NotifyUser` / `@NotifyBroadcast` 注解场景发送（事务提交后生效）；管理侧也可调用本章的 `POST /send` 主动发单点消息。**没有对外发送广播消息的 REST 端点**，广播只由业务场景注解产生。

用户侧列表与未读数是「单点 + 广播」合并视图：合并后无法把分页下推到 SQL，服务端按创建时间倒序全局排序后在内存中切片。

**端点总览**

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/notification/unread-count` | 任意登录用户 | 未读消息数（单点 + 广播） |
| GET | `/api/notification/list` | 任意登录用户 | 消息列表（合并、可筛选、分页） |
| PUT | `/api/notification/{id}/read` | 任意登录用户 | 标记单点消息已读 |
| PUT | `/api/notification/broadcast/{id}/read` | 任意登录用户 | 标记广播消息已读 |
| PUT | `/api/notification/read-all` | 任意登录用户 | 全部已读（单点 + 广播） |
| DELETE | `/api/notification/{id}` | 任意登录用户 | 删除单点消息（逻辑删除） |
| POST | `/api/notification/send` | 教务管理员 / 院系管理员 | 向指定用户发送单点消息 |

> **错误口径**：鉴权拒绝为真实 HTTP 401/403；业务错误（404「消息不存在」、400「引用的广播消息不存在」等）为 HTTP 200 + 响应体 `code`。下文错误表中的 `code` 均指响应体字段。
>
> 所有查询/已读/删除操作都只作用于**当前登录用户自己的数据**；不存在按 userId 查他人消息的入参。

### 16.1 消息类型与模型

**NotificationTypeEnum**（`@EnumValue` 持久化 `code`，`@JsonValue` 输出 `description`）

| 持久化 code | JSON 输出 / 说明 |
|-------------|------------------|
| SYSTEM | 系统消息 |
| SELECTION | 选课消息 |
| SCHEDULE | 排课消息 |
| COURSE | 课程消息 |
| GRADE | 成绩消息 |
| WARNING | 学业预警 |
| TEACHING_EVAL | 评教消息 |
| PRACTICE | 实践创新 |

- **入参**（`/send` 的 `type`）：既可传 code（如 `SYSTEM`），也可传中文 description（如 `系统消息`），解析大小写敏感；未知值在 JSON 反序列化阶段即失败，最终返回 400「请求体格式错误」。
- **出参**：所有响应中的 `type` 一律输出中文 `description`。

**NotificationTargetEnum**（广播目标群体，持久化 code）

| code | 含义 | 可见用户 |
|------|------|----------|
| STUDENT | 全体学生 | `user.user_type = student` |
| ALL | 全体用户 | 任意登录用户 |

可见性判定：学生可见 `ALL + STUDENT`，其它用户类型（teacher / academic_admin / department）**只可见 `ALL`**。广播创建时 `target` 缺省为 `ALL`、`type` 缺省为 `SYSTEM`（仅注解场景可触发）。

**已读回执模型**：`notification_read(user_id, broadcast_id, read_time)` 仅在用户主动标记广播已读时写入；未读 = 可见广播数 − 回执行数，因此不为每个接收者落库。回执的唯一性目前**只在应用层预检**（先查后插，捕获 `DuplicateKeyException`），数据库侧仅普通索引；极端并发下仍可能出现同一 `(user_id, broadcast_id)` 的重复回执，而未读数按回执行数扣减，重复行会让计数偏小。

### 16.2 未读消息数

```
GET /api/notification/unread-count
Authorization: Bearer <accessToken>
```

无请求参数。未读数实时计算（无缓存）= 本人单点未读数 + 本人可见广播的未读数。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 3
  }
}
```

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| count | Integer | 未读总数（单点未读 + 广播未读）；无消息时为 0 |

**错误场景**

| code | message |
|------|---------|
| 401 | 请先登录 / token已过期，请刷新 / token无效（真实 HTTP 401） |

### 16.3 消息列表

```
GET /api/notification/list?status=unread&page=1&pageSize=20
Authorization: Bearer <accessToken>
```

**请求参数（Query，均可选）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | String | 否 | `read` / `unread`（不区分大小写）；不传或其它值 = 全部 |
| page | Integer | 否 | 页码，默认 1；小于 1 取 1 |
| pageSize | Integer | 否 | 每页条数，默认 20；小于 1 取 20，大于 100 截断为 100 |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 12,
        "userId": null,
        "type": "系统消息",
        "title": "选课系统维护通知",
        "content": "本周六 22:00-24:00 系统维护",
        "isRead": 0,
        "broadcast": true,
        "createTime": "2026-07-30T10:00:00"
      },
      {
        "id": 8,
        "userId": 6,
        "type": "成绩消息",
        "title": "成绩已发布",
        "content": "《数据结构》成绩已发布",
        "isRead": 1,
        "broadcast": false,
        "createTime": "2026-07-30T09:00:00"
      }
    ],
    "total": 2,
    "page": 1,
    "pageSize": 20,
    "pages": 1
  }
}
```

**PageResult 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| records | Array\<NotificationResponse\> | 当前页消息 |
| total | Long | 经 status 筛选后的总条数（单点 + 广播合并后） |
| page | Integer | 规范化后的页码 |
| pageSize | Integer | 规范化后的每页条数 |
| pages | Long | 总页数（total / pageSize 向上取整） |

**NotificationResponse 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 消息 ID（单点表与广播表各自自增，两个 ID 空间相互独立） |
| userId | Long | 单点消息的接收者 `user.id`；广播消息为 `null` |
| type | String | 消息类型，输出中文 description（见 16.1） |
| title | String | 标题 |
| content | String | 内容，可为 `null` |
| isRead | Integer | `0` 未读 / `1` 已读；广播按当前用户的已读回执计算 |
| broadcast | Boolean | `true` 广播消息；`false` 单点消息 |
| createTime | LocalDateTime | 创建时间 |

**排序与筛选规则**

- 单点 + 广播合并后统一按 `createTime` 倒序（为 `null` 的排最后）。
- `status=unread` 保留 `isRead` 为 0（或单点行中为 null）的消息；`status=read` 保留 `isRead=1` 的消息。
- 单点消息受逻辑删除过滤（`deleted=1` 不再出现）。

**错误场景**

| code | message |
|------|---------|
| 400 | 参数类型错误: page / 参数类型错误: pageSize（传了非数字） |
| 401 | 请先登录 / token已过期，请刷新 / token无效（真实 HTTP 401） |

### 16.4 标记单点消息已读

```
PUT /api/notification/{id}/read
Authorization: Bearer <accessToken>
```

无请求体。仅处理**单点消息**；已读时幂等直接返回，不做重复更新。

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 单点消息 ID（`notification.id`） |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

仅当消息由未读变为已读时，服务端才向该用户的在线 WebSocket 连接推送最新未读数（已读幂等返回不推送）。

**错误场景**

| code | message |
|------|---------|
| 404 | 消息不存在（id 不存在，或传的是广播 id） |
| 403 | 权限不足（消息属于其它用户；HTTP 200 + body code 403） |
| 401 | 请先登录 / token已过期，请刷新 / token无效（真实 HTTP 401） |

> 广播消息请走 16.5；用本端点传广播 id 时，服务端只在 `notification` 表查找，通常得到 404「消息不存在」。

### 16.5 标记广播消息已读

```
PUT /api/notification/broadcast/{id}/read
Authorization: Bearer <accessToken>
```

无请求体。写入一条 `notification_read` 已读回执；已读过则幂等跳过。操作后推送最新未读数。

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 广播消息 ID（`notification_broadcast.id`） |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**错误场景**

| code | message |
|------|---------|
| 400 | 引用的广播消息不存在(id=x) |
| 401 | 请先登录 / token已过期，请刷新 / token无效（真实 HTTP 401） |

> 本端点只校验广播 id 是否存在，**不校验该广播是否对当前用户可见**（例如教师可以对 `target_type=STUDENT` 的广播写已读回执）。不可见广播的回执不会影响该用户的未读数，但会留下一条孤立记录。

### 16.6 全部已读

```
PUT /api/notification/read-all
Authorization: Bearer <accessToken>
```

无请求体、无参数。双轨执行：

1. 本人全部未读**单点**消息置为已读；
2. 本人可见的**全部广播**批量写入已读回执（学生 = `ALL + STUDENT`，其它类型 = `ALL`）。

幂等；完成后推送最新未读数。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**错误场景**

| code | message |
|------|---------|
| 401 | 请先登录 / token已过期，请刷新 / token无效（真实 HTTP 401） |

> 「全部已读」会把**所有可见广播**（包括从未打开过的）都记为已读，而不是只标记列表中已加载的部分。

### 16.7 删除消息

```
DELETE /api/notification/{id}
Authorization: Bearer <accessToken>
```

无请求体、无参数。**逻辑删除**（`Notification.deleted` 置 1，`@TableLogic`），删除后该消息不再出现在列表与未读数中。

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 单点消息 ID |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**错误场景**

| code | message |
|------|---------|
| 404 | 消息不存在 |
| 403 | 权限不足（消息属于其它用户；HTTP 200 + body code 403） |
| 401 | 请先登录 / token已过期，请刷新 / token无效（真实 HTTP 401） |

> 本端点**只能删除单点消息**，广播消息没有删除接口。广播 id 与单点 id 是两套独立自增序列：传广播 id 通常得到 404「消息不存在」；若恰巧存在同 id 且属于本人的单点消息，则会删除那条单点消息（前端务必用 `broadcast` 字段区分后再调删除）。

### 16.8 发送消息

管理侧主动向指定用户发送**单点消息**（写库 + 在线实时推送）。

```
POST /api/notification/send
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**权限**：`@RequireManagement`，仅教务管理员 / 院系管理员；其它用户类型返回真实 HTTP 403「权限不足」。

**请求体（Body）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 接收者 `user.id` |
| type | NotificationTypeEnum | 是 | 消息类型，可传 code 或中文 description；不能为 `null` |
| title | String | 是 | 标题，不能为空白；数据库列上限 128 字符 |
| content | String | 否 | 内容；数据库列上限 512 字符 |

**请求示例**

```json
{
  "userId": 6,
  "type": "SYSTEM",
  "title": "选课系统维护通知",
  "content": "本周六 22:00-24:00 系统维护，请提前完成选课"
}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 15,
    "userId": 6,
    "type": "系统消息",
    "title": "选课系统维护通知",
    "content": "本周六 22:00-24:00 系统维护，请提前完成选课",
    "isRead": 0,
    "broadcast": false,
    "createTime": "2026-11-24T10:30:00"
  }
}
```

**错误场景**

| code | message |
|------|---------|
| 400 | 接收用户ID不能为空 |
| 400 | 消息类型不能为空 |
| 400 | 标题不能为空 |
| 400 | 引用的用户不存在(id=x)（userId 不存在或已逻辑删除） |
| 400 | 请求体格式错误（JSON 非法，或 `type` 传了未知的字符串值） |
| 403 | 权限不足（非教务/院系管理员，真实 HTTP 403） |
| 500 | 服务器内部错误，请联系管理员（title/content 超出数据库列宽） |

> 本端点只发单点消息，**不支持广播**；也不校验接收者与发送者的院系范围，任何教务/院系管理员可向任意 `userId` 发送。
>
> 未做标题/内容长度前置校验，超出数据库列宽（128 / 512）会由数据库报错并落到 500。

### 16.9 WebSocket 实时推送

实时消息走独立 WebSocket 通道（不走 `/api/**`，因此不使用 Bearer 头）：

```
ws://<host>/ws/notification?token=<accessToken>
```

**握手鉴权**：`token` 为 query 参数（浏览器原生 WebSocket 不支持自定义请求头）；缺失、JWT 无效/过期、缺少 `tokenId` 均在握手阶段拒绝（HTTP 401）。Redis 会话缺失时与 REST 拦截器一致，会基于 token claims 重建最小会话后放行。

**下行消息（服务端推送）**

| 场景 | 报文 |
|------|------|
| 连接建立 | `{"type":"unread_count","count":3}` |
| 收到新单点消息 | `{"type":"notification","data":{...NotificationResponse...}}`，随后再推一条 `{"type":"unread_count","count":N}` |
| 广播消息 | 向目标群体的在线连接推送 `{"type":"notification","data":{...NotificationResponse...}}`（广播无单一接收者，不逐个重算未读数） |
| REST 已读操作后 | `{"type":"unread_count","count":N}` |

**客户端上行**

- 发送文本 `ping`（去空白后忽略大小写匹配）→ 服务端重推当前未读数。

**其它约定**

- 同一用户多端在线：所有连接都会收到推送。
- 在线用户的推送失败只记日志，不影响业务返回；离线用户不丢消息，上线后通过未读接口/连接建立推送补齐。
- 广播消息的目标群体按 `user.user_type` 匹配（`STUDENT` 只推学生、`ALL` 推所有在线用户）。

---

## 17. 实践与创新管理模块

实践与创新管理模块（`module/practice`）按业务子域拆分为四块：毕业设计与论文（`graduation`）、实习与培训（`internship`）、竞赛管理（`competition`）、社会实践（`socialpractice`）。本节只描述**毕业设计与论文**子域，其余子域见后续章节。

毕业设计以「**毕设活动**」为顶层归属单位，把一名学生从选题到成绩发布的完整链路串起来：

```
教务创建活动（DRAFT）──开放──> OPEN
  │
  ├─ 阶段一：学生自拟选题 ──> 院系初审 ──> 教务终审（APPROVED）
  │            └─ 师生匹配：教师自选（选题截止前）/ 院系指定分配（选题截止后）
  │
  ├─ 阶段二：开题报告（指导教师审核）──> 中期检查（指导教师给结论）──> 过程指导记录
  │
  ├─ 阶段三：论文提交（多版本，保留最近 3 版）──> 指导教师形式审查 ──> 教务登记查重
  │
  └─ 阶段四：院系安排答辩（查重通过门禁）──> 指导/评阅/答辩三项评分
                  ──> 自动按活动权重合成总评 ──> 院系确认并发布（学生可见）
```

所有学生、教师相关字段（`studentId` / `teacherId` / `prevTeacherId` / `reviewerId` / `defenseTeacherIds` / `advisorBy` / `reassignBy` / `confirmBy` 等）存的都是 **`user.id`**（不是 student/teacher 子表主键），与 selection / practice 其余子域一致。

### 17.1 通用约定

#### 17.1.1 HTTP 状态码与 body.code

本模块与全站一致，错误响应有两种形态，**前端必须同时判断 HTTP 状态与 body 的 `code`**：

| 场景 | HTTP 状态 | 响应体 | 处理方 |
|------|-----------|--------|--------|
| 未携带 token / token 过期 / token 无效 | 真实 **401** | `{"code":401,"message":"请先登录"}` 等 | `AuthInterceptor` 直接写出 |
| 已登录但用户类型不满足接口要求（含接口漏标注解的默认拒绝） | 真实 **403** | `{"code":403,"message":"权限不足"}` | `AuthInterceptor` 直接写出 |
| 业务异常（参数、状态、门禁、数据不存在、冲突等） | **HTTP 200** | `{"code":400/403/404/409/500,"message":"..."}` | `GlobalExceptionHandler` 转 `Result.fail` |
| 成功 | HTTP 200 | `{"code":200,"message":"success","data":...}` | Controller |

业务错误一律是 **HTTP 200 + body.code**；只读 body.code 会把 401/403 之外的错误漏掉，只读 HTTP 状态会把业务错误当成功。本节的「错误场景」表列的都是 **body.code**。

> ⚠️ `403 权限不足` 有两个来源：**角色不符**由拦截器返回真实 HTTP 403；**业务归属不符**（非本人学生、非本院系、非指导教师等）由 Service 抛 `BusinessException(403)`，是 HTTP 200 + body.code 403。前端两种都要按「无权限」处理。

请求绑定类错误同样走 HTTP 200 + body 400：

| message | 触发 |
|---------|------|
| `缺少必填参数: campaignId` | 漏传必填 query 参数 |
| `参数类型错误: status` | query 参数类型/枚举名不匹配 |
| `缺少必填请求部分: data` | multipart 请求漏传 `data` 部分 |
| `请求体格式错误` | JSON 无法解析 / 枚举值非法（如 `result` 传了未知值） |
| `上传文件过大，请压缩后重试` | multipart 超过 Spring 硬上限 25MB |

#### 17.1.2 角色与鉴权注解

| 文档用词 | `user_type` | 鉴权注解 |
|----------|-------------|----------|
| 教务 | `academic_admin` | `@RequireAcademicAdmin` |
| 院系 | `department` | `@RequireDepartment` |
| 教务/院系（管理侧） | `academic_admin` + `department` | `@RequireManagement` |
| 教师 | `teacher` | `@RequireTeacher` |
| 学生 | `student` | `@RequireStudent` |
| 四角色 | 全部四种 | `@RequireAuth({ACADEMIC_ADMIN, DEPARTMENT, TEACHER, STUDENT})` |
| 学生/教师 | `student` + `teacher` | `@RequireAuth({STUDENT, TEACHER})` |

> ⚠️ **院系数据范围是「本院系」而非注解负责的**：注解只做角色门禁，院系只看到本院学生由 Service 层 `StudentScopeResolver` 按 `college_id` 裁剪（学生 → 班级 → 专业 → 院系两跳推导）。院系归属任一跳缺失（无班级 / 班级无专业 / 专业无院系）按 **fail-closed 不可见**处理。

#### 17.1.3 时间与枚举序列化

- 所有时间字段为 `LocalDateTime`，JSON 为 ISO-8601 字符串，如 `"2026-12-01T00:00:00"`。
- 业务枚举均带 `@EnumValue`（持久化英文 code）+ `@JsonValue`（**响应输出中文描述**）。

| 枚举 | 持久化 code | 响应 JSON 取值（中文） |
|------|-------------|------------------------|
| `CampaignStatusEnum` | `DRAFT` / `OPEN` / `CLOSED` | 草稿 / 进行中 / 已结束 |
| `ProposalStatusEnum` | `PENDING_DEPT` / `DEPT_APPROVED` / `APPROVED` / `REJECTED` | 待院系初审 / 待教务终审 / 审批完毕 / 已驳回 |
| `ProposalReviewStageEnum` | `DEPT` / `ACADEMIC` | 院系初审 / 教务终审 |
| `AssignmentSourceEnum` | `TEACHER_PICK` / `DEPT_ALLOCATE` | 教师自选 / 院系指定 |
| `OpeningReportStatusEnum` | `SUBMITTED` / `APPROVED` / `REVISION` | 已提交 / 已通过 / 需修改 |
| `MidtermConclusionEnum` | `NORMAL` / `WARNING` / `SEVERE_LAGGING` | 正常 / 警告 / 严重滞后 |
| `ThesisStatusEnum` | `SUBMITTED` / `APPROVED` / `REVISION` / `DUPLICATE_PASSED` / `DUPLICATE_FAILED` | 待形式审查 / 形式审查通过 / 形式审查退回 / 查重通过 / 查重不通过 |
| `DuplicateResultEnum` | `PASS` / `FAIL` | 通过 / 不通过 |
| `GuidanceFormEnum` | `ONLINE` / `OFFLINE` / `PHONE` | 线上 / 线下 / 电话 |
| `GraduationScoreStatusEnum` | `INCOMPLETE` / `COMPLETE` / `PUBLISHED` | 分项未齐备 / 已合成总评 / 已发布 |

> ⚠️ **请求方向存在不对称**：JSON 请求体中的枚举由 `@JsonCreator` 解析，**code 与中文描述都接受**（如 `"result":"PASS"` 或 `"result":"通过"`）；而 **query 参数中的枚举走 Spring 默认转换，只接受枚举名（英文 code）**。例如 `?status=OPEN` 合法，`?status=进行中` 会返回 `400 参数类型错误: status`。看板 `status` 是普通 String，取值规则见 17.2.11。

例外：中期检查记录的 `status` 在实体中是普通 String，**响应为英文** `SUBMITTED` / `REVIEWED`，不走枚举中文转换。

#### 17.1.4 分页参数

所有标注 `PageQuery` 的端点支持：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码，从 1 开始；缺省/小于 1 取 1 |
| pageSize | Integer | 否 | 每页条数；缺省/小于 1 取 20，**大于 100 截断为 100** |

分页响应 `PageResult<T>`：

| 字段 | 类型 | 说明 |
|------|------|------|
| records | Array | 当前页数据 |
| total | Long | 总条数 |
| page | Integer | 当前页码 |
| pageSize | Integer | 每页条数 |
| pages | Long | 总页数 |

#### 17.1.5 附件传输（与第 11 节衔接）

毕业设计共 6 个文件落点，全部复用第 11 节的文件模块能力：

| 业务 | `biz` 目录 | 扩展名白名单 | 单文件上限 |
|------|-----------|--------------|-----------|
| 论文 | `graduation-thesis` | `.doc` `.docx` `.pdf` `.zip` `.rar` | 20MB |
| 开题报告 | `graduation-opening-report` | 同上 | 20MB |
| 中期检查 | `graduation-midterm` | 同上 | 20MB |
| 查重报告 | `graduation-duplicate-report` | 同上 | 20MB |
| 活动资料 | `graduation-campaign-material` | 同上 | 20MB |
| 答辩材料 | `graduation-defense-material` | 同上 | 20MB |

提交端点统一支持**二选一**：

- 小文件：multipart 的 `file` 部分整传（≤20MB）
- 大文件：先走第 11 节分片上传，再提交 `filePath`（形如 `objects/{biz}/{sha256}.pdf`）+ `fileOriginal`

两者同时给 → `400 file 与 filePath 只能二选一`；必填端点两者都缺 → `400 文件不能为空`；`filePath` 不属于该端点对应的业务目录 → `403 文件不属于该业务目录: {path}`。走 `filePath` 时 `fileOriginal` 留空会回退为产物文件名（`{sha256}{ext}`）。

> ⚠️ **`filePath` 的承载位置因端点而异**（前端最易踩）：开题 / 中期 / 论文 / 查重登记把 `filePath` + `fileOriginal` 放在 multipart 的 **`data` JSON 内**（该部分需以 `Content-Type: application/json` 提交）；活动资料 / 答辩材料上传则把它们作为 **`@RequestParam` 表单字段**。两处不能混用。

Spring multipart 硬上限为 **25MB**（`application.yaml`），超限返回 `400 上传文件过大，请压缩后重试`；业务层每业务 20MB 校验返回 `400 文件过大，最大允许 20MB`；扩展名不符返回 `400 不支持的文件格式: .xxx`。

各下载端点均为 GET，响应头为 RFC5987 文件名 + `Accept-Ranges: bytes` + 内容 SHA-256 强 ETag，支持 `Range` → **206 断点续传**；错误响应仍是 JSON（HTTP 200 + body.code）。

#### 17.1.6 通知与操作日志

- **通知**：选题审批、教师自选、院系分配、改派、开题审核、中期结论、论文形式审查、查重结果、成绩发布共 **9 个场景**通过 `PracticeNoticeScenes`（`@NotifyUser`，类型 `PRACTICE`）发送站内消息，均由 `NotifyAspect` 在**事务提交后**发送（回滚不发；无事务立即发）。通知失败只记 warn，不影响业务返回。
- **操作日志**：关键管理动作以 JSONL 追加写入 `practice.operation-log-path`（默认 `uploads/practice/graduation-operation-logs`）下按活动分文件的 `campaign-{campaignId}.jsonl`，**不落数据库**，文件追加不参与业务事务（业务回滚时已写入的日志行不会撤销）。记录的动作见 17.2.11。

---

### 17.2 毕业设计与论文

#### 17.2.1 端点总览

毕业设计共 **64 个端点**，其中 8 个附件端点（查重报告 2 个、活动资料 4 个、答辩材料 2 个）的传输层细节已在第 11.5 节详述，本节仍列出并补充业务语义。

| 方法 | 路径 | 权限 | 说明 | 小节 |
|------|------|------|------|------|
| POST | `/api/practice/graduation/campaigns` | 教务 | 创建毕设活动 | 17.2.2 |
| PUT | `/api/practice/graduation/campaigns/{id}` | 教务 | 更新活动（选题开始后受限） | 17.2.2 |
| PUT | `/api/practice/graduation/campaigns/{id}/status?status=` | 教务 | 变更活动状态 | 17.2.2 |
| GET | `/api/practice/graduation/campaigns?status=&page=&pageSize=` | 教务 | 活动分页列表 | 17.2.2 |
| GET | `/api/practice/graduation/campaigns/{id}` | 四角色 | 活动详情 | 17.2.2 |
| GET | `/api/practice/graduation/campaigns/available` | 学生 | 我可参与的进行中活动 | 17.2.2 |
| GET | `/api/practice/graduation/campaigns/selector` | 教师/院系 | 活动下拉选择器（非草稿） | 17.2.2 |
| GET | `/api/practice/graduation/campaigns/{id}/detail` | 教务 | 教务详情入口（同详情） | 17.2.2 |
| POST | `/api/practice/graduation/campaigns/{id}/materials` | 教务 | 上传活动资料（见 11.5.2） | 17.2.3 |
| GET | `/api/practice/graduation/campaigns/{id}/materials` | 四角色 | 活动资料列表 | 17.2.3 |
| GET | `/api/practice/graduation/campaigns/{id}/materials/{mid}/download` | 四角色 | 活动资料下载 | 17.2.3 |
| DELETE | `/api/practice/graduation/campaigns/{id}/materials/{mid}` | 教务 | 删除活动资料 | 17.2.3 |
| POST | `/api/practice/graduation/proposals` | 学生 | 提交/重提选题申请 | 17.2.4 |
| PUT | `/api/practice/graduation/proposals/{id}/review/dept` | 院系 | 院系初审 | 17.2.4 |
| PUT | `/api/practice/graduation/proposals/{id}/review/academic` | 教务 | 教务终审 | 17.2.4 |
| GET | `/api/practice/graduation/proposals/my` | 学生 | 我的选题申请 | 17.2.4 |
| GET | `/api/practice/graduation/proposals/pending/dept?campaignId=` | 院系 | 院系待初审队列 | 17.2.4 |
| GET | `/api/practice/graduation/proposals/pending/academic?campaignId=` | 教务 | 教务待终审队列 | 17.2.4 |
| POST | `/api/practice/graduation/assignments/picks` | 教师 | 教师自由选择学生 | 17.2.5 |
| DELETE | `/api/practice/graduation/assignments/picks/{id}` | 教师 | 教师放弃自选 | 17.2.5 |
| POST | `/api/practice/graduation/assignments/allocations` | 院系 | 院系指定分配 | 17.2.5 |
| POST | `/api/practice/graduation/assignments/reassigns` | 院系 | 院系改派 | 17.2.5 |
| GET | `/api/practice/graduation/assignments/teacher/pool?campaignId=` | 教师 | 教师自选池 | 17.2.5 |
| GET | `/api/practice/graduation/assignments/my?campaignId=` | 学生/教师 | 我的匹配 | 17.2.5 |
| GET | `/api/practice/graduation/assignments/overview?campaignId=` | 教务 | 分配总览 | 17.2.5 |
| GET | `/api/practice/graduation/assignments/unassigned?campaignId=&collegeId=` | 教务/院系 | 未分配学生 ID 清单 | 17.2.5 |
| POST | `/api/practice/graduation/process/opening-reports` | 学生 | 提交/重提开题报告（multipart） | 17.2.6 |
| PUT | `/api/practice/graduation/process/opening-reports/{id}/review` | 教师 | 审核开题报告 | 17.2.6 |
| GET | `/api/practice/graduation/process/opening-reports/my?campaignId=` | 学生 | 我的开题报告 | 17.2.6 |
| GET | `/api/practice/graduation/process/opening-reports/teacher?campaignId=` | 教师 | 名下学生开题报告 | 17.2.6 |
| GET | `/api/practice/graduation/process/opening-reports/{id}/download` | 四角色 | 开题报告附件下载 | 17.2.6 |
| POST | `/api/practice/graduation/process/midterms` | 学生 | 提交/重提中期检查（multipart） | 17.2.6 |
| PUT | `/api/practice/graduation/process/midterms/{id}/review` | 教师 | 评审中期检查 | 17.2.6 |
| GET | `/api/practice/graduation/process/midterms/my?campaignId=` | 学生 | 我的中期检查 | 17.2.6 |
| GET | `/api/practice/graduation/process/midterms/teacher?campaignId=` | 教师 | 名下学生中期检查 | 17.2.6 |
| GET | `/api/practice/graduation/process/midterms/{id}/download` | 四角色 | 中期检查附件下载 | 17.2.6 |
| POST | `/api/practice/graduation/process/guidance-logs` | 教师 | 记录过程指导日志 | 17.2.6 |
| GET | `/api/practice/graduation/process/guidance-logs?campaignId=&studentId=` | 教师/院系/教务 | 指导记录查询 | 17.2.6 |
| POST | `/api/practice/graduation/theses` | 学生 | 提交/重提论文（multipart） | 17.2.7 |
| PUT | `/api/practice/graduation/theses/{id}/review` | 教师 | 论文形式审查 | 17.2.7 |
| GET | `/api/practice/graduation/theses/my?campaignId=` | 学生 | 我的论文（含全部版本） | 17.2.7 |
| GET | `/api/practice/graduation/theses/teacher?campaignId=` | 教师 | 名下学生论文 | 17.2.7 |
| GET | `/api/practice/graduation/theses/campaign?campaignId=&status=` | 教务 | 活动内论文（按状态） | 17.2.7 |
| GET | `/api/practice/graduation/theses/export-package?campaignId=&status=` | 教务 | 导出查重数据包 zip | 17.2.7 |
| GET | `/api/practice/graduation/theses/{id}/download` | 四角色 | 论文文件下载 | 17.2.7 |
| POST | `/api/practice/graduation/theses/duplicate-checks` | 教务 | 登记查重结果（multipart，见 11.5.1） | 17.2.8 |
| GET | `/api/practice/graduation/theses/{id}/duplicate-checks` | 四角色 | 论文查重记录 | 17.2.8 |
| GET | `/api/practice/graduation/theses/duplicate-checks/{checkId}/download` | 四角色 | 查重报告下载（见 11.5.1） | 17.2.8 |
| POST | `/api/practice/graduation/defense/arrange` | 院系 | 安排/更新答辩 | 17.2.9 |
| GET | `/api/practice/graduation/defense/list?campaignId=` | 教务/院系/学生 | 答辩安排列表 | 17.2.9 |
| POST | `/api/practice/graduation/defense/{id}/material` | 教务/院系 | 上传/替换答辩材料（见 11.5.3） | 17.2.9 |
| GET | `/api/practice/graduation/defense/{id}/material/download` | 学生/院系/教务 | 答辩材料下载（见 11.5.3） | 17.2.9 |
| POST | `/api/practice/graduation/defense/scores/advisor` | 教师 | 录入指导分 | 17.2.10 |
| GET | `/api/practice/graduation/defense/scores/advisor?campaignId=` | 教师 | 指导分录入列表 | 17.2.10 |
| POST | `/api/practice/graduation/defense/scores/reviewer` | 教师 | 录入评阅分 | 17.2.10 |
| GET | `/api/practice/graduation/defense/scores/reviewer?campaignId=` | 教师 | 评阅分录入列表 | 17.2.10 |
| POST | `/api/practice/graduation/defense/scores/defense` | 教务/院系 | 录入答辩分 | 17.2.10 |
| POST | `/api/practice/graduation/defense/scores/confirm` | 院系 | 确认并发布总评 | 17.2.10 |
| GET | `/api/practice/graduation/defense/scores?campaignId=` | 四角色 | 成绩列表 | 17.2.10 |
| GET | `/api/practice/graduation/defense/scores/my?campaignId=` | 学生 | 我的成绩 | 17.2.10 |
| GET | `/api/practice/graduation/defense/scores/export?campaignId=` | 教务 | 导出成绩总表 xlsx | 17.2.10 |
| GET | `/api/practice/graduation/dashboard/{campaignId}?status=&keyword=&collegeId=&page=&pageSize=` | 教务/院系 | 看板分页 | 17.2.11 |
| GET | `/api/practice/graduation/dashboard/{campaignId}/export?format=&status=&keyword=&collegeId=` | 教务/院系 | 看板导出 xlsx/csv | 17.2.11 |
| GET | `/api/practice/graduation/dashboard/{campaignId}/logs?page=&pageSize=` | 教务 | 操作日志分页 | 17.2.11 |

---

#### 17.2.2 毕设活动（Campaign）

**端点**

| 方法 | 路径 | 鉴权注解 | 说明 |
|------|------|----------|------|
| POST | `/api/practice/graduation/campaigns` | `@RequireAcademicAdmin` | 创建活动，落 `DRAFT` |
| PUT | `/api/practice/graduation/campaigns/{id}` | `@RequireAcademicAdmin` | 全字段可选更新 |
| PUT | `/api/practice/graduation/campaigns/{id}/status?status=` | `@RequireAcademicAdmin` | 变更状态 |
| GET | `/api/practice/graduation/campaigns?status=&page=&pageSize=` | `@RequireAcademicAdmin` | 按状态分页，`id` 倒序 |
| GET | `/api/practice/graduation/campaigns/{id}` | `@RequireAuth`（四角色） | 详情（DRAFT 也可见） |
| GET | `/api/practice/graduation/campaigns/available` | `@RequireStudent` | 进行中且年级匹配的活动 |
| GET | `/api/practice/graduation/campaigns/selector` | `@RequireAuth({TEACHER, DEPARTMENT})` | 所有非 DRAFT 活动 |
| GET | `/api/practice/graduation/campaigns/{id}/detail` | `@RequireAcademicAdmin` | 与 `GET /{id}` 返回一致 |

**创建请求体（CampaignCreateRequest）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 活动名称（**全局唯一**，非按年度分组） |
| allowedGradeIds | List\<Long\> | 是 | 参与年级 ID 列表，不能为空 |
| topicStartTime | LocalDateTime | 是 | 选题开始时间 |
| topicEndTime | LocalDateTime | 是 | 选题截止时间，必须晚于开始时间 |
| supervisorCapacity | Integer | 是 | 每教师可分配学生数上限（>0） |
| freeSelectCapacity | Integer | 是 | 每教师自由选择学生数上限（>0，且 ≤ supervisorCapacity） |
| openingStartTime / openingEndTime | LocalDateTime | 否 | 开题报告提交窗口；成对出现时截止须晚于开始 |
| midtermStartTime / midtermEndTime | LocalDateTime | 否 | 中期检查提交窗口；同上 |
| thesisStartTime / thesisEndTime | LocalDateTime | 否 | 论文提交窗口；同上 |
| advisorWeight | Integer | 否 | 指导教师评分权重，默认 **30** |
| reviewerWeight | Integer | 否 | 评阅教师评分权重，默认 **20** |
| defenseWeight | Integer | 否 | 答辩评分权重，默认 **50** |

三个权重之和必须为 **100**（含默认值组合）。

**请求示例**

```json
{
  "name": "2027届毕业设计",
  "allowedGradeIds": [4],
  "topicStartTime": "2026-12-01T00:00:00",
  "topicEndTime": "2026-12-31T23:59:59",
  "supervisorCapacity": 8,
  "freeSelectCapacity": 5,
  "openingStartTime": "2027-01-01T00:00:00",
  "openingEndTime": "2027-02-28T23:59:59",
  "midtermStartTime": "2027-03-01T00:00:00",
  "midtermEndTime": "2027-04-15T23:59:59",
  "thesisStartTime": "2027-04-16T00:00:00",
  "thesisEndTime": "2027-05-15T23:59:59",
  "advisorWeight": 30,
  "reviewerWeight": 20,
  "defenseWeight": 50
}
```

**响应（CampaignResponse，创建/更新/详情/列表项共用）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 活动 ID |
| name | String | 活动名称 |
| allowedGradeIds | List\<Long\> | 参与年级 ID（响应按升序去重） |
| topicStartTime / topicEndTime | LocalDateTime | 选题窗口 |
| supervisorCapacity | Integer | 可分配上限 |
| freeSelectCapacity | Integer | 自由选择上限 |
| openingStartTime / openingEndTime | LocalDateTime | 开题窗口（可空） |
| midtermStartTime / midtermEndTime | LocalDateTime | 中期窗口（可空） |
| thesisStartTime / thesisEndTime | LocalDateTime | 论文窗口（可空） |
| advisorWeight / reviewerWeight / defenseWeight | Integer | 成绩权重 |
| status | String | 活动状态，**中文**（草稿/进行中/已结束） |
| createTime | LocalDateTime | 创建时间 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 7,
    "name": "2027届毕业设计",
    "allowedGradeIds": [4],
    "topicStartTime": "2026-12-01T00:00:00",
    "topicEndTime": "2026-12-31T23:59:59",
    "supervisorCapacity": 8,
    "freeSelectCapacity": 5,
    "openingStartTime": "2027-01-01T00:00:00",
    "openingEndTime": "2027-02-28T23:59:59",
    "midtermStartTime": "2027-03-01T00:00:00",
    "midtermEndTime": "2027-04-15T23:59:59",
    "thesisStartTime": "2027-04-16T00:00:00",
    "thesisEndTime": "2027-05-15T23:59:59",
    "advisorWeight": 30,
    "reviewerWeight": 20,
    "defenseWeight": 50,
    "status": "草稿",
    "createTime": "2026-11-20T09:00:00"
  }
}
```

**更新（CampaignUpdateRequest）**：字段与创建完全相同，但**全部可选**，只更新非空字段。`allowedGradeIds` 传 `null` 表示不更新；传空数组时，未开始阶段视为不更新，选题已开始阶段会因「与现有年级不一致」落入 `409 选题开始后不可修改参与年级`。

**业务规则**

1. **名称唯一**：创建/改名时查全表同名（逻辑删除行不计），重复返回 `409 同年度已存在同名活动`。虽然文案写「同年度」，代码实际为**全局唯一**。
2. **名额**：`freeSelectCapacity ≤ supervisorCapacity`，否则 `400 自由选择上限不能超过可分配上限`。
3. **R-4.1 选题开始后仅允许延长/上调**：当 `now` 已晚于 `topicStartTime` 时，更新接口逐项拒绝：
   - 改名 → `409 选题开始后不可修改活动名称`
   - 改参与年级 → `409 选题开始后不可修改参与年级`
   - 改选题开始时间 → `409 选题开始后不可修改选题开始时间`
   - 缩短选题截止 → `409 选题开始后仅允许延长截止时间`
   - 下调可分配名额 → `409 选题开始后仅允许上调可分配名额`
   - 下调自由选择名额 → `409 选题开始后仅允许上调自由选择名额`
   > ⚠️ 代码**只拦上述 6 项**；开题/中期/论文三个窗口与三项成绩权重在选题开始后**仍可任意修改**，不受 R-4.1 限制。
4. **R-4.2 同年级唯一进行中**：创建/更新参与年级时，若任一目标年级已被某个 `OPEN` 活动占用 → `409 同年级已存在进行中的活动，不可重复开展`。仅与 `OPEN` 活动比较，`DRAFT` 不算冲突。
5. **状态变更**：`PUT /{id}/status?status=OPEN|CLOSED|DRAFT`。**没有状态机校验**——任意状态可切到任意状态（例如 CLOSED 可重新开放、OPEN 可退回 DRAFT）。切到 `OPEN` 前会兜底校验主题时间窗与两个名额非空，缺失 → `400 活动配置不完整，无法开放`。`status` 参数缺失 → `400 缺少必填参数: status`。
6. **学生可用活动**：`GET /available` 只返回 `OPEN` 且 `allowedGradeIds` 含该生 `gradeId` 的活动；学生档案不存在或 `gradeId` 为空 → `data: []`。
7. **选择器**：`GET /selector` 返回全部状态 ≠ DRAFT 的活动（OPEN + CLOSED），`id` 倒序，不分页。
8. **详情可见性**：`GET /{id}` 对四角色均放行，**不判断活动状态**（DRAFT 也能被学生/教师/院系读取）。

**错误场景（创建/更新/状态）**

| code | message |
|------|---------|
| 400 | 活动名称不能为空 |
| 400 | 参与年级不能为空 |
| 400 | 选题开始时间不能为空 / 选题截止时间不能为空 |
| 400 | 选题截止时间必须晚于开始时间 |
| 400 | 教师可分配学生数上限不能为空 / 教师可分配学生数上限必须大于0 |
| 400 | 教师自由选择学生数上限不能为空 / 教师自由选择学生数上限必须大于0 |
| 400 | 自由选择上限不能超过可分配上限 |
| 400 | 开题报告截止时间必须晚于开始时间 / 中期检查截止时间必须晚于开始时间 / 论文提交截止时间必须晚于开始时间 |
| 400 | 成绩权重之和必须为100 |
| 400 | 状态不能为空（Service 兜底；query 缺失时为「缺少必填参数: status」） |
| 400 | 活动配置不完整，无法开放 |
| 404 | 活动不存在 |
| 409 | 同年度已存在同名活动 |
| 409 | 同年级已存在进行中的活动，不可重复开展 |
| 409 | 选题开始后不可修改活动名称 / 参与年级 / 选题开始时间 |
| 409 | 选题开始后仅允许延长截止时间 / 上调可分配名额 / 上调自由选择名额 |

> 操作日志：创建、更新、变更状态各记一条（动作 `创建毕设活动` / `更新毕设活动` / `变更活动状态`）。

---

#### 17.2.3 活动资料（Campaign Material）

活动资料由教务下发模板/规范，一个活动可挂多份。**传输层细节（`file` / `filePath` 二选一、断点续传下载、删除由回收任务对账）见 [11.5.2](#1152-毕设活动资料一个活动多份)**，本节只补充业务语义。

| 方法 | 路径 | 鉴权注解 | 说明 |
|------|------|----------|------|
| POST | `/api/practice/graduation/campaigns/{id}/materials` | `@RequireAcademicAdmin` | 上传（multipart：`filePath`/`fileOriginal` 表单字段 + 可选 `file`，必填其一） |
| GET | `/api/practice/graduation/campaigns/{id}/materials` | `@RequireAuth`（四角色） | 列表，`id` 倒序 |
| GET | `/api/practice/graduation/campaigns/{id}/materials/{mid}/download` | `@RequireAuth`（四角色） | 下载 |
| DELETE | `/api/practice/graduation/campaigns/{id}/materials/{mid}` | `@RequireAcademicAdmin` | 逻辑删 + 释放引用 + 记日志 |

**响应项（CampaignMaterialResponse）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 资料 ID（下载/删除用） |
| campaignId | Long | 活动 ID |
| fileOriginal | String | 展示文件名 |
| uploaderId | Long | 上传人 `user.id` |
| uploaderName | String | 上传人姓名 |
| createTime | LocalDateTime | 上传时间 |

**业务规则与错误**

- 活动不存在（任意端点）→ `404 活动不存在`。
- `mid` 不存在或不属于该活动 → `404 资料不存在`。
- 扩展名白名单 `.doc/.docx/.pdf/.zip/.rar`，整传 ≤20MB；`filePath` 必须属于 `graduation-campaign-material`。
- 可见性与活动详情一致：四角色均可列表/下载，**不判断活动是否 DRAFT**。
- 上传/删除均写活动操作日志（`上传活动资料` / `删除活动资料`）。

---

#### 17.2.4 选题申请与两级审批（Proposal）

学生自拟题目 → 院系初审 → 教务终审。审批流水逐条留痕（`graduation_proposal_review`），响应随申请一并返回。

**端点**

| 方法 | 路径 | 鉴权注解 | 说明 |
|------|------|----------|------|
| POST | `/api/practice/graduation/proposals` | `@RequireStudent` | 提交 / 驳回后重提 |
| PUT | `/api/practice/graduation/proposals/{id}/review/dept` | `@RequireDepartment` | 院系初审 |
| PUT | `/api/practice/graduation/proposals/{id}/review/academic` | `@RequireAcademicAdmin` | 教务终审 |
| GET | `/api/practice/graduation/proposals/my` | `@RequireStudent` | 我的全部申请，`id` 倒序 |
| GET | `/api/practice/graduation/proposals/pending/dept?campaignId=` | `@RequireDepartment` | 本院系待初审，`submitTime` 升序 |
| GET | `/api/practice/graduation/proposals/pending/academic?campaignId=` | `@RequireAcademicAdmin` | 全校待终审，`submitTime` 升序 |

**状态机**

```
学生提交 ──> PENDING_DEPT（待院系初审）
                ├─ 院系通过 ──> DEPT_APPROVED（待教务终审）
                │                  ├─ 教务通过 ──> APPROVED（终态，审批完毕）
                │                  └─ 教务驳回 ──> REJECTED
                └─ 院系驳回 ──> REJECTED
REJECTED ──学生修改重提──> PENDING_DEPT（重新走完整两级审批，清空 rejectReason）
```

**提交请求体（ProposalDeclareRequest）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| campaignId | Long | 是 | 活动 ID |
| title | String | 是 | 自拟题目名称（trim 后落库） |
| content | String | 是 | 主要内容说明，**trim 后不少于 100 字符** |

**审批请求体（ProposalReviewRequest，两级共用）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| approve | Boolean | 是 | `true` 通过 / `false` 驳回 |
| comment | String | 驳回必填 | 审批意见（驳回时对学生可见，作为 `rejectReason`） |

**响应（ProposalResponse）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 申请 ID |
| campaignId | Long | 活动 ID |
| studentId | Long | 学生 `user.id` |
| studentName / studentNo | String | 学生姓名 / 学号 |
| title | String | 题目名称 |
| content | String | 主要内容说明 |
| status | String | 中文：待院系初审 / 待教务终审 / 审批完毕 / 已驳回 |
| rejectReason | String | 最近一次驳回理由（通过后清空） |
| submitTime | LocalDateTime | 最近一次提交时间 |
| reviews | Array | 审批流水，`reviewTime` 升序 |

**`reviews[]`（ProposalReviewView）**

| 字段 | 类型 | 说明 |
|------|------|------|
| stage | String | 中文：院系初审 / 教务终审 |
| action | String | `APPROVE` / `REJECT` |
| reviewerId | Long | 审批人 `user.id` |
| reviewerName | String | 审批人姓名 |
| reviewTime | LocalDateTime | 审批时间 |
| comment | String | 审批意见 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 101,
    "campaignId": 7,
    "studentId": 3001,
    "studentName": "张三",
    "studentNo": "2023001001",
    "title": "基于 Spring Boot 的高校实践管理系统",
    "content": "（不少于100字的主要内容说明……）",
    "status": "待院系初审",
    "rejectReason": null,
    "submitTime": "2026-12-02T10:15:30",
    "reviews": []
  }
}
```

**业务规则**

1. **提交门禁**：活动必须 `OPEN`；`now` 必须落在 `[topicStartTime, topicEndTime]`（等于截止时间不算超时）；学生档案必须存在；学生 `gradeId` 必须在活动参与年级内。
2. **一人一条有效申请（R-5.3）**：同一活动同一学生只有一条记录；已存在且状态 ≠ `REJECTED` 时拒绝重提。提交/重提在 Redis 分布式锁 `lock:grad:proposal:{campaignId}:{studentUserId}` 内完成（TTL 30s），并发抢锁失败 → `409 操作过于频繁，请稍后重试`。
3. **重提**：仅 `REJECTED` 可重提；重提复用原记录，状态回 `PENDING_DEPT`、`rejectReason` 置空、`submitTime` 刷新，历史审批流水保留。
4. **院系初审范围**：`isOutsideDept` 判为院外（含归属无法判定）→ `403 权限不足`。待初审队列在内存中按 `college_id` 过滤，院系管理员未挂 `college_id` 时返回**空列表**（fail-closed）。
5. **教务终审**：不做院系范围限制，全校可见。
6. **状态校验**：院系初审要求当前 `PENDING_DEPT`；教务终审要求当前 `DEPT_APPROVED`；否则 409。
7. 每次审批追加一条流水（`action`=APPROVE/REJECT），并发送站内消息、写操作日志（`院系初审` / `教务终审`）。

**错误场景**

| code | message |
|------|---------|
| 400 | 活动不能为空 / 题目名称不能为空 / 主要内容说明不能为空 |
| 400 | 主要内容说明不少于100字 |
| 400 | 审批级别不能为空 / 审批结果不能为空 |
| 400 | 驳回必须填写理由 |
| 403 | 当前年级不参与本活动 |
| 403 | 权限不足（院系初审非本院学生） |
| 404 | 活动不存在 / 学生档案不存在 / 选题申请不存在 |
| 409 | 活动未开放选题 / 选题尚未开始 / 选题已截止 |
| 409 | 你已存在进行中的选题申请，驳回后才能修改重提 |
| 409 | 该申请不在待院系初审状态 / 该申请不在待教务终审状态 |
| 409 | 操作过于频繁，请稍后重试（锁竞争） |

> ⚠️ `GET /pending/dept` / `GET /pending/academic` 的 `campaignId` 是必填 query 参数，漏传 → body 400 `缺少必填参数: campaignId`。

---

#### 17.2.5 师生双选与分配（Assignment）

一名学生在一个活动内**只能有一条有效匹配**。来源两种：`TEACHER_PICK`（教师自选，选题截止前）与 `DEPT_ALLOCATE`（院系指定，选题截止后）。

**端点**

| 方法 | 路径 | 鉴权注解 | 说明 |
|------|------|----------|------|
| POST | `/api/practice/graduation/assignments/picks` | `@RequireTeacher` | 教师选择学生（先占先得） |
| DELETE | `/api/practice/graduation/assignments/picks/{id}` | `@RequireTeacher` | 放弃自选（选题截止前） |
| POST | `/api/practice/graduation/assignments/allocations` | `@RequireDepartment` | 院系指定分配（选题截止后） |
| POST | `/api/practice/graduation/assignments/reassigns` | `@RequireDepartment` | 院系改派（选题截止后） |
| GET | `/api/practice/graduation/assignments/teacher/pool?campaignId=` | `@RequireTeacher` | 自选池（活动年级 ∩ 本院系，含未选题） |
| GET | `/api/practice/graduation/assignments/my?campaignId=` | `@RequireAuth({STUDENT, TEACHER})` | 我的匹配（学生本人 / 教师名下） |
| GET | `/api/practice/graduation/assignments/overview?campaignId=` | `@RequireAcademicAdmin` | 每教师已选/指定/空缺 |
| GET | `/api/practice/graduation/assignments/unassigned?campaignId=&collegeId=` | `@RequireManagement` | 未分配学生 `user.id` 列表 |

**请求体**

`PickRequest`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| campaignId | Long | 是 | 活动 ID |
| studentId | Long | 是 | 学生 `user.id` |

`AllocationRequest`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| campaignId | Long | 是 | 活动 ID |
| studentId | Long | 是 | 学生 `user.id` |
| teacherId | Long | 是 | 目标教师 `user.id` |

`ReassignRequest`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| campaignId | Long | 是 | 活动 ID |
| studentId | Long | 是 | 学生 `user.id` |
| newTeacherId | Long | 是 | 新教师 `user.id` |
| reason | String | 是 | 改派原因 |

**响应（AssignmentResponse，pick/allocate/reassign/my 共用）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 匹配记录 ID |
| campaignId | Long | 活动 ID |
| studentId | Long | 学生 `user.id` |
| studentName / studentNo | String | 姓名 / 学号 |
| teacherId | Long | 当前指导教师 `user.id` |
| teacherName | String | 当前指导教师姓名 |
| source | String | 中文：教师自选 / 院系指定 |
| assignTime | LocalDateTime | 匹配/分配时间 |
| prevTeacherId / prevTeacherName | Long/String | 改派前的原教师（未改派为空） |
| reassignReason | String | 改派原因（未改派为空） |
| reassignTime | LocalDateTime | 改派时间（未改派为空） |

**自选池行（TeacherPickPoolRow）**

| 字段 | 类型 | 说明 |
|------|------|------|
| studentId | Long | 学生 `user.id` |
| studentNo / studentName / className | String | 学号 / 姓名 / 班级 |
| proposalTitle | String | 题目名称（未选题为空） |
| proposalContent | String | 主要内容说明（教师据此判断指导方向） |
| proposalStatus | String | 选题状态中文（未选题为空） |
| assigned | Boolean | 是否已被选择/分配 |
| assignmentSource | String | 匹配来源中文（未分配为空） |

**分配总览行（AssignmentOverviewRow）**

| 字段 | 类型 | 说明 |
|------|------|------|
| teacherId / teacherName / teacherNo | Long/String/String | 教师 `user.id` / 姓名 / 工号 |
| pickedCount | Long | 已自由选择数 |
| allocatedCount | Long | 已指定分配数 |
| capacity | Integer | 活动配置的可分配上限 |
| freeCount | Long | 空缺席位 = `max(0, capacity - pickedCount - allocatedCount)` |

**业务规则（逐端点）**

1. **教师自选（R-6.1~R-6.5）**
   - 活动必须 `OPEN`，否则 `409 活动未开放`。
   - 教师必须已挂院系，否则 `403 权限不足`；学生院系必须与教师相同，否则 `403 只能选择本院系学生`。
   - 该教师**自选来源**的已选数 ≥ `freeSelectCapacity` → `409 自由选择名额已达上限`（指定分配不占自由选择名额）。
   - 学生维度 Redis 锁 `lock:grad:assign:{campaignId}:{studentId}`（TTL 30s）内做「查重 + 插入」，先占先得；抢锁失败 → `409 操作过于频繁，请稍后重试`；已匹配 → `409 该学生已被选择，先占先得`。
   - 成功后通知学生（标题「毕业选题匹配结果」），写日志 `教师自选学生`。
   > ⚠️ 自选接口**不校验学生年级是否属于活动参与年级**（池接口会按年级过滤，但写接口没有该检查）；也不要求选题已审批。
2. **放弃自选（R-6.6）**
   - 记录不存在 → `404 匹配记录不存在`；非本人名下 → `403 权限不足`；来源不是 `TEACHER_PICK` → `409 仅可放弃自由选择的学生`；已过选题截止 → `409 选题已截止，撤销请通过院系管理者改派`。
   - 删除为 `@TableLogic` **逻辑删除**（学生立即回到可分配池）。实体注释写「物理删除」，以实际行为为准。
3. **院系指定（R-6.7~R-6.11）**
   - 活动必须 `OPEN`；`now` 必须**晚于** `topicEndTime`，否则 `409 选题截止后才能进行指定分配`（恰好等于截止时间仍不允许）。
   - 学生必须在本院系（`403 只能分配本院系学生`），目标教师必须在本院系（`403 只能分配给本院系教师`）。
   - 目标教师**全部来源**（自选 + 指定）总数 ≥ `supervisorCapacity` → `409 该教师名额已满`。
   - 其余同教师自选（学生锁、重复 409）。成功后通知学生（「院系已为你指定指导教师」），写日志 `院系指定分配`。
4. **改派（R-6.13）**
   - `now` 必须晚于选题截止 → 否则 `409 选题截止后才能执行改派`。
   - **不校验活动状态**（`requireCampaign` 只校验存在），CLOSED 活动仍可改派。
   - 学生/新教师都必须在本院系（`403 只能改派本院系学生` / `403 只能改派给本院系教师`）。
   - 学生必须已有匹配 → `404 该学生尚无指导教师`；新旧教师相同 → `400 新教师与原教师相同`；新教师全量名额已满 → `409 新教师名额已满`。
   - 覆盖 `prevTeacherId` / `reassignReason` / `reassignBy` / `reassignTime`（只保留**最近一次**改派痕迹；原教师姓名随 `prevTeacherId` 一并解析）。
   - 通知学生（「毕业选题改派通知」），写日志 `改派学生`。
5. **自选池**：`queryRows(campaignId, 参与年级, 教师 college, null, null)`，返回本活动参与年级 ∩ 教师本院系的学生，含未选题者（Q-2）。按 `studentId` 升序。
6. **我的匹配**：`campaignId` 可选；学生按 `studentId = 当前用户` 过滤，教师按 `teacherId = 当前用户` 过滤，`assignTime` 倒序。
7. **分配总览**：遍历 `teacher` 表**全部教师**（不限本院系、含 0 人教师），按活动统计。
8. **未分配清单**：只返回 `user.id` 数组。院系管理员强制使用本院系 `college_id`（传入的 `collegeId` 被忽略）；教务可按 `collegeId` 过滤。按 `studentId` 升序。

**错误场景**

| code | message |
|------|---------|
| 400 | 活动不能为空 / 学生不能为空 / 教师不能为空 / 新教师不能为空 / 改派原因不能为空 |
| 400 | 新教师与原教师相同 |
| 403 | 权限不足（教师无院系 / 非本人匹配记录） |
| 403 | 只能选择本院系学生 / 只能分配本院系学生 / 只能分配给本院系教师 |
| 403 | 只能改派本院系学生 / 只能改派给本院系教师 |
| 404 | 活动不存在 / 匹配记录不存在 / 该学生尚无指导教师 |
| 409 | 活动未开放 |
| 409 | 自由选择名额已达上限 / 该教师名额已满 / 新教师名额已满 |
| 409 | 该学生已被选择，先占先得 |
| 409 | 仅可放弃自由选择的学生 |
| 409 | 选题已截止，撤销请通过院系管理者改派 |
| 409 | 选题截止后才能进行指定分配 / 选题截止后才能执行改派 |
| 409 | 操作过于频繁，请稍后重试（锁竞争） |

> ⚠️ **院系为空时的可见性缺口**：`teacher/pool` 在教师 `college_id` 为空时把 `null` 传给查询层（= 不过滤），会返回全部参与年级学生；`unassigned` / 看板在院系管理员 `college_id` 为空时同样落入「不过滤」。写操作（pick/allocate/reassign）仍会 403，但列表会泄漏姓名/学号，调用方需知悉。

---

#### 17.2.6 过程管理：开题报告 / 中期检查 / 指导记录

**端点**

| 方法 | 路径 | 鉴权注解 | 说明 |
|------|------|----------|------|
| POST | `/api/practice/graduation/process/opening-reports` | `@RequireStudent` | 提交/重提开题报告（multipart） |
| PUT | `/api/practice/graduation/process/opening-reports/{id}/review` | `@RequireTeacher` | 指导教师审核 |
| GET | `/api/practice/graduation/process/opening-reports/my?campaignId=` | `@RequireStudent` | 我的开题报告（未提交 `data: null`） |
| GET | `/api/practice/graduation/process/opening-reports/teacher?campaignId=` | `@RequireTeacher` | 名下学生开题报告，`submitTime` 倒序 |
| GET | `/api/practice/graduation/process/opening-reports/{id}/download` | 四角色 | 附件下载 |
| POST | `/api/practice/graduation/process/midterms` | `@RequireStudent` | 提交/重提中期检查（multipart） |
| PUT | `/api/practice/graduation/process/midterms/{id}/review` | `@RequireTeacher` | 评审并给结论 |
| GET | `/api/practice/graduation/process/midterms/my?campaignId=` | `@RequireStudent` | 我的中期检查（未提交 `data: null`） |
| GET | `/api/practice/graduation/process/midterms/teacher?campaignId=` | `@RequireTeacher` | 名下学生中期检查，`submitTime` 倒序 |
| GET | `/api/practice/graduation/process/midterms/{id}/download` | 四角色 | 附件下载 |
| POST | `/api/practice/graduation/process/guidance-logs` | `@RequireTeacher` | 记录指导日志 |
| GET | `/api/practice/graduation/process/guidance-logs?campaignId=&studentId=` | `@RequireAuth({TEACHER, DEPARTMENT, ACADEMIC_ADMIN})` | 指导记录查询，`logTime` 倒序 |

**开题报告状态机**

```
SUBMITTED（已提交）──教师通过──> APPROVED（终态，不可再改）
        └──教师退回──> REVISION（需修改）──学生重提──> SUBMITTED
```

**中期检查状态机**

```
SUBMITTED ──教师评审（给出结论）──> REVIEWED（终态，记录不可再改）
```

**开题提交（multipart）**：`Content-Type: multipart/form-data`

| 部分/字段 | 类型 | 必填 | 说明 |
|-----------|------|------|------|
| `data` | JSON part | 是 | `OpeningReportSubmitRequest` |
| `data.campaignId` | Long | 是 | 活动 ID |
| `data.title` | String | 是 | 开题报告标题 |
| `data.content` | String | 是 | 研究目标/内容/技术路线/进度安排 |
| `data.filePath` | String | 否 | 分片产物路径（与 `file` 二选一） |
| `data.fileOriginal` | String | 否 | 展示文件名（配合 `filePath`） |
| `file` | File part | 否 | 整传附件（与 `filePath` 二选一） |

**中期提交（multipart）**：

| 部分/字段 | 类型 | 必填 | 说明 |
|-----------|------|------|------|
| `data` | JSON part | 是 | `MidtermSubmitRequest` |
| `data.campaignId` | Long | 是 | 活动 ID |
| `data.content` | String | 是 | 进展情况/已完成工作/后续计划 |
| `data.filePath` | String | 否 | 分片产物路径（与 `file` 二选一） |
| `data.fileOriginal` | String | 否 | 展示文件名 |
| `file` | File part | 否 | 整传附件（与 `filePath` 二选一） |

**审核请求**

`OpeningReportReviewRequest`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| approve | Boolean | 是 | `true` 通过 / `false` 退回 |
| comment | String | 退回必填 | 审核意见（学生可见） |

`MidtermReviewRequest`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| conclusion | String | 是 | `NORMAL` / `WARNING` / `SEVERE_LAGGING`（或中文描述） |
| comment | String | 否 | 审核意见 |

**响应（OpeningReportResponse）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 报告 ID |
| campaignId / assignmentId | Long | 活动 ID / 师生匹配 ID |
| studentId / studentName | Long/String | 学生 `user.id` / 姓名 |
| title / content | String | 标题 / 正文 |
| fileOriginal | String | 附件展示名（无附件为空） |
| status | String | 中文：已提交 / 已通过 / 需修改 |
| submitTime | LocalDateTime | 最近提交时间 |
| reviewTeacherId / reviewTeacherName | Long/String | 审核教师 |
| reviewComment | String | 审核意见 |
| reviewTime | LocalDateTime | 审核时间 |

**响应（MidtermResponse）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 记录 ID |
| campaignId / assignmentId | Long | 活动 ID / 匹配 ID |
| studentId / studentName | Long/String | 学生 |
| content | String | 正文 |
| fileOriginal | String | 附件展示名 |
| status | String | **英文** `SUBMITTED` / `REVIEWED`（普通字符串字段，非枚举） |
| conclusion | String | 中文：正常 / 警告 / 严重滞后（未评审为空） |
| submitTime | LocalDateTime | 提交时间 |
| reviewTeacherId / reviewTeacherName | Long/String | 审核教师 |
| reviewComment | String | 审核意见 |
| reviewTime | LocalDateTime | 审核时间 |

**指导记录请求（GuidanceLogCreateRequest）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| campaignId | Long | 是 | 活动 ID |
| studentId | Long | 是 | 学生 `user.id` |
| logTime | LocalDateTime | 是 | 指导时间 |
| form | String | 是 | `ONLINE` / `OFFLINE` / `PHONE`（或中文） |
| summary | String | 是 | 指导内容摘要（trim 后落库） |

**指导记录响应（GuidanceLogResponse）**：`id`、`campaignId`、`studentId`、`studentName`、`logTime`、`form`（中文）、`summary`、`createTime`。

**业务规则**

1. **开题门禁（R-3.1）**：选题申请必须 `APPROVED`（否则 `409 选题审批完毕后才能提交开题报告`），且必须已有师生匹配（否则 `409 确定指导教师后才能提交开题报告`）。
2. **开题窗口**：窗口非空时校验，早于开始 → `409 开题报告提交尚未开始`，晚于截止 → `409 开题报告提交已截止`。
3. **开题重提**：已 `APPROVED` → `409 开题报告已通过审核，不可再修改`；`SUBMITTED` / `REVISION` 可重提，提交后状态回 `SUBMITTED`，清空 `reviewComment` / `reviewTime`（**不清空 `reviewTeacherId`**，响应可能仍显示上次审核教师 ID/姓名）。
4. **开题审核**：仅该生的指导教师可审（否则 403）；仅 `SUBMITTED` 可审（否则 `409 该开题报告已审核`）；退回必须填意见（`400 退回必须填写审核意见`）。通过 → `APPROVED`，退回 → `REVISION`。通知学生（「开题报告审核结果」），写日志 `审核开题报告`。
5. **中期门禁**：开题必须 `APPROVED`（否则 `409 开题报告通过后才能提交中期检查`）；中期窗口校验（`409 中期检查提交尚未开始` / `中期检查提交已截止`）；已 `REVIEWED` → `409 中期检查已评审，不可再修改`；首次提交时必须已有师生匹配（`409 尚未确定指导教师`）。
6. **中期评审**：仅该生指导教师；仅 `SUBMITTED` 可审（否则 `409 该中期检查已评审`）；`conclusion` 必填（`400 中期结论不能为空`）。结论进入看板供预警。通知学生（「中期检查评审结果」），写日志 `评审中期检查`。
7. **指导记录**：教师只能给名下学生记录（`ensureTeacherOfStudent`，否则 `403 权限不足`）。查询规则：教师只看自己的记录；院系只看本院系学生（`college_id` 缺失时返回空）；教务看全部。`campaignId` / `studentId` 均为可选过滤。
8. **附件**：开题 / 中期附件**可选**；重提时若带了新附件，旧引用会被 `release`（内容寻址产物不即时删，交回收任务）。下载端点：记录不存在或没有附件 → `404 开题报告不存在` / `404 中期检查不存在`；归属判权为学生本人 / 该生指导教师 / 本院系 / 教务，越权 → `403 权限不足`。
9. **活动状态**：三个提交接口均**不校验活动是否为 OPEN**，只校验上述门禁与时间窗。

**错误场景**

| code | message |
|------|---------|
| 400 | 活动不能为空 / 开题报告标题不能为空 / 开题报告内容不能为空 / 中期检查内容不能为空 |
| 400 | 审核结果不能为空 / 退回必须填写审核意见 / 中期结论不能为空 |
| 400 | 指导时间不能为空 / 指导形式不能为空 / 指导内容摘要不能为空 |
| 400 | file 与 filePath 只能二选一 |
| 403 | 权限不足 |
| 404 | 开题报告不存在 / 中期检查不存在（记录缺失或无附件） |
| 409 | 选题审批完毕后才能提交开题报告 |
| 409 | 确定指导教师后才能提交开题报告 |
| 409 | 开题报告提交尚未开始 / 开题报告提交已截止 |
| 409 | 开题报告已通过审核，不可再修改 |
| 409 | 该开题报告已审核 |
| 409 | 开题报告通过后才能提交中期检查 |
| 409 | 中期检查提交尚未开始 / 中期检查提交已截止 |
| 409 | 中期检查已评审，不可再修改 / 该中期检查已评审 |
| 409 | 尚未确定指导教师 |
| 409 | 该学生不在你名下 |

> 附件 `biz`：开题 `graduation-opening-report`、中期 `graduation-midterm`；扩展名 `.doc/.docx/.pdf/.zip/.rar`，整传 ≤20MB。

---

#### 17.2.7 论文与版本管理（Thesis）

**端点**

| 方法 | 路径 | 鉴权注解 | 说明 |
|------|------|----------|------|
| POST | `/api/practice/graduation/theses` | `@RequireStudent` | 提交新版本（multipart，文件必填） |
| PUT | `/api/practice/graduation/theses/{id}/review` | `@RequireTeacher` | 指导教师形式审查 |
| GET | `/api/practice/graduation/theses/my?campaignId=` | `@RequireStudent` | 我的全部版本，`version` 升序 |
| GET | `/api/practice/graduation/theses/teacher?campaignId=` | `@RequireTeacher` | 名下学生全部版本，`id` 倒序 |
| GET | `/api/practice/graduation/theses/campaign?campaignId=&status=` | `@RequireAcademicAdmin` | 活动内论文，`submitTime` 倒序 |
| GET | `/api/practice/graduation/theses/export-package?campaignId=&status=` | `@RequireAcademicAdmin` | 导出查重数据包（zip） |
| GET | `/api/practice/graduation/theses/{id}/download` | 四角色 | 论文文件下载 |

**状态机**

```
学生提交（新版本）──> SUBMITTED（待形式审查）
                        ├─ 教师通过 ──> APPROVED（形式审查通过，待查重）
                        │                ├─ 教务登记 PASS ──> DUPLICATE_PASSED（门禁：可答辩/可录成绩）
                        │                └─ 教务登记 FAIL ──> DUPLICATE_FAILED（可重提新版本，或同一版再次检测）
                        └─ 教师退回 ──> REVISION（可重提新版本）
```

**提交（multipart）**

| 部分/字段 | 类型 | 必填 | 说明 |
|-----------|------|------|------|
| `data` | JSON part | 是 | `ThesisSubmitRequest` |
| `data.campaignId` | Long | 是 | 活动 ID |
| `data.title` | String | 是 | 论文题目 |
| `data.filePath` | String | 二选一 | 分片产物路径 |
| `data.fileOriginal` | String | 否 | 展示文件名 |
| `file` | File part | 二选一 | 整传文件；与 `filePath` 至少给一个 |

**审查请求（ThesisReviewRequest）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| approve | Boolean | 是 | `true` 通过 / `false` 退回 |
| comment | String | 退回必填 | 审查意见 |

**响应（ThesisResponse）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 论文（版本）ID |
| campaignId / assignmentId | Long | 活动 ID / 匹配 ID |
| studentId / studentName | Long/String | 学生 |
| title | String | 论文题目 |
| fileName | String | 存储相对路径（`objects/graduation-thesis/...`，下载端点按 `id` 取文件，无需前端拼接） |
| fileOriginal | String | 展示文件名 |
| version | Integer | 版本号（从 1 递增） |
| isLatest | Integer | 1 = 最新版，0 = 历史版本 |
| status | String | 中文：待形式审查 / 形式审查通过 / 形式审查退回 / 查重通过 / 查重不通过 |
| submitTime | LocalDateTime | 提交时间 |
| reviewTeacherId / reviewTeacherName | Long/String | 形式审查教师 |
| reviewComment | String | 审查意见 |
| reviewTime | LocalDateTime | 审查时间 |
| duplicateChecks | Array | 该版本的查重记录（`checkTime` 升序），字段见 17.2.8 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 501,
    "campaignId": 7,
    "assignmentId": 88,
    "studentId": 3001,
    "studentName": "张三",
    "title": "基于 Spring Boot 的高校实践管理系统",
    "fileName": "objects/graduation-thesis/9f2c...e1.pdf",
    "fileOriginal": "毕业论文-张三.pdf",
    "version": 2,
    "isLatest": 1,
    "status": "待形式审查",
    "submitTime": "2027-04-20T14:30:00",
    "reviewTeacherId": null,
    "reviewTeacherName": null,
    "reviewComment": null,
    "reviewTime": null,
    "duplicateChecks": []
  }
}
```

**业务规则**

1. **提交门禁（R-3.2）**：开题报告必须 `APPROVED`（`409 开题报告通过后才能提交论文`）；必须已有师生匹配（`409 尚未确定指导教师，无法提交论文`）；论文窗口校验（`409 论文提交尚未开始` / `论文提交已截止`）。**不校验活动状态**。
2. **版本管理（R-8.2）**：
   - 每次提交插入新行，`version = 最新版.version + 1`（首版为 1），旧版 `isLatest` 置 0。
   - 最新版状态为 `APPROVED`（待查重）→ `409 论文已通过形式审查待查重，不可修改`；`DUPLICATE_PASSED` → `409 论文已查重通过，不可修改`；`SUBMITTED` / `REVISION` / `DUPLICATE_FAILED` 允许重提。
   - 仅保留**最近 3 个版本**：超限时删除最旧版本行（逻辑删）并释放其文件引用。
3. **文件必填**：`file` 与 `data.filePath` 二选一，都缺 → `400 文件不能为空`；`biz=graduation-thesis`。
4. **形式审查（R-8.3）**：仅该生指导教师；仅 `SUBMITTED` 可审（`409 该论文不在待形式审查状态`）；退回必须填意见（`400 退回必须填写审查意见`）。通过 → `APPROVED`，退回 → `REVISION`。通知学生（「毕业论文形式审查结果」），写日志 `论文形式审查`。
5. **列表范围**：
   - 学生：本人全部版本，可按 `campaignId` 过滤。
   - 教师：名下学生全部版本（`campaignId` 必填）。
   - 教务：活动内**全部版本**（**不按 `isLatest` 过滤**），可按 `status` 过滤。
   > ⚠️ `status` 是 query 参数，只接受枚举名（`SUBMITTED` / `APPROVED` / `REVISION` / `DUPLICATE_PASSED` / `DUPLICATE_FAILED`），传中文会 400。
6. **导出查重数据包（R-8.4）**：仅打包**最新版**（`isLatest=1`，可按 `status` 过滤），产物结构：
   - `查重名单.xlsx`：表头 `学号 | 姓名 | 院系 | 论文题目 | 版本 | 状态 | 提交时间`
   - 每篇论文一个文件条目：`{学号}-{姓名}-{题目}-v{版本}{原扩展名}`（非法文件名字符替换为 `_`）
   - zip **流式落盘**到 `{file.storage-path}/exports/`，响应支持 Range 断点续传；暂存文件由 `file.export-expire-hours`（默认 24h）清理。
   - 文件名：`查重数据包-{活动名}-{yyyyMMddHHmmss}.zip`；导出动作写日志 `导出查重数据包`。
7. **下载判权**：学生本人 / 该生指导教师 / 本院系 / 教务；越权 403；论文不存在 `404 论文不存在`。

**错误场景**

| code | message |
|------|---------|
| 400 | 活动不能为空 / 论文题目不能为空 |
| 400 | 文件不能为空 / file 与 filePath 只能二选一 |
| 400 | 审查结果不能为空 / 退回必须填写审查意见 |
| 403 | 权限不足 |
| 404 | 活动不存在 / 论文不存在 |
| 409 | 开题报告通过后才能提交论文 |
| 409 | 论文提交尚未开始 / 论文提交已截止 |
| 409 | 尚未确定指导教师，无法提交论文 |
| 409 | 论文已通过形式审查待查重，不可修改 |
| 409 | 论文已查重通过，不可修改 |
| 409 | 该论文不在待形式审查状态 |
| 500 | 查重名单导出失败 / 查重数据包打包失败 |

---

#### 17.2.8 查重登记与查重报告（Duplicate Check）

查重采用**第三方平台模式**：教务线下检测后登记结果，每次检测一行记录，历史全部保留（不通过后可再次检测）。

| 方法 | 路径 | 鉴权注解 | 说明 |
|------|------|----------|------|
| POST | `/api/practice/graduation/theses/duplicate-checks` | `@RequireAcademicAdmin` | 登记查重结果（multipart，可附报告） |
| GET | `/api/practice/graduation/theses/{id}/duplicate-checks` | 四角色 | 指定论文的查重记录 |
| GET | `/api/practice/graduation/theses/duplicate-checks/{checkId}/download` | 四角色 | 查重报告下载（无附件 404） |

> 传输层（`data` + 可选 `file`、报告下载、Breaking change 提示）见 [11.5.1](#1151-查重报告附件)。

**登记请求（multipart）**

| 部分/字段 | 类型 | 必填 | 说明 |
|-----------|------|------|------|
| `data` | JSON part | 是 | `DuplicateCheckRegisterRequest` |
| `data.thesisId` | Long | 是 | 论文（版本）ID |
| `data.duplicateRate` | Integer | 是 | 重复率百分比，0–100 |
| `data.platform` | String | 否 | 检测平台名称 |
| `data.checkTime` | LocalDateTime | 是 | 检测时间 |
| `data.result` | String | 是 | `PASS` / `FAIL`（或中文「通过」/「不通过」） |
| `data.comment` | String | 否 | 备注 |
| `data.filePath` | String | 否 | 查重报告分片产物路径 |
| `data.fileOriginal` | String | 否 | 报告展示文件名 |
| `file` | File part | 否 | 报告整传文件；与 `filePath` 二选一 |

**响应（DuplicateCheckResponse）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 查重记录 ID |
| thesisId | Long | 论文 ID |
| duplicateRate | Integer | 重复率 |
| platform | String | 检测平台 |
| checkTime | LocalDateTime | 检测时间 |
| result | String | 中文：通过 / 不通过 |
| comment | String | 备注 |
| fileName | String | 报告存储路径（有值即表示可下载） |
| fileOriginal | String | 报告展示文件名 |
| operatorId / operatorName | Long/String | 登记人 |
| createTime | LocalDateTime | 登记时间 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 9,
    "thesisId": 501,
    "duplicateRate": 12,
    "platform": "知网",
    "checkTime": "2027-04-25T09:00:00",
    "result": "通过",
    "comment": "低于 20% 阈值",
    "fileName": "objects/graduation-duplicate-report/ab12...cd.pdf",
    "fileOriginal": "查重报告-张三.pdf",
    "operatorId": 1,
    "operatorName": "教务老师",
    "createTime": "2027-04-25T10:20:00"
  }
}
```

**业务规则**

1. 论文必须存在（`404 论文不存在`）；必须是**最新版**且状态为 `APPROVED`（待查重）或 `DUPLICATE_FAILED`（再检测），否则 `409 该论文不在待查重状态`。
2. 重复率必须在 0–100 之间（`400 重复率需在0-100之间`）。
3. 登记后立即改写论文状态：`PASS → DUPLICATE_PASSED`，`FAIL → DUPLICATE_FAILED`。`DUPLICATE_PASSED` 是答辩安排与成绩录入的硬门禁。
4. 通知学生（「论文查重结果」），写日志 `登记查重结果`（含论文 ID、重复率、结论）。
5. `GET /{id}/duplicate-checks`：按 `checkTime` 升序返回该论文所有版本的检测记录。
   > ⚠️ 该端点只做登录 + 四角色门禁，**不校验论文归属**：任意四角色用户知道 `id` 即可读取查重记录（下载报告端点有归属校验）。
6. 报告下载：记录不存在 `404 查重记录不存在`；该次未上传报告 `404 该次检测未上传查重报告`；越权 `403 权限不足`。

**错误场景**

| code | message |
|------|---------|
| 400 | 论文不能为空 / 重复率不能为空 / 检测时间不能为空 / 查重结论不能为空 |
| 400 | 重复率需在0-100之间 |
| 400 | file 与 filePath 只能二选一 |
| 403 | 权限不足 |
| 403 | 文件不属于该业务目录: objects/... |
| 404 | 论文不存在 / 查重记录不存在 / 该次检测未上传查重报告 |
| 409 | 该论文不在待查重状态 |

---

#### 17.2.9 答辩安排与答辩材料（Defense）

**端点**

| 方法 | 路径 | 鉴权注解 | 说明 |
|------|------|----------|------|
| POST | `/api/practice/graduation/defense/arrange` | `@RequireDepartment` | 安排/更新答辩（查重通过门禁） |
| GET | `/api/practice/graduation/defense/list?campaignId=` | `@RequireAuth({ACADEMIC_ADMIN, DEPARTMENT, STUDENT})` | 答辩安排列表 |
| POST | `/api/practice/graduation/defense/{id}/material` | `@RequireManagement` | 上传/替换答辩材料（见 11.5.3） |
| GET | `/api/practice/graduation/defense/{id}/material/download` | `@RequireAuth({STUDENT, DEPARTMENT, ACADEMIC_ADMIN})` | 答辩材料下载（见 11.5.3） |

**安排请求（DefenseArrangeRequest）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| campaignId | Long | 是 | 活动 ID |
| studentId | Long | 是 | 学生 `user.id` |
| groupName | String | 否 | 答辩分组名称 |
| defenseTime | LocalDateTime | 否 | 答辩时间 |
| location | String | 否 | 答辩地点 |
| reviewerId | Long | 否 | 评阅教师 `user.id`（评阅分录入的权限依据） |
| defenseTeacherIds | List\<Long\> | 否 | 答辩教师 `user.id` 列表（落库为逗号分隔；空数组存 null） |

**响应（DefenseResponse）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 答辩安排 ID |
| campaignId / studentId | Long | 活动 ID / 学生 `user.id` |
| studentName / studentNo | String | 姓名 / 学号 |
| groupName / location | String | 分组 / 地点 |
| defenseTime | LocalDateTime | 答辩时间 |
| reviewerId / reviewerName | Long/String | 评阅教师 |
| defenseTeacherIds | List\<Long\> | 答辩教师 ID 列表 |
| defenseTeacherNames | List\<String\> | 答辩教师姓名列表 |
| fileName | String | 答辩材料存储路径（有值即可下载） |
| fileOriginal | String | 材料展示文件名 |

**业务规则**

1. **安排门禁（R-3.3）**：该生在该活动的**最新版论文**必须 `DUPLICATE_PASSED`，否则 `409 论文查重通过后才能安排答辩`。
2. 院系只能安排本院系学生（`isOutsideDept` → `403 权限不足`）。**不校验活动状态**（DRAFT/CLOSED 均可安排）。
3. **upsert**：同一「活动 + 学生」只有一条答辩安排；重复调用为覆盖更新（分组/时间/地点/评阅人/答辩教师组整体覆盖）。评阅人与答辩教师 ID **不校验存在性**。
4. 列表范围：教务全部；院系本院系（`college_id` 缺失时为空列表）；学生仅本人。`id` 倒序。
   > ⚠️ 教师的四角色注解**不含 teacher**，教师无法调用 `GET /defense/list`；评阅教师查看分组请走 `GET /defense/scores/reviewer` 返回的 `groupName`。
5. 写日志 `安排答辩`；上传/替换材料写日志 `上传答辩材料`。

**错误场景**

| code | message |
|------|---------|
| 400 | 活动不能为空 / 学生不能为空 |
| 400 | 文件不能为空 / file 与 filePath 只能二选一 |
| 403 | 权限不足 |
| 403 | 文件不属于该业务目录: objects/... |
| 404 | 活动不存在 / 答辩安排不存在 / 尚未上传答辩材料 |
| 409 | 论文查重通过后才能安排答辩 |

---

#### 17.2.10 成绩录入、合成与发布（Score）

三项分项成绩分别由**指导教师**、**评阅教师**、**院系/教务**录入；三项齐全后按活动权重自动合成总评；院系确认后发布给学生。

**端点**

| 方法 | 路径 | 鉴权注解 | 说明 |
|------|------|----------|------|
| POST | `/api/practice/graduation/defense/scores/advisor` | `@RequireTeacher` | 录入指导分 |
| GET | `/api/practice/graduation/defense/scores/advisor?campaignId=` | `@RequireTeacher` | 指导分录入列表（名下学生） |
| POST | `/api/practice/graduation/defense/scores/reviewer` | `@RequireTeacher` | 录入评阅分 |
| GET | `/api/practice/graduation/defense/scores/reviewer?campaignId=` | `@RequireTeacher` | 评阅分录入列表（本人为评阅人） |
| POST | `/api/practice/graduation/defense/scores/defense` | `@RequireManagement` | 录入答辩分 |
| POST | `/api/practice/graduation/defense/scores/confirm` | `@RequireDepartment` | 院系确认并发布 |
| GET | `/api/practice/graduation/defense/scores?campaignId=` | 四角色 | 成绩列表 |
| GET | `/api/practice/graduation/defense/scores/my?campaignId=` | `@RequireStudent` | 我的成绩（无记录 `data: null`） |
| GET | `/api/practice/graduation/defense/scores/export?campaignId=` | `@RequireAcademicAdmin` | 导出成绩总表 xlsx |

**请求体**

`ScoreSubmitRequest`（三个录入端点共用）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| campaignId | Long | 是 | 活动 ID |
| studentId | Long | 是 | 学生 `user.id` |
| score | Integer | 是 | 分项得分，0–100 |

`ScoreConfirmRequest`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| campaignId | Long | 是 | 活动 ID |
| studentId | Long | 是 | 学生 `user.id` |

**响应（ScoreResponse）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 成绩记录 ID；录入列表的**骨架行可能为 null**（尚无成绩记录） |
| campaignId / studentId | Long | 活动 ID / 学生 `user.id` |
| studentName | String | 学生姓名 |
| studentNo | String | 学号（**仅** 指导/评阅录入列表填充） |
| groupName | String | 答辩组（**仅** 评阅录入列表填充） |
| advisorScore / advisorBy / advisorName / advisorTime | Integer/Long/String/LocalDateTime | 指导分及录入人/时间 |
| reviewerScore / reviewerBy / reviewerName / reviewerTime | Integer/Long/String/LocalDateTime | 评阅分及录入人/时间 |
| defenseScore / defenseBy / defenseName / defenseTime | Integer/Long/String/LocalDateTime | 答辩分及录入人/时间 |
| totalScore | BigDecimal | 总评（三项齐全后才有值；**保留 1 位小数**，HALF_UP） |
| status | String | 中文：分项未齐备 / 已合成总评 / 已发布 |
| confirmBy / confirmName / confirmTime | Long/String/LocalDateTime | 院系确认人/时间 |
| publishTime | LocalDateTime | 发布时间 |

**成绩状态机**

```
INCOMPLETE（分项未齐备）──三项齐全──> COMPLETE（已合成总评）──院系确认──> PUBLISHED（终态，不可再改）
```

**业务规则**

1. **录入门禁（R-3.3）**：该生最新版论文必须 `DUPLICATE_PASSED`，否则 `409 论文查重通过后才能录入成绩`。**不要求已安排答辩**（答辩分可在无答辩安排时录入）。
2. 得分必须在 0–100（`400 得分需在0-100之间`）。已发布 → `409 成绩已发布，不可修改`。
3. **权限**：
   - 指导分：必须是该生指导教师（`403 权限不足`）。
   - 评阅分：必须存在该生答辩安排且 `reviewerId` 为当前教师（无安排 `404 该学生尚未安排答辩`，非评阅人 `403 你不是该学生的评阅教师`）。
   - 答辩分：教务全权；院系限本院学生（`403 权限不足`）。
4. **合成（R-9.2）**：三项均非空时按下式计算并写入，状态置 `COMPLETE`：
   ```
   totalScore = (advisorScore × advisorWeight + reviewerScore × reviewerWeight + defenseScore × defenseWeight) / 100
   ```
   结果 `BigDecimal` 保留 **1 位小数**、`HALF_UP`（如 86.5）。重复录入任一分项会重算，状态保持 `COMPLETE`。
5. **确认发布（R-9.3）**：仅院系；成绩必须为 `COMPLETE`，否则 `409 总评成绩未合成完毕，不可发布`；无成绩记录 → `404 该学生尚无成绩记录`。发布后写入 `confirmBy`/`confirmTime`/`publishTime`，状态 `PUBLISHED`，**终态不可再改**。通知学生（「毕设成绩发布」），写日志 `发布成绩`。
6. **录入列表**：指导列表 = 该教师名下学生（`studentId` 升序）；评阅列表 = 该教师作为 `reviewerId` 的答辩安排学生，并填 `groupName`。**尚无成绩记录的学生会返回骨架行**（`id`/分项均为 null），保证首次录入可用。
7. **成绩列表**：教务全部；学生本人；教师名下学生；院系本院系（`college_id` 缺失时为空）。按 `studentId` 升序。
   > ⚠️ 学生成绩查询（`/scores/my` 与 `/scores` 的学生视角）**不按发布状态过滤**：任一分项录入、状态仍为 `INCOMPLETE`/`COMPLETE` 时学生也能看到分项与总评。若业务要求「发布后才可见」，需前端自行按 `status` 隐藏。
8. **导出（R-9.4）**：xlsx 表头 `学号 | 姓名 | 院系 | 指导教师评分 | 评阅教师评分 | 答辩评分 | 总评成绩 | 状态 | 发布时间`；缺失分项按 0 输出；文件名 `毕业设计成绩总表-{活动名}-{yyyyMMddHHmmss}.xlsx`；写日志 `导出成绩总表`。响应支持 Range（见 11.5.5）。

**错误场景**

| code | message |
|------|---------|
| 400 | 活动不能为空 / 学生不能为空 / 得分不能为空 |
| 400 | 得分需在0-100之间 |
| 403 | 权限不足 |
| 403 | 你不是该学生的评阅教师 |
| 404 | 活动不存在 / 该学生尚未安排答辩 / 该学生尚无成绩记录 |
| 409 | 论文查重通过后才能录入成绩 |
| 409 | 成绩已发布，不可修改 |
| 409 | 总评成绩未合成完毕，不可发布 |
| 500 | 成绩总表导出失败 |

> 分项录入**没有活动状态门禁**，CLOSED 活动仍可录入（仅受查重门禁与发布状态约束）。

---

#### 17.2.11 看板、导出与操作日志

数据源说明：以**活动参与年级的学生**为底表（按 `studentId` 升序），应用层批量合并用户/班级/院系/年级/选题/审批流水/师生匹配/中期检查（无多表 JOIN SQL）。用户缺失（含逻辑删除）的学生不出行。

**端点**

| 方法 | 路径 | 鉴权注解 | 说明 |
|------|------|----------|------|
| GET | `/api/practice/graduation/dashboard/{campaignId}?status=&keyword=&collegeId=&page=&pageSize=` | `@RequireManagement` | 看板内存分页 |
| GET | `/api/practice/graduation/dashboard/{campaignId}/export?format=&status=&keyword=&collegeId=` | `@RequireManagement` | 导出 xlsx / csv |
| GET | `/api/practice/graduation/dashboard/{campaignId}/logs?page=&pageSize=` | `@RequireAcademicAdmin` | 操作日志分页 |

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| campaignId | Long（path） | 是 | 活动 ID（不存在 → `404 活动不存在`） |
| status | String | 否 | 选题状态筛选，见下表 |
| keyword | String | 否 | 学号或姓名**包含**匹配（区分大小写，trim 前原值） |
| collegeId | Long | 否 | 院系过滤；**院系角色传入会被忽略**，强制本院系 |
| page / pageSize | Integer | 否 | 分页，默认 20、上限 100（仅看板列表） |

**`status` 取值（普通 String，不是活动状态）**

| 取值 | 含义 |
|------|------|
| `NOT_SUBMITTED` | 未开始选题（无申请记录） |
| `PENDING` | 初审中（`PENDING_DEPT` + `DEPT_APPROVED`） |
| `PENDING_DEPT` | 待院系初审 |
| `DEPT_APPROVED` | 待教务终审 |
| `APPROVED` | 审批完毕 |
| `REJECTED` | 已驳回 |
| 空/缺省 | 不过滤 |
| 其它任意值 | 匹配不到任何行（返回空，不报错） |

**看板行（DashboardRow）**

| 字段 | 类型 | 说明 |
|------|------|------|
| studentId | Long | 学生 `user.id` |
| studentNo / studentName | String | 学号 / 姓名 |
| className | String | 班级名 |
| collegeId / collegeName | Long/String | 院系 |
| gradeName | String | 年级名 |
| proposalId | Long | 选题申请 ID（未提交为空） |
| proposalTitle | String | 题目名称（未提交为空） |
| proposalContent | String | 主要内容说明（未提交为空） |
| proposalStatus | String | 选题状态中文（未提交为空） |
| proposalSubmitTime | LocalDateTime | 申请提交时间 |
| proposalApprovedTime | LocalDateTime | 审批完成时间 = **教务终审通过**流水时间（未完成审批为空） |
| assignmentId | Long | 匹配记录 ID（未分配为空） |
| teacherId / teacherName | Long/String | 指导教师（未分配为空） |
| assignmentSource | String | 匹配来源中文（未分配为空） |
| midtermId | Long | 中期检查 ID（未提交为空） |
| midtermConclusion | String | 中期结论中文（未评审为空，仅供预警展示） |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "studentId": 3001,
        "studentNo": "2023001001",
        "studentName": "张三",
        "className": "软件工程2301",
        "collegeId": 2,
        "collegeName": "计算机学院",
        "gradeName": "2023级",
        "proposalId": 101,
        "proposalTitle": "基于 Spring Boot 的高校实践管理系统",
        "proposalContent": "……",
        "proposalStatus": "审批完毕",
        "proposalSubmitTime": "2026-12-02T10:15:30",
        "proposalApprovedTime": "2026-12-05T09:30:00",
        "assignmentId": 88,
        "teacherId": 2001,
        "teacherName": "李老师",
        "assignmentSource": "教师自选",
        "midtermId": 55,
        "midtermConclusion": "警告"
      }
    ],
    "total": 42,
    "page": 1,
    "pageSize": 20,
    "pages": 3
  }
}
```

**业务规则**

1. **数据范围（R-10.1）**：教务按 `collegeId` 可选过滤；院系强制本院系（代码把 `collegeId` 覆盖为 `deptCollegeId`）。
   > ⚠️ 与 pending 队列的 fail-closed 不同：院系管理员 `college_id` 为空时，看板 / 导出的 `effectiveCollegeId` 为 null = **不过滤**，会看到全校数据。
2. **导出（R-5.10）**：`format` 默认 `xlsx`；仅当值为 `csv`（大小写不敏感）时导出 CSV，**其它任意值一律按 xlsx**。CSV 带 UTF-8 BOM，列与 xlsx 相同：`学号 | 姓名 | 院系 | 班级 | 年级 | 选题状态 | 题目名称 | 指导教师 | 匹配来源 | 中期结论 | 申请提交时间 | 审批完成时间`。文件名 `毕业设计看板-{活动名}-{yyyyMMddHHmmss}.xlsx|csv`。导出写日志 `导出看板数据`（详情含格式、条数、`status`/`keyword` 筛选，不含 `collegeId`）。响应头按 11.5.5 支持 Range。
3. **操作日志（R-10.4）**：
   - 存储为 JSONL 文件：`{practice.operation-log-path}/campaign-{campaignId}.jsonl`，一行一条；`id` 由应用层全局递增序列分配（首次写入扫描已有文件恢复最大值）。
   - 查询按 `id` **倒序**分页；坏行跳过；文件不存在返回空页。
   - **不校验活动是否存在**：`campaignId` 目录下无日志时返回空 `PageResult`，不报 404。
   - 记录动作：`创建毕设活动`、`更新毕设活动`、`变更活动状态`、`上传活动资料`、`删除活动资料`、`院系初审`、`教务终审`、`教师自选学生`、`教师放弃自选`、`院系指定分配`、`改派学生`、`审核开题报告`、`评审中期检查`、`论文形式审查`、`登记查重结果`、`导出查重数据包`、`安排答辩`、`上传答辩材料`、`发布成绩`、`导出成绩总表`、`导出看板数据`。
   - **不记录**学生提交类动作（选题提交、开题提交、中期提交、论文提交）与成绩分项录入。
   - 日志文件追加**不参与业务事务**：业务回滚时日志行仍在（留痕尝试记录）。

**响应（PageResult\<OperationLogResponse\>）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 全局递增日志 ID |
| campaignId | Long | 活动 ID |
| operatorId / operatorName | Long/String | 操作人 `user.id` / 姓名（用户已删为空） |
| operatorType | String | 操作人角色：`academic_admin` / `department` / `teacher` |
| action | String | 动作描述（见上） |
| targetType | String | 对象类型（如 `graduation_campaign`、`graduation_assignment`、`graduation_thesis`） |
| targetId | Long | 对象 ID |
| detail | String | 详情备注 |
| createTime | LocalDateTime | 操作时间 |

**错误场景**

| code | message |
|------|---------|
| 404 | 活动不存在 |
| 500 | Excel 导出失败 / 操作日志读取失败 |

---

#### 17.2.12 关键门禁与状态速查

| 环节 | 门禁 | 失败文案（code） |
|------|------|------------------|
| 选题提交 | 活动 OPEN + 选题窗口内 + 年级匹配 + 无进行中申请 | 409 活动未开放选题 / 选题尚未开始 / 选题已截止；403 当前年级不参与本活动；409 你已存在进行中的选题申请，驳回后才能修改重提 |
| 教师自选 | 活动 OPEN + 同院系 + 自选名额未满 + 学生未匹配 | 409 活动未开放 / 自由选择名额已达上限 / 该学生已被选择，先占先得；403 只能选择本院系学生 |
| 院系指定 | 活动 OPEN + 已过选题截止 + 教师全量名额未满 | 409 选题截止后才能进行指定分配 / 该教师名额已满 |
| 院系改派 | 已过选题截止 + 学生已匹配 + 新教师名额未满 | 409 选题截止后才能执行改派 / 新教师名额已满；404 该学生尚无指导教师 |
| 开题提交 | 选题 APPROVED + 已有指导教师 + 开题窗口内 | 409 选题审批完毕后才能提交开题报告 / 确定指导教师后才能提交开题报告 / 开题报告提交已截止 |
| 中期提交 | 开题 APPROVED + 中期窗口内 | 409 开题报告通过后才能提交中期检查 / 中期检查提交已截止 |
| 论文提交 | 开题 APPROVED + 已有指导教师 + 论文窗口内 + 最新版未 APPROVED/DUPLICATE_PASSED | 409 开题报告通过后才能提交论文 / 尚未确定指导教师，无法提交论文 / 论文提交已截止 / 论文已通过形式审查待查重，不可修改 / 论文已查重通过，不可修改 |
| 查重登记 | 最新版 + 状态 APPROVED 或 DUPLICATE_FAILED | 409 该论文不在待查重状态 |
| 答辩安排 | 最新版论文 DUPLICATE_PASSED | 409 论文查重通过后才能安排答辩 |
| 成绩录入 | 最新版论文 DUPLICATE_PASSED + 成绩未 PUBLISHED | 409 论文查重通过后才能录入成绩 / 成绩已发布，不可修改 |
| 成绩发布 | 三项齐全（COMPLETE）+ 院系角色 | 409 总评成绩未合成完毕，不可发布 |
| 论文下载 / 查重报告下载 | 学生本人 / 指导教师 / 本院系 / 教务 | 403 权限不足；404 论文不存在 / 查重记录不存在 / 该次检测未上传查重报告 |

**完整状态枚举**（持久化 code 与响应中文的对应）见 17.1.3。

---

### 17.3 实习与培训

实习与培训子域（`module/practice/internship/`）包含三块能力：**实习项目 + 实习报名 + 实习成果报告**、**培训课程 + 培训报名**。

**角色与数据范围**

| 角色 | userType | 能力 |
|------|----------|------|
| 院系管理员 | `department` | **发布**实习/培训；更新 / 改状态 / 删除 / 审核**仅本人负责项**（`supervisorId` / `teacherId` == 当前 `user.id`）；查看本人负责项及其报名、报告 |
| 教务管理员 | `academic_admin` | 更新 / 改状态 / 删除 / 审核 / 查看全部（**不含发布**，见下方注意） |
| 学生 | `student` | 报名 / 撤销、提交报告、查看可选列表与我的记录 |
| 任意已登录 | - | 按 id 查看实习/培训详情（**不校验状态**，草稿也可查看） |

> ⚠️ **发布权限只给院系管理员**：`POST /api/practice/internships` 与 `POST /api/practice/trainings`
> 标注的是 `@RequireDepartment`，教务管理员调用会直接得到 403「权限不足」。其余管理动作标注
> `@RequireManagement`（院系 + 教务），院系侧再由 Service 校验「是否本人负责」。

**通用约定**

- 状态枚举持久化 `code`；响应经 `@JsonValue` 输出**中文描述**（如 `"status": "开放"`）。JSON 请求体中的枚举可传 `code` 或中文描述（传非法值返回 400「请求体格式错误」），query 参数只认枚举名 / `code`（如 `?status=OPEN`；传中文会 400「参数类型错误: status」）。
- 请求体 JSON 无法解析（语法错误、类型不匹配）统一返回 400「请求体格式错误」。
- 业务异常统一返回 **HTTP 200 + body.code**（400 / 403 / 404 / 409）；鉴权失败由 `AuthInterceptor` 直接写 **HTTP 401 / 403** + `{"code":...,"message":...}`。
- 分页参数 `page`（默认 1）/ `pageSize`（默认 20，上限 100，超出截断）；响应 `PageResult` 为 `records` / `total` / `page` / `pageSize` / `pages`。
- 所有 `PUT /{id}/status` 的 `status` 为**必填 query 参数**（枚举名 / code）；缺失返回 400「缺少必填参数: status」，非法值返回 400「参数类型错误: status」。
- 常规列表按 `id` 倒序（新 → 旧）；竞赛结果列表例外，按 `awardTime` 倒序。
- 服务端**不校验状态迁移合法性**：`PUT /{id}/status` 可以把任意状态改成任意状态；只有报名 / 申报动作会强制目标项目处于 `OPEN`。
- `semesterId` 可空，缺省取当前学期（`semester.status = CURRENT`）；无当前学期时存 `null`。

**共用枚举**

| 枚举 | 持久化 code（JSON 请求可用值） | 响应文案 |
|------|------------------------------|----------|
| `InternshipStatusEnum` / `TrainingStatusEnum` / `SocialPracticeStatusEnum` | `DRAFT` / `OPEN` / `CLOSED` | 草稿 / 开放 / 关闭 |
| `AuditStatusEnum` | `PENDING` / `APPROVED` / `REJECTED` | 待审核 / 已通过 / 已驳回 |
| `ReportStatusEnum` | `SUBMITTED` / `REVIEWED` | 已提交 / 已评审 |
| `EnrollStatusEnum` | `ENROLLED` / `CANCELLED` | 已报名 / 已取消 |

> `EnrollStatusEnum.CANCELLED` 当前**不会落库**：退课走逻辑删除，见 17.3.5。

---

#### 17.3.1 实习项目

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/internships` | 院系管理员 | 发布实习（落 `DRAFT`） |
| PUT | `/api/practice/internships/{id}` | 院系（本人负责）/ 教务 | 部分更新 |
| PUT | `/api/practice/internships/{id}/status?status=` | 院系（本人负责）/ 教务 | 修改状态 |
| GET | `/api/practice/internships?supervisorId=&status=&page=&pageSize=` | 院系 / 教务 | 分页列表 |
| GET | `/api/practice/internships/{id}` | 任意已登录 | 详情 |
| GET | `/api/practice/internships/available` | 学生 | 可选实习列表（仅 `OPEN`） |
| DELETE | `/api/practice/internships/{id}` | 院系（本人负责）/ 教务 | 删除 |

**创建请求体（`POST`）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semesterId | Long | 否 | 学期 ID，缺省当前学期 |
| title | String | 是 | 实习标题（空白 400） |
| company | String | 否 | 实习单位 |
| description | String | 否 | 描述 |
| supervisorId | Long | 否 | 负责教师 **user.id**，缺省为创建者 |
| startTime | LocalDateTime | 否 | 开始时间 |
| endTime | LocalDateTime | 否 | 结束时间 |
| capacity | Integer | 是 | 容量，必须 > 0 |

```json
{
  "title": "2026 暑期企业实习",
  "company": "某某科技有限公司",
  "description": "后端开发实习岗位",
  "startTime": "2026-07-01T09:00:00",
  "endTime": "2026-08-31T18:00:00",
  "capacity": 30
}
```

**更新请求体（`PUT`）**：字段同上（**不含** `semesterId`，学期不可改），全部可空，`null` 表示不修改；`title` 传空白仍 400；`capacity` 传值时必须 > 0 且 **不得小于已通过人数**。

> 服务端不校验 `supervisorId` 是否存在；解析不到姓名时响应 `supervisorName` 为 `null`。

**改状态 Query**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | InternshipStatusEnum | 是 | `DRAFT` / `OPEN` / `CLOSED` |

**列表 Query**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| supervisorId | Long | 否 | 按负责教师过滤；**仅教务传入时生效** |
| status | InternshipStatusEnum | 否 | 状态过滤 |
| page / pageSize | Integer | 否 | 分页 |

> **数据范围**：院系管理员固定只看到 `supervisor_id = 当前 user.id` 的实习（`supervisorId` 参数被忽略）；教务管理员看到全部。按 id 查看详情不做归属与状态过滤。
>
> **可选列表**：`GET /available` 仅按 `status = OPEN` 过滤，**不排除当前学生已报名的项目**（Service 接收 `studentUserId` 但未使用）；前端需结合 `GET /applications/my` 去重或置灰。实习 / 培训 / 实践的报名、申报动作只校验项目状态，**不校验** `startTime` / `endTime`。

**InternshipResponse 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 实习项目 ID |
| semesterId | Long | 学期 ID（可空） |
| title | String | 标题 |
| company | String | 实习单位 |
| description | String | 描述 |
| supervisorId | Long | 负责教师 user.id |
| supervisorName | String | 负责教师姓名 |
| startTime / endTime | LocalDateTime | 起止时间 |
| capacity | Integer | 容量 |
| selectedCount | Integer | 已通过人数（**占容量**，审核通过时才 +1） |
| pendingCount | Integer | 待审核人数（Redis `SCARD`，**不占容量**） |
| status | String | 状态（中文描述） |
| createTime | LocalDateTime | 创建时间 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 12,
    "semesterId": 3,
    "title": "2026 暑期企业实习",
    "company": "某某科技有限公司",
    "description": "后端开发实习岗位",
    "supervisorId": 88,
    "supervisorName": "李老师",
    "startTime": "2026-07-01T09:00:00",
    "endTime": "2026-08-31T18:00:00",
    "capacity": 30,
    "selectedCount": 5,
    "pendingCount": 2,
    "status": "开放",
    "createTime": "2026-06-01T10:00:00"
  }
}
```

> `pendingCount` 来自 Redis 集合 `practice:internship:pending:{internshipId}`（不设 TTL），仅作快速读数；
> 事实源仍是 `internship_application`。Redis 数据丢失时该值会退化为 0，但不影响审核与容量控制。

**错误场景**

| code | message | 触发 |
|------|---------|------|
| 400 | 实习标题不能为空 | 创建 / 更新标题空白 |
| 400 | 实习容量必须大于0 | 创建 / 更新容量 ≤ 0 |
| 404 | 实习项目不存在 | 更新 / 改状态 / 详情 / 删除 |
| 403 | 权限不足 | 院系管理员操作非本人负责的实习；教务调用创建接口；非管理侧角色访问 |
| 409 | 容量不能小于已通过人数 | 更新容量 < `selectedCount` |
| 400 | 缺少必填参数: status | 改状态未传 `status` |
| 400 | 参数类型错误: status | `status` 非枚举值 |
| 409 | 存在未完结的报名，不可删除 | 删除时仍有 `PENDING` / `APPROVED` 报名 |

删除为逻辑删除，并清理该实习的 Redis 待审核集合；`REJECTED` 与已撤销报名不阻止删除。

---

#### 17.3.2 实习报名与审核

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/internships/applications` | 学生 | 报名 |
| DELETE | `/api/practice/internships/applications/{id}` | 学生 | 撤销报名（仅 `PENDING`） |
| POST | `/api/practice/internships/applications/{id}/review` | 院系（本人负责）/ 教务 | 审核 |
| GET | `/api/practice/internships/applications/my` | 学生 | 我的报名（全量，不分页） |
| GET | `/api/practice/internships/{internshipId}/applications?page=&pageSize=` | 院系（本人负责）/ 教务 | 某实习的报名列表 |

**报名请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| internshipId | Long | 是 | 实习项目 ID |
| applyReason | String | 否 | 报名理由 |

```json
{ "internshipId": 12, "applyReason": "希望积累后端开发经验" }
```

**审核请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| approved | Boolean | 是 | `true` 通过 / `false` 驳回 |
| reviewComment | String | 否 | 审核意见 |

**报名核心规则**

1. 实习必须存在且 `status = OPEN`，否则 409「实习项目未开放」。
2. 同一学生对同一实习存在 `PENDING` 或 `APPROVED` 报名时拒绝重复报名；`REJECTED` / 已撤销不阻止再次报名。
3. **报名时不检查容量、不占容量**：报名落库为 `PENDING`，同时学生 id 写入 Redis 待审核集合。
4. **容量只在审核通过时消费**：审核通过执行条件更新 `selected_count = selected_count + 1 WHERE selected_count < capacity`；受影响行数为 0 时返回 409「实习容量已满，暂时无法通过该报名」，报名**保持 `PENDING`**（事务回滚，待扩容后可再审）。
5. 审核通过或驳回都会移出 Redis 待审核集合；通过/驳回后发出站内通知（「实习报名审核结果」），事务提交后发送。
6. 撤销仅限本人 `PENDING` 记录，执行逻辑删除并移出 Redis 集合；已处理（通过 / 驳回）不可撤销。

**InternshipApplicationResponse 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 报名记录 ID |
| internshipId | Long | 实习项目 ID |
| internshipTitle | String | 实习标题 |
| studentId | Long | 学生 user.id |
| studentName | String | 学生姓名 |
| status | String | `待审核` / `已通过` / `已驳回` |
| applyReason | String | 报名理由 |
| applyTime | LocalDateTime | 报名时间 |
| reviewTime | LocalDateTime | 审核时间（未审核 null） |
| reviewComment | String | 审核意见 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 101,
    "internshipId": 12,
    "internshipTitle": "2026 暑期企业实习",
    "studentId": 203,
    "studentName": "张三",
    "status": "待审核",
    "applyReason": "希望积累后端开发经验",
    "applyTime": "2026-06-05T14:00:00",
    "reviewTime": null,
    "reviewComment": null
  }
}
```

**错误场景**

| code | message | 触发 |
|------|---------|------|
| 400 | 实习项目不能为空 | 报名未传 `internshipId` |
| 404 | 实习项目不存在 | 报名 / 查看报名列表 |
| 409 | 实习项目未开放 | 实习非 `OPEN` |
| 409 | 已报名该实习 | 已有 `PENDING` / `APPROVED` 报名 |
| 404 | 报名不存在 | 撤销 / 审核 |
| 403 | 权限不足 | 撤销他人报名；审核非本人负责的实习 |
| 409 | 已处理的报名不可撤销 | 撤销非 `PENDING` 记录 |
| 400 | 审核结果不能为空 | `approved` 缺失 |
| 409 | 该报名已处理 | 重复审核 |
| 409 | 实习容量已满，暂时无法通过该报名 | 审核通过但容量已满 |

---

#### 17.3.3 实习成果报告

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/internship-reports` | 学生 | 提交 / 重传报告（`multipart/form-data`） |
| GET | `/api/practice/internship-reports/my` | 学生 | 我的报告（全量） |
| GET | `/api/practice/internship-reports?status=&page=&pageSize=` | 院系（本人负责）/ 教务 | 分页列表 |
| POST | `/api/practice/internship-reports/{id}/review` | 院系（本人负责）/ 教务 | 评审 |
| DELETE | `/api/practice/internship-reports/{id}` | 学生本人 / 院系（本人负责）/ 教务 | 删除 |
| GET | `/api/practice/internship-reports/{id}/download` | 学生本人 / 院系（本人负责）/ 教务 | 下载附件 |

**提交（multipart）**

| 部分 / 字段 | 类型 | 必填 | 说明 |
|------------|------|------|------|
| `data`（part，`application/json`） | JSON | 是 | 见下表 |
| `data.internshipId` | Long | 是 | 实习项目 ID |
| `data.title` | String | 是 | 报告标题（空白 400） |
| `data.summary` | String | 否 | 实习总结 |
| `data.filePath` | String | 与 `file` 二选一 | 分片产物路径（`objects/internship-report/{sha256}{ext}`） |
| `data.fileOriginal` | String | 否 | 展示文件名；留空回退产物文件名（摘要名），**建议必传** |
| `file`（part） | Binary | 与 `filePath` 二选一 | 整传文件（≤20MB） |

```bash
curl -X POST 'http://host/api/practice/internship-reports' \
  -H "Authorization: Bearer $TOKEN" \
  -F 'data={"internshipId":12,"title":"实习报告","summary":"……","filePath":"objects/internship-report/<sha256>.pdf","fileOriginal":"实习报告.pdf"};type=application/json'
```

**业务规则**

1. 学生必须在该实习上有 **`APPROVED`** 报名，否则 409「实习未通过审核，不可提交报告」。
2. 同一学生对同一实习**只有一份报告**（按 `internshipId + studentId` 查找）；重复提交为**重传**，覆盖标题 / 摘要 / 文件并刷新 `submitTime`。
3. **重传**仅限 `SUBMITTED`（已 `REVIEWED` 再提交 409「报告已评审，不可修改」）；**删除**时学生仅限本人 `SUBMITTED`，教务 / 负责院系不受状态限制。
4. 评审把状态从 `SUBMITTED` 改为 `REVIEWED`，写入 `score` / `feedback` / `reviewTime`，并发送站内通知（「实习报告评审结果」）。
5. 教务可查看 / 评审全部；院系管理员只能查看 / 评审**本人负责实习**下的报告。
6. 删除：教务全权；院系限本人负责实习；学生仅本人且 `SUBMITTED`。删除为逻辑删除，附件引用一并释放（见 17.6）。
7. 下载权限：教务全权；学生本人；该实习的负责教师（院系）。

> ⚠️ 报告表**无数据库唯一约束**，服务端按 `internshipId + studentId` 取首条（`LIMIT 1`）做 upsert；
> 并发双击提交可能留下重复行，读取按查询首条返回。

**评审请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| score | Integer | 否 | 评分（无范围校验） |
| feedback | String | 否 | 反馈意见 |

**InternshipReportResponse 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 报告 ID |
| internshipId | Long | 实习项目 ID |
| internshipTitle | String | 实习标题 |
| studentId | Long | 学生 user.id |
| studentName | String | 学生姓名 |
| title | String | 报告标题 |
| summary | String | 实习总结 |
| fileOriginal | String | 附件展示名（**不返回存储路径**，下载走专用端点） |
| submitTime | LocalDateTime | 提交时间 |
| score | Integer | 评分（未评审 null；无范围校验） |
| feedback | String | 反馈意见 |
| reviewTime | LocalDateTime | 评审时间 |
| status | String | `已提交` / `已评审` |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 55,
    "internshipId": 12,
    "internshipTitle": "2026 暑期企业实习",
    "studentId": 203,
    "studentName": "张三",
    "title": "实习报告",
    "summary": "为期两个月的实习总结……",
    "fileOriginal": "实习报告.pdf",
    "submitTime": "2026-09-01T10:00:00",
    "score": null,
    "feedback": null,
    "reviewTime": null,
    "status": "已提交"
  }
}
```

**错误场景**

| code | message | 触发 |
|------|---------|------|
| 400 | 实习项目不能为空 | `data.internshipId` 缺失 |
| 400 | 报告标题不能为空 | `data.title` 空白 |
| 400 | 缺少必填请求部分: data | multipart 缺少 `data` |
| 404 | 实习项目不存在 | 提交 |
| 409 | 实习未通过审核，不可提交报告 | 无 `APPROVED` 报名 |
| 409 | 报告已评审，不可修改 | 重传已评审报告 |
| 404 | 报告不存在 | 评审 / 删除 / 下载 |
| 403 | 权限不足 | 非本人 / 非负责院系 / 非教务 |
| 409 | 该报告已评审 | 重复评审 |
| 404 | 文件不存在 | 下载时附件缺失 |

文件大小 / 格式 / 二选一错误见 17.6。

---

#### 17.3.4 培训课程

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/trainings` | 院系管理员 | 发布培训（落 `DRAFT`） |
| PUT | `/api/practice/trainings/{id}` | 院系（本人负责）/ 教务 | 部分更新 |
| PUT | `/api/practice/trainings/{id}/status?status=` | 院系（本人负责）/ 教务 | 修改状态 |
| GET | `/api/practice/trainings?teacherId=&status=&page=&pageSize=` | 院系 / 教务 | 分页列表 |
| GET | `/api/practice/trainings/{id}` | 任意已登录 | 详情 |
| GET | `/api/practice/trainings/available` | 学生 | 可选培训（仅 `OPEN`） |
| DELETE | `/api/practice/trainings/{id}` | 院系（本人负责）/ 教务 | 删除 |

**创建请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semesterId | Long | 否 | 缺省当前学期 |
| title | String | 是 | 培训标题（空白 400） |
| description | String | 否 | 描述 |
| teacherId | Long | 否 | 授课 / 负责教师 **user.id**，缺省创建者 |
| startTime | LocalDateTime | 否 | 开始时间 |
| endTime | LocalDateTime | 否 | 结束时间 |
| capacity | Integer | 是 | 容量，必须 > 0 |

**更新请求体**：`title` / `description` / `teacherId` / `startTime` / `endTime` / `capacity`，全部可空；`capacity` 传值时不得小于 `enrolledCount`。

> 服务端不校验 `teacherId` 是否存在；解析不到姓名时响应 `teacherName` 为 `null`。

**列表 Query**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| teacherId | Long | 否 | 按教师过滤；**仅教务传入时生效** |
| status | TrainingStatusEnum | 否 | 状态过滤 |
| page / pageSize | Integer | 否 | 分页 |

> **可选列表**：`GET /available` 仅按 `status = OPEN` 过滤，**不排除当前学生已报名的课程**；前端需结合 `GET /enrollments/my` 去重或置灰。

**TrainingResponse 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 培训课程 ID |
| semesterId | Long | 学期 ID |
| title | String | 标题 |
| description | String | 描述 |
| teacherId | Long | 教师 user.id |
| teacherName | String | 教师姓名 |
| startTime / endTime | LocalDateTime | 起止时间 |
| capacity | Integer | 容量 |
| enrolledCount | Integer | 已报名人数（维护计数：报名 +1，退课 -1） |
| status | String | 状态（中文描述） |
| createTime | LocalDateTime | 创建时间 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 7,
    "semesterId": 3,
    "title": "Java 后端实战培训",
    "description": "Spring Boot 项目实战",
    "teacherId": 88,
    "teacherName": "李老师",
    "startTime": "2026-10-08T14:00:00",
    "endTime": "2026-12-20T16:00:00",
    "capacity": 50,
    "enrolledCount": 12,
    "status": "开放",
    "createTime": "2026-09-20T09:00:00"
  }
}
```

**错误场景**

| code | message | 触发 |
|------|---------|------|
| 400 | 培训标题不能为空 | 创建 / 更新标题空白 |
| 400 | 培训容量必须大于0 | 创建 / 更新容量 ≤ 0 |
| 403 | 权限不足 | 院系管理员操作非本人负责课程；教务调用创建接口；非管理侧角色访问 |
| 404 | 培训课程不存在 | 更新 / 改状态 / 详情 / 删除 |
| 409 | 容量不能小于已报名人数 | 更新容量 < `enrolledCount` |
| 400 | 缺少必填参数: status / 参数类型错误: status | 改状态参数问题 |
| 409 | 存在已报名学生，不可删除 | 删除时仍有 `ENROLLED` 报名 |

---

#### 17.3.5 培训报名

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/trainings/{courseId}/enrollments` | 学生 | 报名（**即报即生效，无审核**） |
| DELETE | `/api/practice/trainings/enrollments/{id}` | 学生 | 退课 |
| GET | `/api/practice/trainings/enrollments/my` | 学生 | 我的报名（全量） |
| GET | `/api/practice/trainings/{courseId}/enrollments?page=&pageSize=` | 院系（本人负责）/ 教务 | 某课程的报名列表 |

**规则**

1. 课程必须存在且 `status = OPEN`；否则 409「培训课程未开放」（**不校验** `startTime` / `endTime`）。
2. 同一学生对同一课程存在 `ENROLLED` 报名时 409「已报名该培训」。
3. 报名执行**条件更新防超卖**：`enrolled_count = enrolled_count + 1 WHERE enrolled_count < capacity`，受影响行数为 0 返回 409「培训已满」。
4. 退课仅限本人 `ENROLLED` 记录：**逻辑删除报名行**并 `enrolled_count = GREATEST(enrolled_count - 1, 0)`。删除后学生可重新报名。
5. 教务/院系管理员查看报名列表时，院系侧要求课程为本人负责。

> ⚠️ 退课不会把记录改成 `CANCELLED`，而是逻辑删除；`EnrollStatusEnum.CANCELLED` 在当前实现中不会出现在响应里。

**TrainingEnrollmentResponse 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 报名记录 ID |
| courseId | Long | 培训课程 ID |
| courseTitle | String | 课程标题 |
| studentId | Long | 学生 user.id |
| studentName | String | 学生姓名 |
| enrollTime | LocalDateTime | 报名时间 |
| status | String | `已报名` |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 301,
    "courseId": 7,
    "courseTitle": "Java 后端实战培训",
    "studentId": 203,
    "studentName": "张三",
    "enrollTime": "2026-09-25T11:30:00",
    "status": "已报名"
  }
}
```

**错误场景**

| code | message | 触发 |
|------|---------|------|
| 404 | 培训课程不存在 | 报名 / 查看报名列表 |
| 409 | 培训课程未开放 | 课程非 `OPEN` |
| 409 | 已报名该培训 | 重复报名 |
| 409 | 培训已满 | 容量条件更新失败 |
| 404 | 报名记录不存在 | 退课 |
| 403 | 权限不足 | 退他人报名；查看非本人负责课程的报名 |
| 409 | 该报名已取消 | 记录状态非 `ENROLLED`（防御性分支；退课实际执行逻辑删除，重复退课返回 404） |

---

### 17.4 竞赛管理

竞赛子域（`module/practice/competition/`）由**教务管理员**统一管理；学生报名（个人 / 团队）后由教务审核，教务录入获奖结果并维护获奖证书。

**角色**

| 角色 | 能力 |
|------|------|
| 教务管理员 | 发布 / 更新 / 改状态 / 删除竞赛、审核报名、录入 / 删除结果、上传证书、查看全部 |
| 学生 | 查看可选竞赛、报名 / 撤销、我的报名、我的结果、下载本人证书 |
| 任意已登录 | 查看竞赛详情、查看某竞赛的结果列表 |

**枚举**

| 枚举 | code（JSON 请求可用值） | 响应文案 |
|------|------------------------|----------|
| `CompetitionStatusEnum` | `DRAFT` / `OPEN` / `CLOSED` / `ENDED` | 草稿 / 开放报名 / 报名关闭 / 已结束 |
| `CompetitionLevelEnum` | `NATIONAL` / `PROVINCIAL` / `SCHOOL` | 国家级 / 省级 / 校级 |
| `AwardEnum` | `FIRST` / `SECOND` / `THIRD` / `EXCELLENCE` / `PARTICIPATION` | 一等奖 / 二等奖 / 三等奖 / 优秀奖 / 参与奖 |
| `AuditStatusEnum` | `PENDING` / `APPROVED` / `REJECTED` | 待审核 / 已通过 / 已驳回 |

> JSON 请求体中的 `level` / `award` 可传 code 或中文描述（如 `"level": "国家级"` 与 `"level": "NATIONAL"` 等价）；
> 响应输出中文描述。query 参数（如 `?status=OPEN`）只认枚举名 / code。

---

#### 17.4.1 竞赛项目

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/competitions` | 教务 | 发布竞赛（落 `DRAFT`） |
| PUT | `/api/practice/competitions/{id}` | 教务 | 部分更新 |
| PUT | `/api/practice/competitions/{id}/status?status=` | 教务 | 修改状态 |
| GET | `/api/practice/competitions?status=&page=&pageSize=` | 教务 | 分页列表（全部） |
| GET | `/api/practice/competitions/{id}` | 任意已登录 | 详情（不校验状态） |
| GET | `/api/practice/competitions/available` | 学生 | 可选竞赛（仅 `OPEN`，不校验报名时间窗） |
| DELETE | `/api/practice/competitions/{id}` | 教务 | 删除 |

**创建请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semesterId | Long | 否 | 缺省当前学期 |
| name | String | 是 | 竞赛名称（空白 400） |
| description | String | 否 | 描述 |
| organizer | String | 否 | 主办方 |
| level | CompetitionLevelEnum | 否 | 级别 |
| regStartTime | LocalDateTime | 否 | 报名开始时间 |
| regEndTime | LocalDateTime | 否 | 报名截止时间 |
| contestTime | LocalDateTime | 否 | 比赛时间 |

```json
{
  "name": "2026 全国大学生程序设计竞赛",
  "description": "校内选拔赛",
  "organizer": "计算机学院",
  "level": "NATIONAL",
  "regStartTime": "2026-09-01T00:00:00",
  "regEndTime": "2026-09-30T23:59:59",
  "contestTime": "2026-10-20T09:00:00"
}
```

**更新请求体**：`name` / `description` / `organizer` / `level` / `regStartTime` / `regEndTime` / `contestTime`，全部可空（`semesterId` 不可改）。

**CompetitionResponse 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 竞赛 ID |
| semesterId | Long | 学期 ID |
| name | String | 竞赛名称 |
| description | String | 描述 |
| organizer | String | 主办方 |
| level | String | 级别（中文描述） |
| regStartTime / regEndTime | LocalDateTime | 报名起止 |
| contestTime | LocalDateTime | 比赛时间 |
| status | String | 状态（中文描述） |
| createTime | LocalDateTime | 创建时间 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 4,
    "semesterId": 3,
    "name": "2026 全国大学生程序设计竞赛",
    "description": "校内选拔赛",
    "organizer": "计算机学院",
    "level": "国家级",
    "regStartTime": "2026-09-01T00:00:00",
    "regEndTime": "2026-09-30T23:59:59",
    "contestTime": "2026-10-20T09:00:00",
    "status": "开放报名",
    "createTime": "2026-08-20T10:00:00"
  }
}
```

**关键规则**

- 竞赛**没有容量字段**，报名不设名额上限。
- 报名时间窗（`regStartTime` / `regEndTime`）**只在报名时校验**；`available` 只按 `status = OPEN` 过滤，不判断当前是否在窗口内，也**不排除当前学生已报名的竞赛**（前端需结合 `GET /registrations/my` 去重或置灰）。
- `status` 的 `ENDED`（已结束）只是状态值，**不阻止**录结果、传证书或改状态；服务端不校验状态迁移。
- 删除前要求没有 `PENDING` / `APPROVED` 报名，否则 409「存在未完结的报名，不可删除」。由于结果只能挂在 `APPROVED` 报名上，实际存在结果的竞赛通常无法删除；若确实执行了删除，**不会级联清理结果行与证书**（结果查询要求竞赛存在，删除后这些行不再可见）。

**错误场景**

| code | message | 触发 |
|------|---------|------|
| 400 | 竞赛名称不能为空 | 创建 / 更新名称空白 |
| 403 | 权限不足 | 非教务调用 |
| 404 | 竞赛不存在 | 更新 / 改状态 / 详情 / 删除 / 报名列表 / 结果列表 |
| 400 | 缺少必填参数: status / 参数类型错误: status | 改状态参数问题 |
| 409 | 存在未完结的报名，不可删除 | 删除时仍有 `PENDING` / `APPROVED` 报名 |

---

#### 17.4.2 竞赛报名

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/competitions/registrations` | 学生 | 报名（个人 / 团队） |
| DELETE | `/api/practice/competitions/registrations/{id}` | 学生 | 撤销（仅 `PENDING`） |
| POST | `/api/practice/competitions/registrations/{id}/review` | 教务 | 审核 |
| GET | `/api/practice/competitions/registrations/my` | 学生 | 我的报名（全量） |
| GET | `/api/practice/competitions/{competitionId}/registrations?page=&pageSize=` | 教务 | 某竞赛的报名列表 |

**报名请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| competitionId | Long | 是 | 竞赛 ID |
| teamName | String | 否 | 团队名；个人赛留空 |
| members | String | 否 | 团队成员 **user.id 逗号分隔**；个人赛留空 |

```json
{
  "competitionId": 4,
  "teamName": "Code Warriors",
  "members": "203,204,205"
}
```

> `members` 按字符串**原样存储**：服务端不校验成员是否存在、不解析、不去重。团队赛与个人赛的区分完全由 `teamName` / `members` 是否为空表达（无独立类型字段）。

**报名核心规则**

1. 竞赛必须存在且 `status = OPEN`，否则 409「竞赛未开放报名」。
2. 当前时间早于 `regStartTime` → 409「报名尚未开始」；晚于 `regEndTime` → 409「报名已截止」（空值不校验）。
3. 同一学生对同一竞赛存在 `PENDING` / `APPROVED` 报名时 409「已报名该竞赛」。
4. 报名落库为 `PENDING`；**没有容量限制**。
5. 审核仅教务；通过 / 驳回后发送站内通知（「竞赛报名审核结果」）。
6. 撤销仅限本人 `PENDING` 记录，执行逻辑删除；`APPROVED` / `REJECTED` 不可撤销。

**审核请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| approved | Boolean | 是 | `true` 通过 / `false` 驳回 |
| reviewComment | String | 否 | 审核意见 |

**RegistrationResponse 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 报名记录 ID |
| competitionId | Long | 竞赛 ID |
| competitionName | String | 竞赛名称 |
| studentId | Long | 报名人 user.id |
| studentName | String | 报名人姓名 |
| teamName | String | 团队名（个人赛 null） |
| members | String | 成员 user.id 逗号分隔（个人赛 null） |
| status | String | `待审核` / `已通过` / `已驳回` |
| registerTime | LocalDateTime | 报名时间 |
| reviewTime | LocalDateTime | 审核时间 |
| reviewComment | String | 审核意见 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 501,
    "competitionId": 4,
    "competitionName": "2026 全国大学生程序设计竞赛",
    "studentId": 203,
    "studentName": "张三",
    "teamName": "Code Warriors",
    "members": "203,204,205",
    "status": "待审核",
    "registerTime": "2026-09-10T09:00:00",
    "reviewTime": null,
    "reviewComment": null
  }
}
```

**错误场景**

| code | message | 触发 |
|------|---------|------|
| 400 | 竞赛不能为空 | 报名未传 `competitionId` |
| 404 | 竞赛不存在 | 报名 / 查看报名列表 |
| 409 | 竞赛未开放报名 | 竞赛非 `OPEN` |
| 409 | 报名尚未开始 | 早于 `regStartTime` |
| 409 | 报名已截止 | 晚于 `regEndTime` |
| 409 | 已报名该竞赛 | 已有 `PENDING` / `APPROVED` 报名 |
| 404 | 报名不存在 | 撤销 / 审核 |
| 403 | 权限不足 | 撤销他人报名 |
| 409 | 已处理的报名不可撤销 | 撤销非 `PENDING` 记录 |
| 400 | 审核结果不能为空 | `approved` 缺失 |
| 409 | 该报名已处理 | 重复审核 |

---

#### 17.4.3 竞赛结果与获奖证书

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/competitions/results` | 教务 | 录入 / 更新获奖结果（按 `registrationId` upsert） |
| DELETE | `/api/practice/competitions/results/{id}` | 教务 | 删除结果（证书引用一并释放） |
| POST | `/api/practice/competitions/results/{id}/certificate` | 教务 | 上传 / 替换获奖证书（`multipart/form-data`，详见 11.5.4） |
| GET | `/api/practice/competitions/results/{id}/certificate/download` | 教务 / 获奖学生本人 | 下载证书（详见 11.5.4） |
| GET | `/api/practice/competitions/{competitionId}/results` | 任意已登录 | 某竞赛的结果列表 |
| GET | `/api/practice/competitions/{competitionId}/results/my` | 学生 | 我的结果（无结果返回 `data: null`） |

**录入结果请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| competitionId | Long | 是 | 竞赛 ID |
| registrationId | Long | 是 | 报名记录 ID |
| award | AwardEnum | 是 | 获奖等级 |
| score | Integer | 否 | 分数 |
| comment | String | 否 | 备注 |

```json
{
  "competitionId": 4,
  "registrationId": 501,
  "award": "FIRST",
  "score": 95,
  "comment": "校赛第一名"
}
```

**规则**

1. 竞赛与报名记录都必须存在；报名记录必须属于该竞赛，否则 400「报名记录不属于该竞赛」。
2. 仅 **`APPROVED`** 报名可录结果，否则 409「报名未通过审核，不可录入结果」；**不校验竞赛状态**（`DRAFT` / `ENDED` 下同样可录）。
3. **upsert**：同一 `registrationId` 已有结果则更新 `award` / `score` / `comment`，没有则新建并写 `awardTime`。更新**不会**改变首次录入的 `awardTime`，也不会清空已上传证书。
4. 每次录入（含更新）都会发送站内通知（「竞赛结果通知」），事务提交后发送。
5. 结果列表按 `awardTime` 倒序；对任意已登录用户可见（含学生姓名与证书展示名）。
6. 删除结果会释放证书引用；竞赛删除不会级联删除结果。
7. 结果表**无数据库唯一约束**：upsert 按 `registrationId` 取首条（`LIMIT 1`），并发录入可能留下重复行。

**CompetitionResultResponse 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 结果 ID |
| competitionId | Long | 竞赛 ID |
| competitionName | String | 竞赛名称 |
| registrationId | Long | 报名记录 ID |
| studentId | Long | 获奖学生 user.id |
| studentName | String | 获奖学生姓名 |
| award | String | 获奖等级（中文描述） |
| score | Integer | 分数 |
| comment | String | 备注 |
| fileName | String | 证书存储相对路径（`objects/competition-certificate/{sha256}{ext}`，可空） |
| fileOriginal | String | 证书展示文件名（可空） |
| awardTime | LocalDateTime | 获奖录入时间 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 61,
    "competitionId": 4,
    "competitionName": "2026 全国大学生程序设计竞赛",
    "registrationId": 501,
    "studentId": 203,
    "studentName": "张三",
    "award": "一等奖",
    "score": 95,
    "comment": "校赛第一名",
    "fileName": "objects/competition-certificate/9f2c...e1.pdf",
    "fileOriginal": "获奖证书.pdf",
    "awardTime": "2026-10-25T16:00:00"
  }
}
```

**证书上传 / 下载**

- 上传：`multipart/form-data`，表单字段 `filePath` + `fileOriginal`（分片产物）与 `file` 部分（整传）**二选一，必填其一**；替换时旧证书引用被释放。仅教务。
- 下载：`GET`，教务任意；学生仅**本人**结果。证书未上传返回 404「尚未上传获奖证书」。
- 证书 biz 为 `competition-certificate`，扩展名仅 `.jpg` / `.jpeg` / `.png` / `.pdf`。完整参数与示例见 **11.5.4 竞赛获奖证书附件**。

**错误场景（结果录入 / 删除）**

| code | message | 触发 |
|------|---------|------|
| 400 | 竞赛不能为空 / 报名记录不能为空 / 获奖等级不能为空 | 必填字段缺失 |
| 404 | 竞赛不存在 | 录入结果 |
| 404 | 报名记录不存在 | 录入结果 |
| 400 | 报名记录不属于该竞赛 | `registrationId` 与 `competitionId` 不匹配 |
| 409 | 报名未通过审核，不可录入结果 | 报名非 `APPROVED` |
| 404 | 结果不存在 | 删除 / 上传证书 / 下载证书 |
| 403 | 权限不足 | 学生下载他人证书 |
| 404 | 尚未上传获奖证书 | 证书下载时 `fileName` 为空 |

---

### 17.5 社会实践

社会实践子域（`module/practice/socialpractice/`）由**教务管理员**发布项目并审核申报；学生可个人 / 团队申报，审核通过后提交实践报告。

**角色**

| 角色 | 能力 |
|------|------|
| 教务管理员 | 发布 / 更新 / 改状态 / 删除实践项目、审核申报、查看全部申报与报告、评审报告 |
| 学生 | 查看可选项目、申报 / 撤销、我的申报、提交 / 重传 / 删除报告、下载本人报告 |
| 任意已登录 | 查看实践项目详情（不校验状态） |

**枚举**

| 枚举 | code | 响应文案 |
|------|------|----------|
| `SocialPracticeStatusEnum` | `DRAFT` / `OPEN` / `CLOSED` | 草稿 / 开放 / 关闭 |
| `AuditStatusEnum` | `PENDING` / `APPROVED` / `REJECTED` | 待审核 / 已通过 / 已驳回 |
| `ReportStatusEnum` | `SUBMITTED` / `REVIEWED` | 已提交 / 已评审 |

---

#### 17.5.1 实践项目

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/social-practices` | 教务 | 发布实践项目（落 `DRAFT`） |
| PUT | `/api/practice/social-practices/{id}` | 教务 | 部分更新 |
| PUT | `/api/practice/social-practices/{id}/status?status=` | 教务 | 修改状态 |
| GET | `/api/practice/social-practices?status=&page=&pageSize=` | 教务 | 分页列表 |
| GET | `/api/practice/social-practices/{id}` | 任意已登录 | 详情 |
| GET | `/api/practice/social-practices/available` | 学生 | 可选项目（仅 `OPEN`，不排除已申报） |
| DELETE | `/api/practice/social-practices/{id}` | 教务 | 删除 |

**创建请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semesterId | Long | 否 | 缺省当前学期 |
| title | String | 是 | 实践标题（空白 400） |
| description | String | 否 | 描述 |
| organizer | String | 否 | 主办 / 组织方 |
| startTime | LocalDateTime | 否 | 开始时间 |
| endTime | LocalDateTime | 否 | 结束时间 |
| capacity | Integer | 是 | 容量，必须 > 0 |

```json
{
  "title": "2026 暑期乡村振兴社会实践",
  "description": "乡村调研与支教",
  "organizer": "校团委",
  "startTime": "2026-07-10T08:00:00",
  "endTime": "2026-07-25T18:00:00",
  "capacity": 40
}
```

**更新请求体**：`title` / `description` / `organizer` / `startTime` / `endTime` / `capacity`，全部可空；`capacity` 传值时不得小于 `selectedCount`。

**SocialPracticeResponse 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 实践项目 ID |
| semesterId | Long | 学期 ID |
| title | String | 标题 |
| description | String | 描述 |
| organizer | String | 主办方 |
| startTime / endTime | LocalDateTime | 起止时间 |
| capacity | Integer | 容量 |
| selectedCount | Integer | **已申报人数（含待审核）**，申报时即 +1 |
| status | String | 状态（中文描述） |
| createTime | LocalDateTime | 创建时间 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 9,
    "semesterId": 3,
    "title": "2026 暑期乡村振兴社会实践",
    "description": "乡村调研与支教",
    "organizer": "校团委",
    "startTime": "2026-07-10T08:00:00",
    "endTime": "2026-07-25T18:00:00",
    "capacity": 40,
    "selectedCount": 18,
    "status": "开放",
    "createTime": "2026-06-15T09:00:00"
  }
}
```

**错误场景**

| code | message | 触发 |
|------|---------|------|
| 400 | 实践标题不能为空 | 创建 / 更新标题空白 |
| 400 | 实践容量必须大于0 | 创建 / 更新容量 ≤ 0 |
| 403 | 权限不足 | 非教务调用 |
| 404 | 实践项目不存在 | 更新 / 改状态 / 详情 / 删除 / 申报列表 |
| 409 | 容量不能小于已申报人数 | 更新容量 < `selectedCount` |
| 400 | 缺少必填参数: status / 参数类型错误: status | 改状态参数问题 |
| 409 | 存在未完结的申报，不可删除 | 删除时仍有 `PENDING` / `APPROVED` 申报 |

---

#### 17.5.2 项目申报与审核

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/social-practices/applications` | 学生 | 申报（个人 / 团队） |
| DELETE | `/api/practice/social-practices/applications/{id}` | 学生 | 撤销（仅 `PENDING`） |
| POST | `/api/practice/social-practices/applications/{id}/review` | 教务 | 审核 |
| GET | `/api/practice/social-practices/applications/my` | 学生 | 我的申报（全量） |
| GET | `/api/practice/social-practices/{practiceId}/applications?page=&pageSize=` | 教务 | 某项目的申报列表 |

**申报请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| practiceId | Long | 是 | 实践项目 ID |
| teamName | String | 否 | 团队名（个人申报留空） |
| members | String | 否 | 团队成员 **user.id 逗号分隔**（个人申报留空） |
| applyReason | String | 否 | 申报理由 |

```json
{
  "practiceId": 9,
  "teamName": "乡研小队",
  "members": "203,204",
  "applyReason": "希望参与乡村调研"
}
```

> 与竞赛报名一致，`members` 按字符串原样存储，服务端不校验成员。

**申报核心规则（与实习相反）**

1. 项目必须存在且 `status = OPEN`，否则 409「实践项目未开放」（**不校验** `startTime` / `endTime`）。
2. 同一学生存在 `PENDING` / `APPROVED` 申报时 409「已申报该实践」。
3. **申报时立即占用容量**：条件更新 `selected_count = selected_count + 1 WHERE selected_count < capacity`，满员返回 409「实践已满」。
4. 审核**驳回**时释放容量（`GREATEST(selected_count - 1, 0)`）；审核**通过**不改变计数。
5. 学生撤销仅限本人 `PENDING` 记录，逻辑删除并释放容量；已处理不可撤销。
6. 审核结果发送站内通知（「社会实践申报审核结果」）。

**审核请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| approved | Boolean | 是 | `true` 通过 / `false` 驳回 |
| reviewComment | String | 否 | 审核意见 |

**SocialPracticeApplicationResponse 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 申报记录 ID |
| practiceId | Long | 实践项目 ID |
| practiceTitle | String | 项目标题 |
| studentId | Long | 申报人 user.id |
| studentName | String | 申报人姓名 |
| teamName | String | 团队名 |
| members | String | 成员 user.id 逗号分隔 |
| status | String | `待审核` / `已通过` / `已驳回` |
| applyReason | String | 申报理由 |
| applyTime | LocalDateTime | 申报时间 |
| reviewTime | LocalDateTime | 审核时间 |
| reviewComment | String | 审核意见 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 701,
    "practiceId": 9,
    "practiceTitle": "2026 暑期乡村振兴社会实践",
    "studentId": 203,
    "studentName": "张三",
    "teamName": "乡研小队",
    "members": "203,204",
    "status": "待审核",
    "applyReason": "希望参与乡村调研",
    "applyTime": "2026-06-20T10:00:00",
    "reviewTime": null,
    "reviewComment": null
  }
}
```

**错误场景**

| code | message | 触发 |
|------|---------|------|
| 400 | 实践项目不能为空 | 申报未传 `practiceId` |
| 404 | 实践项目不存在 | 申报 / 查看申报列表 |
| 409 | 实践项目未开放 | 项目非 `OPEN` |
| 409 | 已申报该实践 | 已有 `PENDING` / `APPROVED` 申报 |
| 409 | 实践已满 | 容量条件更新失败 |
| 404 | 申报不存在 | 撤销 / 审核 |
| 403 | 权限不足 | 撤销他人申报 |
| 409 | 已处理的申报不可撤销 | 撤销非 `PENDING` 记录 |
| 400 | 审核结果不能为空 | `approved` 缺失 |
| 409 | 该申报已处理 | 重复审核 |

---

#### 17.5.3 社会实践报告

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/practice/social-practice-reports` | 学生 | 提交 / 重传报告（`multipart/form-data`） |
| GET | `/api/practice/social-practice-reports/my` | 学生 | 我的报告（全量） |
| GET | `/api/practice/social-practice-reports?status=&page=&pageSize=` | 教务 | 分页列表 |
| POST | `/api/practice/social-practice-reports/{id}/review` | 教务 | 评审 |
| DELETE | `/api/practice/social-practice-reports/{id}` | 学生本人 / 教务 | 删除 |
| GET | `/api/practice/social-practice-reports/{id}/download` | 学生本人 / 教务 | 下载附件 |

**提交（multipart）**

| 部分 / 字段 | 类型 | 必填 | 说明 |
|------------|------|------|------|
| `data`（part，`application/json`） | JSON | 是 | 见下表 |
| `data.practiceId` | Long | 是 | 实践项目 ID |
| `data.title` | String | 是 | 报告标题（空白 400） |
| `data.summary` | String | 否 | 实践总结 |
| `data.filePath` | String | 与 `file` 二选一 | 分片产物路径（`objects/social-practice-report/{sha256}{ext}`） |
| `data.fileOriginal` | String | 否 | 展示文件名；留空回退产物文件名（摘要名），**建议必传** |
| `file`（part） | Binary | 与 `filePath` 二选一 | 整传文件（≤20MB） |

```bash
curl -X POST 'http://host/api/practice/social-practice-reports' \
  -H "Authorization: Bearer $TOKEN" \
  -F 'data={"practiceId":9,"title":"社会实践报告","summary":"……","filePath":"objects/social-practice-report/<sha256>.docx","fileOriginal":"社会实践报告.docx"};type=application/json'
```

**业务规则**

1. 学生必须在该项目上有 **`APPROVED`** 申报，否则 409「实践未通过审核，不可提交报告」。
2. 同一学生对同一项目**只有一份报告**（按 `practiceId + studentId` 查找）；重复提交为重传，覆盖标题 / 摘要 / 文件。
3. **重传**仅限 `SUBMITTED`（已 `REVIEWED` 再提交 409「报告已评审，不可修改」）；**删除**时学生仅限本人 `SUBMITTED`，教务不受状态限制。
4. 评审仅教务，写入 `score` / `feedback` / `reviewTime`，状态改为 `REVIEWED`，发送站内通知（「社会实践报告评审结果」）。
5. 删除：教务全权；学生仅本人且 `SUBMITTED`。逻辑删除并释放附件引用。
6. 下载：教务全权；学生本人。院系管理员无任何报告接口权限（403）。

> ⚠️ 与实习报告一致：报告表**无数据库唯一约束**，按 `practiceId + studentId` 取首条做 upsert；并发提交可能留下重复行。

**评审请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| score | Integer | 否 | 评分（无范围校验） |
| feedback | String | 否 | 反馈意见 |

**SocialPracticeReportResponse 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 报告 ID |
| practiceId | Long | 实践项目 ID |
| practiceTitle | String | 项目标题 |
| studentId | Long | 学生 user.id |
| studentName | String | 学生姓名 |
| title | String | 报告标题 |
| summary | String | 实践总结 |
| fileOriginal | String | 附件展示名（**不返回存储路径**） |
| submitTime | LocalDateTime | 提交时间 |
| score | Integer | 评分（未评审 null） |
| feedback | String | 反馈意见 |
| reviewTime | LocalDateTime | 评审时间 |
| status | String | `已提交` / `已评审` |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 71,
    "practiceId": 9,
    "practiceTitle": "2026 暑期乡村振兴社会实践",
    "studentId": 203,
    "studentName": "张三",
    "title": "社会实践报告",
    "summary": "本次实践完成了 3 个村落的调研……",
    "fileOriginal": "社会实践报告.docx",
    "submitTime": "2026-07-30T20:00:00",
    "score": null,
    "feedback": null,
    "reviewTime": null,
    "status": "已提交"
  }
}
```

**错误场景**

| code | message | 触发 |
|------|---------|------|
| 400 | 实践项目不能为空 | `data.practiceId` 缺失 |
| 400 | 报告标题不能为空 | `data.title` 空白 |
| 400 | 缺少必填请求部分: data | multipart 缺少 `data` |
| 404 | 实践项目不存在 | 提交 |
| 409 | 实践未通过审核，不可提交报告 | 无 `APPROVED` 申报 |
| 409 | 报告已评审，不可修改 | 重传已评审报告 |
| 404 | 报告不存在 | 评审 / 删除 / 下载 |
| 403 | 权限不足 | 非本人 / 非教务 |
| 409 | 该报告已评审 | 重复评审 |
| 404 | 文件不存在 | 下载时附件缺失 |

---

### 17.6 文件附件与导出（practice）

本小节覆盖 17.3–17.5 涉及的 3 类附件（实习成果报告 / 社会实践报告 / 竞赛获奖证书）。毕业设计相关的附件与导出见 11.5 及 17.1–17.2；**17.3–17.5 本身没有导出端点**。

#### 17.6.1 二选一约定

所有附件提交端点统一走 `PracticeFileSupport.resolveSubmit`：

- **小文件**：multipart 的 `file` 部分整传（业务上限 **20MB**）。
- **大文件**：不传 `file`，改传分片产物的 `filePath`（文件模块产出，`objects/{biz}/{sha256}{ext}`）；`fileOriginal` 为展示名，留空时回退为产物文件名（摘要名）。
- 两者同时给 → 400「file 与 filePath 只能二选一」；两者都不给 → 400「文件不能为空」。

| 业务 | biz code | 允许扩展名 | 整传上限 |
|------|----------|-----------|----------|
| 实习成果报告 | `internship-report` | `.doc` / `.docx` / `.pdf` / `.zip` / `.rar` | 20MB |
| 社会实践报告 | `social-practice-report` | `.doc` / `.docx` / `.pdf` / `.zip` / `.rar` | 20MB |
| 竞赛获奖证书 | `competition-certificate` | `.jpg` / `.jpeg` / `.png` / `.pdf` | 20MB |

> 分片路径不受 20MB 限制，上限取全局 `file.max-file-size`（当前 2GB）；但**上传会话初始化时**就会按同一 biz 校验扩展名。
> 整传还会受 Spring multipart 硬上限 25MB 约束：超过 25MB 由全局异常处理返回 400「上传文件过大，请压缩后重试」；
> 20–25MB 之间则命中业务校验，返回 400「文件过大，最大允许 20MB」。

#### 17.6.2 附件端点清单

| 方法 | 路径 | 权限 | 附件语义 | biz |
|------|------|------|----------|-----|
| POST | `/api/practice/internship-reports` | 学生 | `data`（JSON part）+ 可选 `file` part；`data.filePath` 与 `file` 二选一 | `internship-report` |
| GET | `/api/practice/internship-reports/{id}/download` | 学生本人 / 院系（本人负责）/ 教务 | 下载 | - |
| POST | `/api/practice/social-practice-reports` | 学生 | `data`（JSON part）+ 可选 `file` part；`data.filePath` 与 `file` 二选一 | `social-practice-report` |
| GET | `/api/practice/social-practice-reports/{id}/download` | 学生本人 / 教务 | 下载 | - |
| POST | `/api/practice/competitions/results/{id}/certificate` | 教务 | 表单字段 `filePath` / `fileOriginal` 与 `file` part 二选一；重复上传为替换 | `competition-certificate` |
| GET | `/api/practice/competitions/results/{id}/certificate/download` | 教务 / 获奖学生本人 | 下载 | - |

> 证书两个端点已在 **11.5.4** 记录，此处只列清单；上传/替换语义：旧证书引用释放，新文件落库，响应对应更新后的结果视图。

#### 17.6.3 下载行为

业务下载端点均为 `GET`，返回二进制流：

| 响应头 / 行为 | 值 |
|--------------|-----|
| `Content-Disposition` | `attachment; filename*=UTF-8''<RFC5987 编码的展示名>` |
| `Accept-Ranges` | `bytes`（支持 `Range` 请求，命中返回 **206**） |
| 强 `ETag` | 内容寻址产物取磁盘文件名的 sha256（`"{sha256}"`）；legacy 文件无摘要，不输出 ETag |
| `Last-Modified` | 文件修改时间 |
| `Content-Type` | 按磁盘文件名的扩展名映射（pdf/doc/docx/zip/rar/图片等），未知回退 `application/octet-stream` |

> ⚠️ 下载端点的**业务错误仍返回统一 JSON**（HTTP 200 + `body.code`），不是二进制流：
> 报告不存在 404、权限不足 403、附件缺失 404「文件不存在」、存储路径非法 400「非法的文件路径」。
> 只有鉴权失败是真正的 HTTP 401 / 403（拦截器写出）。

#### 17.6.4 附件错误清单

| code | message | 触发 |
|------|---------|------|
| 400 | file 与 filePath 只能二选一 | 同时传整传文件与分片产物 |
| 400 | 文件不能为空 | 两者都没传 |
| 400 | 非法的文件路径: {path} | 路径穿越 / 分片产物文件名不符合 `{sha256}{ext}`（`bind` 带路径后缀；`resolve` 报「非法的文件路径」） |
| 403 | 文件不属于该业务目录: {path} | `filePath` 的 biz 段与端点不匹配（如把证书传到报告端点） |
| 404 | 文件不存在 | 产物已被清理或路径无效 |
| 400 | 不支持的文件格式: {ext} | 扩展名不在该 biz 白名单（无扩展名时显示「（无扩展名）」） |
| 400 | 文件过大，最大允许 20MB | 整传超过业务上限 |
| 400 | 上传文件过大，请压缩后重试 | 超过 multipart 硬上限 25MB（全局兜底） |

#### 17.6.5 文件生命周期

- 业务删除 / 重传时调用 `release` **释放引用**，而不是立即删盘。
- 内容寻址产物（`objects/...`）按内容摘要命名，同一文件可能被多行引用，**绝不即时删除**；由 `FileMaintenanceTask`
  经 `PracticeFileUsageContributor` 对账（实习报告 / 社会实践报告 / 竞赛结果等 9 张表的 `file_name`），
  在「无引用 + 宽限期（`file.orphan-grace-hours`）」后回收。
- legacy 文件（无 `objects/` 前缀的旧 UUID 名）与业务行 1:1，可安全即时删除；迁移收敛条件见根目录《文件上传下载接口文档.md》。

**导出**：本小节涉及的三类业务均无导出端点。practice 的导出能力集中在毕业设计子域
（查重数据包、看板、答辩成绩），传输层说明见 11.5.5。

---

## 18. 其它模块

本节收录不属于前述业务域、但前端会直接调用的通用端点：用户个性化偏好与前端日志上报。

### 18.1 用户偏好

任何已登录用户只能读写/重置**自己的**偏好（路径固定为 `/me`，没有跨用户或管理员接口）。偏好是一个**无固定 schema 的顶层 JSON 对象**，服务端不校验键名与取值——没有 `theme` / `language` 等枚举，也**不提供任何服务端默认值**（前端需自行对缺失 key 应用默认主题/语言）。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/preferences/me` | 任意登录用户 | 读取本人偏好 |
| PUT | `/api/preferences/me` | 任意登录用户 | 顶层浅合并本人偏好 |
| DELETE | `/api/preferences/me` | 任意登录用户 | 重置本人偏好（物理删除） |

存储：`user_preference` 表，`prefs` 为 JSON 列，一个用户一行（应用层保证；数据库只有 `user_id` 普通索引，无唯一约束）。

#### 18.1.1 读取偏好

```
GET /api/preferences/me
Authorization: Bearer <accessToken>
```

**响应示例（已有偏好）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "theme": "dark",
    "language": "zh-CN",
    "layout": {
      "sidebarCollapsed": false
    }
  }
}
```

**响应示例（从未保存 / 刚重置）**

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

> 返回的就是用户保存的原始键值，服务端不注入任何默认字段；`data` 为 `{}` 表示「使用系统默认（由前端决定）」。

**错误场景**

| code | message |
|------|---------|
| 401 | 请先登录 / token已过期，请刷新 / token无效（真实 HTTP 401） |

#### 18.1.2 浅合并偏好

```
PUT /api/preferences/me
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**请求体**：任意 JSON 对象（顶层 `Map<String, Object>`）。合并规则：

| 情形 | 行为 |
|------|------|
| key 已存在 | 用新值覆盖 |
| key 不存在 | 新增该 key |
| value 为 `null` | 删除该 key |
| 请求体未提及的 key | 保持不变 |
| value 是嵌套对象 | **整体替换**，不做深合并 |

**请求示例**

```json
{
  "theme": "light",
  "notifications": null,
  "layout": {
    "sidebarCollapsed": true
  }
}
```

若原偏好为：

```json
{
  "theme": "dark",
  "language": "zh-CN",
  "notifications": true,
  "layout": {
    "sidebarCollapsed": false,
    "density": "compact"
  }
}
```

合并结果为：

```json
{
  "theme": "light",
  "language": "zh-CN",
  "layout": {
    "sidebarCollapsed": true
  }
}
```

（`notifications` 被删除；`layout` 被整体替换，原 `density` 丢失。）

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "theme": "light",
    "language": "zh-CN",
    "layout": {
      "sidebarCollapsed": true
    }
  }
}
```

**限制与错误场景**

| code | message | 触发条件 |
|------|---------|---------|
| 400 | 请求体格式错误 | 空请求体、字面量 `null`、JSON 语法错误，或请求体不是 JSON 对象（如数组/标量） |
| 400 | 偏好数据过大 | 合并后的完整偏好序列化后超过 64KB（65536 字节） |
| 400 | 偏好数据序列化失败 | 合并结果无法序列化为 JSON（正常 HTTP 入参不会触发，属防御分支） |
| 401 | 请先登录 / token已过期，请刷新 / token无效 | 真实 HTTP 401 |

> 大小限制衡量的是**合并后的完整偏好**，不是本次 delta 的大小——已有数据接近上限时，一个小增量也可能触发「偏好数据过大」。
>
> 首次调用（库中无行）即使传 `{}` 也会落一行空偏好。数据库没有 `(user_id)` 唯一约束，两个「首次 PUT」并发时可能产生两行；读取侧 `getOne(..., false)` 取首行兜底，不会报错。
>
> 服务层另有防御性校验「请求体不能为空」，正常 HTTP 调用会在参数绑定阶段先被 400「请求体格式错误」拦截。

#### 18.1.3 重置偏好

```
DELETE /api/preferences/me
Authorization: Bearer <accessToken>
```

**物理删除**该用户的偏好行（非逻辑删除），回到「无偏好」状态。幂等：没有偏好行时同样返回成功。

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

重置后再次 `GET /me` 返回 `{}`。

**错误场景**

| code | message |
|------|---------|
| 401 | 请先登录 / token已过期，请刷新 / token无效（真实 HTTP 401） |

### 18.2 前端日志

前端错误/页面访问/手动埋点的批量上报端点。为让**登录页的报错也能上报**，该路径在 `WebMvcConfig` 中被显式排除出 `AuthInterceptor`（`excludePathPatterns("/api/front-logs")`），**无需登录、无需 `@RequireAuth`**，携带了 Authorization 也不会被解析。

```
POST /api/front-logs
Content-Type: application/json
```

**请求体（Body）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| logs | Array\<FrontendLogItem\> | 否 | 日志批次；`null`/缺省视为空批。服务端只处理**前 20 条**，超出部分静默丢弃 |

**FrontendLogItem 字段说明**（所有字段均可空，缺失不拒绝）

| 字段 | 类型 | 截断上限 | 说明 |
|------|------|----------|------|
| level | String | — | `error` → 按 error 级别落盘；`warn` → 按 warn 级别；其它值（含 `null`、大写 `ERROR`）→ info |
| type | String | 30 | 日志类型（`js-error` / `unhandled-rejection` / `vue-error` / `api-error` / `page-view` / `manual` 等，服务端不校验取值） |
| message | String | 1000 | 日志消息；空/缺省显示为 `-` |
| stack | String | 8000 | 错误堆栈；空/缺省则不输出该段 |
| page | String | 500 | 发生时的前端路由 `fullPath`；空/缺省显示为 `-` |
| api | Object | 500 | `api-error` 的接口上下文（method / path / status / code / durationMs），序列化后截断 |
| breadcrumbs | Array\<Object\> | 4000 | 错误前的行为痕迹（route / api / click），序列化后截断；空数组不输出 |
| client | Object | 4000 | 客户端环境（version / ua / locale / viewport），序列化后截断 |
| userId | Long | — | 客户端上报的用户 id；`null` 时日志显示「匿名」，此时 `userType` 不输出 |
| userType | String | 20 | 仅 `userId` 非空时输出 |
| occurTime | Long | — | 客户端毫秒时间戳，服务端按服务器时区格式化为 `yyyy-MM-dd HH:mm:ss.SSS`；缺省不输出 |
| count | Integer | — | 前端去重聚合的重复次数；大于 1 时输出「重复:N」 |

**请求示例**

```json
{
  "logs": [
    {
      "level": "error",
      "type": "api-error",
      "message": "接口请求失败",
      "stack": "Error: Request failed with status code 500\n    at request.js:42",
      "page": "/course/12",
      "api": {
        "method": "GET",
        "path": "/api/courses/12",
        "status": 500,
        "code": 500,
        "durationMs": 1200
      },
      "breadcrumbs": [
        { "type": "route", "to": "/course/12" },
        { "type": "click", "target": "btn-refresh" }
      ],
      "client": {
        "version": "1.2.0",
        "locale": "zh-CN"
      },
      "userId": 6,
      "userType": "student",
      "occurTime": 1790000000000,
      "count": 3
    },
    {
      "level": "warn",
      "type": "page-view",
      "message": "进入登录页",
      "page": "/login",
      "occurTime": 1790000000123
    }
  ]
}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**落盘格式**（每批每条一行，字段以 ` | ` 分隔）

```
前端日志 | 类型:api-error | 用户[userId=6, userType=student] | IP:203.0.113.7 | 页面:/course/12 | API:{"method":"GET","path":"/api/courses/12","status":500,"code":500,"durationMs":1200} | 消息:接口请求失败 | 堆栈:Error: Request failed with status code 500\n    at request.js:42 | 面包屑:[{"type":"route","to":"/course/12"},{"type":"click","target":"btn-refresh"}] | 客户端:{"version":"1.2.0","locale":"zh-CN"} | 重复:3 | 发生时间:2026-11-24 10:30:00.123
```

**落盘行为**

- 专用 logger `frontend-log`（`additivity=false`），只进 `logs/yyyy/MM/dd/frontend.N.log`（默认 `./logs`，可用 `logging.file.path` 覆盖）；单文件 50MB 滚动，保留 90 天，总容量上限 3GB。
- 所有客户端可控文本（type / userType / page / message / stack / breadcrumbs / client）服务端强制截断，超出部分以 `...(截断)` 结尾。
- 文本中的 `\r` / `\n` 转义为字面量 `\r` / `\n`，保证一条日志一行、防日志行伪造。
- 单条格式化失败只记一条内部 warn，不影响批次内其它条目，HTTP 仍返回成功。
- IP 解析顺序：`X-Forwarded-For` 第一跳 → `X-Real-IP` → `remoteAddr`。

**错误场景**

| code | message | 触发条件 |
|------|---------|---------|
| 400 | 请求体格式错误 | 空请求体、JSON 语法错误，或 `logs` 不是数组 |
| 429 | 请求过于频繁，请稍后再试 | 日志上报档限流（独立计数桶，按客户端 IP 300 次/分钟） |
| 500 | 服务器内部错误，请联系管理员 | 其它未预期异常 |

> **安全与使用注意**：该端点免登录，且 `userId` / `userType` 以客户端上报值为准（可伪造），仅作排查线索，不能用于鉴权或审计。
>
> 限流档位为日志上报专用档（`/api/front-logs` 前缀，默认 300 次/分钟）：由于鉴权拦截器不执行，请求上下文中没有 userId，计数维度恒为客户端 IP。超出阈值返回 HTTP 200 + body `code=429`。
>
> 批次超过 20 条时没有错误提示，多的条目直接丢弃——前端应自行按 20 条分批上报。

---

## 19. 附录

### 19.1 完整调用流程示例

```bash
# 1. 注册（需已登录：先由管理员账号登录拿到 accessToken）
curl -X POST http://localhost:8080/api/register \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <adminAccessToken>" \
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

### 19.2 token 有效期配置

```yaml
jwt:
  secret: <Base64 密钥>
  access-token-expiration: 30m    # accessToken 有效期
  refresh-token-expiration: 7d    # refreshToken / session 有效期
```

### 19.3 密码安全

- 算法：**PBKDF2WithHmacSHA256**
- 迭代次数：**100,000**
- 密钥长度：**256 bit**
- 存储格式：`iterations:salt:hash`
- 注册时 `EncryptUtils.hashWithPbkdf2()` 生成密文
- 登录时 `EncryptUtils.verifyPbkdf2()` 做恒定时间比对
