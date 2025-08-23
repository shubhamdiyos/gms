package com.gms.service;

import com.gms.model.entity.User;
import com.gms.model.request.UserRequest;
import com.gms.model.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface UserService {
    UserResponse getById(Integer id);
    Page<UserResponse> listBySchool(Integer schoolId, Pageable pageable);
    List<User> findBySchool(Integer schoolId);
    ResponseEntity<UserResponse> create(UserRequest request, Integer empId, Integer schoolId);
    ResponseEntity<UserResponse> update(UserRequest request, Integer empId, Integer schoolId);
}
