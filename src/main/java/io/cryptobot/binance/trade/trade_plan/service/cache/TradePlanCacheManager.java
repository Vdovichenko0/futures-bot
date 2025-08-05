package io.cryptobot.binance.trade.trade_plan.service.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradePlanCacheManager {
    
    private final CacheManager cacheManager;
    
    private static final String CACHE_GET_PLAN = "tradePlanGetPlan";
    private static final String CACHE_GET_ALL = "tradePlanGetAll";
    private static final String CACHE_GET_ALL_ACTIVE_TRUE = "tradePlanGetAllActiveTrue";
    private static final String CACHE_GET_ALL_ACTIVE_FALSE = "tradePlanGetAllActiveFalse";
    
    /**
     * Сбрасывает кеш для конкретного плана по символу
     */
    public void evictPlanCache(String symbol) {
        var cache = cacheManager.getCache(CACHE_GET_PLAN);
        cache.evict(symbol);
        log.debug("Evicted cache for plan: {}", symbol);
    }
    
    /**
     * Сбрасывает кеш для нескольких планов одновременно
     */
    public void evictPlansCache(List<String> symbols) {
        symbols.forEach(symbol -> {
            var cache = cacheManager.getCache(CACHE_GET_PLAN);
            cache.evict(symbol);
        });
        log.debug("Evicted cache for {} plans", symbols.size());
    }
    
    /**
     * Сбрасывает кеш для нескольких планов одновременно
     */
    public void evictPlansCache(Set<String> symbols) {
        symbols.forEach(symbol -> {
            var cache = cacheManager.getCache(CACHE_GET_PLAN);
            cache.evict(symbol);
        });
        log.debug("Evicted cache for {} plans", symbols.size());
    }
    
    /**
     * Сбрасывает все кеши связанные с планами
     */
    public void evictAllPlanCaches() {
        evictCache(CACHE_GET_PLAN);
        evictCache(CACHE_GET_ALL);
        evictCache(CACHE_GET_ALL_ACTIVE_TRUE);
        evictCache(CACHE_GET_ALL_ACTIVE_FALSE);
        log.debug("Evicted all trade plan caches");
    }
    
    /**
     * Сбрасывает кеши для списков планов (getAll, getAllActiveTrue, getAllActiveFalse)
     */
    public void evictListCaches() {
        String[] listCacheNames = {
            CACHE_GET_ALL,
            CACHE_GET_ALL_ACTIVE_TRUE, 
            CACHE_GET_ALL_ACTIVE_FALSE
        };
        
        for (String cacheName : listCacheNames) {
            evictCache(cacheName);
        }
        log.debug("Evicted all trade plan list caches");
    }
    
    /**
     * Сбрасывает кеш для конкретного плана и всех списков
     */
    public void evictPlanAndListCaches(String symbol) {
        evictPlanCache(symbol);
        evictListCaches();
        log.debug("Evicted cache for plan {} and all list caches", symbol);
    }
    
    /**
     * Сбрасывает кеш для нескольких планов и все списки
     */
    public void evictPlansAndListCaches(List<String> symbols) {
        evictPlansCache(symbols);
        evictListCaches();
        log.debug("Evicted cache for {} plans and all list caches", symbols.size());
    }
    
    /**
     * Сбрасывает кеш для нескольких планов и все списки
     */
    public void evictPlansAndListCaches(Set<String> symbols) {
        evictPlansCache(symbols);
        evictListCaches();
        log.debug("Evicted cache for {} plans and all list caches", symbols.size());
    }
    
    /**
     * Сбрасывает все кеши TradePlan
     */
    public void evictAllTradePlanCaches() {
        String[] allCacheNames = {
            CACHE_GET_PLAN,
            CACHE_GET_ALL,
            CACHE_GET_ALL_ACTIVE_TRUE, 
            CACHE_GET_ALL_ACTIVE_FALSE
        };
        
        for (String cacheName : allCacheNames) {
            evictCache(cacheName);
        }
        log.debug("Evicted all trade plan caches");
    }
    
    /**
     * Проверяет, существует ли кеш для конкретного плана
     */
    public boolean isPlanCached(String symbol) {
        var cache = cacheManager.getCache(CACHE_GET_PLAN);
        return cache != null && cache.get(symbol) != null;
    }
    
    /**
     * Получает статистику кеша
     */
    public void logCacheStats() {
        String[] cacheNames = {
            CACHE_GET_PLAN,
            CACHE_GET_ALL,
            CACHE_GET_ALL_ACTIVE_TRUE, 
            CACHE_GET_ALL_ACTIVE_FALSE
        };
        
        for (String cacheName : cacheNames) {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null && cache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache) {
                com.github.benmanes.caffeine.cache.Cache<?, ?> nativeCache = 
                    (com.github.benmanes.caffeine.cache.Cache<?, ?>) cache.getNativeCache();
                log.info("Cache {} stats: size={}, estimatedSize={}", 
                    cacheName, nativeCache.asMap().size(), nativeCache.estimatedSize());
            }
        }
    }
    
    private void evictCache(String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
} 