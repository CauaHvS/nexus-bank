import { Link } from 'react-router-dom'
import type { AccountView } from '../types/accounts.types'

function formatCurrency(value: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value)
}

const typeLabel: Record<string, string> = {
  CHECKING: 'Conta Corrente',
  SAVINGS: 'Conta Poupanca',
}

export function AccountCard({ account }: { account: AccountView }) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-900">
      <div className="flex items-start justify-between">
        <div>
          <p className="font-semibold text-gray-900 dark:text-gray-100">{typeLabel[account.type] ?? account.type}</p>
          <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
            Ag {account.agency} / Cc {account.accountNumber}
          </p>
        </div>
        <span className="rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700 dark:bg-green-900 dark:text-green-300">
          {account.status}
        </span>
      </div>
      <p className="mt-3 text-2xl font-bold text-gray-900 dark:text-gray-100">
        {formatCurrency(account.balance)}
      </p>
      <div className="mt-4 flex gap-2">
        <Link
          to={`/extrato?accountId=${account.accountId}`}
          className="flex-1 rounded-md border border-gray-300 px-3 py-1.5 text-center text-xs font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-800 transition"
        >
          Ver extrato
        </Link>
        <Link
          to="/transferencias"
          className="flex-1 rounded-md bg-primary-600 px-3 py-1.5 text-center text-xs font-medium text-white hover:bg-primary-700 transition"
        >
          Transferir
        </Link>
      </div>
    </div>
  )
}
