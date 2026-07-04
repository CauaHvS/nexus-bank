import { useState } from 'react'
import { useAccounts } from '../hooks/useAccounts'
import { AccountCard } from '../components/AccountCard'
import { OpenAccountModal } from '../components/OpenAccountModal'
import { Skeleton } from '@/components/ui/Skeleton'
import { ErrorMessage } from '@/components/ui/ErrorMessage'

export function ContasPage() {
  const { data: accounts, isLoading, isError } = useAccounts()
  const [showModal, setShowModal] = useState(false)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">Minhas Contas</h1>
        <button
          onClick={() => setShowModal(true)}
          className="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 transition"
        >
          Abrir conta
        </button>
      </div>

      {isError && <ErrorMessage message="Nao foi possivel carregar as contas." />}

      {isLoading ? (
        <div className="space-y-4">
          {[1, 2].map(i => <Skeleton key={i} className="h-36 w-full rounded-lg" />)}
        </div>
      ) : accounts?.length === 0 ? (
        <div className="rounded-lg border border-dashed border-gray-300 p-12 text-center dark:border-gray-700">
          <p className="text-sm text-gray-500 dark:text-gray-400">Voce ainda nao tem contas abertas.</p>
          <button onClick={() => setShowModal(true)}
            className="mt-4 rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 transition">
            Abrir primeira conta
          </button>
        </div>
      ) : (
        <div className="space-y-4">
          {accounts?.map(acc => <AccountCard key={acc.accountId} account={acc} />)}
        </div>
      )}

      {showModal && <OpenAccountModal onClose={() => setShowModal(false)} />}
    </div>
  )
}
