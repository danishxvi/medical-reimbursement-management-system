package com.mrms.notification.internal;

import com.mrms.shared.config.MrmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Sends waiting e-mail and SMS messages, with retries and backoff. */
@Component
class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int BATCH = 50;

    private final OutboundMessageRepository messages;
    private final MessageChannels channels;
    private final MrmsProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    OutboxDispatcher(OutboundMessageRepository messages, MessageChannels channels, MrmsProperties props, Clock clock,
                     PlatformTransactionManager transactions) {
        this.messages = messages;
        this.channels = channels;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${mrms.notifications.dispatch-interval:PT30S}", initialDelayString = "PT20S")
    void scheduled() {
        if (props.jobs().enabled()) {
            dispatch(clock.instant());
        }
    }

    /** Sends every message due at {@code now}; returns how many were delivered. */
    int dispatch(Instant now) {
        List<Long> due = messages.due(OutboundMessage.Status.PENDING, now, Limit.of(BATCH));
        int sent = 0;
        for (Long id : due) {
            // Claim first, in its own transaction, so no other server sends the same message
            Integer claimed = tx.execute(s -> messages.claim(id, OutboundMessage.Status.PENDING,
                    OutboundMessage.Status.SENDING));
            if (claimed == null || claimed == 0) {
                continue;
            }
            OutboundMessage m = messages.findById(id).orElseThrow();
            String error = null;
            try {
                channels.send(m);
            } catch (RuntimeException e) {
                error = e.getMessage();
                log.warn("Could not send {} message {}: {}", m.getChannel(), id, error);
            }
            String finalError = error;
            tx.executeWithoutResult(s -> messages.findById(id).ifPresent(msg -> {
                if (finalError == null) {
                    msg.sent(clock.instant());
                } else {
                    msg.failed(finalError, clock.instant());
                }
            }));
            if (error == null) {
                sent++;
            }
        }
        return sent;
    }
}
