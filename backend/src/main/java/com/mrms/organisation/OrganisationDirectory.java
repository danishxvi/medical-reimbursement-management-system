package com.mrms.organisation;

import com.mrms.organisation.OrganisationViews.DependentView;
import com.mrms.organisation.OrganisationViews.EmployeeProfileView;
import com.mrms.organisation.OrganisationViews.OfficeRef;
import com.mrms.organisation.OrganisationViews.SchoolRef;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read only lookups other modules may use. */
public interface OrganisationDirectory {

    Optional<SchoolRef> school(Long schoolId);

    Optional<OfficeRef> dispensary(Long dispensaryId);

    Optional<OfficeRef> pao(Long paoId);

    List<SchoolRef> schoolsOfPao(Long paoId);

    Map<Long, String> schoolNames(Collection<Long> schoolIds);

    Map<Long, String> dispensaryNames(Collection<Long> dispensaryIds);

    Optional<EmployeeProfileView> profileOf(Long userId);

    /** The dependent, only if it is an active dependent of the given employee. */
    Optional<DependentView> dependentOf(Long employeeUserId, Long dependentId);

    long countSchools();
}
