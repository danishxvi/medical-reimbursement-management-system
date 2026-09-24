package com.mrms.enac;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Read API of the e-NAC module, used by the claim module and dashboards. */
public interface NacLookup {

    List<NacItemRef> items(Collection<Long> itemIds);

    /** Items of issued certificates of this employee that were marked not available. */
    List<NacItemRef> claimableItems(Long employeeUserId);

    Map<String, Long> countsByStatusForEmployee(Long employeeUserId);

    Map<String, Long> countsByStatusForDispensary(Long dispensaryId);
}
