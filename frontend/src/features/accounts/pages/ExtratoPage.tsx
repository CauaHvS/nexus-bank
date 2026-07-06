import { useState } from 'react'
import { useAccounts } from '../hooks/useAccounts'
import { useStatement } from '../hooks/useStatement'
import { StatementList } from '../components/StatementList'
import { ErrorMessage } from '@/components/ui/ErrorMessage'

export function ExtratoPage() {
  const { data: accounts, isLoading: loadingAccounts, isError } = useAccounts()
  const [selectedIndex, setSelectedIndex] = useState(0)
  const account = accounts?.[selectedIndex]
  const { data: statement, isLoading: loadingStatement } = useStatement(account?.accountId)

  if (isError) return <ErrorMessage message="Nao foi possivel carregar as contas." />

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">Extrato</h1>
        <p className="text-sm text-gray-500 dark:text-gray-400">Historico de movimentacoes</p>
      </div>

      {accounts && accounts.length > 1 && (
        <div className="flex gap-2">
          {accounts.map((acc, i) => (
            <button
              key={acc.accountId}
              onClick={() => setSelectedIndex(i)}
              className={`rounded-lg px-3 py-1 text-sm ${
                i === selectedIndex
                  ? 'bg-indigo-600 text-white'
                  : 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300'
              }`}
            >
              {acc.accountNumber}
            </button>
          ))}
        </div>
      )}

      <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <h2 className="mb-4 text-sm font-medium text-gray-700 dark:text-gray-300">
          Movimentacoes recentes
        </h2>
        <StatementList
          entries={statement?.content ?? []}
          isLoading={loadingAccounts || loadingStatement}
        />
      </div>
    </div>
  )
}
