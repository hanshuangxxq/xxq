package com.xrq.xxq.module.mojor.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.mojor.entity.Major;

/**
 * 专业服务。
 * <p>
 * 专业是「院系 → 专业 → 班级 → 学生」归属链的第二层：往上必须挂院系（{@code college_id}），
 * 往下被班级引用（{@code class_name.major_id}）。这两端任一断裂，其下学生就会失去院系归属
 * 并在院系视图中消失，故写入口径在此集中校验。
 */
public interface MajorService extends IService<Major> {

    /** 新增专业：专业名称与所属院系均必填，院系须存在。 */
    Major create(Major major);

    /** 修改专业：专业不存在抛 404；传了 collegeId 则须存在（传 null 按 MyBatis Plus 策略保持原值）。 */
    Major update(Long id, Major major);

    /** 删除专业：该专业下仍有班级时抛 409（班级被删则其学生一并失去专业与院系）。 */
    void delete(Long id);
}
