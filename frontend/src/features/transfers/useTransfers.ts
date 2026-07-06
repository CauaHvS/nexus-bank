import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  cancelScheduledTransfer,
  getTransfer,
  initiateTransfer,
  type TransferRequest,
} from './transfersApi'

export function useInitiateTransfer() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: TransferRequest) => initiateTransfer(data),
    onSuccess: () => {
      // Invalida extrato e saldo ao concluir uma transferencia
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      queryClient.invalidateQueries({ queryKey: ['statement'] })
    },
  })
}

export function useGetTransfer(transferId: string | null) {
  return useQuery({
    queryKey: ['transfer', transferId],
    queryFn: () => getTransfer(transferId!),
    enabled: !!transferId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      if (status === 'PENDING') return 3000
      return false
    },
  })
}

export function useCancelScheduledTransfer() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (transferId: string) => cancelScheduledTransfer(transferId),
    onSuccess: (_data, transferId) => {
      queryClient.invalidateQueries({ queryKey: ['transfer', transferId] })
    },
  })
}
