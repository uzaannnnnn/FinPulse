export interface BudgetDto {
  id: string;
  categoryId: string;
  categoryName: string;
  categoryIcon?: string | null;
  categoryColor?: string | null;
  month: number;
  year: number;
  amountLimit: number;
  spentAmount: number;
  remainingAmount: number;
  utilizationPercentage: number;
  isOverBudget: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateBudgetRequest {
  categoryId: string;
  month: number;
  year: number;
  amountLimit: number;
}

export interface UpdateBudgetRequest {
  amountLimit: number;
}
