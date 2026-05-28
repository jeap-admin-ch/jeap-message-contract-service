package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageTypeRepositoryReferenceCacheRefresher {

    private final MessageTypeRepositoryReferenceCache referenceCache;

    public MessageTypeRepositoryReferenceCacheRefresher(MessageTypeRepositoryReferenceCache referenceCache) {
        this.referenceCache = referenceCache;
    }

    @Timed(value = "messagetyperepositorycache.init.time",
            description = "Time taken to initialize the local reference repository cache", histogram = true)
    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        if (!referenceCache.isEnabled()) {
            log.debug("Reference repository cache disabled; skipping startup refresh");
            return;
        }
        log.info("Refreshing reference repository cache on startup");
        referenceCache.refreshAll();
    }

    @Timed(value = "messagetyperepositorycache.refresh.time",
            description = "Time taken to refresh the local reference repository cache", histogram = true)
    @Scheduled(cron = "${messages.repository-cache.refresh-cron:0 0 1 * * *}")
    public void refreshScheduled() {
        if (!referenceCache.isEnabled()) {
            return;
        }
        log.info("Refreshing reference repository cache (scheduled)");
        referenceCache.refreshAll();
    }
}
