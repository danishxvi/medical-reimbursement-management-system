package com.mrms.rates.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.rates.RateBasis;
import com.mrms.rates.RateCsv;
import com.mrms.rates.RateLookup;
import com.mrms.rates.internal.RateEntities.RateItem;
import com.mrms.rates.internal.RateEntities.RateList;
import com.mrms.shared.security.CurrentUser;
import com.mrms.shared.web.BusinessRuleException;
import com.mrms.shared.web.NotFoundException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

interface RateListRepository extends JpaRepository<RateList, Long> {

    List<RateList> findAllByOrderByEffectiveFromDesc();

    List<RateList> findByActiveTrue();

    boolean existsByCodeIgnoreCase(String code);
}

interface RateItemRepository extends JpaRepository<RateItem, Long> {

    Optional<RateItem> findByRateListIdAndCode(Long rateListId, String code);

    @Query("""
            select i from RateItem i
            where i.rateListId = :listId
              and (upper(i.code) like :prefix or lower(i.name) like :contains)
            order by i.code
            """)
    List<RateItem> search(@Param("listId") Long listId, @Param("prefix") String prefix,
                          @Param("contains") String contains, Limit limit);

    @Query("""
            select i from RateItem i
            where i.rateListId = :listId
              and (:q is null or upper(i.code) like :q or upper(i.name) like :q)
            """)
    Page<RateItem> page(@Param("listId") Long listId, @Param("q") String q, Pageable pageable);
}

@Service
class RateService implements RateLookup {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final RateListRepository lists;
    private final RateItemRepository items;
    private final AuditTrail audit;
    private final Clock clock;

    RateService(RateListRepository lists, RateItemRepository items, AuditTrail audit, Clock clock) {
        this.lists = lists;
        this.items = items;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<RateQuote> quote(String code, LocalDate serviceDate, RateBasis basis, String wardEntitlement,
                                     boolean indoor) {
        if (code == null || code.isBlank() || basis == null || basis == RateBasis.AS_BILLED || serviceDate == null) {
            return Optional.empty();
        }
        return listFor(serviceDate).flatMap(list -> items.findByRateListIdAndCode(list.getId(),
                        code.trim().toUpperCase(Locale.ROOT))
                .map(item -> {
                    RateCalculator.Result r = RateCalculator.apply(item, basis, wardEntitlement, indoor);
                    return new RateQuote(item.getCode(), item.getName(), item.getSpeciality(), list.getCode(),
                            list.getTitle(), basis, r.base(), r.applicable(),
                            list.getCode() + "/" + item.getCode() + "/" + basis.name(),
                            r.explanation() + " (" + list.getTitle() + ")");
                }));
    }

    /** The active list covering a date; the most recent one wins if two overlap. */
    Optional<RateList> listFor(LocalDate date) {
        return lists.findByActiveTrue().stream()
                .filter(l -> l.covers(date))
                .max(Comparator.comparing(RateList::getEffectiveFrom));
    }

    @Transactional(readOnly = true)
    List<RateDtos.RateSearchHit> search(String q, LocalDate date) {
        LocalDate on = date == null ? LocalDate.now(clock.withZone(IST)) : date;
        String term = q == null ? "" : q.trim();
        if (term.length() < 2) {
            return List.of();
        }
        return listFor(on).map(list -> items.search(list.getId(), term.toUpperCase(Locale.ROOT) + "%",
                        "%" + term.toLowerCase(Locale.ROOT) + "%", Limit.of(25)).stream()
                .map(i -> RateDtos.RateSearchHit.of(i, list))
                .toList()).orElse(List.of());
    }

    // ------------------------------------------------------------------
    // Administration
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<RateDtos.RateListView> allLists() {
        return lists.findAllByOrderByEffectiveFromDesc().stream().map(RateDtos.RateListView::of).toList();
    }

    @Transactional(readOnly = true)
    Page<RateDtos.RateItemView> listItems(Long listId, String q, Pageable pageable) {
        lists.findById(listId).orElseThrow(() -> new NotFoundException("Rate list"));
        String term = q == null || q.isBlank() ? null : "%" + q.trim().toUpperCase(Locale.ROOT) + "%";
        return items.page(listId, term, pageable).map(RateDtos.RateItemView::of);
    }

    /**
     * Imports a list as inactive. An administrator reviews it and then
     * activates it; nothing changes for claims until then.
     */
    @Transactional
    RateDtos.RateListView importList(RateDtos.ImportRequest meta, byte[] csv) {
        String code = meta.code().trim().toUpperCase(Locale.ROOT);
        if (lists.existsByCodeIgnoreCase(code)) {
            throw new BusinessRuleException("DUPLICATE_CODE", "A rate list with code " + code + " already exists");
        }
        List<RateCsv.Row> rows;
        try {
            rows = RateCsv.parse(new ByteArrayInputStream(csv));
        } catch (RateCsv.InvalidException e) {
            throw new BusinessRuleException("INVALID_RATE_FILE", e.getMessage());
        } catch (IOException e) {
            throw new BusinessRuleException("INVALID_RATE_FILE", "The file could not be read");
        }
        RateList list = lists.save(new RateList(code, meta.title().trim(), meta.orderReference().trim(),
                blankToNull(meta.sourceUrl()), sha256(csv), meta.cityTier(), meta.effectiveFrom(),
                blankToNull(meta.notes()), rows.size(), CurrentUser.id(), clock.instant()));
        items.saveAll(rows.stream().map(r -> new RateItem(list.getId(), r.serialNo(), r.code(), r.name(),
                r.speciality(), r.nonNabh(), r.nabh(), r.superSpeciality())).toList());
        audit.record("RATE_LIST_IMPORTED", "RATE_LIST", list.getId(),
                code + ", " + rows.size() + " rates, effective " + meta.effectiveFrom());
        return RateDtos.RateListView.of(list);
    }

    /** Activating a list ends any older open ended list the day before it starts. */
    @Transactional
    RateDtos.RateListView activate(Long id) {
        RateList list = lists.findById(id).orElseThrow(() -> new NotFoundException("Rate list"));
        lists.findByActiveTrue().stream()
                .filter(other -> !other.getId().equals(id) && other.getEffectiveFrom().isBefore(list.getEffectiveFrom()))
                .forEach(other -> other.endBefore(list.getEffectiveFrom()));
        list.activate();
        audit.record("RATE_LIST_ACTIVATED", "RATE_LIST", id, list.getCode());
        return RateDtos.RateListView.of(list);
    }

    @Transactional
    RateDtos.RateListView deactivate(Long id) {
        RateList list = lists.findById(id).orElseThrow(() -> new NotFoundException("Rate list"));
        list.deactivate();
        audit.record("RATE_LIST_DEACTIVATED", "RATE_LIST", id, list.getCode());
        return RateDtos.RateListView.of(list);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
