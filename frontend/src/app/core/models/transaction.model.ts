import { TransactionType } from './category.model';

export type TransactionSortField = 'transactionDate' | 'amount' | 'createdAt';
export type SortDirection = 'ASC' | 'DESC';

export interface TransactionDto {
  id: string;
  categoryId: string;
  categoryName: string;
  categoryIcon?: string | null;
  categoryColor?: string | null;
  type: TransactionType;
  amount: number;
  description?: string | null;
  transactionDate: string;
  receiptUrl?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTransactionRequest {
  categoryId: string;
  type: TransactionType;
  amount: number;
  description?: string | null;
  transactionDate: string;
  receiptUrl?: string | null;
}

export interface UpdateTransactionRequest {
  categoryId: string;
  type: TransactionType;
  amount: number;
  description?: string | null;
  transactionDate: string;
  receiptUrl?: string | null;
}

export interface TransactionPageResponse {
  content: TransactionDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ReceiptUploadResponse {
  receiptUrl: string;
  filePath: string;
  contentType: string;
  sizeBytes: number;
}

export interface TransactionFilterParams {
  page?: number;
  size?: number;
  sortBy?: TransactionSortField;
  sortDir?: SortDirection;
  type?: TransactionType;
  categoryId?: string;
  startDate?: string;
  endDate?: string;
  search?: string;
}
