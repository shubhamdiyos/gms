
package com.gms.service.impl;

import com.gms.model.entity.Classroom;
import com.gms.model.entity.Employee;
import com.gms.model.entity.Subject;
import com.gms.model.entity.TeacherAssignment;
import com.gms.exception.NotFoundException;
import com.gms.model.request.TeacherAssignmentRequest;
import com.gms.model.response.ClassroomResponse;
import com.gms.model.response.EmployeeResponse;
import com.gms.model.response.SubjectResponse;
import com.gms.model.response.TeacherAssignmentResponse;
import com.gms.repository.ClassroomRepository;
import com.gms.repository.EmployeeRepository;
import com.gms.repository.SubjectRepository;
import com.gms.repository.TeacherAssignmentRepository;
import com.gms.service.TeacherAssignmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherAssignmentServiceImpl implements TeacherAssignmentService {

    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final SubjectRepository subjectRepository;
    private final ClassroomRepository classroomRepository;

    public TeacherAssignmentServiceImpl(TeacherAssignmentRepository teacherAssignmentRepository, 
                                        EmployeeRepository employeeRepository, 
                                        SubjectRepository subjectRepository, 
                                        ClassroomRepository classroomRepository) {
        this.teacherAssignmentRepository = teacherAssignmentRepository;
        this.employeeRepository = employeeRepository;
        this.subjectRepository = subjectRepository;
        this.classroomRepository = classroomRepository;
    }

    @Override
    @Transactional
    public TeacherAssignmentResponse createAssignment(TeacherAssignmentRequest request) {
        Employee teacher = employeeRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new NotFoundException("TEACHER_NOT_FOUND", "Teacher not found"));

        Subject subject = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new NotFoundException("SUBJECT_NOT_FOUND", "Subject not found"));

        Classroom classroom = classroomRepository.findById(request.getClassroomId())
                .orElseThrow(() -> new NotFoundException("CLASSROOM_NOT_FOUND", "Classroom not found"));

        // Validation
        if (!teacher.isTeaching()) {
            throw new IllegalArgumentException("The selected employee is not a teacher.");
        }

        if (!teacher.getSchool().getId().equals(subject.getSchool().getId()) || 
            !teacher.getSchool().getId().equals(classroom.getSchool().getId())) {
            throw new IllegalArgumentException("Teacher, Subject, and Classroom must belong to the same school.");
        }

        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setTeacher(teacher);
        assignment.setSubject(subject);
        assignment.setClassroom(classroom);
        assignment.setAcademicYear(request.getAcademicYear());

        TeacherAssignment savedAssignment = teacherAssignmentRepository.save(assignment);

        return mapToResponse(savedAssignment);
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
