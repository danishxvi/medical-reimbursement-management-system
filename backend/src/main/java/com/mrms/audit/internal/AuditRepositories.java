package com.mrms.audit.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    @Query("""
            select e from AuditEntry e
            where (:entityType is null or e.entityType = :entityType)
              and (:entityId is null or e.entityId = :entityId)
              and (:actor is null or lower(e.actorUsername) = lower(:actor))
              and (:action is null or e.action = :action)
            """)
    Page<AuditEntry> search(@Param("entityType") String entityType,
                            @Param("entityId") String entityId,
                            @Param("actor") String actor,
                            @Param("action") String action,
                            Pageable pageable);

    List<AuditEntry> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);
}

interface AuditChainHeadRepository extends JpaRepository<AuditChainHead, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AuditChainHead h where h.id = 1")
    Optional<AuditChainHead> lockHead();
}
