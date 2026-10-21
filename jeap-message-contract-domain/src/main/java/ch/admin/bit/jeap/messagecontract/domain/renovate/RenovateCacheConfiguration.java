package ch.admin.bit.jeap.messagecontract.domain.renovate;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Caches the data used to answer Renovate datasource requests, see {@link RenovateLookups}.
 * <p>
 * The cache manager is registered as a non-default candidate: it is only used where it is referenced by name, it is not
 * injected into beans that ask for a {@link org.springframework.cache.CacheManager} by type, and it does not keep
 * Spring Boot from configuring the application's default cache manager. Caching elsewhere in the application is
 * therefore not affected by the Renovate caches.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
class RenovateCacheConfiguration {

    static final String CACHE_MANAGER = "renovateCacheManager";
    static final String DEPLOYED_CONTRACTS = "renovate-deployed-contracts";
    static final String CONTRACT_SCHEMAS = "renovate-contract-schemas";
    static final String REGISTRY_SNAPSHOTS = "renovate-registry-snapshots";
    static final String CANDIDATE_SCHEMAS = "renovate-candidate-schemas";

    /**
     * Bounds the memory used per cache. A Renovate run touches one entry per message type and environment, which is
     * far below this bound.
     */
    static final long MAXIMUM_SIZE = 1000;

    /**
     * @param ticker the time source of the caches, only replaced in tests to let the time to live elapse
     */
    @Bean(name = CACHE_MANAGER, defaultCandidate = false)
    CaffeineCacheManager renovateCacheManager(RenovateProperties properties,
                                              @Qualifier(CACHE_MANAGER) ObjectProvider<Ticker> ticker) {
        Duration timeToLive = properties.getCacheTtl().isNegative() ? Duration.ZERO : properties.getCacheTtl();
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .expireAfterWrite(timeToLive)
                .maximumSize(MAXIMUM_SIZE)
                .ticker(ticker.getIfAvailable(Ticker::systemTicker));
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeine);
        // Registering the cache names up front disables the creation of further caches on demand
        cacheManager.setCacheNames(List.of(DEPLOYED_CONTRACTS, CONTRACT_SCHEMAS, REGISTRY_SNAPSHOTS, CANDIDATE_SCHEMAS));
        return cacheManager;
    }
}
