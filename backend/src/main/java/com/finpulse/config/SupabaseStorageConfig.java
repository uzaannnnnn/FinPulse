package com.finpulse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP {@link RestClient} bean configured for Supabase Storage REST API calls.
 */
@Configuration
public class SupabaseStorageConfig {

    @Bean("supabaseRestClient")
    public RestClient supabaseRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        return builder
                .requestFactory(requestFactory)
                .build();
    }
}
