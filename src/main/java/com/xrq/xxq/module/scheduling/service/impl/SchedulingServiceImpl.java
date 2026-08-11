package com.xrq.xxq.module.scheduling.service.impl;

import java.time.DayOfWeek;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.selection.entity.SelectionClass;
import com.xrq.xxq.module.selection.entity.SelectionClassMember;
import com.xrq.xxq.module.selection.mapper.SelectionClassMapper;
import com.xrq.xxq.module.selection.mapper.SelectionClassMemberMapper;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.time.entity.Time;
import com.xrq.xxq.module.time.entity.TimeRestriction;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.college.mapper.CollegeMapper;
import com.xrq.xxq.module.course.service.CourseInfoResolver;
import com.xrq.xxq.module.local.entity.LocalTypeEnum;
import com.xrq.xxq.module.local.mapper.LocalMapper;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.time.mapper.TimeMapper;
import com.xrq.xxq.module.time.mapper.TimeRestrictionMapper;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.teachinfo.cache.ClassScheduleCacheManager;
import com.xrq.xxq.module.scheduling.cache.DraftCacheManager;
import com.xrq.xxq.module.scheduling.cache.DraftItem;
import com.xrq.xxq.module.scheduling.domain.CourseSchedule;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.scheduling.domain.Lesson;
import com.xrq.xxq.module.scheduling.domain.Room;
import com.xrq.xxq.module.scheduling.domain.StudentGroup;
import com.xrq.xxq.module.scheduling.domain.Timeslot;
import com.xrq.xxq.module.scheduling.service.SchedulingService;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.util.TeacherNameResolver;

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

    private final SemesterService semesterService;
    private final DraftCacheManager draftCacheManager;
    private final ClassScheduleCacheManager classScheduleCacheManager;

    private final TeachInfoMapper teachInfoMapper;
    private final TimeMapper timeMapper;
    private final LocalMapper localMapper;
    private final CourseInfoResolver courseInfoResolver;
    private final StudentMapper studentMapper;
    private final ClassNameMapper classNameMapper;
    private final CollegeMapper collegeMapper;
    private final TimeRestrictionMapper timeRestrictionMapper;
    private final SelectionClassMapper selectionClassMapper;
    private final SelectionClassMemberMapper selectionClassMemberMapper;
    private final TeacherNameResolver teacherNameResolver;

    private final Map<Long, CourseSchedule> solutionCache = new ConcurrentHashMap<>();

    @Override
    public Long solve(Long semesterId) {
        List<DraftItem> drafts = draftCacheManager.getAllDrafts();
        if (drafts.isEmpty()) {
            throw new BusinessException(400, "没有待排课的授课草稿");
        }

        // 排课会改写 teach_info 的时段/教室，先清空课表缓存避免脏读
        classScheduleCacheManager.clearAll();

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
        // 直接走 mapper 跳过 TeachInfoServiceImpl 重写的逐条缓存淘汰（开头已 clearAll，避免 N 次 evict）
        // 选课分班产生的草稿已入库（id 不为 null），用 updateById 避免主键冲突；新草稿用 insert 分配 id
        // 已存在 id 一次性批量查出（替代循环内逐条 selectById）
        List<Long> draftIds = drafts.stream().map(DraftItem::toTeachInfo)
                .map(TeachInfo::getId).filter(Objects::nonNull).distinct().toList();
        Set<Long> existingIds = draftIds.isEmpty()
                ? Set.of()
                : teachInfoMapper.selectByIds(draftIds).stream()
                        .map(TeachInfo::getId).collect(Collectors.toSet());
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
            if (ti.getId() != null && existingIds.contains(ti.getId())) {
                teachInfoMapper.updateById(ti);
            } else {
                teachInfoMapper.insert(ti);
            }
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
            // 首次检测到求解结束：更新分数（缓存已由 saveSolution 持续淘汰，此处无需再清）
            if (!"FINISHED".equals(solution.getSolverStatus())) {
                solution.setSolverStatus("FINISHED");
                solutionManager.update(solution, SolutionUpdatePolicy.UPDATE_SCORE_ONLY);
            }
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
        // 课程名：常规课走 course 表，公选课走 selection_campaign
        List<Long> courseIds = teachInfos.stream().map(TeachInfo::getCourseId).filter(Objects::nonNull).distinct().toList();
        List<Long> campaignIds = teachInfos.stream().map(TeachInfo::getCampaignId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> courseNameByCourse = courseInfoResolver.resolveCourseNameMap(courseIds);
        Map<Long, String> courseNameByCampaign = courseInfoResolver.resolveCampaignNameMap(campaignIds);
        List<Long> teacherIds = teachInfos.stream().map(TeachInfo::getTeacherId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> teacherNames = teacherNameResolver.namesByIds(teacherIds);

        CourseSchedule schedule = new CourseSchedule();
        schedule.setId(scheduleId);
        schedule.setTimeslotList(timeslots);
        schedule.setRoomList(rooms);
        schedule.setStudentGroupList(new ArrayList<>(classGroups.values()));
        schedule.setLessonList(buildLessons(teachInfos, courseNameByCourse, courseNameByCampaign,
                teacherNames, classGroups, timeslots, rooms));
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
                Long reservedCampaignId = null;
                if (restriction != null && "RESERVED".equals(restriction.getRestrictionType())) {
                    reservedCourseId = restriction.getCourseId();
                    reservedCampaignId = restriction.getCampaignId();
                }

                timeslots.add(new Timeslot(
                        time.getId() * 10 + dow,
                        DayOfWeek.of(dow),
                        time.getStartPeriod(),
                        time.getEndPeriod(),
                        reservedCourseId,
                        reservedCampaignId));
            }
        }
        return timeslots;
    }

    /**
     * 将 local 表记录转换为 Room 问题事实。max 为 null 时用 Integer.MAX_VALUE 表示无限制。
     * 自动排课仅选取普通教室（CLASSROOM），实验室/机房/报告厅不参与自动排课。
     */
    private List<Room> buildRooms() {
        return localMapper.selectList(null).stream()
                .filter(local -> local.getType() == LocalTypeEnum.CLASSROOM)
                .map(local -> {
                    int capacity = local.getMax() != null && local.getMax() > 0
                            ? local.getMax()
                            : Integer.MAX_VALUE;
                    return new Room(local.getId(), local.getBuilding(), local.getClassRoom(), capacity, LocalTypeEnum.CLASSROOM);
                })
                .toList();
    }

    /**
     * 从授课草稿中提取所有唯一班级名称，构建 StudentGroup。
     * 真实班级通过 student 表统计人数；选课班（className 在 class_name 表不存在）
     * 通过 SelectionClass.teachInfoId 反查 studentCount，避免 count=0 导致容量约束失效。
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
        Map<Long, String> collegeNameMap = collegeMapper.toNameMap(
                allClasses.stream().map(ClassName::getCollegeId).filter(Objects::nonNull).distinct().toList());
        Map<String, String> classNameToCollege = allClasses.stream()
                .collect(Collectors.toMap(
                        ClassName::getClassName,
                        cn -> cn.getCollegeId() != null ? collegeNameMap.getOrDefault(cn.getCollegeId(), "") : "",
                        (a, b) -> a));

        // 选课班：teachInfoId -> SelectionClass.studentCount
        Map<Long, Integer> selectionClassStudentCount = selectionClassMapper.selectList(null).stream()
                .filter(sc -> sc.getTeachInfoId() != null)
                .collect(Collectors.toMap(
                        SelectionClass::getTeachInfoId,
                        sc -> sc.getStudentCount() != null ? sc.getStudentCount() : 0,
                        (a, b) -> a));

        // 选课班 className -> teachInfoId（选课班 className 是 "活动名-N"，唯一）
        Map<String, Long> selectionClassNameToTeachInfoId = new HashMap<>();
        for (TeachInfo ti : teachInfos) {
            String name = ti.getClassName();
            if (name == null || name.isBlank() || ti.getId() == null) continue;
            String stripped = name.strip();
            if (!classNameToClassId.containsKey(stripped)) {
                selectionClassNameToTeachInfoId.putIfAbsent(stripped, ti.getId());
            }
        }

        Map<String, StudentGroup> groups = new LinkedHashMap<>();
        long id = 1;
        for (TeachInfo ti : teachInfos) {
            String raw = ti.getClassName();
            if (raw == null || raw.isBlank()) continue;
            for (String name : raw.split(",")) {
                name = name.strip();
                if (!name.isEmpty() && !groups.containsKey(name)) {
                    Long classId = classNameToClassId.get(name);
                    int count;
                    if (classId != null) {
                        count = classStudentCount.getOrDefault(classId, 0L).intValue();
                    } else {
                        Long tiId = selectionClassNameToTeachInfoId.get(name);
                        count = tiId != null ? selectionClassStudentCount.getOrDefault(tiId, 0) : 0;
                    }
                    String college = classNameToCollege.getOrDefault(name, "");
                    groups.put(name, new StudentGroup(id++, name, college, count));
                }
            }
        }
        return groups;
    }

    /** 将授课草稿转换为 Lesson 规划实体。 */
    private List<Lesson> buildLessons(List<TeachInfo> teachInfos,
                                       Map<Long, String> courseNameByCourse,
                                       Map<Long, String> courseNameByCampaign,
                                       Map<Long, String> teacherNames,
                                       Map<String, StudentGroup> classGroups,
                                       List<Timeslot> timeslots,
                                       List<Room> rooms) {
        Map<Long, Timeslot> timeslotById = timeslots.stream()
                .collect(Collectors.toMap(Timeslot::getId, t -> t, (a, b) -> a));
        Map<Long, Room> roomById = rooms.stream()
                .collect(Collectors.toMap(Room::getId, r -> r, (a, b) -> a));

        // 选课班数据：teachInfoId -> SelectionClass，selectionClassId -> 成员列表
        Map<Long, SelectionClass> selectionClassByTeachInfoId = selectionClassMapper.selectList(null).stream()
                .filter(sc -> sc.getTeachInfoId() != null)
                .collect(Collectors.toMap(SelectionClass::getTeachInfoId, sc -> sc, (a, b) -> a));
        List<Long> selectionClassIds = selectionClassByTeachInfoId.values().stream()
                .map(SelectionClass::getId).toList();
        Map<Long, List<SelectionClassMember>> membersBySelectionClassId =
                selectionClassIds.isEmpty() ? Map.of()
                        : selectionClassMemberMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SelectionClassMember>()
                                        .in(SelectionClassMember::getClassId, selectionClassIds))
                                .stream()
                                .collect(Collectors.groupingBy(SelectionClassMember::getClassId));

        // 真实班级数据：classId -> List<userId>，className -> classId
        Map<Long, List<Long>> classIdToStudentUserIds = studentMapper.selectList(null).stream()
                .filter(s -> s.getClassId() != null && s.getUserId() != null)
                .collect(Collectors.groupingBy(
                        Student::getClassId,
                        Collectors.mapping(Student::getUserId, Collectors.toList())));
        Map<String, Long> classNameToClassId = classNameMapper.selectList(null).stream()
                .collect(Collectors.toMap(ClassName::getClassName, ClassName::getId, (a, b) -> a));

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

            // studentIds：选课班取成员 userId；必修课/合班取班级学生 userId
            Set<Long> studentIds = new LinkedHashSet<>();
            int studentCount;
            SelectionClass selectionClass = selectionClassByTeachInfoId.get(ti.getId());
            if (selectionClass != null) {
                List<SelectionClassMember> members = membersBySelectionClassId.getOrDefault(
                        selectionClass.getId(), List.of());
                for (SelectionClassMember m : members) {
                    if (m.getStudentId() != null) {
                        studentIds.add(m.getStudentId());
                    }
                }
                studentCount = selectionClass.getStudentCount() != null ? selectionClass.getStudentCount() : 0;
            } else {
                if (raw != null && !raw.isBlank()) {
                    for (String name : raw.split(",")) {
                        String stripped = name.strip();
                        if (stripped.isEmpty()) continue;
                        Long classId = classNameToClassId.get(stripped);
                        if (classId != null) {
                            List<Long> userIds = classIdToStudentUserIds.getOrDefault(classId, List.of());
                            studentIds.addAll(userIds);
                        }
                    }
                }
                studentCount = groups.stream().mapToInt(StudentGroup::getStudentCount).sum();
            }

            String lessonCourseName = ti.getCampaignId() != null
                    ? courseNameByCampaign.getOrDefault(ti.getCampaignId(), "未知课程")
                    : courseNameByCourse.getOrDefault(ti.getCourseId(), "未知课程");
            lessons.add(new Lesson(
                    ti.getId(),
                    ti.getCourseId(),
                    ti.getCampaignId(),
                    lessonCourseName,
                    ti.getTeacherId(),
                    teacherNames.getOrDefault(ti.getTeacherId(), "未知教师"),
                    groups,
                    studentIds,
                    studentCount,
                    ti.getStartWeek(),
                    ti.getEndWeek(),
                    ti.getSemesterId(),
                    initialTimeslot,
                    initialRoom));
        }
        return lessons;
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

        // 每次更优解写回后立即清空课表缓存，避免求解期间查询读到旧缓存（消除脏读窗口）
        classScheduleCacheManager.clearAll();
        log.debug("排课解已更新, scheduleId={}, score={}, assigned={}/{}",
                scheduleId, solution.getScore(), assignedCount, solution.getLessonList().size());
    }
}
