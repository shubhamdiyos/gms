package com.gms.service.impl;

import com.gms.model.entity.Employee;
import com.gms.model.entity.User;
import com.gms.exception.NotFoundException;
import com.gms.model.response.EmployeeResponse;
import com.gms.model.response.ClassroomResponse;
import com.gms.model.response.StudentResponse;
import com.gms.repository.EmployeeRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.TeacherService;
import com.gms.service.ClassroomService;
import com.gms.service.StudentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TeacherServiceImpl implements TeacherService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final ClassroomService classroomService;
    private final StudentService studentService;

    public TeacherServiceImpl(EmployeeRepository employeeRepository, UserRepository userRepository,
                              ClassroomService classroomService, StudentService studentService) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.classroomService = classroomService;
        this.studentService = studentService;
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

        // For now, we'll return all classrooms in the school
        // In a more complete implementation, we would filter by teacher assignments
        return classroomService.findBySchool(teacher.getSchoolId()).stream()
                .map(classroom -> new ClassroomResponse(
                        classroom.getId(),
                        classroom.getSchoolId(),
                        classroom.getName(),
                        classroom.getGrade(),
                        classroom.getSection()
                ))
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

        // For now, we'll return all students in the school
        // In a more complete implementation, we would filter by teacher's assigned classes
        return studentService.findBySchool(teacher.getSchoolId()).stream()
                .map(student -> new StudentResponse(
                        student.getId(),
                        student.getSchoolId(),
                        student.getClassroomId(),
                        student.getSectionId(),
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