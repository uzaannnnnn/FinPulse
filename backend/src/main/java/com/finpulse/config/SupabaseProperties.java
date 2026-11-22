package com.finpulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized Supabase Storage REST configuration bound from {@code supabase.*}.
 */
@ConfigurationProperties(prefix = "supabase")
public class SupabaseProperties {

    private String url = "";
    private String serviceKey = "";
    private String storageBucket = "receipts";
    private String reportsBucket = "reports";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url != null ? url : "";
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey != null ? serviceKey : "";
    }

    public String getStorageBucket() {
        return storageBucket;
    }

    public void setStorageBucket(String storageBucket) {
        this.storageBucket = storageBucket != null ? storageBucket : "receipts";
    }

    public String getReportsBucket() {
        return reportsBucket;
    }

    public void setReportsBucket(String reportsBucket) {
        this.reportsBucket = reportsBucket != null ? reportsBucket : "reports";
    }

    public boolean isConfigured() {
        return url != null && !url.isBlank()
                && serviceKey != null && !serviceKey.isBlank()
                && storageBucket != null && !storageBucket.isBlank();
    }

    public boolean isReportsConfigured() {
        return url != null && !url.isBlank()
                && serviceKey != null && !serviceKey.isBlank()
                && reportsBucket != null && !reportsBucket.isBlank();
    }

    public String getNormalizedUrl() {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
