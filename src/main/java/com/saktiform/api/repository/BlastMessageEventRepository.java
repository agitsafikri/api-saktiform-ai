package com.saktiform.api.repository;

import com.saktiform.api.entity.BlastMessageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlastMessageEventRepository extends JpaRepository<BlastMessageEvent, Long> {

    List<BlastMessageEvent> findByMessageIdOrderByCreatedAtAsc(Long messageId);
}
