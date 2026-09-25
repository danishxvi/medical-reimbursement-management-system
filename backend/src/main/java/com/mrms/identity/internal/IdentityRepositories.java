package com.mrms.identity.internal;

import com.mrms.shared.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByRole(Role role);

    long countByRoleAndEnabledTrue(Role role);

    // One query per office scope: ids of schools, dispensaries and PAOs overlap numerically
    @Query("select u.id from UserAccount u where u.role = :role and u.enabled = true and u.schoolId = :id")
    List<Long> findActiveIdsInSchool(@Param("role") Role role, @Param("id") Long schoolId);

    @Query("select u.id from UserAccount u where u.role = :role and u.enabled = true and u.dispensaryId = :id")
    List<Long> findActiveIdsInDispensary(@Param("role") Role role, @Param("id") Long dispensaryId);

    @Query("select u.id from UserAccount u where u.role = :role and u.enabled = true and u.paoId = :id")
    List<Long> findActiveIdsInPao(@Param("role") Role role, @Param("id") Long paoId);

    @Query("select u.id from UserAccount u where u.role = :role and u.enabled = true")
    List<Long> findActiveIds(@Param("role") Role role);

    @Query("select u.id from UserAccount u where u.role = :role and u.enabled = true and u.zone = :zone")
    List<Long> findActiveIdsInZone(@Param("role") Role role, @Param("zone") String zone);

    @Query("""
            select u from UserAccount u
            where (:role is null or u.role = :role)
              and (:q is null
                   or lower(u.username) like lower(concat('%', :q, '%'))
                   or lower(u.fullName) like lower(concat('%', :q, '%')))
            """)
    Page<UserAccount> search(@Param("role") Role role, @Param("q") String q, Pageable pageable);
}

interface PasswordHistoryRepository extends JpaRepository<PasswordHistoryEntry, Long> {

    List<PasswordHistoryEntry> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
