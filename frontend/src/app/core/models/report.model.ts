export type ReportFormat = 'PDF' | 'CSV';

export type ReportStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface CreateReportExportRequest {
  month: number;
  year: number;
  format: ReportFormat;
}

export interface ReportExportDto {
  id: string;
  month: number;
  year: number;
  format: ReportFormat;
  status: ReportStatus;
  fileName?: string | null;
  fileUrl?: string | null;
  contentType?: string | null;
  sizeBytes?: number | null;
  errorMessage?: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt?: string | null;
}
