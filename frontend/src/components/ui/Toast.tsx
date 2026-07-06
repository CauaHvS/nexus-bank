import { useEffect, useState } from 'react'

export interface ToastMessage {
  id: string
  text: string
}

interface ToastProps {
  messages: ToastMessage[]
  onDismiss: (id: string) => void
}

export function ToastContainer({ messages, onDismiss }: ToastProps) {
  return (
    <div
      aria-live="polite"
      aria-atomic="false"
      className="fixed bottom-20 left-1/2 -translate-x-1/2 lg:bottom-6 lg:left-auto lg:right-6 lg:translate-x-0 z-50 flex flex-col gap-2 items-center lg:items-end pointer-events-none"
    >
      {messages.map((msg) => (
        <ToastItem key={msg.id} message={msg} onDismiss={onDismiss} />
      ))}
    </div>
  )
}

function ToastItem({ message, onDismiss }: { message: ToastMessage; onDismiss: (id: string) => void }) {
  const [visible, setVisible] = useState(true)

  useEffect(() => {
    const timer = setTimeout(() => {
      setVisible(false)
      setTimeout(() => onDismiss(message.id), 300)
    }, 4000)
    return () => clearTimeout(timer)
  }, [message.id, onDismiss])

  return (
    <div
      role="status"
      className={`pointer-events-auto flex items-center gap-3 px-4 py-3 rounded-lg shadow-lg bg-gray-900 dark:bg-gray-800 text-white text-sm font-medium max-w-xs transition-all duration-300 ${
        visible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2'
      }`}
    >
      <span
        className="w-2 h-2 rounded-full bg-blue-400 flex-shrink-0"
        aria-hidden="true"
      />
      {message.text}
      <button
        onClick={() => { setVisible(false); setTimeout(() => onDismiss(message.id), 300) }}
        className="ml-2 text-gray-400 hover:text-white transition-colors"
        aria-label="Fechar notificacao"
      >
        x
      </button>
    </div>
  )
}
