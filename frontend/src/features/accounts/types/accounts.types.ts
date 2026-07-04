export type AccountType = 'CHECKING' | 'SAVINGS'
export type AccountStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED'
export type Currency = 'BRL' | 'USD' | 'EUR'

export interface AccountView {
  accountId: string
  accountNumber: string
  agency: string
  type: AccountType
  currency: Currency
  balance: number
  status: AccountStatus
}

export interface BalanceView {
  accountId: string
  balance: number
  currency: string
  updatedAt: string
}

export interface StatementEntry {
  entryId: string
  type: 'DEBIT' | 'CREDIT'
  amount: number
  description: string
  occurredAt: string
  balanceAfter: number
}

export interface StatementResult {
  content: StatementEntry[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface OpenAccountRequest {
  type: AccountType
  currency: Currency
}
