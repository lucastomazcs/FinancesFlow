export type User = { id: string; name: string; email: string }
export type LoginResponse = { accessToken: string; tokenType: string; expiresInSeconds: number; user: User }
export type MonthlyReport = { month: string; totalIncome: number; totalExpenses: number; balance: number }
export type Transaction = {
  id: string
  description: string
  amount: number
  type: 'INCOME' | 'EXPENSE'
  category: string
  transactionDate: string
  createdAt: string
}
export type TransactionPayload = Omit<Transaction, 'id' | 'createdAt'>
export type TransactionPage = {
  content: Transaction[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}
export type CreditCard = {
  id: string
  name: string
  creditLimit: number
  availableLimit: number
  closingDay: number
  dueDay: number
  createdAt: string
}
export type CreditCardPayload = Omit<CreditCard, 'id' | 'availableLimit' | 'createdAt'>
export type CardInvoiceItem = {
  purchaseId: string
  description: string
  category: string
  amount: number
  installmentNumber: number
  installmentCount: number
  purchaseDate: string
}
export type CardInvoice = {
  cardId: string
  cardName: string
  month: string
  dueDate: string
  total: number
  paid: boolean
  paidAt: string | null
  purchases: CardInvoiceItem[]
}
export type CardPurchasePayload = {
  description: string
  category: string
  amount: number
  purchaseDate: string
  installments: number
}
export type Budget = {
  id: string
  category: string
  month: string
  monthlyLimit: number
  spent: number
  remaining: number
  overLimit: boolean
}
export type BudgetPayload = { category: string; monthlyLimit: number; month: string }

type ApiProblem = { detail?: string; errors?: { field: string; message: string }[] }

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message)
    this.name = 'ApiError'
  }
}

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(path, { ...options, headers })
  const body = response.status === 204 ? null : await response.json().catch(() => null) as ApiProblem | T | null
  if (!response.ok) {
    const problem = body as ApiProblem | null
    const fieldError = problem?.errors?.[0]
    const message = fieldError ? `${fieldError.field}: ${fieldError.message}` : problem?.detail || `A API respondeu com ${response.status}.`
    throw new ApiError(message, response.status)
  }
  return body as T
}

export function login(credentials: { email: string; password: string }) {
  return request<LoginResponse>('/api/auth/login', { method: 'POST', body: JSON.stringify(credentials) })
}

export function register(details: { name: string; email: string; password: string }) {
  return request<User>('/api/auth/register', { method: 'POST', body: JSON.stringify(details) })
}

export function getMonthlyReport(token: string, month: string) {
  return request<MonthlyReport>(`/api/reports/monthly?month=${encodeURIComponent(month)}`, {}, token)
}

export function getTransactions(token: string, filters: { month?: string; type?: string; category?: string; page?: number; size?: number }) {
  const query = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value))
  })
  return request<TransactionPage>(`/api/transactions?${query.toString()}`, {}, token)
}

export function createTransaction(token: string, transaction: TransactionPayload) {
  return request<Transaction>('/api/transactions', {
    method: 'POST',
    body: JSON.stringify(transaction),
  }, token)
}

export function updateTransaction(token: string, id: string, transaction: TransactionPayload) {
  return request<Transaction>(`/api/transactions/${id}`, {
    method: 'PUT',
    body: JSON.stringify(transaction),
  }, token)
}

export function deleteTransaction(token: string, id: string) {
  return request<void>(`/api/transactions/${id}`, { method: 'DELETE' }, token)
}

export function getCards(token: string) {
  return request<CreditCard[]>('/api/cards', {}, token)
}

export function createCard(token: string, card: CreditCardPayload) {
  return request<CreditCard>('/api/cards', { method: 'POST', body: JSON.stringify(card) }, token)
}

export function createCardPurchase(token: string, cardId: string, purchase: CardPurchasePayload) {
  return request(`/api/cards/${cardId}/purchases`, { method: 'POST', body: JSON.stringify(purchase) }, token)
}

export function getCardInvoice(token: string, cardId: string, month: string) {
  return request<CardInvoice>(`/api/cards/${cardId}/invoices?month=${encodeURIComponent(month)}`, {}, token)
}

export function payCardInvoice(token: string, cardId: string, month: string) {
  return request<{ cardId: string; month: string; amount: number; paidAt: string }>(
    `/api/cards/${cardId}/invoices/pay?month=${encodeURIComponent(month)}`, { method: 'POST' }, token,
  )
}

export function getBudgets(token: string, month: string) {
  return request<Budget[]>(`/api/budgets?month=${encodeURIComponent(month)}`, {}, token)
}

export function createBudget(token: string, budget: BudgetPayload) {
  return request<Budget>('/api/budgets', { method: 'POST', body: JSON.stringify(budget) }, token)
}
