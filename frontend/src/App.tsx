import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import {
  ApiError,
  createBudget,
  createCard,
  createCardPurchase,
  createTransaction,
  deleteTransaction,
  getCardInvoice,
  getCards,
  getBudgets,
  getMonthlyReport,
  getTransactions,
  login,
  payCardInvoice,
  register,
  updateTransaction,
  type CardInvoice,
  type CardPurchasePayload,
  type CreditCard,
  type CreditCardPayload,
  type Budget,
  type BudgetPayload,
  type MonthlyReport,
  type Transaction,
  type TransactionPage,
  type TransactionPayload,
  type User,
} from './api'
import './App.css'

type Page = 'overview' | 'transactions' | 'cards' | 'budgets'

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const dateFormat = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' })

function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function localDate() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function App() {
  const [token, setToken] = useState(() => localStorage.getItem('financesflow.token'))
  const [user, setUser] = useState<User | null>(() => {
    const saved = localStorage.getItem('financesflow.user')
    return saved ? JSON.parse(saved) as User : null
  })
  const [month, setMonth] = useState(currentMonth)
  const [page, setPage] = useState<Page>('overview')
  const [report, setReport] = useState<MonthlyReport | null>(null)
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [transactionPage, setTransactionPage] = useState<TransactionPage | null>(null)
  const [transactionsLoading, setTransactionsLoading] = useState(false)
  const [pageNumber, setPageNumber] = useState(0)
  const [typeFilter, setTypeFilter] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [categoryDraft, setCategoryDraft] = useState('')
  const [cards, setCards] = useState<CreditCard[]>([])
  const [budgets, setBudgets] = useState<Budget[]>([])
  const [selectedCardId, setSelectedCardId] = useState('')
  const [invoice, setInvoice] = useState<CardInvoice | null>(null)
  const [invoiceLoading, setInvoiceLoading] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [editingTransaction, setEditingTransaction] = useState<Transaction | null>(null)
  const [transactionToDelete, setTransactionToDelete] = useState<Transaction | null>(null)
  const [cardForm, setCardForm] = useState<'create' | 'purchase' | null>(null)
  const [showBudgetForm, setShowBudgetForm] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    if (!token) return
    let current = true
    setLoading(true)
    setError('')
    Promise.all([getMonthlyReport(token, month), getCards(token), getBudgets(token, month)])
      .then(([monthlyReport, allCards, allBudgets]) => {
        if (!current) return
        setReport(monthlyReport)
        setCards(allCards)
        setBudgets(allBudgets)
        setSelectedCardId((selected) => allCards.some((card) => card.id === selected) ? selected : allCards[0]?.id || '')
      })
      .catch((cause: unknown) => {
        if (!current) return
        if (cause instanceof ApiError && cause.status === 401) {
          localStorage.removeItem('financesflow.token')
          localStorage.removeItem('financesflow.user')
          setToken(null)
          setUser(null)
          setReport(null)
          setTransactions([])
          setTransactionPage(null)
          setCards([])
          setBudgets([])
          setInvoice(null)
        }
        else setError(cause instanceof Error ? cause.message : 'Não foi possível carregar seus dados.')
      })
      .finally(() => { if (current) setLoading(false) })
    return () => { current = false }
  }, [token, month, refreshKey])

  useEffect(() => {
    if (!token) return
    let current = true
    setTransactionsLoading(true)
    getTransactions(token, {
      month,
      type: page === 'transactions' ? typeFilter : undefined,
      category: page === 'transactions' ? categoryFilter : undefined,
      page: page === 'transactions' ? pageNumber : 0,
      size: page === 'transactions' ? 10 : 5,
    })
      .then((result) => {
        if (!current) return
        setTransactionPage(result)
        setTransactions(result.content)
      })
      .catch((cause: unknown) => {
        if (!current) return
        if (cause instanceof ApiError && cause.status === 401) {
          localStorage.removeItem('financesflow.token')
          localStorage.removeItem('financesflow.user')
          setToken(null)
          setUser(null)
          setReport(null)
          setTransactions([])
          setTransactionPage(null)
          setCards([])
          setBudgets([])
          setInvoice(null)
        } else setError(cause instanceof Error ? cause.message : 'Não foi possível carregar as transações.')
      })
      .finally(() => { if (current) setTransactionsLoading(false) })
    return () => { current = false }
  }, [token, month, page, pageNumber, typeFilter, categoryFilter, refreshKey])

  useEffect(() => {
    if (!token || page !== 'cards' || !selectedCardId) {
      setInvoice(null)
      return
    }
    let current = true
    setInvoiceLoading(true)
    getCardInvoice(token, selectedCardId, month)
      .then((result) => { if (current) setInvoice(result) })
      .catch((cause: unknown) => { if (current) setError(cause instanceof Error ? cause.message : 'Não foi possível carregar a fatura.') })
      .finally(() => { if (current) setInvoiceLoading(false) })
    return () => { current = false }
  }, [token, page, selectedCardId, month, refreshKey])

  function authenticate(accessToken: string, authenticatedUser: User) {
    localStorage.setItem('financesflow.token', accessToken)
    localStorage.setItem('financesflow.user', JSON.stringify(authenticatedUser))
    setToken(accessToken)
    setUser(authenticatedUser)
  }

  function signOut() {
    localStorage.removeItem('financesflow.token')
    localStorage.removeItem('financesflow.user')
    setToken(null)
    setUser(null)
    setReport(null)
    setTransactions([])
    setTransactionPage(null)
    setCards([])
    setBudgets([])
    setInvoice(null)
  }

  async function saveTransaction(payload: TransactionPayload) {
    if (!token) return
    await createTransaction(token, payload)
    setShowForm(false)
    setPageNumber(0)
    setRefreshKey((value) => value + 1)
  }

  async function saveTransactionChanges(id: string, payload: TransactionPayload) {
    if (!token) return
    await updateTransaction(token, id, payload)
    setEditingTransaction(null)
    setRefreshKey((value) => value + 1)
  }

  async function confirmDeleteTransaction() {
    if (!token || !transactionToDelete) return
    setError('')
    try {
      await deleteTransaction(token, transactionToDelete.id)
      setTransactionToDelete(null)
      setPageNumber(0)
      setRefreshKey((value) => value + 1)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível excluir a transação.')
      setTransactionToDelete(null)
    }
  }

  async function saveCard(payload: CreditCardPayload) {
    if (!token) return
    const created = await createCard(token, payload)
    setSelectedCardId(created.id)
    setCardForm(null)
    setRefreshKey((value) => value + 1)
  }

  async function saveBudget(payload: BudgetPayload) {
    if (!token) return
    await createBudget(token, payload)
    setShowBudgetForm(false)
    setRefreshKey((value) => value + 1)
  }

  async function savePurchase(payload: CardPurchasePayload) {
    if (!token || !selectedCardId) return
    await createCardPurchase(token, selectedCardId, payload)
    setCardForm(null)
    setRefreshKey((value) => value + 1)
  }

  async function payInvoice() {
    if (!token || !selectedCardId) return
    setError('')
    try {
      await payCardInvoice(token, selectedCardId, month)
      setRefreshKey((value) => value + 1)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível pagar a fatura.')
    }
  }

  if (!token) return <AuthScreen onAuthenticated={authenticate} />

  const monthName = new Date(`${month}-02T12:00:00`).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })
  const firstName = user?.name.split(' ')[0] || 'pessoa'

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#inicio" aria-label="FinancesFlow início">
          <span className="brand-mark">f</span>
          <span>finances<span className="brand-light">flow</span></span>
        </a>

        <div className="nav-caption">ESPAÇO FINANCEIRO</div>
        <nav className="navigation" aria-label="Navegação principal">
          <button className={page === 'overview' ? 'nav-item active' : 'nav-item'} onClick={() => setPage('overview')}>
            <span className="nav-symbol">◫</span> Visão geral
          </button>
          <button className={page === 'transactions' ? 'nav-item active' : 'nav-item'} onClick={() => setPage('transactions')}>
            <span className="nav-symbol">↕</span> Transações
          </button>
          <button className={page === 'cards' ? 'nav-item active' : 'nav-item'} onClick={() => setPage('cards')}>
            <span className="nav-symbol">▤</span> Cartões
          </button>
          <button className={page === 'budgets' ? 'nav-item active' : 'nav-item'} onClick={() => setPage('budgets')}>
            <span className="nav-symbol">◎</span> Orçamentos
          </button>
        </nav>

        <div className="sidebar-note">
          <span className="note-spark">✳</span>
          <p>Pequenos passos deixam seu dinheiro mais claro.</p>
        </div>

        <div className="profile-row">
          <div className="avatar">{firstName.charAt(0).toUpperCase()}</div>
          <div className="profile-copy"><strong>{user?.name || 'Minha conta'}</strong><span>Conta pessoal</span></div>
          <button className="logout-button" onClick={signOut} title="Sair" aria-label="Sair">↗</button>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div className="breadcrumb"><span>FinancesFlow</span><span className="crumb-divider">/</span><strong>{page === 'overview' ? 'Visão geral' : page === 'transactions' ? 'Transações' : page === 'cards' ? 'Cartões' : 'Orçamentos'}</strong></div>
          <div className="topbar-actions">
            <label className="month-picker"><span>Mês</span><input type="month" value={month} onChange={(event) => { setMonth(event.target.value); setPageNumber(0) }} /></label>
            {page === 'cards' ? <button className="primary-button top-create" onClick={() => setCardForm('create')}><span>＋</span> Novo cartão</button> : page === 'budgets' ? <button className="primary-button top-create" onClick={() => setShowBudgetForm(true)}><span>＋</span> Novo orçamento</button> : <button className="primary-button top-create" onClick={() => setShowForm(true)}><span>＋</span> Nova transação</button>}
          </div>
        </header>

        <section className="page-content">
          <div className="welcome-row">
            <div>
              <div className="eyebrow">SEU DINHEIRO, MAIS PERTO</div>
              <h1>{page === 'overview' ? `Olá, ${firstName}` : page === 'transactions' ? 'Suas transações' : page === 'cards' ? 'Seus cartões' : 'Seus orçamentos'}<span className="heading-period">.</span></h1>
              <p className="subtitle">{page === 'overview' ? <>Aqui está um resumo de <strong>{monthName}</strong>.</> : page === 'transactions' ? <>Entradas e saídas registradas em <strong>{monthName}</strong>.</> : page === 'cards' ? 'Acompanhe limites, compras e faturas.' : <>Limites e gastos registrados em <strong>{monthName}</strong>.</>}</p>
            </div>
            <div className="date-chip"><span className="date-dot" /> {monthName}</div>
          </div>

          {error && <div className="error-banner" role="alert"><span>!</span>{error}</div>}

          {page === 'overview' ? (
            <>
              <div className="summary-grid">
                <SummaryCard label="Receitas" value={report?.totalIncome} kind="income" icon="↗" loading={loading} />
                <SummaryCard label="Despesas" value={report?.totalExpenses} kind="expense" icon="↘" loading={loading} />
                <SummaryCard label="Saldo do mês" value={report?.balance} kind="balance" icon="＝" loading={loading} />
              </div>

              <div className="dashboard-grid">
                <section className="panel transactions-panel">
                  <div className="panel-heading">
                    <div><div className="panel-kicker">MOVIMENTAÇÕES</div><h2>Últimas transações</h2></div>
                    <button className="text-button" onClick={() => setPage('transactions')}>Ver todas <span>→</span></button>
                  </div>
                  {transactionsLoading ? <LoadingRows /> : <TransactionList items={transactions.slice(0, 5)} />}
                </section>

                <section className="panel balance-panel">
                  <div className="panel-kicker">PANORAMA DO MÊS</div>
                  <h2>Como está seu fluxo?</h2>
                  <p className="panel-description">Compare o que entrou com o que saiu neste mês.</p>
                  <div className="flow-row"><span><i className="flow-dot positive" /> Receitas</span><strong>{money.format(report?.totalIncome ?? 0)}</strong></div>
                  <div className="flow-track"><span className="flow-fill positive-fill" style={{ width: `${flowPercent(report?.totalIncome, report?.totalExpenses)}%` }} /></div>
                  <div className="flow-row expense-flow"><span><i className="flow-dot negative" /> Despesas</span><strong>{money.format(report?.totalExpenses ?? 0)}</strong></div>
                  <div className="flow-track"><span className="flow-fill negative-fill" style={{ width: `${flowPercent(report?.totalExpenses, report?.totalIncome)}%` }} /></div>
                  <div className="balance-insight"><span className="insight-icon">✳</span><p>{(report?.balance ?? 0) >= 0 ? 'Seu saldo está positivo neste mês.' : 'Suas despesas passaram das receitas neste mês.'}</p></div>
                </section>
              </div>
              <BudgetOverview budgets={budgets} loading={loading} onOpen={() => setPage('budgets')} />
            </>
          ) : page === 'transactions' ? (
            <section className="panel all-transactions-panel">
              <div className="panel-heading">
                <div><div className="panel-kicker">HISTÓRICO</div><h2>Transações de {monthName}</h2></div>
                <span className="count-pill">{transactionsLoading ? '…' : transactionPage?.totalElements ?? 0}</span>
              </div>
              <form className="transaction-filters" onSubmit={(event) => { event.preventDefault(); setCategoryFilter(categoryDraft.trim()); setPageNumber(0) }}>
                <select aria-label="Filtrar por tipo" value={typeFilter} onChange={(event) => { setTypeFilter(event.target.value); setPageNumber(0) }}>
                  <option value="">Todas</option><option value="INCOME">Receitas</option><option value="EXPENSE">Despesas</option>
                </select>
                <input aria-label="Filtrar por categoria" value={categoryDraft} onChange={(event) => setCategoryDraft(event.target.value)} placeholder="Categoria exata" />
                <button className="secondary-button" type="submit">Filtrar</button>
                <button className="text-button clear-filters" type="button" onClick={() => { setTypeFilter(''); setCategoryFilter(''); setCategoryDraft(''); setPageNumber(0) }}>Limpar</button>
              </form>
              {transactionsLoading ? <LoadingRows /> : <TransactionList items={transactions}
                onEdit={(transaction) => setEditingTransaction(transaction)}
                onDelete={(transaction) => setTransactionToDelete(transaction)} />}
              <div className="pagination-controls">
                <button className="secondary-button" disabled={!transactionPage || transactionPage.first || transactionsLoading} onClick={() => setPageNumber((value) => Math.max(0, value - 1))}>← Anterior</button>
                <span>Página {transactionPage && transactionPage.totalPages > 0 ? transactionPage.page + 1 : 0} de {transactionPage?.totalPages ?? 0}</span>
                <button className="secondary-button" disabled={!transactionPage || transactionPage.last || transactionsLoading} onClick={() => setPageNumber((value) => value + 1)}>Próxima →</button>
              </div>
            </section>
          ) : page === 'cards' ? (
            <CardsPage cards={cards} selectedCardId={selectedCardId} onSelect={setSelectedCardId}
              invoice={invoice} invoiceLoading={invoiceLoading} month={month} onCreate={() => setCardForm('create')}
              onPurchase={() => setCardForm('purchase')} onPayInvoice={payInvoice} loading={loading} />
          ) : (
            <BudgetsPage budgets={budgets} month={month} loading={loading} onCreate={() => setShowBudgetForm(true)} />
          )}

          <footer className="page-footer"><span>FinancesFlow</span><span>Seu controle financeiro, no seu ritmo.</span></footer>
        </section>
      </main>

      {showForm && <TransactionForm onClose={() => setShowForm(false)} onSave={saveTransaction} />}
      {editingTransaction && <TransactionForm initial={editingTransaction} onClose={() => setEditingTransaction(null)}
        onSave={(payload) => saveTransactionChanges(editingTransaction.id, payload)} />}
      {transactionToDelete && <DeleteTransactionDialog transaction={transactionToDelete}
        onClose={() => setTransactionToDelete(null)} onConfirm={confirmDeleteTransaction} />}
      {cardForm === 'create' && <CreditCardForm onClose={() => setCardForm(null)} onSave={saveCard} />}
      {cardForm === 'purchase' && selectedCardId && <CardPurchaseForm onClose={() => setCardForm(null)} onSave={savePurchase} />}
      {showBudgetForm && <BudgetForm month={month} onClose={() => setShowBudgetForm(false)} onSave={saveBudget} />}
    </div>
  )
}

function CardsPage({ cards, selectedCardId, onSelect, invoice, invoiceLoading, month, onCreate, onPurchase, onPayInvoice, loading }: {
  cards: CreditCard[]; selectedCardId: string; onSelect: (id: string) => void; invoice: CardInvoice | null
  invoiceLoading: boolean; month: string; onCreate: () => void; onPurchase: () => void
  onPayInvoice: () => void; loading: boolean
}) {
  const selectedCard = cards.find((card) => card.id === selectedCardId)
  const monthName = new Date(`${month}-02T12:00:00`).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })
  if (loading) return <section className="panel cards-panel"><LoadingRows /></section>
  if (!cards.length) return (
    <section className="panel cards-empty-panel">
      <div className="empty-illustration">▤</div><h2>Nenhum cartão cadastrado</h2>
      <p>Cadastre seu cartão para acompanhar o limite disponível, registrar compras e consultar as faturas.</p>
      <button className="primary-button" onClick={onCreate}>＋ Cadastrar primeiro cartão</button>
    </section>
  )
  return (
    <div className="cards-layout">
      <section className="cards-column">
        <div className="cards-selector" aria-label="Seus cartões">
          {cards.map((card) => (
            <button className={`credit-card-tile ${selectedCardId === card.id ? 'selected' : ''}`} key={card.id} onClick={() => onSelect(card.id)}>
              <span className="card-chip">▦</span><span className="card-title">{card.name}</span>
              <span className="card-limit-label">Limite disponível</span><strong>{money.format(card.availableLimit)}</strong>
              <span className="card-cycle">Fecha dia {card.closingDay} · vence dia {card.dueDay}</span>
            </button>
          ))}
        </div>
        {selectedCard && <section className="panel card-details-panel">
          <div className="panel-heading"><div><div className="panel-kicker">CARTÃO SELECIONADO</div><h2>{selectedCard.name}</h2></div>
            <button className="primary-button" onClick={onPurchase}>＋ Registrar compra</button></div>
          <div className="card-limit-summary"><div><span>Limite total</span><strong>{money.format(selectedCard.creditLimit)}</strong></div>
            <div><span>Disponível</span><strong>{money.format(selectedCard.availableLimit)}</strong></div>
            <div><span>Em uso</span><strong>{money.format(selectedCard.creditLimit - selectedCard.availableLimit)}</strong></div></div>
        </section>}
      </section>
      <section className="panel invoice-panel">
        <div className="panel-heading"><div><div className="panel-kicker">FATURA</div><h2>{monthName}</h2></div>
          {invoice && <span className={`invoice-status ${invoice.paid ? 'paid' : ''}`}>{invoice.paid ? 'Paga' : 'Em aberto'}</span>}</div>
        {invoiceLoading ? <LoadingRows /> : invoice ? <>
          <div className="invoice-total"><span>Total da fatura</span><strong>{money.format(invoice.total)}</strong>
            <small>Vencimento em {dateFormat.format(new Date(`${invoice.dueDate}T12:00:00`))}</small></div>
          {invoice.purchases.length ? <div className="invoice-purchases">{invoice.purchases.map((purchase, index) =>
            <article className="invoice-purchase" key={`${purchase.purchaseId}-${purchase.installmentNumber}-${index}`}>
              <span className="purchase-dot">↘</span><div className="purchase-copy"><strong>{purchase.description}</strong>
                <span>{purchase.category} · parcela {purchase.installmentNumber}/{purchase.installmentCount}</span></div>
              <strong className="purchase-amount">{money.format(purchase.amount)}</strong>
            </article>)}</div> : <div className="invoice-empty">Nenhuma compra nesta fatura.</div>}
          {invoice.total > 0 && <button className="primary-button pay-invoice-button" onClick={onPayInvoice} disabled={invoice.paid}>{invoice.paid ? 'Fatura paga' : 'Pagar fatura'}</button>}
        </> : <div className="invoice-empty">Selecione um cartão para carregar a fatura.</div>}
      </section>
    </div>
  )
}

function BudgetsPage({ budgets, month, loading, onCreate }: {
  budgets: Budget[]; month: string; loading: boolean; onCreate: () => void
}) {
  const monthName = new Date(`${month}-02T12:00:00`).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })
  if (loading) return <section className="panel budgets-panel"><LoadingRows /></section>
  if (!budgets.length) return (
    <section className="panel cards-empty-panel">
      <div className="empty-illustration">◎</div><h2>Nenhum orçamento neste mês</h2>
      <p>Defina um limite para uma categoria e acompanhe quanto já gastou durante {monthName}.</p>
      <button className="primary-button" onClick={onCreate}>＋ Criar primeiro orçamento</button>
    </section>
  )
  return (
    <section className="panel budgets-panel">
      <div className="panel-heading">
        <div><div className="panel-kicker">LIMITES POR CATEGORIA</div><h2>Orçamentos de {monthName}</h2></div>
        <span className="count-pill">{budgets.length}</span>
      </div>
      <div className="budget-list">
        {budgets.map((budget) => {
          const progress = budget.monthlyLimit <= 0 ? 0 : Math.min(100, Math.max(0, budget.spent / budget.monthlyLimit * 100))
          const status = budget.overLimit
            ? `Acima do limite em ${money.format(Math.abs(budget.remaining))}`
            : budget.remaining === 0 ? 'Limite atingido' : `Restam ${money.format(budget.remaining)}`
          return <article className={`budget-item ${budget.overLimit ? 'over-limit' : ''}`} key={budget.id}>
            <div className="budget-item-heading"><div><strong>{budget.category}</strong><span>{money.format(budget.spent)} gastos de {money.format(budget.monthlyLimit)}</span></div>
              <span className="budget-status">{status}</span></div>
            <div className="budget-track" role="progressbar" aria-label={`Uso do orçamento de ${budget.category}`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(progress)}>
              <span className="budget-fill" style={{ width: `${progress}%` }} />
            </div>
          </article>
        })}
      </div>
    </section>
  )
}

function BudgetOverview({ budgets, loading, onOpen }: { budgets: Budget[]; loading: boolean; onOpen: () => void }) {
  const highlighted = [...budgets].sort((left, right) => {
    if (left.overLimit !== right.overLimit) return left.overLimit ? -1 : 1
    const leftUsage = left.monthlyLimit > 0 ? left.spent / left.monthlyLimit : 0
    const rightUsage = right.monthlyLimit > 0 ? right.spent / right.monthlyLimit : 0
    return rightUsage - leftUsage
  }).slice(0, 3)
  return <section className="panel budget-overview-panel">
    <div className="panel-heading">
      <div><div className="panel-kicker">ACOMPANHAMENTO</div><h2>Orçamentos do mês</h2></div>
      <button className="text-button" onClick={onOpen}>{budgets.length ? 'Ver todos' : 'Criar orçamento'} <span>→</span></button>
    </div>
    {loading ? <LoadingRows /> : highlighted.length ? <div className="budget-overview-list">
      {highlighted.map((budget) => {
        const usage = budget.monthlyLimit > 0 ? Math.max(0, budget.spent / budget.monthlyLimit * 100) : 0
        const progress = Math.min(100, usage)
        const status = budget.overLimit
          ? `Excedeu ${money.format(Math.abs(budget.remaining))}`
          : budget.remaining === 0 ? 'Limite atingido' : `${Math.round(usage)}% utilizado`
        return <article className={`budget-overview-item ${budget.overLimit ? 'over-limit' : ''}`} key={budget.id}>
          <div className="budget-overview-copy"><strong>{budget.category}</strong><span>{money.format(budget.spent)} de {money.format(budget.monthlyLimit)}</span></div>
          <div className="budget-overview-track" role="progressbar" aria-label={`Uso do orçamento de ${budget.category}`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(progress)}>
            <span className="budget-fill" style={{ width: `${progress}%` }} />
          </div>
          <span className="budget-overview-status">{status}</span>
        </article>
      })}
    </div> : <div className="budget-overview-empty">Nenhum orçamento cadastrado para este mês. Crie limites por categoria para acompanhar seus gastos.</div>}
  </section>
}

function BudgetForm({ month, onClose, onSave }: { month: string; onClose: () => void; onSave: (payload: BudgetPayload) => Promise<void> }) {
  const [category, setCategory] = useState('')
  const [monthlyLimit, setMonthlyLimit] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('')
    try { await onSave({ category: category.trim(), monthlyLimit: Number(monthlyLimit), month }) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Não foi possível criar o orçamento.') }
    finally { setBusy(false) }
  }
  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <section className="transaction-modal" role="dialog" aria-modal="true" aria-labelledby="budget-title">
      <div className="modal-head"><div><div className="panel-kicker">NOVO ORÇAMENTO</div><h2 id="budget-title">Limite por categoria</h2></div><button className="close-button" onClick={onClose} aria-label="Fechar">×</button></div>
      <form onSubmit={submit} className="transaction-form">
        <label>Categoria<input value={category} onChange={(event) => setCategory(event.target.value)} placeholder="Ex.: Alimentação" maxLength={60} required /></label>
        <label>Limite mensal<input type="number" min="0.01" step="0.01" value={monthlyLimit} onChange={(event) => setMonthlyLimit(event.target.value)} placeholder="0,00" required /></label>
        <p className="form-hint">Este orçamento vale para {new Date(`${month}-02T12:00:00`).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })}. Os gastos são calculados pela API.</p>
        {error && <div className="form-error" role="alert">{error}</div>}
        <div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>Cancelar</button><button className="primary-button" disabled={busy}>{busy ? 'Salvando…' : 'Salvar orçamento'}</button></div>
      </form>
    </section>
  </div>
}

function CreditCardForm({ onClose, onSave }: { onClose: () => void; onSave: (payload: CreditCardPayload) => Promise<void> }) {
  const [name, setName] = useState('')
  const [creditLimit, setCreditLimit] = useState('')
  const [closingDay, setClosingDay] = useState('10')
  const [dueDay, setDueDay] = useState('17')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('')
    try { await onSave({ name, creditLimit: Number(creditLimit), closingDay: Number(closingDay), dueDay: Number(dueDay) }) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Não foi possível cadastrar o cartão.') }
    finally { setBusy(false) }
  }
  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <section className="transaction-modal" role="dialog" aria-modal="true" aria-labelledby="card-title">
      <div className="modal-head"><div><div className="panel-kicker">NOVO CARTÃO</div><h2 id="card-title">Cadastrar cartão</h2></div><button className="close-button" onClick={onClose} aria-label="Fechar">×</button></div>
      <form onSubmit={submit} className="transaction-form">
        <label>Nome do cartão<input value={name} onChange={(event) => setName(event.target.value)} placeholder="Ex.: Cartão principal" maxLength={60} required /></label>
        <label>Limite total<input type="number" min="0.01" step="0.01" value={creditLimit} onChange={(event) => setCreditLimit(event.target.value)} placeholder="0,00" required /></label>
        <div className="form-columns"><label>Dia de fechamento<input type="number" min="1" max="28" value={closingDay} onChange={(event) => setClosingDay(event.target.value)} required /></label>
          <label>Dia de vencimento<input type="number" min="1" max="28" value={dueDay} onChange={(event) => setDueDay(event.target.value)} required /></label></div>
        <p className="form-hint">Por segurança, a API aceita dias de fechamento e vencimento entre 1 e 28.</p>
        {error && <div className="form-error" role="alert">{error}</div>}
        <div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>Cancelar</button><button className="primary-button" disabled={busy}>{busy ? 'Salvando…' : 'Salvar cartão'}</button></div>
      </form>
    </section>
  </div>
}

function CardPurchaseForm({ onClose, onSave }: { onClose: () => void; onSave: (payload: CardPurchasePayload) => Promise<void> }) {
  const [description, setDescription] = useState('')
  const [category, setCategory] = useState('')
  const [amount, setAmount] = useState('')
  const [purchaseDate, setPurchaseDate] = useState(localDate)
  const [installments, setInstallments] = useState('1')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('')
    try { await onSave({ description, category, amount: Number(amount), purchaseDate, installments: Number(installments) }) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Não foi possível registrar a compra.') }
    finally { setBusy(false) }
  }
  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <section className="transaction-modal" role="dialog" aria-modal="true" aria-labelledby="purchase-title">
      <div className="modal-head"><div><div className="panel-kicker">NOVA COMPRA</div><h2 id="purchase-title">Registrar compra no cartão</h2></div><button className="close-button" onClick={onClose} aria-label="Fechar">×</button></div>
      <form onSubmit={submit} className="transaction-form">
        <label>Descrição<input value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Ex.: Fone de ouvido" maxLength={120} required /></label>
        <div className="form-columns"><label>Categoria<input value={category} onChange={(event) => setCategory(event.target.value)} placeholder="Ex.: Eletrônicos" maxLength={60} required /></label>
          <label>Valor total<input type="number" min="0.01" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="0,00" required /></label></div>
        <div className="form-columns"><label>Data da compra<input type="date" value={purchaseDate} onChange={(event) => setPurchaseDate(event.target.value)} required /></label>
          <label>Parcelas<input type="number" min="1" max="48" value={installments} onChange={(event) => setInstallments(event.target.value)} required /></label></div>
        <p className="form-hint">Informe o valor total. A API divide a compra nas parcelas e distribui cada parcela na fatura correta.</p>
        {error && <div className="form-error" role="alert">{error}</div>}
        <div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>Cancelar</button><button className="primary-button" disabled={busy}>{busy ? 'Salvando…' : 'Registrar compra'}</button></div>
      </form>
    </section>
  </div>
}

function SummaryCard({ label, value, kind, icon, loading }: { label: string; value?: number; kind: string; icon: string; loading: boolean }) {
  return (
    <article className={`summary-card ${kind}`}>
      <div className="summary-top"><span>{label}</span><span className="summary-icon">{icon}</span></div>
      <div className="summary-value">{loading ? <span className="skeleton-value" /> : money.format(value ?? 0)}</div>
      <div className="summary-foot"><span className="summary-dot" /> Atualizado para o mês selecionado</div>
    </article>
  )
}

function TransactionList({ items, onEdit, onDelete }: {
  items: Transaction[]; onEdit?: (item: Transaction) => void; onDelete?: (item: Transaction) => void
}) {
  if (!items.length) return <div className="empty-state"><div className="empty-illustration">↕</div><strong>Nenhuma transação por aqui</strong><span>Quando você registrar uma movimentação, ela aparece nesta lista.</span></div>
  return (
    <div className="transaction-list">
      {items.map((item) => (
        <article className="transaction-row" key={item.id}>
          <div className={`transaction-icon ${item.type.toLowerCase()}`}>{item.type === 'INCOME' ? '↗' : '↘'}</div>
          <div className="transaction-main"><strong>{item.description}</strong><span>{item.category} <i>·</i> {dateFormat.format(new Date(`${item.transactionDate}T12:00:00`))}</span></div>
          <strong className={`transaction-amount ${item.type.toLowerCase()}`}>{item.type === 'INCOME' ? '+' : '−'}{money.format(item.amount)}</strong>
          {(onEdit || onDelete) && <div className="transaction-actions">
            {onEdit && <button onClick={() => onEdit(item)} aria-label={`Editar ${item.description}`} title="Editar">✎</button>}
            {onDelete && <button className="delete-action" onClick={() => onDelete(item)} aria-label={`Excluir ${item.description}`} title="Excluir">×</button>}
          </div>}
        </article>
      ))}
    </div>
  )
}

function LoadingRows() {
  return <div className="loading-list"><span /><span /><span /></div>
}

function flowPercent(value = 0, comparison = 0) {
  const total = value + comparison
  return total === 0 ? 0 : Math.max(8, Math.round(value / total * 100))
}

function AuthScreen({ onAuthenticated }: { onAuthenticated: (token: string, user: User) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      if (mode === 'register') await register({ name, email, password })
      const session = await login({ email, password })
      onAuthenticated(session.accessToken, session.user)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível entrar. Tente novamente.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-page">
      <section className="auth-story">
        <a className="brand auth-brand" href="#inicio"><span className="brand-mark">f</span><span>finances<span className="brand-light">flow</span></span></a>
        <div className="story-copy"><span className="story-label"><i /> FINANÇAS COM MAIS INTENÇÃO</span><h1>Clareza para<br />seguir em frente<span>.</span></h1><p>Um lugar simples para acompanhar suas escolhas e cuidar do que importa.</p></div>
        <div className="story-bottom"><span>01 — Visão clara</span><span>02 — Um passo de cada vez</span></div>
        <div className="story-orbit orbit-one" /><div className="story-orbit orbit-two" />
      </section>
      <section className="auth-side">
        <div className="auth-card">
          <div className="auth-mobile-brand"><span className="brand-mark">f</span><span>finances<span className="brand-light">flow</span></span></div>
          <div className="auth-eyebrow">BEM-VINDO AO SEU ESPAÇO</div>
          <h2>{mode === 'login' ? 'Entre na sua conta' : 'Crie sua conta'}</h2>
          <p className="auth-subtitle">{mode === 'login' ? 'Acompanhe seu dinheiro com mais tranquilidade.' : 'Comece a organizar sua vida financeira.'}</p>
          <form className="auth-form" onSubmit={submit}>
            {mode === 'register' && <label>Seu nome<input autoComplete="name" value={name} onChange={(event) => setName(event.target.value)} placeholder="Como podemos te chamar?" required maxLength={100} /></label>}
            <label>E-mail<input type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="voce@exemplo.com" required /></label>
            <label>Senha<input type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Mínimo de 8 caracteres" minLength={mode === 'register' ? 8 : 1} required /></label>
            {error && <div className="form-error" role="alert">{error}</div>}
            <button className="primary-button auth-submit" disabled={busy}>{busy ? 'Aguarde…' : mode === 'login' ? 'Entrar na conta' : 'Criar conta'} <span>→</span></button>
          </form>
          <div className="auth-switch">{mode === 'login' ? 'Ainda não tem uma conta?' : 'Já tem uma conta?'} <button onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }}>{mode === 'login' ? 'Criar conta' : 'Entrar'}</button></div>
          <div className="auth-privacy"><span>⌑</span> Seus dados financeiros são privados e protegidos.</div>
        </div>
        <div className="auth-copyright">FINANCESFLOW <span>·</span> CONTROLE FEITO PARA VOCÊ</div>
      </section>
    </div>
  )
}

function TransactionForm({ initial, onClose, onSave }: { initial?: Transaction; onClose: () => void; onSave: (payload: TransactionPayload) => Promise<void> }) {
  const [description, setDescription] = useState(initial?.description ?? '')
  const [amount, setAmount] = useState(initial ? String(initial.amount) : '')
  const [category, setCategory] = useState(initial?.category ?? '')
  const [type, setType] = useState<'INCOME' | 'EXPENSE'>(initial?.type ?? 'EXPENSE')
  const [transactionDate, setTransactionDate] = useState(initial?.transactionDate ?? localDate())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await onSave({ description, amount: Number(amount), type, category, transactionDate })
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível salvar a transação.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section className="transaction-modal" role="dialog" aria-modal="true" aria-labelledby="transaction-title">
        <div className="modal-head"><div><div className="panel-kicker">{initial ? 'EDITAR MOVIMENTAÇÃO' : 'NOVA MOVIMENTAÇÃO'}</div><h2 id="transaction-title">{initial ? 'Editar transação' : 'Adicionar transação'}</h2></div><button className="close-button" onClick={onClose} aria-label="Fechar">×</button></div>
        <form onSubmit={submit} className="transaction-form">
          <div className="type-toggle"><button type="button" className={type === 'EXPENSE' ? 'selected expense-selected' : ''} onClick={() => setType('EXPENSE')}>Despesa</button><button type="button" className={type === 'INCOME' ? 'selected income-selected' : ''} onClick={() => setType('INCOME')}>Receita</button></div>
          <label>Descrição<input value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Ex.: Mercado da semana" maxLength={120} required /></label>
          <label>Valor<input type="number" min="0.01" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="0,00" required /></label>
          <div className="form-columns"><label>Categoria<input value={category} onChange={(event) => setCategory(event.target.value)} placeholder="Ex.: Alimentação" maxLength={60} required /></label><label>Data<input type="date" value={transactionDate} onChange={(event) => setTransactionDate(event.target.value)} required /></label></div>
          {error && <div className="form-error" role="alert">{error}</div>}
          <div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>Cancelar</button><button className="primary-button" disabled={busy}>{busy ? 'Salvando…' : initial ? 'Salvar alterações' : 'Salvar transação'}</button></div>
        </form>
      </section>
    </div>
  )
}

function DeleteTransactionDialog({ transaction, onClose, onConfirm }: {
  transaction: Transaction; onClose: () => void; onConfirm: () => Promise<void>
}) {
  const [busy, setBusy] = useState(false)
  async function confirm() {
    setBusy(true)
    await onConfirm()
    setBusy(false)
  }
  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
    <section className="transaction-modal delete-modal" role="dialog" aria-modal="true" aria-labelledby="delete-title">
      <div className="modal-head"><div><div className="panel-kicker">EXCLUIR MOVIMENTAÇÃO</div><h2 id="delete-title">Confirmar exclusão?</h2></div>
        <button className="close-button" onClick={onClose} aria-label="Fechar" disabled={busy}>×</button></div>
      <p>Tem certeza de que deseja excluir <strong>{transaction.description}</strong>? Essa ação não pode ser desfeita.</p>
      <div className="modal-actions"><button className="secondary-button" onClick={onClose} disabled={busy}>Cancelar</button>
        <button className="danger-button" onClick={confirm} disabled={busy}>{busy ? 'Excluindo…' : 'Excluir transação'}</button></div>
    </section>
  </div>
}

export default App
