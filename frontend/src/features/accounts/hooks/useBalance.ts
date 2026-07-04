import { useQuery } from '@tanstack/react-query'
import { accountsApi } from '../api/accountsApi'

export function useBalance(accountId: string | undefined) {
  return useQuery({
    queryKey: ['balance', accountId],
    queryFn: () => accountsApi.getBalance(accountId!),
    enabled: !!accountId,
    staleTime: 30_000,
  })
}
