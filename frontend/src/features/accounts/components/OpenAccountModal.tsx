import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useOpenAccount } from '../hooks/useOpenAccount'

const schema = z.object({
  type: z.enum(['CHECKING', 'SAVINGS']),
  currency: z.enum(['BRL']),
})
type FormData = z.infer<typeof schema>

interface Props { onClose: () => void }

export function OpenAccountModal({ onClose }: Props) {
  const { register, handleSubmit } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { type: 'CHECKING', currency: 'BRL' },
  })
  const { mutate, isPending, isError, error } = useOpenAccount()

  const onSubmit = (data: FormData) => mutate(data, { onSuccess: onClose })

  const apiError = isError ? ((error as any)?.response?.data?.detail ?? 'Erro ao abrir conta.') : null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-6 shadow-xl dark:bg-gray-900">
        <h2 className="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">Abrir nova conta</h2>

        {apiError && (
          <div role="alert" className="mb-3 rounded border border-red-200 bg-red-50 p-2 text-xs text-red-700">
            {apiError}
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">Tipo de conta</label>
            <select {...register('type')} className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100">
              <option value="CHECKING">Conta Corrente</option>
              <option value="SAVINGS">Conta Poupanca</option>
            </select>
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="flex-1 rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 transition">
              Cancelar
            </button>
            <button type="submit" disabled={isPending}
              className="flex-1 rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:opacity-60 transition">
              {isPending ? 'Abrindo...' : 'Confirmar'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
