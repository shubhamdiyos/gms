package com.gms.service.impl;

import com.gms.model.entity.*;
import com.gms.exception.NotFoundException;
import com.gms.model.response.EmployeeResponse;
import com.gms.model.response.ClassroomResponse;
import com.gms.model.response.StudentResponse;
import com.gms.repository.*;
import com.gms.service.TeacherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TeacherServiceImpl implements TeacherService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final StudentRepository studentRepository;

    public TeacherServiceImpl(EmployeeRepository employeeRepository, UserRepository userRepository,
                              TeacherAssignmentRepository teacherAssignmentRepository,
                              StudentRepository studentRepository) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.teacherAssignmentRepository = teacherAssignmentRepository;
        this.studentRepository = studentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getTeacherProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Employee teacher = user.getEmployee();
        if (teacher == null) {
            throw new NotFoundException("TEACHER_NOT_FOUND", "Teacher profile not found for this user");
        }

        return new EmployeeResponse(
                teacher.getId(),
                teacher.getFullName(),
                teacher.getEmail(),
                teacher.getDesignation(),
                teacher.getDepartment()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassroomResponse> getAssignedClasses(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Employee teacher = user.getEmployee();
        if (teacher == null) {
            throw new NotFoundException("TEACHER_NOT_FOUND", "Teacher profile not found for this user");
        }

        // Get actual assigned classrooms through teacher assignments
        List<TeacherAssignment> assignments = teacherAssignmentRepository.findByTeacher_Id(teacher.getId());
        
        return assignments.stream()
                .map(assignment -> {
                    Classroom classroom = assignment.getClassroom();
                    return new ClassroomResponse(
                            classroom.getId(),
                            classroom.getSchool().getId(),
                            classroom.getName(),
                            classroom.getGrade(),
                            classroom.getSection()
                    );
                })
                .distinct() // Remove duplicates if teacher teaches multiple subjects in same classroom
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentResponse> getAssignedStudents(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Employee teacher = user.getEmployee();
        if (teacher == null) {
            throw new NotFoundException("TEACHER_NOT_FOUND", "Teacher profile not found for this user");
        }

        // Get all classrooms assigned to this teacher
        List<TeacherAssignment> assignments = teacherAssignmentRepository.findByTeacher_Id(teacher.getId());
        List<Integer> assignedClassroomIds = assignments.stream()
                .map(assignment -> assignment.getClassroom().getId())
                .distinct()
                .collect(Collectors.toList());

        if (assignedClassroomIds.isEmpty()) {
            return List.of(); // Return empty list if no assignments
        }

        // Get students from assigned classrooms
        List<Student> students = studentRepository.findByClassroom_IdInAndStatusNot(assignedClassroomIds, "0");
        
        return students.stream()
                .map(student -> new StudentResponse(
                        student.getId(),
                        student.getSchool().getId(),
                        student.getClassroom().getId(),
                        student.getSection() != null ? student.getSection().getId() : null,
                        student.getStudentId(),
                        student.getFirstName(),
                        student.getLastName(),
                        student.getEmail(),
                        student.getPhoneNumber(),
                        student.getDateOfBirth(),
                        student.getGender(),
                        student.getStatus()
                ))
                .collect(Collectors.toList());
    }
}