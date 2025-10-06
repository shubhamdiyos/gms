package com.gms.service;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Abstract CRUD Service following the established pattern.
 * Services extend this class and implement specific business logic methods.
 * 
 * @param <T> Entity type
 * @param <ID> Primary key type (UUID for GMS)
 */
public abstract class AbstractCRUDService<T, ID> {
    
    protected final JpaRepository<T, ID> repository;
    
    public AbstractCRUDService(JpaRepository<T, ID> repository) {
        this.repository = repository;
    }
    
    /**
     * Basic create method - can be overridden by subclasses
     */
    public T create(T entity) {
        return repository.save(entity);
    }
    
    /**
     * Basic update method - can be overridden by subclasses
     */
    public T update(T entity) {
        return repository.save(entity);
    }
    
    /**
     * Basic delete method
     */
    public void delete(T entity) {
        repository.delete(entity);
    }
    
    /**
     * Basic delete by ID method
     */
    public void deleteById(ID id) {
        repository.deleteById(id);
    }
    
    /**
     * Find by ID
     */
    public java.util.Optional<T> findById(ID id) {
        return repository.findById(id);
    }
    
    /**
     * Find all entities
     */
    public java.util.List<T> findAll() {
        return repository.findAll();
    }
    
    /**
     * Check if entity exists by ID
     */
    public boolean existsById(ID id) {
        return repository.existsById(id);
    }
}
