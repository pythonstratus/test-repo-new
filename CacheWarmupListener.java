package gov.irs.sbse.os.ts.csp.alsentity.ale.config;

import gov.irs.sbse.os.ts.csp.alsentity.ale.service.CaseViewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Triggers cache warm-up from OUTSIDE CaseViewService.
 *
 * This class exists for one reason: @Async is implemented with a proxy. If CaseViewService calls
 * its own warmCaches() - directly, or from its own @PostConstruct - the proxy is bypassed and the
 * method runs synchronously on the calling thread. If any of your nine cacheInit methods were being
 * invoked that way, they were never async at all, and a request thread was wearing the full cost of
 * cache initialization. That would look exactly like "cache creation is taking a very long time".
 *
 * ApplicationReadyEvent also fires after the context is fully up, so warm-up cannot deadlock
 * against beans that are still initializing.
 */
@Slf4j
@Component
public class CacheWarmupListener {

    @Autowired
    private CaseViewService caseViewService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("Application ready - triggering cache warm-up");
        caseViewService.warmCaches();
    }
}
