
package com.gms.service.impl;

import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.*;
import com.gms.exception.NotFoundException;
import com.gms.model.request.TeacherAssignmentRequest;
import com.gms.model.response.ClassroomResponse;
import com.gms.model.response.EmployeeResponse;
import com.gms.model.response.SubjectResponse;
import com.gms.model.response.TeacherAssignmentResponse;
import com.gms.repository.*;
import com.gms.security.TenantContext;
import com.gms.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeacherAssignmentServiceImpl implements TeacherAssignmentService {

    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final SchoolRepository schoolRepository;
    private final EmployeeService employeeService;
    private final SubjectService subjectService;
    private final ClassroomService classroomService;
    private final UserService userService;

    public TeacherAssignmentServiceImpl(TeacherAssignmentRepository teacherAssignmentRepository, 
                                        SchoolRepository schoolRepository,
                                        EmployeeService employeeService, 
                                        SubjectService subjectService, 
                                        ClassroomService classroomService,
                                        UserService userService) {
        this.teacherAssignmentRepository = teacherAssignmentRepository;
        this.schoolRepository = schoolRepository;
        this.employeeService = employeeService;
        this.subjectService = subjectService;
        this.classroomService = classroomService;
        this.userService = userService;
    }

    @Override
    @Transactional
    public TeacherAssignmentResponse createAssignment(TeacherAssignmentRequest request) {
        Employee teacher = employeeService.findById(request.getTeacherId())
                .orElseThrow(() -> new NotFoundException("TEACHER_NOT_FOUND", "Teacher not found"));

        Subject subject = subjectService.findById(request.getSubjectId())
                .orElseThrow(() -> new NotFoundException("SUBJECT_NOT_FOUND", "Subject not found"));

        Classroom classroom = classroomService.findById(request.getClassroomId())
                .orElseThrow(() -> new NotFoundException("CLASSROOM_NOT_FOUND", "Classroom not found"));

        // Validation - ensure all entities belong to the same school
        if (!teacher.getSchool().getId().equals(TenantContext.schoolId()) || 
            !subject.getSchool().getId().equals(TenantContext.schoolId()) || 
            !classroom.getSchool().getId().equals(TenantContext.schoolId())) {
            throw new IllegalArgumentException("Teacher, Subject, and Classroom must belong to the current school.");
        }

        // Check for existing assignment
        if (teacherAssignmentRepository.existsByTeacher_School_IdAndTeacher_IdAndSubject_IdAndClassroom_IdAndAcademicYear(
                TenantContext.schoolId(), request.getTeacherId(), request.getSubjectId(), request.getClassroomId(),
                request.getAcademicYear())) {
            throw new IllegalArgumentException("This assignment already exists.");
        }

        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setSchool(schoolRepository.getReferenceById(TenantContext.schoolId()));
        assignment.setTeacher(teacher);
        assignment.setSubject(subject);
        assignment.setClassroom(classroom);
        assignment.setAcademicYear(request.getAcademicYear());
        TeacherAssignment savedAssignment = teacherAssignmentRepository.save(assignment);
        return mapToResponse(savedAssignment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherAssignmentResponse> getAllAssignmentsBySchool() {
        List<TeacherAssignment> assignments = teacherAssignmentRepository.findByTeacher_School_Id(TenantContext.schoolId());
        return assignments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherAssignmentResponse> getAssignmentsByTeacher(Integer teacherId) {
        // The scoped employee load above already confines the answer to the caller's school,
        // so the hand-written teacher-school comparison is gone; a foreign id is 404.
        Employee teacher = employeeService.findById(teacherId)
                .orElseThrow(() -> new NotFoundException("TEACHER_NOT_FOUND", "Teacher not found"));

        List<TeacherAssignment> assignments = teacherAssignmentRepository.findByTeacher_School_IdAndTeacher_Id(TenantContext.schoolId(), teacherId);
        return assignments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherAssignmentResponse> getAssignmentsByClassroom(Integer classroomId) {
        // Same: the scoped classroom load replaces the hand-written comparison.
        Classroom classroom = classroomService.findById(classroomId)
                .orElseThrow(() -> new NotFoundException("CLASSROOM_NOT_FOUND", "Classroom not found"));

        List<TeacherAssignment> assignments = teacherAssignmentRepository.findByClassroom_School_IdAndClassroom_Id(TenantContext.schoolId(), classroomId);
        return assignments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherAssignmentResponse> getAssignmentsByAcademicYear(String academicYear) {
        List<TeacherAssignment> assignments = teacherAssignmentRepository
                .findByAcademicYearAndTeacher_School_Id(academicYear, TenantContext.schoolId());
        return assignments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TeacherAssignmentResponse updateAssignment(Integer id, TeacherAssignmentRequest request) {
        // The scoped load filters the assignment's own school — the exact predicate this
        // hand-written check restates through the teacher association — so it is gone; the
        // teacher/subject/classroom comparisons below stay, guarding rows newly attached by id.
        TeacherAssignment assignment = teacherAssignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND", "Assignment not found"));

        // Update assignment
        if (request.getTeacherId() != null) {
            Employee teacher = employeeService.findById(request.getTeacherId())
                    .orElseThrow(() -> new NotFoundException("TEACHER_NOT_FOUND", "Teacher not found"));
            if (!teacher.getSchool().getId().equals(TenantContext.schoolId())) {
                throw new TenantAccessDeniedException("Teacher does not belong to the current school.");
            }
            assignment.setTeacher(teacher);
        }

        if (request.getSubjectId() != null) {
            Subject subject = subjectService.findById(request.getSubjectId())
                    .orElseThrow(() -> new NotFoundException("SUBJECT_NOT_FOUND", "Subject not found"));
            if (!subject.getSchool().getId().equals(TenantContext.schoolId())) {
                throw new TenantAccessDeniedException("Subject does not belong to the current school.");
            }
            assignment.setSubject(subject);
        }

        if (request.getClassroomId() != null) {
            Classroom classroom = classroomService.findById(request.getClassroomId())
                    .orElseThrow(() -> new NotFoundException("CLASSROOM_NOT_FOUND", "Classroom not found"));
            if (!classroom.getSchool().getId().equals(TenantContext.schoolId())) {
                throw new TenantAccessDeniedException("Classroom does not belong to the current school.");
            }
            assignment.setClassroom(classroom);
        }

        if (request.getAcademicYear() != null) {
            assignment.setAcademicYear(request.getAcademicYear());
        }

        TeacherAssignment savedAssignment = teacherAssignmentRepository.save(assignment);
        return mapToResponse(savedAssignment);
    }

    @Override
    @Transactional
    public void deleteAssignment(Integer id) {
        // The scoped load already refused any assignment the caller's tenant does not own.
        TeacherAssignment assignment = teacherAssignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND", "Assignment not found"));

        teacherAssignmentRepository.delete(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherAssignmentResponse> getAssignmentsByUsername(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Employee teacher = user.getEmployee();
        if (teacher == null) {
            throw new NotFoundException("TEACHER_NOT_FOUND", "Teacher profile not found for this user");
        }

        List<TeacherAssignment> assignments = teacherAssignmentRepository.findByTeacher_School_IdAndTeacher_Id(TenantContext.schoolId(), teacher.getId());
        return assignments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private TeacherAssignmentResponse mapToResponse(TeacherAssignment assignment) {
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
        // Subject entity doesn't have description field, removing setDescription call

        ClassroomResponse classroomResponse = new ClassroomResponse();
        classroomResponse.setId(assignment.getClassroom().getId());
        classroomResponse.setName(assignment.getClassroom().getName());
        classroomResponse.setGrade(assignment.getClassroom().getGrade());
        classroomResponse.setSection(assignment.getClassroom().getSection());
        classroomResponse.setSchoolId(assignment.getClassroom().getSchool().getId());

        return new TeacherAssignmentResponse(
                assignment.getId(),
                teacherResponse,
                subjectResponse,
                classroomResponse,
                assignment.getAcademicYear()
        );
    }
}
