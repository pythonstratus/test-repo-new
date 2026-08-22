package gov.irs.sbse.os.ts.csp.alsentity.ale.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * WHAT CHANGED AND WHY
 *
 * 1. THE KEY MISMATCH (the actual bug).
 *    Read path  (line 301): "case-view-" + org + "-status-" + status + "-elevel-" + level + "-levelValue-" + value
 *    Write path (line 121): "case-view-status-" + status + "-org-" + org
 *    These can never match. All nine cacheInit methods were writing entries no reader could find.
 *    Every request missed and rebuilt. Fixed by routing every read and write through caseViewKey().
 *
 * 2. THE COMMENTED-OUT GUARD (lines 122-126, 132).
 *    Replaced with Caffeine's read-through get(key, loader). Because cacheManager is a
 *    CaffeineCacheManager, that call is atomic per key: concurrent callers for the same key block
 *    and share one computation instead of each starting their own. Your DevTools capture showed the
 *    same caseStats URL firing ~8 times, so this alone removes seven redundant builds.
 *
 * 3. NINE cacheInit METHODS -> one loop.
 *    See warmCaches() below, and read the note there about why the status/level warm-up mostly
 *    could not work at all.
 *
 * 4. TIMING.
 *    loadCaseView() logs row count and elapsed ms so you can tell DB time from hydration time.
 *
 * ONE SIGNATURE TO CONFIRM: buildCaseViewFromDb(...) below. Your existing
 * getCaseViewDataByStatusBuildCache(statusId, org) only takes two arguments, but the read key is
 * scoped by level and levelValue, so the real query must be scoped by them too. Point this method
 * at whatever query currently runs after the cache miss inside getCaseViewDataByStatusAndLevel
 * (the part cut off below line 306 in your screenshot).
 */
@Service
@Slf4j
public class CaseViewService extends AbstractViewService {

    private static final String ENTITY_CACHE = "entityCache";
    private static final List<String> ORGS = List.of("CF", "CP", "AD");

    @Autowired
    @Qualifier("secondaryNamedJdbcTemplate")
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private CaffeineCacheManager cacheManager;

    @Autowired
    @Qualifier("primaryDataSource")
    private DataSource dataSource;

    @Autowired
    private EntEmpService entEmpService;

    private SimpleJdbcCall simpleJdbcCall;

    /**
     * Optional targeted warm-up. Format: ORG:LEVEL:LEVELVALUE, comma separated.
     *   entity.cache.warm-targets=CF:6:221219,CP:6:221219
     * Leave empty to warm nothing and rely purely on load-on-miss.
     */
    @Value("${entity.cache.warm-targets:}")
    private List<String> warmTargets;

    @PostConstruct
    private void initSimpleJdbcCall() {
        this.simpleJdbcCall = new SimpleJdbcCall(dataSource)
                .withFunctionName("pyrflag1");
        this.simpleJdbcCall.compile();
    }

    // ------------------------------------------------------------------------
    // Key builders - the only places a cache key is ever constructed
    // ------------------------------------------------------------------------

    /**
     * Matches your existing read-path format exactly (lines 301-302), because that is the format
     * that actually serves user requests. Note "elevel", not "level" - kept as-is deliberately so
     * this change does not silently invalidate anything already warm in a running instance.
     */
    private static String caseViewKey(String org, StatusForView status, Integer level, String levelValue) {
        return "case-view-"
                .concat(org)
                .concat("-status-").concat(status.name())
                .concat("-elevel-").concat(String.valueOf(level))
                .concat("-levelValue-").concat(levelValue);
    }

    private static String roSplitReportKey(String org) {
        return "case-view-ro-split-report-org-" + org;
    }

    // ------------------------------------------------------------------------
    // Read path
    // ------------------------------------------------------------------------

    public List<CaseView> getCaseViewDataByStatusAndLevel(Integer caseStatusId, String org,
                                                          Integer level, String levelValue)
            throws DataAccessException {

        StatusForView resolved = StatusForView.getEnumById(caseStatusId);
        final StatusForView caseStatus = (resolved == null) ? StatusForView.Open : resolved;

        final String key = caseViewKey(org, caseStatus, level, levelValue);
        Cache cache = cacheManager.getCache(ENTITY_CACHE);

        if (cache == null) {
            log.error("Cache '{}' unavailable - available caches {}. Serving uncached.",
                    ENTITY_CACHE, cacheManager.getCacheNames());
            return loadCaseView(key, caseStatus, org, level, levelValue);
        }

        // Caffeine read-through. On a hit this returns immediately with no rebuild - the behaviour
        // the commented-out isEmpty() guard was supposed to provide. On a miss, exactly one thread
        // computes and the rest wait for its result.
        List<CaseView> result = cache.get(key, () -> loadCaseView(key, caseStatus, org, level, levelValue));

        return (result == null) ? Collections.emptyList() : result;
    }

    /**
     * Expensive path. Only ever reached on a genuine miss.
     *
     * Your logs show 21:34:08.563 -> 21:34:14.077 = 5.5s for 643 rows. That is far too slow for
     * that row count to be raw query time. If this timer reports ~5s while the SQL log shows a fast
     * statement, the cost is row-by-row hydration or serialization, not the database.
     */
    private List<CaseView> loadCaseView(String key, StatusForView caseStatus, String org,
                                        Integer level, String levelValue) {
        log.info("Cache miss - building {}", key);
        long startNs = System.nanoTime();

        List<CaseView> data = buildCaseViewFromDb(caseStatus.getId(), org, level, levelValue);
        if (data == null) {
            data = Collections.emptyList();
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        log.info("Built {} - {} rows in {} ms", key, data.size(), elapsedMs);
        if (elapsedMs > 2000) {
            log.warn("Slow build ({} ms, {} rows) for {} - check for per-row queries",
                    elapsedMs, data.size(), key);
        }
        return data;
    }

    /**
     * CONFIRM THIS AGAINST YOUR CODE. Replace the body with the real query that
     * getCaseViewDataByStatusAndLevel runs on a miss today. If your existing
     * getCaseViewDataByStatusBuildCache genuinely ignores level/levelValue, then every level was
     * sharing one dataset and the level in the cache key was decorative - worth checking before
     * you assume the cache was ever correct.
     */
    private List<CaseView> buildCaseViewFromDb(Integer statusId, String org,
                                               Integer level, String levelValue) {
        return getCaseViewDataByStatusBuildCache(statusId, org, level, levelValue);
    }

    // ------------------------------------------------------------------------
    // RO split report - same read-through treatment
    // ------------------------------------------------------------------------

    public Object getCaseROSplitReportCached(String org) {
        final String key = roSplitReportKey(org);
        Cache cache = cacheManager.getCache(ENTITY_CACHE);

        if (cache == null) {
            return getCaseROSplitReport(org);
        }
        return cache.get(key, () -> {
            log.info("Cache miss - building {}", key);
            long startNs = System.nanoTime();
            Object data = getCaseROSplitReport(org);
            log.info("Built {} in {} ms", key, (System.nanoTime() - startNs) / 1_000_000L);
            return data;
        });
    }

    // ------------------------------------------------------------------------
    // Warm-up - replaces cacheInit() through cacheInit6()
    // ------------------------------------------------------------------------

    /**
     * IMPORTANT: the old status/level warm-up could not have worked even with matching keys.
     *
     * The read key is scoped by (org x status x level x levelValue). levelValue is per-request user
     * input - 221219 in your logs. You cannot pre-warm that cartesian product; there are potentially
     * thousands of levelValues. Nine startup builds could only ever have covered nine of them.
     *
     * So the default here warms only the RO split reports, whose keys are NOT levelValue-scoped and
     * are therefore genuinely reusable. Everything else is load-on-miss, which is now deduplicated
     * and actually caches under a key someone reads.
     *
     * If you have known-hot combinations, list them in entity.cache.warm-targets and they will be
     * warmed sequentially at startup.
     *
     * CALL THIS FROM ANOTHER BEAN. @Async is proxy-based: invoking it from inside this class, or
     * from this class's own @PostConstruct, silently runs it synchronously on the caller's thread.
     * An ApplicationReadyEvent listener in a separate @Component is the safe way.
     */
    @Async("entityServiceExecutor")
    public CompletableFuture<Void> warmCaches() {
        long startNs = System.nanoTime();
        log.info("Cache warm-up started");

        for (String org : ORGS) {
            try {
                getCaseROSplitReportCached(org);
            } catch (Exception e) {
                log.error("Warm-up failed for RO split report org={}", org, e);
            }
        }

        if (warmTargets != null) {
            for (String target : warmTargets) {
                if (target == null || target.isBlank()) {
                    continue;
                }
                String[] parts = target.trim().split(":");
                if (parts.length != 3) {
                    log.warn("Ignoring malformed warm target '{}' - expected ORG:LEVEL:LEVELVALUE", target);
                    continue;
                }
                for (StatusForView status : List.of(StatusForView.All, StatusForView.Open)) {
                    try {
                        getCaseViewDataByStatusAndLevel(
                                status.getId(), parts[0], Integer.valueOf(parts[1]), parts[2]);
                    } catch (Exception e) {
                        log.error("Warm-up failed for {} status={}", target, status.name(), e);
                    }
                }
            }
        }

        log.info("Cache warm-up finished in {} ms", (System.nanoTime() - startNs) / 1_000_000L);
        return CompletableFuture.completedFuture(null);
    }

    // ------------------------------------------------------------------------
    // Eviction
    // ------------------------------------------------------------------------

    public void evict(String org, StatusForView status, Integer level, String levelValue) {
        Cache cache = cacheManager.getCache(ENTITY_CACHE);
        if (cache != null) {
            cache.evict(caseViewKey(org, status, level, levelValue));
        }
    }
}
