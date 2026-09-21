package com.xrq.xxq.module.coursework.video.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.module.coursework.video.dto.VideoSaveRequest;
import com.xrq.xxq.module.coursework.video.dto.VideoUpdateRequest;
import com.xrq.xxq.module.coursework.video.dto.VideoView;

/** 教学视频服务：登记（二选一入口）、元数据编辑、软删、列表。播放走 WS，不在本服务内。 */
public interface VideoService {

    VideoView register(Long teacherUserId, VideoSaveRequest req, MultipartFile file);

    VideoView update(Long teacherUserId, Long id, VideoUpdateRequest req);

    /** 软删；文件为内容寻址产物，不即时删，交 FileMaintenanceTask 回收。 */
    void delete(Long teacherUserId, Long id);

    /** 教师列表（归属校验）。 */
    List<VideoView> listForTeacher(Long teacherUserId, Long teachInfoId);

    /** 学生列表（可见性校验）。 */
    List<VideoView> listForStudent(Long studentUserId, Long teachInfoId);

    /** 元数据（WS 播放前置；教师归属 / 学生可见）。 */
    VideoView detail(Long userId, String userType, Long id);
}
