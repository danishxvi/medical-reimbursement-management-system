package com.mrms.escalation.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.escalation.SlaAlert;
import com.mrms.identity.AccountSummary;
import com.mrms.identity.Accounts;
import com.mrms.organisation.OrganisationDirectory;
import com.mrms.organisation.OrganisationViews.OfficeRef;
import com.mrms.shared.config.MrmsProperties;
import com.mrms.shared.domain.QueueSource;
import com.mrms.shared.domain.Role;
import com.mrms.shared.security.CurrentUser;
import com.mrms.shared.security.MrmsPrincipal;
import com.mrms.shared.web.BusinessRuleException;
import com.mrms.shared.web.ForbiddenException;
import com.mrms.shared.web.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * What the Zonal Oversight officer (or an administrator, for every zone)
 * sees: records past their time limit right now, and each office's record
 * of delays. Status and dates only; never medical content.
 */
@Service
class OversightService {

    private static final Duration NUDGE_INTERVAL = Duration.ofHours(1);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Kolkata"));
    private static final Map<String, String> STAGES = Map.of(
            "PENDING_HOS", "With Head of School",
            "PENDING_PAO_AUDIT", "PAO scrutiny",
            "PENDING_SANCTION", "Awaiting sanction",
            "PENDING_PHARMACIST", "With pharmacist",
            "PENDING_MEDICAL_OFFICER", "With Medical Officer");

    private final List<QueueSource> sources;
    private final SlaEventRepository events;
    private final SlaNudgeRepository nudges;
    private final SlaWatch watch;
    private final Accounts accounts;
    private final OrganisationDirectory organisation;
    private final ApplicationEventPublisher publisher;
    private final AuditTrail audit;
    private final MrmsProperties props;
    private final Clock clock;

    OversightService(List<QueueSource> sources, SlaEventRepository events, SlaNudgeRepository nudges, SlaWatch watch,
                     Accounts accounts, OrganisationDirectory organisation, ApplicationEventPublisher publisher,
                     AuditTrail audit, MrmsProperties props, Clock clock) {
        this.sources = sources;
        this.events = events;
        this.nudges = nudges;
        this.watch = watch;
        this.accounts = accounts;
        this.organisation = organisation;
        this.publisher = publisher;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    record OverdueItem(String subjectType, Long subjectId, String reference, String stageLabel, String officeType,
                       String officeName, String heldBy, Instant stageEnteredAt, int slaDays, double daysOverdue,
                       String level, Instant lastReminderAt) {
    }

    record OfficeRecord(String officeType, Long officeId, String officeName, String stage, long breaches, long open) {
    }

    record Overview(String zone, int overdue, int escalated, int dueSoon, long breaches90d, List<OverdueItem> items,
                    List<OfficeRecord> offices) {
    }

    @Transactional(readOnly = true)
    Overview overview() {
        String zone = myZone();
        Instant now = clock.instant();
        Names names = new Names();
        List<OverdueItem> items = new ArrayList<>();
        int dueSoon = 0;
        for (QueueSource.Item item : scopedItems(zone)) {
            Duration limit = Duration.ofDays(item.slaDays());
            Duration elapsed = Duration.between(item.stageEnteredAt(), now);
            if (elapsed.compareTo(limit) < 0) {
                if (elapsed.compareTo(limit.multipliedBy(props.sla().reminderPercent()).dividedBy(100)) >= 0) {
                    dueSoon++;
                }
                continue;
            }
            double overdueDays = elapsed.minus(limit).toMinutes() / 1440.0;
            boolean escalated = elapsed.compareTo(limit.multipliedBy(props.sla().escalationFactor())) >= 0;
            Instant lastNudge = nudges.findFirstBySubjectTypeAndSubjectIdOrderByOccurredAtDesc(item.subjectType(),
                    item.subjectId()).map(SlaEntities.SlaNudge::getOccurredAt).orElse(null);
            items.add(new OverdueItem(item.subjectType(), item.subjectId(), item.reference(), item.stageLabel(),
                    item.officeType(), names.office(item.officeType(), item.officeId()),
                    item.assignedTo() == null ? null : names.person(item.assignedTo()), item.stageEnteredAt(),
                    item.slaDays(), Math.round(overdueDays * 10) / 10.0, escalated ? "ESCALATED" : "BREACH",
                    lastNudge));
        }
        items.sort(Comparator.comparingDouble(OverdueItem::daysOverdue).reversed());

        List<SlaEntities.SlaEvent> breaches = events.byLevelSince(SlaEntities.Level.BREACH.name(), now.minus(Duration.ofDays(90))).stream()
                .filter(e -> zone == null || zone.equals(e.getZone()))
                .toList();
        Map<String, List<SlaEntities.SlaEvent>> byOffice = breaches.stream()
                .collect(Collectors.groupingBy(e -> e.getOfficeType() + "|" + e.getOfficeId() + "|" + e.getStage()));
        List<OfficeRecord> offices = byOffice.values().stream().map(list -> {
            SlaEntities.SlaEvent first = list.get(0);
            return new OfficeRecord(first.getOfficeType(), first.getOfficeId(),
                    names.office(first.getOfficeType(), first.getOfficeId()),
                    STAGES.getOrDefault(first.getStage(), first.getStage()), list.size(),
                    list.stream().filter(e -> e.getResolvedAt() == null).count());
        }).sorted(Comparator.comparingLong(OfficeRecord::breaches).reversed()).toList();

        return new Overview(zone, items.size(), (int) items.stream().filter(i -> i.level().equals("ESCALATED")).count(),
                dueSoon, breaches.size(), items, offices);
    }

    /** A reminder sent by hand, at most once an hour per record, and recorded. */
    @Transactional
    void nudge(String subjectType, Long subjectId) {
        MrmsPrincipal me = CurrentUser.get();
        String zone = myZone();
        Instant now = clock.instant();
        QueueSource.Item item = scopedItems(zone).stream()
                .filter(i -> i.subjectType().equals(subjectType) && i.subjectId().equals(subjectId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Record"));
        if (Duration.between(item.stageEnteredAt(), now).compareTo(Duration.ofDays(item.slaDays())) < 0) {
            throw new BusinessRuleException("NOT_OVERDUE", "This record is still within its time limit");
        }
        nudges.findFirstBySubjectTypeAndSubjectIdOrderByOccurredAtDesc(subjectType, subjectId)
                .filter(n -> n.getOccurredAt().isAfter(now.minus(NUDGE_INTERVAL)))
                .ifPresent(n -> {
                    throw new BusinessRuleException("RECENTLY_REMINDED",
                            "A reminder was already sent at " + TIME.format(n.getOccurredAt()) + ". Try again later");
                });
        nudges.save(new SlaEntities.SlaNudge(subjectType, subjectId, item.stage(), me.userId(), now));
        List<Long> recipients = item.assignedTo() != null ? List.of(item.assignedTo())
                : accounts.activeUserIds(item.officialRole(), item.officeId());
        audit.record("OVERSIGHT_REMINDER", subjectType, subjectId, item.reference() + ", " + item.stageLabel());
        publisher.publishEvent(new SlaAlert("NUDGE", subjectType, subjectId, item.reference(), item.stageLabel(),
                item.slaDays(), recipients, null, false));
    }

    private List<QueueSource.Item> scopedItems(String zone) {
        return sources.stream().flatMap(s -> s.openItems().stream())
                .filter(i -> i.slaDays() > 0 && i.stageEnteredAt() != null)
                .filter(i -> zone == null || zone.equals(watch.zoneOf(i.schoolId())))
                .toList();
    }

    /** The officer's zone; null for an administrator, who sees every zone. */
    private String myZone() {
        MrmsPrincipal me = CurrentUser.get();
        if (me.role() == Role.ADMIN) {
            return null;
        }
        if (me.role() != Role.OVERSIGHT) {
            throw new ForbiddenException("Only oversight officers and administrators see time limits");
        }
        return accounts.find(me.userId()).map(AccountSummary::zone).filter(Objects::nonNull)
                .orElseThrow(() -> new BusinessRuleException("NO_ZONE", "Your account is not linked to a zone"));
    }

    /** Names looked up once per request. */
    private final class Names {

        private final Map<String, String> offices = new HashMap<>();
        private final Map<Long, String> people = new HashMap<>();

        String office(String type, Long id) {
            return offices.computeIfAbsent(type + id, k -> switch (type) {
                case "SCHOOL" -> organisation.school(id).map(s -> s.name()).orElse("School " + id);
                case "PAO" -> organisation.pao(id).map(OfficeRef::name).orElse("PAO " + id);
                default -> organisation.dispensary(id).map(OfficeRef::name).orElse("Dispensary " + id);
            });
        }

        String person(Long id) {
            return people.computeIfAbsent(id, k -> accounts.fullNames(List.of(id)).getOrDefault(id, "Official"));
        }
    }
}
