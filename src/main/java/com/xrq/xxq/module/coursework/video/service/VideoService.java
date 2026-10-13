package com.xrq.xxq.module.coursework.video.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.module.coursework.video.dto.VideoSaveRequest;
import com.xrq.xxq.module.coursework.video.dto.VideoUpdateRequest;
import com.xrq.xxq.module.coursework.video.dto.VideoView;

/** 教学视频服务：登记（挂节，二选一入口）、元数据编辑/改挂、软删、公共区列表。播放走 WS。 */
public interface VideoService {

    VideoView register(Long teacherUserId, VideoSaveRequest req, MultipartFile file);

    VideoView update(Long teacherUserId, Long id, VideoUpdateRequest req);

    /** 软删；文件为内容寻址产物，不即时删，交 FileMaintenanceTask 回收。 */
    void delete(Long teacherUserId, Long id);

    /** 元数据（WS 播放前置；教师归属 / 学生可见）。 */
    VideoView detail(Long userId, String userType, Long id);

    /** 未挂节视频列表(公共区;含孤儿兜底。教师查归属 / 学生查可见)。 */
    List<VideoView> listCommon(Long userId, String userType, Long teachInfoId);

    /** 未挂节视频数(含孤儿兜底;供资料根层 browse 的 commonVideoCount)。 */
    long countCommon(Long anchorId);
}
