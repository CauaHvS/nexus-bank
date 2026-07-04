import { useQuery } from '@tanstack/react-query'
import { accountsApi } from '../api/accountsApi'

export function useStatement(accountId: string | undefined, page = 0) {
  return useQuery({
    queryKey: ['statement', accountId, page],
    queryFn: () => accountsApi.getStatement(accountId!, { page, size: 20 }),
    enabled: !!accountId,
  })
}
