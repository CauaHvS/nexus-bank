import { useMutation, useQueryClient } from '@tanstack/react-query'
import { accountsApi } from '../api/accountsApi'

export function useOpenAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: accountsApi.open,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['accounts'] }),
  })
}
