package com.mrms.escalation.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface SlaEventRepository extends JpaRepository<SlaEntities.SlaEvent, Long> {

    List<SlaEntities.SlaEvent> findByResolvedAtIsNull();

    @Query("select e from SlaEvent e where e.level = :level and e.occurredAt >= :since")
    List<SlaEntities.SlaEvent> byLevelSince(@Param("level") String level, @Param("since") Instant since);
}

interface SlaNudgeRepository extends JpaRepository<SlaEntities.SlaNudge, Long> {

    Optional<SlaEntities.SlaNudge> findFirstBySubjectTypeAndSubjectIdOrderByOccurredAtDesc(String subjectType, Long subjectId);
}
