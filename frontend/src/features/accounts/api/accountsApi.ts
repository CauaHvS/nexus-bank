import { apiClient } from '@/api/client'
import type { AccountView, BalanceView, OpenAccountRequest, StatementResult } from '../types/accounts.types'

export const accountsApi = {
  list: () =>
    apiClient.get<AccountView[]>('/accounts').then(r => r.data),

  getById: (id: string) =>
    apiClient.get<AccountView>(`/accounts/${id}`).then(r => r.data),

  getBalance: (id: string) =>
    apiClient.get<BalanceView>(`/accounts/${id}/balance`).then(r => r.data),

  getStatement: (id: string, params?: { page?: number; size?: number; startDate?: string; endDate?: string }) =>
    apiClient.get<StatementResult>(`/accounts/${id}/statement`, { params }).then(r => r.data),

  open: (data: OpenAccountRequest) =>
    apiClient.post<AccountView>('/accounts', data).then(r => r.data),
}
