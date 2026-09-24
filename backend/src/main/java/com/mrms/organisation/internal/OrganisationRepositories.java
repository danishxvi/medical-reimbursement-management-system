package com.mrms.organisation.internal;

import com.mrms.organisation.internal.OfficeEntities.Dispensary;
import com.mrms.organisation.internal.OfficeEntities.PayAccountsOffice;
import com.mrms.organisation.internal.OfficeEntities.School;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface PaoRepository extends JpaRepository<PayAccountsOffice, Long> {

    boolean existsByCodeIgnoreCase(String code);

    List<PayAccountsOffice> findAllByOrderByNameAsc();
}

interface SchoolRepository extends JpaRepository<School, Long> {

    boolean existsByCodeIgnoreCase(String code);

    List<School> findByPaoIdOrderByNameAsc(Long paoId);

    @Query("""
            select s from School s
            where :q is null
               or lower(s.name) like lower(concat('%', :q, '%'))
               or lower(s.code) like lower(concat('%', :q, '%'))
            """)
    Page<School> search(@Param("q") String q, Pageable pageable);
}

interface DispensaryRepository extends JpaRepository<Dispensary, Long> {

    boolean existsByCodeIgnoreCase(String code);

    List<Dispensary> findAllByOrderByNameAsc();
}

interface EmployeeProfileRepository extends JpaRepository<EmployeeProfile, Long> {

    Optional<EmployeeProfile> findByUserId(Long userId);

    boolean existsByEmployeeCodeIgnoreCase(String code);

    @Query("""
            select p from EmployeeProfile p
            where :q is null
               or lower(p.employeeCode) like lower(concat('%', :q, '%'))
               or lower(p.designation) like lower(concat('%', :q, '%'))
            """)
    Page<EmployeeProfile> search(@Param("q") String q, Pageable pageable);
}
