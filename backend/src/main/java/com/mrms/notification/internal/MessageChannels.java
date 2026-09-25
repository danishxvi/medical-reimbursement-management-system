package com.mrms.notification.internal;

import com.mrms.shared.config.MrmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Delivery of e-mail and SMS. Each channel is off, log (development: the
 * message is written to the log instead of being sent) or real: SMTP for
 * e-mail, an HTTP gateway for SMS (for example the NIC SMS gateway, with a
 * DLT registered template).
 */
@Component
class MessageChannels {

    private static final Logger log = LoggerFactory.getLogger(MessageChannels.class);

    private final MrmsProperties props;
    private final ObjectProvider<JavaMailSender> mail;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    MessageChannels(MrmsProperties props, ObjectProvider<JavaMailSender> mail) {
        this.props = props;
        this.mail = mail;
    }

    boolean emailEnabled() {
        return !"off".equalsIgnoreCase(props.notifications().email().mode());
    }

    boolean smsEnabled() {
        return !"off".equalsIgnoreCase(props.notifications().sms().mode());
    }

    /** Sends one message; throws with a short reason if it could not be delivered. */
    void send(OutboundMessage m) {
        if (m.getChannel() == OutboundMessage.Channel.EMAIL) {
            email(m);
        } else {
            sms(m);
        }
    }

    private void email(OutboundMessage m) {
        MrmsProperties.Notify.Email cfg = props.notifications().email();
        if ("log".equalsIgnoreCase(cfg.mode())) {
            log.info("E-mail (not sent, log mode) to {}: {}", mask(m.getDestination()), m.getSubject());
            return;
        }
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("No mail server configured (spring.mail.host)");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(cfg.from());
        message.setTo(m.getDestination());
        message.setSubject(m.getSubject());
        message.setText(m.getBody());
        sender.send(message);
    }

    private void sms(OutboundMessage m) {
        MrmsProperties.Notify.Sms cfg = props.notifications().sms();
        if ("log".equalsIgnoreCase(cfg.mode())) {
            log.info("SMS (not sent, log mode) to {}: {}", mask(m.getDestination()), m.getBody());
            return;
        }
        if (cfg.urlTemplate() == null || cfg.urlTemplate().isBlank()) {
            throw new IllegalStateException("No SMS gateway configured (MRMS_SMS_URL_TEMPLATE)");
        }
        String url = cfg.urlTemplate()
                .replace("{mobile}", enc(m.getDestination()))
                .replace("{message}", enc(m.getBody()))
                .replace("{templateId}", enc(cfg.templateId() == null ? "" : cfg.templateId()));
        try {
            HttpResponse<Void> response = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("SMS gateway answered " + response.statusCode());
            }
        } catch (IOException e) {
            throw new IllegalStateException("SMS gateway not reachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Destinations are personal data: logs show only enough to tell them apart. */
    static String mask(String destination) {
        if (destination == null) {
            return "";
        }
        int at = destination.indexOf('@');
        if (at > 1) {
            return destination.charAt(0) + "***" + destination.substring(at).toLowerCase(Locale.ROOT);
        }
        return destination.length() > 4 ? "******" + destination.substring(destination.length() - 4) : "****";
    }
}
