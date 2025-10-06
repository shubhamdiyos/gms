package com.gms.repository;

import com.gms.model.entity.Parent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParentRepository extends JpaRepository<Parent, Integer> {

    List<Parent> findByStudentsId(Integer studentId);
    
    Optional<Parent> findByEmail(String email);
    
    boolean existsByEmail(String email);
}
