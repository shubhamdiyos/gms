package com.gms.controller;

import com.gms.model.entity.User;
import com.gms.model.request.UserRequest;
import com.gms.model.response.UserResponse;
import com.gms.service.impl.UserServiceImpl;
import com.gms.repository.UserRepository;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:3000"})
@RequestMapping(Constants.API_BASE_V1 + "/users")
public class UserController extends AbstractCRUDController<User, Integer> {

    private final UserServiceImpl userService;
    private final UserRepository userRepository;

    public UserController(UserServiceImpl service, UserRepository userRepository) {
        super(service);
        this.userService = service;
        this.userRepository = userRepository;
    }

    @PostMapping("/create")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return userService.create(request, empId, schoolId);
    }

    @PutMapping("/update")
    public ResponseEntity<UserResponse> update(@Valid @RequestBody UserRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return userService.update(request, empId, schoolId);
    }

    @GetMapping
    public ResponseEntity<List<User>> getAll() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        List<User> users = userService.findBySchool(schoolId);
        if (users.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(users);
    }

    @GetMapping("/me")
    public ResponseEntity<User> me() {
        String username = SecurityUtil.getUsernameFromToken();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }
        return userRepository.findByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).build());
    }

    @PutMapping("/me")
    public ResponseEntity<User> updateMe(@Valid @RequestBody UserRequest request) {
        String username = SecurityUtil.getUsernameFromToken();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        // Do not allow role/email/password updates here
        userRepository.save(user);
        return ResponseEntity.ok(user);
    }
}
