package com.gms.controller;

import com.gms.model.entity.User;
import com.gms.model.request.UserRequest;
import com.gms.model.response.UserResponse;
import com.gms.service.UserService;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User administration for the caller's tenant.
 *
 * <p>This controller answers {@code GET /} and {@code GET /{id}} with {@link UserResponse} rather
 * than with the JPA entity, because the entity's lazy {@code School} proxy failed JSON writing and
 * answered 500 for every caller — reproduced in {@code docs/audit/BASELINE_STATUS.md} §4.5. Every
 * read and write here is scoped to the caller's school: an administrator sees and deletes only its
 * own school's accounts.</p>
 */
@RestController
@RequestMapping(Constants.API_BASE_V1 + "/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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

    /**
     * The tenant's own accounts. `GET /users` previously serialised the {@code User} entity, which
     * failed on the lazy {@code School} proxy and answered 500 for every caller — reproduced in
     * {@code docs/audit/BASELINE_STATUS.md} §4.5. Mandate §10.2 keeps entities inside the service
     * layer for exactly this reason.
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAll() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        List<UserResponse> users = userService.findBySchool(schoolId).stream()
                .map(userService::toResponse)
                .toList();
        if (users.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(userService.getById(id, schoolId));
        } catch (com.gms.exception.NotFoundException ex) {
            return ResponseEntity.status(404).build();
        }
    }

    /**
     * Deletes one of the caller's own accounts.
     *
     * <p>The inherited {@code existsById}/{@code deleteById} pair reached
     * {@code repository.deleteById(id)} with no tenant predicate, so any administrator could
     * delete any other school's account; the replacement takes the caller's school and refuses
     * the row if it belongs to another one.</p>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        if (!userService.deleteWithinSchool(id, schoolId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        String username = SecurityUtil.getUsernameFromToken();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }
        return userService.findByUsername(username)
                .map(user -> ResponseEntity.ok(userService.toResponse(user)))
                .orElse(ResponseEntity.status(404).build());
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UserRequest request) {
        String username = SecurityUtil.getUsernameFromToken();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.findByUsername(username).orElse(null);
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
        return ResponseEntity.ok(userService.toResponse(userService.save(user)));
    }
}
