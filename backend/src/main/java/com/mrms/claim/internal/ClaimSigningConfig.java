package com.mrms.claim.internal;

import com.mrms.claim.internal.ClaimDtos.HosForwardRequest;
import com.mrms.esign.SignableAction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The claim actions that must be signed. The payload is read with the same
 * JSON rules as the action's own request, so both compute the same digest.
 */
@Configuration(proxyBeanMethods = false)
class ClaimSigningConfig {

    @Bean
    SignableAction hosCertifySigning(ReviewService reviews, ObjectMapper json) {
        return SignableAction.of(ReviewService.HOS_CERTIFY, "CLAIM", (id, payload) ->
                reviews.describeHosCertify(Long.valueOf(id), json.treeToValue(payload, HosForwardRequest.class)));
    }

    @Bean
    SignableAction sanctionSigning(ReviewService reviews) {
        return SignableAction.of(ReviewService.SANCTION, "CLAIM", (id, payload) ->
                reviews.describeDecision(ReviewService.SANCTION, Long.valueOf(id), text(payload, "remarks")));
    }

    @Bean
    SignableAction rejectSigning(ReviewService reviews) {
        return SignableAction.of(ReviewService.REJECT, "CLAIM", (id, payload) ->
                reviews.describeDecision(ReviewService.REJECT, Long.valueOf(id), text(payload, "reason")));
    }

    static String text(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        return node == null || node.isNull() ? null : com.mrms.shared.web.SafeText.clean(node.asString());
    }
}
