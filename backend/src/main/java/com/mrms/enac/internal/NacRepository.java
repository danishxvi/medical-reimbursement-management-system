package com.mrms.enac.internal;

import com.mrms.enac.NacTypes.NacStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface NacRepository extends JpaRepository<NacRequest, Long> {

    List<NacRequest> findByEmployeeUserIdOrderByCreatedAtDesc(Long employeeUserId);

    /** The whole queue of a stage, oldest first (seniority order). */
    List<NacRequest> findByDispensaryIdAndStatusOrderByQueueSinceAscIdAsc(Long dispensaryId, NacStatus status);

    Optional<NacRequest> findFirstByDispensaryIdAndStatusAndAssignedTo(Long dispensaryId, NacStatus status,
                                                                      Long assignedTo);

    /** Head of the queue: the oldest request nobody has taken yet. */
    List<NacRequest> findByDispensaryIdAndStatusAndAssignedToIsNullOrderByQueueSinceAscIdAsc(
            Long dispensaryId, NacStatus status, Limit limit);

    @Query("select r from NacRequest r join r.items i where i.id in :itemIds")
    List<NacRequest> findContainingItems(@Param("itemIds") Collection<Long> itemIds);

    @Query("""
            select r from NacRequest r
            where r.employeeUserId = :employeeId and r.status = com.mrms.enac.NacTypes.NacStatus.ISSUED
            order by r.issuedAt desc
            """)
    List<NacRequest> findIssuedForEmployee(@Param("employeeId") Long employeeId);

    @Query("select r.status, count(r) from NacRequest r where r.employeeUserId = :id group by r.status")
    List<Object[]> countByStatusForEmployee(@Param("id") Long employeeUserId);

    @Query("select r.status, count(r) from NacRequest r where r.dispensaryId = :id group by r.status")
    List<Object[]> countByStatusForDispensary(@Param("id") Long dispensaryId);

    long countByDispensaryIdAndStatusAndStageEnteredAtBefore(Long dispensaryId, NacStatus status, Instant before);

    @Query(value = "select nextval('nac_number_seq')", nativeQuery = true)
    long nextNumber();
}
