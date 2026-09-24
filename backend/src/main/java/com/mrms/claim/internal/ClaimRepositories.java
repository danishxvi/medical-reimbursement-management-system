package com.mrms.claim.internal;

import com.mrms.claim.ClaimStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface ClaimRepository extends JpaRepository<Claim, Long> {

    List<Claim> findByEmployeeUserIdOrderByCreatedAtDesc(Long employeeUserId);

    // ---------------- Queues (seniority order) ----------------

    List<Claim> findBySchoolIdAndStatusOrderByFirstSubmittedAtAscIdAsc(Long schoolId, ClaimStatus status);

    List<Claim> findByPaoIdAndStatusOrderByFirstSubmittedAtAscIdAsc(Long paoId, ClaimStatus status);

    List<Claim> findBySchoolIdAndStatusAndAssignedToIsNullOrderByFirstSubmittedAtAscIdAsc(
            Long schoolId, ClaimStatus status, Limit limit);

    List<Claim> findByPaoIdAndStatusAndAssignedToIsNullOrderByFirstSubmittedAtAscIdAsc(
            Long paoId, ClaimStatus status, Limit limit);

    List<Claim> findByStatusAndAssignedTo(ClaimStatus status, Long assignedTo);

    // ---------------- Reviewer listings ----------------

    @Query("""
            select c from Claim c
            where c.schoolId = :schoolId and c.status <> com.mrms.claim.ClaimStatus.DRAFT
              and (:status is null or c.status = :status)
            order by c.firstSubmittedAt desc
            """)
    List<Claim> findForSchool(@Param("schoolId") Long schoolId, @Param("status") ClaimStatus status, Limit limit);

    @Query("""
            select c from Claim c
            where c.paoId = :paoId and c.hosCertifiedAt is not null
              and (:status is null or c.status = :status)
            order by c.firstSubmittedAt desc
            """)
    List<Claim> findForPao(@Param("paoId") Long paoId, @Param("status") ClaimStatus status, Limit limit);

    // ---------------- Duplicate detection ----------------

    @Query("""
            select distinct c from Claim c join c.items i
            where c.id <> :claimId and c.status in :statuses and i.nacItemId in :nacItemIds
            """)
    List<Claim> findActiveUsingNacItems(@Param("claimId") Long claimId,
                                        @Param("nacItemIds") Collection<Long> nacItemIds,
                                        @Param("statuses") Collection<ClaimStatus> statuses);

    @Query("""
            select distinct c from Claim c join c.items i
            where c.id <> :claimId and c.status in :statuses and i.billDocumentId in :documentIds
            """)
    List<Claim> findActiveUsingDocuments(@Param("claimId") Long claimId,
                                         @Param("documentIds") Collection<UUID> documentIds,
                                         @Param("statuses") Collection<ClaimStatus> statuses);

    @Query("""
            select count(i) from Claim c join c.items i
            where c.id <> :claimId and c.employeeUserId = :employeeId and c.status in :statuses
              and lower(i.billNumber) = lower(:billNumber) and lower(i.vendorName) = lower(:vendor)
              and i.billDate = :billDate
            """)
    long countSameBill(@Param("claimId") Long claimId, @Param("employeeId") Long employeeId,
                       @Param("billNumber") String billNumber, @Param("vendor") String vendor,
                       @Param("billDate") LocalDate billDate,
                       @Param("statuses") Collection<ClaimStatus> statuses);

    @Query("""
            select i.nacItemId from Claim c join c.items i
            where c.employeeUserId = :employeeId and c.status in :statuses and i.nacItemId is not null
            """)
    List<Long> usedNacItemIds(@Param("employeeId") Long employeeId,
                              @Param("statuses") Collection<ClaimStatus> statuses);

    // ---------------- Statistics ----------------

    @Query("select c.status, count(c), coalesce(sum(c.claimedAmount), 0) from Claim c "
            + "where c.employeeUserId = :id group by c.status")
    List<Object[]> statsForEmployee(@Param("id") Long employeeUserId);

    @Query("select c.status, count(c), coalesce(sum(c.claimedAmount), 0) from Claim c "
            + "where c.schoolId = :id and c.status <> com.mrms.claim.ClaimStatus.DRAFT group by c.status")
    List<Object[]> statsForSchool(@Param("id") Long schoolId);

    @Query("select c.status, count(c), coalesce(sum(c.claimedAmount), 0) from Claim c "
            + "where c.paoId = :id and c.hosCertifiedAt is not null group by c.status")
    List<Object[]> statsForPao(@Param("id") Long paoId);

    @Query("select c.status, count(c), coalesce(sum(c.claimedAmount), 0) from Claim c "
            + "where c.status <> com.mrms.claim.ClaimStatus.DRAFT group by c.status")
    List<Object[]> statsOverall();

    @Query("select coalesce(sum(c.admittedAmount), 0) from Claim c where c.status = com.mrms.claim.ClaimStatus.PAID "
            + "and (:employeeId is null or c.employeeUserId = :employeeId) "
            + "and (:schoolId is null or c.schoolId = :schoolId) "
            + "and (:paoId is null or c.paoId = :paoId)")
    BigDecimal paidTotal(@Param("employeeId") Long employeeId, @Param("schoolId") Long schoolId,
                         @Param("paoId") Long paoId);

    @Query("select c.firstSubmittedAt, c.paidAt from Claim c where c.status = com.mrms.claim.ClaimStatus.PAID "
            + "and (:schoolId is null or c.schoolId = :schoolId) "
            + "and (:paoId is null or c.paoId = :paoId) "
            + "order by c.paidAt desc")
    List<Object[]> recentPaidDurations(@Param("schoolId") Long schoolId, @Param("paoId") Long paoId, Limit limit);

    @Query("select c.status, count(c) from Claim c where c.status in :statuses and c.stageEnteredAt < :before "
            + "and (:schoolId is null or c.schoolId = :schoolId) "
            + "and (:paoId is null or c.paoId = :paoId) group by c.status")
    List<Object[]> countStageEnteredBefore(@Param("statuses") Collection<ClaimStatus> statuses,
                                           @Param("before") Instant before,
                                           @Param("schoolId") Long schoolId, @Param("paoId") Long paoId);

    // ---------------- Budget ----------------

    List<Claim> findBySchoolIdAndStatusIn(Long schoolId, Collection<ClaimStatus> statuses);

    @Query(value = "select nextval('claim_number_seq')", nativeQuery = true)
    long nextNumber();
}

interface ClaimEventRepository extends JpaRepository<ClaimEvent, Long> {

    List<ClaimEvent> findByClaimIdOrderByOccurredAtAscIdAsc(Long claimId);
}
