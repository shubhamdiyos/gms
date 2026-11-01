package com.gms.service;

import com.gms.model.entity.User;
import com.gms.model.request.UserRequest;
import com.gms.model.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

public interface UserService {
    UserResponse getById(Integer id, Integer schoolId);
    Page<UserResponse> listBySchool(Integer schoolId, Pageable pageable);
    List<User> findBySchool(Integer schoolId);
    ResponseEntity<UserResponse> create(UserRequest request, Integer empId, Integer schoolId);
    ResponseEntity<UserResponse> update(UserRequest request, Integer empId, Integer schoolId);

    /**
     * Deletes a user, but only if it belongs to the caller's school.
     *
     * <p>Replaces the inherited {@code existsById}/{@code deleteById} pair, which reached
     * {@code repository.deleteById(id)} with no tenant predicate: any school administrator could
     * delete any other school's account.</p>
     *
     * @return true when a row was deleted, false when no such user exists in that school
     */
    boolean deleteWithinSchool(Integer id, Integer schoolId);
    
    // Additional methods needed for service-to-service communication
    Optional<User> findByUsername(String username);
    Optional<User> findByEmployee_Id(Integer employeeId);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    User save(User user);

    /**
     * Maps a user onto the transport DTO. Controllers must serialise this rather than the entity:
     * the entity's lazy associations fail JSON writing (500) and its shape is not a contract.
     */
    UserResponse toResponse(User user);
}
