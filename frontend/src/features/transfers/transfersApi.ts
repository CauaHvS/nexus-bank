import { apiClient } from '@/api/client'

export type PaymentType = 'INTERNAL' | 'PIX' | 'TED'
export type TransferStatus = 'PENDING' | 'SCHEDULED' | 'COMPLETED' | 'FAILED' | 'COMPENSATION_FAILED'

export interface TransferRequest {
  sourceAccountId: string
  targetAccountId: string
  amount: number
  currency: string
  paymentType: PaymentType
  description?: string
  scheduledFor?: string
}

export interface TransferResponse {
  transferId: string
  sourceAccountId: string
  targetAccountId: string
  amount: number
  currency: string
  paymentType: string
  status: TransferStatus
  idempotencyKey: string
  createdAt: string
  completedAt?: string
  failureReason?: string
  scheduledFor?: string
}

export async function initiateTransfer(data: TransferRequest): Promise<TransferResponse> {
  const idempotencyKey = crypto.randomUUID()
  const response = await apiClient.post<TransferResponse>('/transfers', data, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return response.data
}

export async function getTransfer(transferId: string): Promise<TransferResponse> {
  const response = await apiClient.get<TransferResponse>(`/transfers/${transferId}`)
  return response.data
}

export async function cancelScheduledTransfer(transferId: string): Promise<void> {
  await apiClient.delete(`/transfers/${transferId}/schedule`)
}
