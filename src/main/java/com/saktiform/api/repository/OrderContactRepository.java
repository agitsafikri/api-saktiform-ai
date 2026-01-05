package com.saktiform.api.repository;

import com.saktiform.api.entity.OrderContact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderContactRepository extends JpaRepository<OrderContact, Long> {
}