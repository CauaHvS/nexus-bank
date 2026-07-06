/** Gera um CPF matematicamente válido para uso em testes. */
export function generateCpf(): string {
  const digits = Array.from({ length: 9 }, () => Math.floor(Math.random() * 10))

  // Garante que não são todos iguais (CPF inválido mesmo com dígitos corretos)
  if (new Set(digits).size === 1) digits[0] = (digits[0] + 1) % 10

  let sum = digits.reduce((acc, d, i) => acc + d * (10 - i), 0)
  let first = 11 - (sum % 11)
  if (first >= 10) first = 0

  const withFirst = [...digits, first]
  sum = withFirst.reduce((acc, d, i) => acc + d * (11 - i), 0)
  let second = 11 - (sum % 11)
  if (second >= 10) second = 0

  return [...withFirst, second].join('')
}
