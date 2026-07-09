package com.xrq.xxq.module.scheduling.service.impl;

import java.time.DayOfWeek;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.time.entity.Time;
import com.xrq.xxq.module.time.entity.TimeRestriction;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.local.mapper.LocalMapper;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.time.mapper.TimeMapper;
import com.xrq.xxq.module.time.mapper.TimeRestrictionMapper;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.teachinfo.service.TeachInfoService;
import com.xrq.xxq.module.scheduling.cache.DraftCacheManager;
import com.xrq.xxq.module.scheduling.cache.DraftItem;
import com.xrq.xxq.module.scheduling.domain.CourseSchedule;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.scheduling.domain.Lesson;
import com.xrq.xxq.module.scheduling.domain.Room;
import com.xrq.xxq.module.scheduling.domain.StudentGroup;
import com.xrq.xxq.module.scheduling.domain.Timeslot;
import com.xrq.xxq.module.scheduling.service.SchedulingService;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolutionUpdatePolicy;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 排课服务实现。
 * <p>
 * 核心流程：
 * <ol>
 *   <li>前端通过草稿缓存提交授课安排（不写库）</li>
 *   <li>触发排课时，草稿批量入库获取ID，组装排课问题</li>
 *   <li>委托 Timefold {@link SolverManager} 异步求解</li>
 *   <li>每次找到更优解时，将分配结果写回 teach_info 表</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingServiceImpl implements SchedulingService {

    private final SolverManager<CourseSchedule> solverManager;
    private final SolutionManager<CourseSchedule, HardSoftScore> solutionManager;

    private final TeachInfoService teachInfoService;
    private final SemesterService semesterService;
    private final DraftCacheManager draftCacheManager;

    private final TeachInfoMapper teachInfoMapper;
    private final TimeMapper timeMapper;
    private final LocalMapper localMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final ClassNameMapper classNameMapper;
    private final TimeRestrictionMapper timeRestrictionMapper;

    private final Map<Long, CourseSchedule> solutionCache = new ConcurrentHashMap<>();

    @Override
    public Long solve(Long semesterId) {
        List<DraftItem> drafts = draftCacheManager.getAllDrafts();
        if (drafts.isEmpty()) {
            throw new BusinessException(400, "没有待排课的授课草稿");
        }

        Semester semester;
        if (semesterId != null) {
            semester = semesterService.getById(semesterId);
            if (semester == null) {
                throw new BusinessException(404, "学期不存在: " + semesterId);
            }
        } else {
            // 优先从草稿中获取学期ID，草稿无学期ID时才回退到当前学期
            semesterId = drafts.stream()
                    .map(DraftItem::getSemesterId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (semesterId != null) {
                semester = semesterService.getById(semesterId);
                if (semester == null) {
                    throw new BusinessException(404, "学期不存在: " + semesterId);
                }
            } else {
                semester = semesterService.getCurrent();
                if (semester == null) {
                    throw new BusinessException(400, "没有当前学期数据，请先设置学期或指定 semesterId");
                }
            }
        }

        // 保存草稿并收集 DB 生成的 ID，确保后续查询一致
        List<TeachInfo> saved = new ArrayList<>();
        for (DraftItem draft : drafts) {
            TeachInfo ti = draft.toTeachInfo();
            if (ti.getSemesterId() == null) {
                ti.setSemesterId(semester.getId());
            }
            if (ti.getStartWeek() == null) {
                ti.setStartWeek(semester.getStartWeek());
            }
            if (ti.getEndWeek() == null) {
                ti.setEndWeek(semester.getEndWeek());
            }
            if (ti.getStartWeek() < semester.getStartWeek() || ti.getEndWeek() > semester.getEndWeek()) {
                throw new BusinessException(400,
                        "课程周次范围超出学期范围（" + semester.getStartWeek() + "-" + semester.getEndWeek() + "周）");
            }
            teachInfoService.save(ti);
            saved.add(ti);
        }
        List<Long> savedIds = saved.stream().map(TeachInfo::getId).toList();
        log.info("草稿已入库, count={}", saved.size());

        Long scheduleId = System.currentTimeMillis();
        CourseSchedule problem = buildProblem(scheduleId, saved);
        solutionCache.put(scheduleId, problem);

        solverManager.solveBuilder()
                .withProblemId(scheduleId)
                .withProblemFinder(id -> buildProblem((Long) id, teachInfoMapper.selectByIds (savedIds)))
                .withBestSolutionEventConsumer(event -> saveSolution(event.solution()))
                .run();

        // 排课求解成功启动后才清空草稿箱，防止中途失败导致 Redis 数据丢失
        draftCacheManager.clear();

        log.info("排课求解已启动, scheduleId={}, lessons={}, timeslots={}, rooms={}",
                scheduleId, problem.getLessonList().size(),
                problem.getTimeslotList().size(), problem.getRoomList().size());
        return scheduleId;
    }

    @Override
    public CourseSchedule getSolution(Long scheduleId) {
        CourseSchedule solution = solutionCache.get(scheduleId);
        if (solution == null) {
            return null;
        }

        SolverStatus status = solverManager.getSolverStatus(scheduleId);
        if (status == SolverStatus.NOT_SOLVING) {
            solution.setSolverStatus("FINISHED");
            solutionManager.update(solution, SolutionUpdatePolicy.UPDATE_SCORE_ONLY);
            if (solution.getScore() != null && !solution.getScore().isFeasible()) {
                throw new BusinessException(500, "无法完成排课：当前时间限制下无法为所有课程分配合适的时间段，请调整时间限制或减少课程数量");
            }
        } else {
            solution.setSolverStatus(status.name());
        }
        return solution;
    }

    @Override
    public void stopSolving(Long scheduleId) {
        solverManager.terminateEarly(scheduleId);
        log.info("排课求解已终止, scheduleId={}", scheduleId);
    }

    // ────────────────────────── 问题构建 ──────────────────────────

    private CourseSchedule buildProblem(Long scheduleId, List<TeachInfo> teachInfos) {
        List<Timeslot> timeslots = buildTimeslots();
        List<Room> rooms = buildRooms();
        Map<String, StudentGroup> classGroups = buildStudentGroups(teachInfos);
        Map<Long, String> courseNames = loadCourseNames();
        Map<Long, String> teacherNames = loadTeacherNames();

        CourseSchedule schedule = new CourseSchedule();
        schedule.setId(scheduleId);
        schedule.setTimeslotList(timeslots);
        schedule.setRoomList(rooms);
        schedule.setStudentGroupList(new ArrayList<>(classGroups.values()));
        schedule.setLessonList(buildLessons(teachInfos, courseNames, teacherNames, classGroups, timeslots, rooms));
        schedule.setSolverStatus("NOT_SOLVING");
        return schedule;
    }

    /**
     * 从草稿直接构建排课问题（草稿已含课程名和教师名，无需查库）。
     */
    private CourseSchedule buildProblemFromDrafts(Long scheduleId, List<DraftItem> drafts) {
        List<TeachInfo> teachInfos = drafts.stream().map(DraftItem::toTeachInfo).toList();
        List<Timeslot> timeslots = buildTimeslots();
        List<Room> rooms = buildRooms();
        Map<String, StudentGroup> classGroups = buildStudentGroups(teachInfos);

        Map<Long, String> courseNames = drafts.stream()
                .collect(Collectors.toMap(DraftItem::getCourseId, DraftItem::getCourseName, (a, b) -> a));
        Map<Long, String> teacherNames = drafts.stream()
                .collect(Collectors.toMap(DraftItem::getTeacherId, DraftItem::getTeacherName, (a, b) -> a));

        CourseSchedule schedule = new CourseSchedule();
        schedule.setId(scheduleId);
        schedule.setTimeslotList(timeslots);
        schedule.setRoomList(rooms);
        schedule.setStudentGroupList(new ArrayList<>(classGroups.values()));
        schedule.setLessonList(buildLessons(teachInfos, courseNames, teacherNames, classGroups, timeslots, rooms));
        schedule.setSolverStatus("NOT_SOLVING");
        return schedule;
    }

    /**
     * 为每个 Time 记录 × 每周7天，生成 Timeslot。
     * ID 编码 = timeId * 10 + dayOfWeek，求解后反向拆解写回 teach_info。
     */
    private List<Timeslot> buildTimeslots() {
        List<TimeRestriction> restrictions = timeRestrictionMapper.selectList(null);
        Map<String, TimeRestriction> restrictionMap = restrictions.stream()
                .collect(Collectors.toMap(
                        r -> r.getTimeId() + "_" + r.getDayOfWeek(),
                        r -> r,
                        (a, b) -> a));

        List<Time> times = timeMapper.selectList(null);
        List<Timeslot> timeslots = new ArrayList<>(times.size() * 7);
        for (Time time : times) {
            for (int dow = 1; dow <= 7; dow++) {
                String key = time.getId() + "_" + dow;
                TimeRestriction restriction = restrictionMap.get(key);

                if (restriction != null && "BLOCKED".equals(restriction.getRestrictionType())) {
                    continue;
                }

                Long reservedCourseId = null;
                if (restriction != null && "RESERVED".equals(restriction.getRestrictionType())) {
                    reservedCourseId = restriction.getCourseId();
                }

                timeslots.add(new Timeslot(
                        time.getId() * 10 + dow,
                        DayOfWeek.of(dow),
                        time.getStartPeriod(),
                        time.getEndPeriod(),
                        reservedCourseId));
            }
        }
        return timeslots;
    }

    /** 将 local 表记录转换为 Room 问题事实。max 为 null 时用 Integer.MAX_VALUE 表示无限制。 */
    private List<Room> buildRooms() {
        return localMapper.selectList(null).stream()
                .map(local -> {
                    int capacity = local.getMax() != null && local.getMax() > 0
                            ? local.getMax()
                            : Integer.MAX_VALUE;
                    return new Room(local.getId(), local.getBuilding(), local.getClassRoom(), capacity);
                })
                .toList();
    }

    /**
     * 从授课草稿中提取所有唯一班级名称，构建 StudentGroup。
     * 通过 student 表统计每个班级的学生人数，用于教室容量约束。
     */
    private Map<String, StudentGroup> buildStudentGroups(List<TeachInfo> teachInfos) {
        Map<Long, Long> classStudentCount = studentMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(Student::getClassId, Collectors.counting()));

        List<ClassName> allClasses = classNameMapper.selectList(null);
        Map<String, Long> classNameToClassId = allClasses.stream()
                .collect(Collectors.toMap(
                        cn -> cn.getClassName(),
                        cn -> cn.getId(),
                        (a, b) -> a));
        Map<String, String> classNameToCollege = allClasses.stream()
                .collect(Collectors.toMap(
                        ClassName::getClassName,
                        cn -> cn.getCollege() != null ? cn.getCollege() : "",
                        (a, b) -> a));

        Map<String, StudentGroup> groups = new LinkedHashMap<>();
        long id = 1;
        for (TeachInfo ti : teachInfos) {
            String raw = ti.getClassName();
            if (raw == null || raw.isBlank()) continue;
            for (String name : raw.split(",")) {
                name = name.strip();
                if (!name.isEmpty() && !groups.containsKey(name)) {
                    Long classId = classNameToClassId.get(name);
                    int count = classId != null
                            ? classStudentCount.getOrDefault(classId, 0L).intValue()
                            : 0;
                    String college = classNameToCollege.getOrDefault(name, "");
                    groups.put(name, new StudentGroup(id++, name, college, count));
                }
            }
        }
        return groups;
    }

    /** 将授课草稿转换为 Lesson 规划实体。 */
    private List<Lesson> buildLessons(List<TeachInfo> teachInfos,
                                       Map<Long, String> courseNames,
                                       Map<Long, String> teacherNames,
                                       Map<String, StudentGroup> classGroups,
                                       List<Timeslot> timeslots,
                                       List<Room> rooms) {
        Map<Long, Timeslot> timeslotById = timeslots.stream()
                .collect(Collectors.toMap(Timeslot::getId, t -> t, (a, b) -> a));
        Map<Long, Room> roomById = rooms.stream()
                .collect(Collectors.toMap(Room::getId, r -> r, (a, b) -> a));

        List<Lesson> lessons = new ArrayList<>(teachInfos.size());
        for (TeachInfo ti : teachInfos) {
            Timeslot initialTimeslot = null;
            if (ti.getTimeId() != null && ti.getDayOfWeek() != null) {
                initialTimeslot = timeslotById.get(ti.getTimeId() * 10 + ti.getDayOfWeek());
            }
            Room initialRoom = null;
            if (ti.getLocalId() != null) {
                initialRoom = roomById.get(ti.getLocalId());
            }

            List<StudentGroup> groups = new ArrayList<>();
            String raw = ti.getClassName();
            if (raw != null && !raw.isBlank()) {
                for (String name : raw.split(",")) {
                    StudentGroup sg = classGroups.get(name.strip());
                    if (sg != null) {
                        groups.add(sg);
                    }
                }
            }

            lessons.add(new Lesson(
                    ti.getId(),
                    ti.getCourseId(),
                    courseNames.getOrDefault(ti.getCourseId(), "未知课程"),
                    ti.getTeacherId(),
                    teacherNames.getOrDefault(ti.getTeacherId(), "未知教师"),
                    groups,
                    ti.getStartWeek(),
                    ti.getEndWeek(),
                    ti.getSemesterId(),
                    initialTimeslot,
                    initialRoom));
        }
        return lessons;
    }

    private Map<Long, String> loadCourseNames() {
        return courseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Course::getId, Course::getCourseName));
    }

    /**
     * 加载教师ID → 教师姓名的映射。
     * teach_info.teacher_id → teacher.id → teacher.userId → user.name。
     */
    private Map<Long, String> loadTeacherNames() {
        List<User> users = userMapper.selectList(null);
        Map<Long, String> userIdToName = users.stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

        return teacherMapper.selectList(null).stream()
                .collect(Collectors.toMap(
                        Teacher::getId,
                        t -> userIdToName.getOrDefault(t.getUserId(), "未知")));
    }

    // ────────────────────────── 结果持久化 ──────────────────────────

    /**
     * 每次找到更优解时回调：将分配结果写回 teach_info 表并更新缓存。
     */
    private void saveSolution(CourseSchedule solution) {
        Long scheduleId = solution.getId();
        solution.setSolverStatus("SOLVING");
        solutionCache.put(scheduleId, solution);

        int assignedCount = 0;
        for (Lesson lesson : solution.getLessonList()) {
            Timeslot ts = lesson.getTimeslot();
            Room room = lesson.getRoom();
            if (ts == null && room == null) {
                continue;
            }

            LambdaUpdateWrapper<TeachInfo> uw = new LambdaUpdateWrapper<>();
            uw.eq(TeachInfo::getId, lesson.getId());
            if (ts != null) {
                uw.set(TeachInfo::getDayOfWeek, ts.getDayOfWeek().getValue());
                uw.set(TeachInfo::getTimeId, ts.getId() / 10);
            }
            if (room != null) {
                uw.set(TeachInfo::getLocalId, room.getId());
            }
            if (lesson.getStartWeek() != null) {
                uw.set(TeachInfo::getStartWeek, lesson.getStartWeek());
            }
            if (lesson.getEndWeek() != null) {
                uw.set(TeachInfo::getEndWeek, lesson.getEndWeek());
            }
            teachInfoMapper.update(null, uw);
            assignedCount++;
        }

        log.debug("排课解已更新, scheduleId={}, score={}, assigned={}/{}",
                scheduleId, solution.getScore(), assignedCount, solution.getLessonList().size());
    }
}
