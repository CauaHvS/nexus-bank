import { apiClient } from './client'

export interface HealthStatus {
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'
  components?: Record<string, { status: string; details?: Record<string, unknown> }>
}

export async function fetchHealth(): Promise<HealthStatus> {
  const { data } = await apiClient.get<HealthStatus>('/actuator/health')
  return data
}
