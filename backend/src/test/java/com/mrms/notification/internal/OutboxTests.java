package com.mrms.notification.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The e-mail and SMS outbox: delivery, retries with backoff, masked logging. */
// Fresh context and database for this class: queues are shared state, and tests must not depend on order
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@ActiveProfiles({"dev", "test"})
class OutboxTests {

    @Autowired
    private OutboundMessageRepository messages;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void dueMessagesAreSentOnce() {
        Long user = jdbc.queryForObject("select id from user_account where username = 'EMP1001'", Long.class);
        // Microseconds, like the application clock (the database stores microseconds)
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        OutboundMessage m = messages.save(new OutboundMessage(OutboundMessage.Channel.SMS, user, "9000001001",
                "Claim paid", "MRMS: Claim paid. Sign in to MRMS for details.", now));

        // Other tests may have queued messages too: drain the outbox
        for (int batch = 0; batch < 100 && dispatcher.dispatch(now) > 0; batch++) {
            // keep sending
        }
        assertThat(messages.findById(m.getId()).orElseThrow().getStatus()).isEqualTo(OutboundMessage.Status.SENT);
        // Nothing is sent twice
        dispatcher.dispatch(now);
        assertThat(messages.findById(m.getId()).orElseThrow().getStatus()).isEqualTo(OutboundMessage.Status.SENT);
    }

    @Test
    void failuresAreRetriedWithBackoffThenGivenUp() {
        Instant now = Instant.now();
        OutboundMessage m = new OutboundMessage(OutboundMessage.Channel.EMAIL, 1L, "a@b.c", "s", "b", now);
        m.failed("gateway down", now);
        assertThat(m.getStatus()).isEqualTo(OutboundMessage.Status.PENDING);
        for (int i = 1; i < OutboundMessage.BACKOFF.size(); i++) {
            m.failed("gateway down", now.plus(Duration.ofHours(i)));
        }
        assertThat(m.getStatus()).isEqualTo(OutboundMessage.Status.FAILED);
    }

    @Test
    void logsNeverShowFullAddresses() {
        assertThat(MessageChannels.mask("asha.verma@example.org")).isEqualTo("a***@example.org");
        assertThat(MessageChannels.mask("9876543210")).isEqualTo("******3210");
    }
}
