package com.mrms.esign.internal;

import com.mrms.shared.config.MrmsProperties;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * A stand in for an eSign Service Provider, for development and tests only
 * (dev or test profile and mrms.esign.simulator=true). It behaves like a
 * real ESP from the portal's point of view: it checks the ASP's signature on
 * the request, "authenticates" the user with a fixed OTP, issues a short
 * lived certificate in the name given, signs the document hash and returns a
 * signed response.
 *
 * <p>Like a real ESP page, it is the only place an Aadhaar number is typed.
 * The number is checked for format (Verhoeff check digit) and then
 * discarded; it is never stored or logged.
 */
@RestController
@Profile({"dev", "test"})
@ConditionalOnProperty(prefix = "mrms.esign", name = "simulator", havingValue = "true")
class EspSimulatorController {

    static final String OTP = "123456";

    private final EsignKeys keys;
    private final MrmsProperties props;
    private final Clock clock;

    EspSimulatorController(EsignKeys keys, MrmsProperties props, Clock clock) {
        this.keys = keys;
        this.props = props;
        this.clock = clock;
    }

    @PostMapping(value = "/api/dev/esp/sign", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<String> sign(@RequestParam Map<String, String> form) {
        String xml = form.get(props.esign().requestField());
        Document request;
        try {
            request = EsignProtocol.verifiedRequest(xml, List.of(keys.aspCertificate));
        } catch (RuntimeException e) {
            return page("Request refused", "<p class=err>" + esc(e.getMessage()) + "</p>");
        }
        return authPage(request, Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8)), null);
    }

    @PostMapping(value = "/api/dev/esp/authorise", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<String> authorise(@RequestParam("request") String requestB64,
                                     @RequestParam(value = "aadhaar", defaultValue = "") String aadhaar,
                                     @RequestParam(value = "name", defaultValue = "") String name,
                                     @RequestParam(value = "otp", defaultValue = "") String otp,
                                     @RequestParam(value = "decision", defaultValue = "sign") String decision) {
        String xml = new String(Base64.getDecoder().decode(requestB64), StandardCharsets.UTF_8);
        Document request = EsignProtocol.verifiedRequest(xml, List.of(keys.aspCertificate));
        Element root = request.getDocumentElement();
        String txn = root.getAttribute("txn");
        String responseUrl = root.getAttribute("responseUrl");
        Instant now = clock.instant();
        EsignKeys.Simulator sim = keys.simulator;

        if ("cancel".equals(decision)) {
            String response = EsignProtocol.signedResponse(txn, false, "USER_CANCELLED", "Signing cancelled by the user",
                    null, null, now, sim.espKey(), sim.espCertificate());
            return returnPage(responseUrl, response, "You cancelled signing.");
        }
        String digits = aadhaar.replaceAll("\\s", "");
        String problem = !(digits.matches("\\d{12}") || digits.matches("\\d{16}")) || !verhoeff(digits)
                ? "Enter a valid 12 digit Aadhaar number or 16 digit Virtual ID"
                : name.isBlank() || name.length() > 100 ? "Enter the name as in Aadhaar"
                : !OTP.equals(otp.trim()) ? "The OTP is not correct" : null;
        if (problem != null) {
            return authPage(request, requestB64, problem);
        }

        try {
            KeyPair userKeys = EsignKeys.rsa();
            X509Certificate userCert = issue(name.trim(), userKeys, sim, now);
            String hashHex = root.getElementsByTagName("InputHash").item(0).getTextContent().trim();
            byte[] pkcs7 = EsignProtocol.signHash(HexFormat.of().parseHex(hashHex), userKeys.getPrivate(), userCert,
                    sim.caCertificate());
            String response = EsignProtocol.signedResponse(txn, true, null, null, userCert, pkcs7, now,
                    sim.espKey(), sim.espCertificate());
            return returnPage(responseUrl, response, "Signed by " + name.trim() + ".");
        } catch (Exception e) {
            throw new IllegalStateException("Simulator could not sign", e);
        }
    }

    // ------------------------------------------------------------------

    private ResponseEntity<String> authPage(Document request, String requestB64, String error) {
        Element hash = (Element) request.getElementsByTagName("InputHash").item(0);
        String body = "<p class=muted>Simulated Aadhaar eSign for development. A real provider shows its own page "
                + "here and sends a one time password to the mobile number linked with Aadhaar.</p>"
                + "<div class=box><div class=label>Document</div><div>" + esc(hash.getAttribute("docInfo"))
                + "</div><div class=label>SHA-256 to be signed</div><code>" + esc(hash.getTextContent()) + "</code></div>"
                + (error == null ? "" : "<p class=err>" + esc(error) + "</p>")
                + "<form method=post action='/api/dev/esp/authorise'>"
                + "<input type=hidden name=request value='" + esc(requestB64) + "'>"
                + "<label>Aadhaar number or Virtual ID<input name=aadhaar inputmode=numeric autocomplete=off "
                + "placeholder='12 or 16 digits'></label>"
                + "<label>Name as in Aadhaar (simulated eKYC)<input name=name autocomplete=off></label>"
                + "<label>OTP<input name=otp inputmode=numeric autocomplete=one-time-code placeholder='" + OTP
                + " in the simulator'></label>"
                + "<div class=row><button name=decision value=cancel class=ghost>Cancel</button>"
                + "<button name=decision value=sign>Sign</button></div></form>";
        return page("Sign with Aadhaar", body);
    }

    private ResponseEntity<String> returnPage(String responseUrl, String responseXml, String message) {
        String body = "<p>" + esc(message) + "</p>"
                + "<form method=post action='" + esc(responseUrl) + "'>"
                + "<input type=hidden name='" + esc(props.esign().responseField()) + "' value='" + esc(responseXml) + "'>"
                + "<div class=row><button>Return to MRMS</button></div></form>"
                + "<p class=muted>A real provider returns you automatically.</p>";
        return page("eSign", body);
    }

    private static ResponseEntity<String> page(String title, String body) {
        String html = "<!doctype html><html lang=en><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width, initial-scale=1'><title>" + esc(title) + "</title>"
                + "<style>body{font:15px system-ui,sans-serif;background:#fff7ee;color:#2b1d12;margin:0;padding:24px}"
                + "main{max-width:460px;margin:auto;background:#fff;border:1px solid #f1dcc6;border-radius:14px;padding:24px}"
                + "h1{font-size:20px;color:#c25400;margin:0 0 12px}.muted{color:#735a45;font-size:13px}"
                + ".box{background:#fff4e8;border-radius:10px;padding:12px;margin:12px 0}"
                + ".label{font-size:11px;text-transform:uppercase;color:#735a45;margin-top:6px}"
                + "code{font-size:11px;word-break:break-all}label{display:block;margin:12px 0;font-size:13px}"
                + "input{display:block;width:100%;box-sizing:border-box;margin-top:4px;padding:10px;border:1px solid #f1dcc6;"
                + "border-radius:10px;font:inherit}.row{display:flex;gap:8px;justify-content:flex-end;margin-top:16px}"
                + "button{padding:10px 16px;border-radius:10px;border:1px solid #c25400;background:#c25400;color:#fff;font:inherit}"
                + "button.ghost{background:#fff;color:#c25400}.err{color:#a94700;font-weight:600}</style></head>"
                + "<body><main><h1>" + esc(title) + "</h1>" + body + "</main></body></html>";
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                // Own page: inline styles only, forms only to this origin and the portal's return address
                .header("Content-Security-Policy",
                        "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'")
                .header("Cache-Control", "no-store")
                .body(html);
    }

    private static X509Certificate issue(String name, KeyPair keys, EsignKeys.Simulator sim, Instant now)
            throws Exception {
        X500Name subject = new X500Name("CN=" + name.replaceAll("[,=+<>#;\"\\\\]", " ")
                + ", OU=Simulated Aadhaar eKYC, O=MRMS development");
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                sim.caCertificate(), EsignKeys.serial(), Date.from(now.minusSeconds(60)),
                Date.from(now.plus(Duration.ofMinutes(30))), subject, keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(sim.caKey())));
    }

    /** Verhoeff check digit, as used by Aadhaar and Virtual IDs. */
    static boolean verhoeff(String digits) {
        int[][] d = {
                {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, {1, 2, 3, 4, 0, 6, 7, 8, 9, 5}, {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
                {3, 4, 0, 1, 2, 8, 9, 5, 6, 7}, {4, 0, 1, 2, 3, 9, 5, 6, 7, 8}, {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
                {6, 5, 9, 8, 7, 1, 0, 4, 3, 2}, {7, 6, 5, 9, 8, 2, 1, 0, 4, 3}, {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
                {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}};
        int[][] p = {
                {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, {1, 5, 7, 6, 2, 8, 3, 0, 9, 4}, {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
                {8, 9, 1, 6, 0, 4, 3, 5, 2, 7}, {9, 4, 5, 3, 1, 2, 6, 8, 7, 0}, {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
                {2, 7, 9, 3, 8, 0, 6, 4, 1, 5}, {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}};
        int c = 0;
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(digits.length() - 1 - i) - '0';
            c = d[c][p[i % 8][digit]];
        }
        return c == 0;
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }
}
