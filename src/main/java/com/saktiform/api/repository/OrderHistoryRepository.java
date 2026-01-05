package com.saktiform.api.repository;

import com.saktiform.api.entity.OrderHistory;
import com.saktiform.api.model.Order.OrderLogsDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, UUID> {
    @Query("""
    SELECT new com.saktiform.api.model.Order.OrderLogsDto(
          oh.log, oh.createdAt
        )
        FROM OrderHistory as oh WHERE oh.idOrder = :idOrder
        ORDER BY oh.createdAt DESC
    """)
    List<OrderLogsDto> getOrderLogs(@Param("idOrder") UUID id);
}