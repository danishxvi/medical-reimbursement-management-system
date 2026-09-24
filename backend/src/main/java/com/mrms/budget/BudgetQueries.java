package com.mrms.budget;

import java.math.BigDecimal;

/** Read API of the budget module for dashboards. */
public interface BudgetQueries {

    SchoolPosition position(Long schoolId, String financialYear);

    long openDemands(Long paoId);

    /**
     * @param allocated        total allocated to the school in the year
     * @param paid             total paid out of it
     * @param balance          allocated minus paid
     * @param pipelinePending  estimated value of claims still under scrutiny
     * @param awaitingFunds    admitted value of sanctioned, unpaid claims
     * @param suggestedDemand  what the school should ask for: pipeline minus balance, never negative
     */
    record SchoolPosition(Long schoolId, String financialYear, BigDecimal allocated, BigDecimal paid,
                          BigDecimal balance, long pendingCount, BigDecimal pipelinePending,
                          long awaitingFundsCount, BigDecimal awaitingFunds, BigDecimal suggestedDemand) {
    }
}
