
package com.gms.service.impl;

import com.gms.model.entity.*;
import com.gms.exception.NotFoundException;
import com.gms.model.request.TimetableRequest;
import com.gms.model.response.*;
import com.gms.repository.*;
import com.gms.security.TenantContext;
import com.gms.service.TimetableService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TimetableServiceImpl implements TimetableService {

    private final TimetableRepository timetableRepository;
    private final ClassroomRepository classroomRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final TimetableSlotRepository timetableSlotRepository;

    public TimetableServiceImpl(TimetableRepository timetableRepository, 
                                ClassroomRepository classroomRepository, 
                                TeacherAssignmentRepository teacherAssignmentRepository, 
                                StudentRepository studentRepository,
                                UserRepository userRepository,
                                TimetableSlotRepository timetableSlotRepository) {
        this.timetableRepository = timetableRepository;
        this.classroomRepository = classroomRepository;
        this.teacherAssignmentRepository = teacherAssignmentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.timetableSlotRepository = timetableSlotRepository;
    }

    @Override
    @Transactional
    public TimetableResponse createOrUpdateTimetable(TimetableRequest request) {
        Classroom classroom = classroomRepository.findById(request.getClassroomId())
                .orElseThrow(() -> new NotFoundException("CLASSROOM_NOT_FOUND", "Classroom not found"));

        Timetable timetable = timetableRepository.findByClassroom_School_IdAndClassroom_Id(TenantContext.schoolId(), request.getClassroomId())
                .orElse(new Timetable());

        timetable.setClassroom(classroom);
        timetable.setAcademicYear(request.getAcademicYear());
        timetable.getSlots().clear();

        for (var slotRequest : request.getSlots()) {
            TeacherAssignment assignment = teacherAssignmentRepository.findById(slotRequest.getAssignmentId())
                    .orElseThrow(() -> new NotFoundException("TEACHER_ASSIGNMENT_NOT_FOUND", "Teacher Assignment not found"));
            
            if (!assignment.getClassroom().getId().equals(classroom.getId())) {
                throw new IllegalArgumentException("Teacher assignment must belong to the same classroom as the timetable.");
            }

            TimetableSlot slot = new TimetableSlot();
            slot.setTimetable(timetable);
            slot.setDayOfWeek(slotRequest.getDayOfWeek());
            slot.setPeriod(slotRequest.getPeriod());
            slot.setAssignment(assignment);
            timetable.getSlots().add(slot);
        }

        Timetable savedTimetable = timetableRepository.save(timetable);
        return mapToResponse(savedTimetable);
    }

    @Override
    @Transactional(readOnly = true)
    public TimetableResponse getTimetableForClassroom(Integer classroomId) {
        Timetable timetable = timetableRepository.findByClassroom_School_IdAndClassroom_Id(TenantContext.schoolId(), classroomId)
                .orElseThrow(() -> new NotFoundException("TIMETABLE_NOT_FOUND", "Timetable not found for the given classroom"));
        return mapToResponse(timetable);
    }

    @Override
    @Transactional(readOnly = true)
    public TimetableResponse getTimetableForTeacher(Integer teacherId) {
        List<TimetableSlot> teacherSlots = timetableSlotRepository
                .findByAssignment_Teacher_School_IdAndAssignment_Teacher_Id(TenantContext.schoolId(), teacherId);

        // A teacher has no single timetable, so there is no TimetableResponse to build from those
        // slots yet — returning the empty response is ACD-02's stub, unchanged here. What changed is
        // that the slots are read with the tenant in the query: the version this replaced walked
        // every timetable in the platform in Java to reach the same rows.
        return new TimetableResponse();
    }

    @Override
    @Transactional(readOnly = true)
    public TimetableResponse getTimetableForStudent(Integer studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("STUDENT_NOT_FOUND", "Student not found"));
        return getTimetableForClassroom(student.getClassroomId());
    }
    
    @Override
    @Transactional(readOnly = true)
    public TimetableResponse getTimetableForAuthenticatedStudent(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
                
        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }
        
        return getTimetableForClassroom(student.getClassroomId());
    }

    public TimetableResponse mapToResponse(Timetable timetable) {
        List<TimetableSlotResponse> slotResponses = timetable.getSlots().stream()
                .map(this::mapToSlotResponse)
                .collect(Collectors.toList());

        return new TimetableResponse(
                timetable.getId(),
                timetable.getClassroom().getId(),
                timetable.getClassroom().getName(),
                timetable.getAcademicYear(),
                slotResponses
        );
    }

    public TimetableSlotResponse mapToSlotResponse(TimetableSlot slot) {
        return new TimetableSlotResponse(
                slot.getDayOfWeek(),
                slot.getPeriod(),
                mapToAssignmentResponse(slot.getAssignment())
        );
    }

    private TeacherAssignmentResponse mapToAssignmentResponse(TeacherAssignment assignment) {
        EmployeeResponse teacherResponse = new EmployeeResponse(
                assignment.getTeacher().getId(),
                assignment.getTeacher().getFullName(),
                assignment.getTeacher().getEmail(),
                assignment.getTeacher().getDesignation(),
                assignment.getTeacher().getDepartment()
        );

        SubjectResponse subjectResponse = new SubjectResponse();
        subjectResponse.setId(assignment.getSubject().getId());
        subjectResponse.setName(assignment.getSubject().getName());
        subjectResponse.setCode(assignment.getSubject().getCode());

        ClassroomResponse classroomResponse = new ClassroomResponse();
        classroomResponse.setId(assignment.getClassroom().getId());
        classroomResponse.setName(assignment.getClassroom().getName());
        classroomResponse.setGrade(assignment.getClassroom().getGrade());
        classroomResponse.setSection(assignment.getClassroom().getSection());

        return new TeacherAssignmentResponse(
                assignment.getId(),
                teacherResponse,
                subjectResponse,
                classroomResponse,
                assignment.getAcademicYear()
        );
    }
}
