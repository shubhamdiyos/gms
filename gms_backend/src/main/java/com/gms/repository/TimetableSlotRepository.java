
package com.gms.repository;

import com.gms.model.entity.TimetableSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, Integer> {
}
