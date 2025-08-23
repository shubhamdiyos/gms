package com.gms.controller;

import com.gms.service.AbstractCRUDService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Abstract CRUD Controller following the established pattern.
 * Controllers extend this class and implement specific endpoint methods.
 * 
 * @param <T> Entity type
 * @param <ID> Primary key type (UUID for GMS)
 */
public abstract class AbstractCRUDController<T, ID> {
    
    protected final AbstractCRUDService<T, ID> service;
    
    public AbstractCRUDController(AbstractCRUDService<T, ID> service) {
        this.service = service;
    }
    
    /**
     * Basic get by ID endpoint - can be overridden by subclasses
     */
    @GetMapping("/{id}")
    public ResponseEntity<T> getById(@PathVariable ID id) {
        Optional<T> entity = service.findById(id);
        return entity.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Basic get all endpoint - can be overridden by subclasses
     */
    @GetMapping
    public ResponseEntity<List<T>> getAll() {
        List<T> entities = service.findAll();
        if (entities.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(entities);
    }
    
    /**
     * Basic delete endpoint - can be overridden by subclasses
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable ID id) {
        if (service.existsById(id)) {
            service.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
