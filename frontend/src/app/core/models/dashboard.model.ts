import { BudgetDto } from './budget.model';
import { TransactionDto } from './transaction.model';

export interface MonthlyCashflowDto {
  year: number;
  month: number;
  label: string;
  totalIncome: number;
  totalExpense: number;
  net: number;
}

export interface CategoryBreakdownDto {
  categoryId: string;
  categoryName: string;
  categoryColor?: string | null;
  categoryIcon?: string | null;
  amount: number;
  percentage: number;
}

export interface DashboardSummaryDto {
  month: number;
  year: number;
  totalIncome: number;
  totalExpense: number;
  balance: number;
  cumulativeBalance: number;
  transactionCount: number;
  cashflowSeries: MonthlyCashflowDto[];
  expenseByCategory: CategoryBreakdownDto[];
  budgetProgress: BudgetDto[];
  recentTransactions: TransactionDto[];
  generatedAt: string;
  fromCache: boolean;
}
