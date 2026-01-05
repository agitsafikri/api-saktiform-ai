package com.saktiform.api.repository;

import com.saktiform.api.entity.OrderSequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface OrderSequenceRepository extends JpaRepository<OrderSequence, String> {
}