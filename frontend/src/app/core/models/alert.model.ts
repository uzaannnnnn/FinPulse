export interface BudgetAlertDto {
  id: string;
  budgetId?: string | null;
  categoryId: string;
  categoryName: string;
  categoryColor?: string | null;
  month: number;
  year: number;
  amountLimit: number;
  spentAmount: number;
  utilizationPercentage: number;
  message: string;
  isRead: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AlertListResponse {
  unreadCount: number;
  alerts: BudgetAlertDto[];
}
