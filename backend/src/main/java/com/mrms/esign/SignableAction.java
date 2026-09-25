package com.mrms.esign;

import tools.jackson.databind.JsonNode;

/**
 * Implemented by a module for each action that must be signed. Before an
 * eSign transaction starts, the signing module asks the owning module to
 * check that the current user may perform the action and to compute the
 * digest of the content that will be approved.
 *
 * <p>The same digest must come out when the module later recomputes it from
 * the request that executes the action; otherwise the signature does not
 * cover what is being done and the action is refused.
 */
public interface SignableAction {

    /** Stable purpose code, for example CLAIM_HOS_CERTIFY. */
    String purpose();

    /** Subject type recorded with the signature, for example CLAIM. */
    String subjectType();

    /**
     * @param subjectId the record being acted on
     * @param payload   the request body the action will receive, without any password or transaction id
     * @throws com.mrms.shared.web.ApiException if the user may not act on this subject now
     */
    Signable describe(String subjectId, JsonNode payload);

    /**
     * @param digest       SHA-256 (hex) of the canonical content being approved
     * @param documentInfo short description shown by the eSign provider, for example
     *                     "HoS certificate for claim MR/9900001/2026-27/000001, Rs. 850.00"
     */
    record Signable(String digest, String documentInfo) {
    }

    /** Convenience for modules that declare their actions as beans. */
    static SignableAction of(String purpose, String subjectType,
                             java.util.function.BiFunction<String, JsonNode, Signable> describe) {
        return new SignableAction() {
            @Override
            public String purpose() {
                return purpose;
            }

            @Override
            public String subjectType() {
                return subjectType;
            }

            @Override
            public Signable describe(String subjectId, JsonNode payload) {
                return describe.apply(subjectId, payload);
            }
        };
    }
}
