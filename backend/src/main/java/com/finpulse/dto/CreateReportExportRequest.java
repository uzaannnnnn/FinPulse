package com.finpulse.dto;

import com.finpulse.entity.ReportFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for {@code POST /api/reports/export}.
 */
public record CreateReportExportRequest(
        @NotNull(message = "Bulan wajib diisi")
        @Min(value = 1, message = "Bulan harus antara 1 sampai 12")
        @Max(value = 12, message = "Bulan harus antara 1 sampai 12")
        Integer month,

        @NotNull(message = "Tahun wajib diisi")
        @Min(value = 2020, message = "Tahun minimal 2020")
        @Max(value = 2100, message = "Tahun maksimal 2100")
        Integer year,

        @NotNull(message = "Format laporan wajib diisi (PDF atau CSV)")
        ReportFormat format
) {
}
