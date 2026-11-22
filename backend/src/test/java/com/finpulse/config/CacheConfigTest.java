package com.finpulse.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finpulse.dto.BudgetDto;
import com.finpulse.dto.CategoryBreakdownDto;
import com.finpulse.dto.DashboardSummaryDto;
import com.finpulse.dto.MonthlyCashflowDto;
import com.finpulse.dto.TransactionDto;
import com.finpulse.entity.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheConfigTest {

    @Test
    void jacksonRedisSerializerShouldRoundTripDashboardSummaryDtoWithoutJavaSerialization() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        Jackson2JsonRedisSerializer<DashboardSummaryDto> serializer =
                new Jackson2JsonRedisSerializer<>(mapper, DashboardSummaryDto.class);

        Instant now = Instant.parse("2026-11-21T13:30:00Z");
        DashboardSummaryDto dto = new DashboardSummaryDto(
                11,
                2026,
                new BigDecimal("10000000.00"),
                new BigDecimal("3500000.00"),
                new BigDecimal("6500000.00"),
                new BigDecimal("25000000.00"),
                5L,
                List.of(new MonthlyCashflowDto(2026, 11, "Nov 2026",
                        new BigDecimal("10000000.00"), new BigDecimal("3500000.00"), new BigDecimal("6500000.00"))),
                List.of(new CategoryBreakdownDto(UUID.randomUUID(), "Makanan", "#F59E0B", "utensils",
                        new BigDecimal("3500000.00"), new BigDecimal("100.00"))),
                List.of(new BudgetDto(UUID.randomUUID(), UUID.randomUUID(), "Makanan", "utensils", "#F59E0B",
                        11, 2026, new BigDecimal("2000000.00"), new BigDecimal("3500000.00"),
                        new BigDecimal("-1500000.00"), new BigDecimal("175.00"), true, now, now)),
                List.of(new TransactionDto(UUID.randomUUID(), UUID.randomUUID(), "Makanan", "utensils", "#F59E0B",
                        TransactionType.EXPENSE, new BigDecimal("50000.00"), "Kopi", LocalDate.of(2026, 11, 21),
                        null, now, now)),
                now,
                false
        );

        byte[] serialized = serializer.serialize(dto);
        assertThat(serialized).isNotNull();
        String json = new String(serialized, StandardCharsets.UTF_8);
        assertThat(json).contains("\"generatedAt\":\"2026-11-21T13:30:00Z\"");
        assertThat(json).contains("\"isOverBudget\":true");
        assertThat(json).contains("\"transactionDate\":\"2026-11-21\"");

        DashboardSummaryDto deserialized = serializer.deserialize(serialized);
        assertThat(deserialized).isEqualTo(dto);
    }

    @Test
    void cacheErrorHandlerShouldSwallowRedisOutageExceptionsWithoutThrowing() {
        CacheConfig cacheConfig = new CacheConfig();
        CacheErrorHandler errorHandler = cacheConfig.errorHandler();
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn(CacheConfig.DASHBOARD_SUMMARY_CACHE);
        RuntimeException outage = new RuntimeException("Redis connection refused");

        assertThatCode(() -> errorHandler.handleCacheGetError(outage, cache, "user:2026:11:6"))
                .doesNotThrowAnyException();
        assertThatCode(() -> errorHandler.handleCachePutError(outage, cache, "user:2026:11:6", "val"))
                .doesNotThrowAnyException();
        assertThatCode(() -> errorHandler.handleCacheEvictError(outage, cache, "user:2026:11:6"))
                .doesNotThrowAnyException();
        assertThatCode(() -> errorHandler.handleCacheClearError(outage, cache))
                .doesNotThrowAnyException();
    }
}
