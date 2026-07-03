import { useHealth } from '@/hooks/useHealth'
import { Skeleton } from '@/components/ui/Skeleton'
import { ErrorMessage } from '@/components/ui/ErrorMessage'

export function HealthPage() {
  const { data, isLoading, isError, error } = useHealth()

  if (isLoading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-4 w-64" />
        <Skeleton className="h-32 w-full" />
      </div>
    )
  }

  if (isError) {
    return (
      <ErrorMessage
        message={`Não foi possível conectar ao backend: ${error instanceof Error ? error.message : 'Erro desconhecido'}`}
      />
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
          Status do Sistema
        </h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          Conectividade com o backend — será substituído pelo Dashboard na Fase 2.
        </p>
      </div>

      <div className="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-900">
        <div className="flex items-center gap-3">
          <span
            className={`h-3 w-3 rounded-full ${
              data?.status === 'UP' ? 'bg-green-500' : 'bg-red-500'
            }`}
          />
          <span className="font-medium">
            Backend:{' '}
            <span
              className={
                data?.status === 'UP' ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
              }
            >
              {data?.status}
            </span>
          </span>
        </div>

        {data?.components && (
          <div className="mt-4 space-y-2">
            {Object.entries(data.components).map(([name, component]) => (
              <div
                key={name}
                className="flex items-center justify-between rounded-md bg-gray-50 px-3 py-2 text-sm dark:bg-gray-800"
              >
                <span className="text-gray-600 dark:text-gray-400">{name}</span>
                <span
                  className={
                    component.status === 'UP'
                      ? 'text-green-600 dark:text-green-400'
                      : 'text-yellow-600 dark:text-yellow-400'
                  }
                >
                  {component.status}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
