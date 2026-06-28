package com.saktiform.api.repository;

import com.saktiform.api.entity.BlockedIp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BlockedIpRepository extends JpaRepository<BlockedIp, Long> {
    boolean existsByIpAddress(String ipAddress);

    @Query("SELECT b.ipAddress FROM BlockedIp b")
    List<String> findAllIpAddresses();
}
