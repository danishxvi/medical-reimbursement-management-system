package com.mrms.identity.internal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public legal texts. The privacy notice must be readable before signing in. */
@RestController
@RequestMapping("/api/legal")
class LegalController {

    @GetMapping("/privacy")
    PrivacyNotice.View privacy() {
        return PrivacyNotice.view();
    }
}
