export type TransactionType = 'INCOME' | 'EXPENSE';

export interface CategoryDto {
  id: string;
  name: string;
  type: TransactionType;
  icon?: string | null;
  color?: string | null;
  isDefault: boolean;
}
