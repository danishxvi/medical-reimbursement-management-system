package com.mrms.esign.internal;

import com.mrms.esign.Signatures;
import com.mrms.shared.config.MrmsProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.Map;

@RestController
class EsignController {

    /** Largest response accepted from an ESP (a signature with a short certificate chain is a few KB). */
    private static final int MAX_RESPONSE_CHARS = 200_000;

    private final EsignService service;
    private final MrmsProperties props;

    EsignController(EsignService service, MrmsProperties props) {
        this.service = service;
        this.props = props;
    }

    /** How the portal signs, so the interface can show a password box or the eSign button. */
    @GetMapping("/api/esign/config")
    Map<String, Object> config() {
        Signatures.SigningMode mode = service.mode();
        return Map.of("mode", mode.name(),
                "providerName", props.esign().providerName() == null ? "" : props.esign().providerName(),
                "simulator", mode == Signatures.SigningMode.ESIGN && props.esign().simulator());
    }

    record StartRequest(
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z_]+") String purpose,
            @NotBlank @Size(max = 40) String subjectId,
            @NotNull JsonNode payload) {
    }

    @PostMapping("/api/esign/start")
    EsignService.Started start(@Valid @RequestBody StartRequest body) {
        return service.start(body.purpose(), body.subjectId(), body.payload());
    }

    @GetMapping("/api/esign/{txn}")
    EsignService.Status status(@PathVariable String txn) {
        return service.status(txn);
    }

    /** The signed request, for the launch page that posts it to the ESP. */
    @GetMapping("/api/esign/{txn}/request")
    EsignService.Started request(@PathVariable String txn) {
        return service.request(txn);
    }

    /**
     * The ESP posts the signed response here through the user's browser. The
     * session cookie is not sent on this cross site request (SameSite=Strict)
     * and is not needed: the response is authenticated by the ESP's signature
     * and bound to a random, single use transaction id.
     */
    @PostMapping(value = "/api/esign/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Void> callback(@RequestParam Map<String, String> form) {
        String xml = form.get(props.esign().responseField());
        EsignService.Outcome outcome = xml == null || xml.isBlank() || xml.length() > MAX_RESPONSE_CHARS
                ? new EsignService.Outcome(null, false, "Empty response")
                : service.complete(xml);
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, URI.create(service.completionUrl(outcome)).toString())
                .build();
    }
}
