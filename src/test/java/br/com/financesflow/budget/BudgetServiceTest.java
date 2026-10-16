package br.com.financesflow.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.financesflow.card.CardPurchase;
import br.com.financesflow.card.CardPurchaseRepository;
import br.com.financesflow.card.CreditCard;
import br.com.financesflow.transaction.Transaction;
import br.com.financesflow.transaction.TransactionRepository;
import br.com.financesflow.transaction.TransactionType;
import br.com.financesflow.user.AppUser;
import br.com.financesflow.user.AppUserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class BudgetServiceTest {

    private BudgetRepository budgetRepository;
    private TransactionRepository transactionRepository;
    private CardPurchaseRepository cardPurchaseRepository;
    private AppUserRepository userRepository;
    private BudgetService service;

    @BeforeEach
    void setUp() {
        budgetRepository = mock(BudgetRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        cardPurchaseRepository = mock(CardPurchaseRepository.class);
        userRepository = mock(AppUserRepository.class);
        service = new BudgetService(budgetRepository, transactionRepository,
                cardPurchaseRepository, userRepository);
    }

    @Test
    void spentIncludesCategoryExpensesAndTheInstallmentForTheSelectedMonth() {
        // Arrange: R$ 40 + R$ 10 em despesas e uma parcela de R$ 100 em novembro.
        var ownerId = UUID.randomUUID();
        var month = YearMonth.of(2026, 11);
        var budget = budget(ownerId, "Mercado", month, "140.00");
        var firstExpense = mock(Transaction.class);
        var secondExpense = mock(Transaction.class);
        when(firstExpense.getAmount()).thenReturn(new BigDecimal("40.00"));
        when(secondExpense.getAmount()).thenReturn(new BigDecimal("10.00"));
        when(budgetRepository.findAllByOwner_IdAndBudgetMonthOrderByCategoryAsc(ownerId, month.atDay(1)))
                .thenReturn(List.of(budget));
        when(transactionRepository
                .findByOwner_IdAndCategoryIgnoreCaseAndTypeAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                        ownerId, "Mercado", TransactionType.EXPENSE, month.atDay(1), month.plusMonths(1).atDay(1)))
                .thenReturn(List.of(firstExpense, secondExpense));
        var purchase = new CardPurchase(mock(CreditCard.class), "Compra parcelada", "Mercado",
                new BigDecimal("300.00"), LocalDate.of(2026, 10, 10), month.atDay(1), 3);
        when(cardPurchaseRepository
                .findAllByCard_Owner_IdAndCategoryIgnoreCaseAndInvoiceMonthLessThanEqual(
                        ownerId, "Mercado", month.atDay(1)))
                .thenReturn(List.of(purchase));

        // Act: busca-se o orçamento do usuário para o mês.
        var response = service.findAll(ownerId, month).get(0);

        // Assert: a parcela completa o limite e o sistema informa que ultrapassou em R$ 10.
        assertEquals(new BigDecimal("150.00"), response.spent());
        assertEquals(new BigDecimal("-10.00"), response.remaining());
        assertTrue(response.overLimit());
    }

    @Test
    void reachingTheLimitExactlyIsNotConsideredOverLimit() {
        // Arrange: despesas somam exatamente o limite de R$ 100.
        var ownerId = UUID.randomUUID();
        var month = YearMonth.of(2026, 11);
        var budget = budget(ownerId, "Transporte", month, "100.00");
        var expense = mock(Transaction.class);
        when(expense.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(budgetRepository.findAllByOwner_IdAndBudgetMonthOrderByCategoryAsc(ownerId, month.atDay(1)))
                .thenReturn(List.of(budget));
        when(transactionRepository
                .findByOwner_IdAndCategoryIgnoreCaseAndTypeAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                        ownerId, "Transporte", TransactionType.EXPENSE, month.atDay(1), month.plusMonths(1).atDay(1)))
                .thenReturn(List.of(expense));

        // Act: calculamos o resumo.
        var response = service.findAll(ownerId, month).get(0);

        // Assert: limite atingido significa saldo zero, mas ainda não é estouro.
        assertEquals(new BigDecimal("0.00"), response.remaining());
        assertFalse(response.overLimit());
    }

    @Test
    void duplicateCategoryAndMonthReturnsConflictWithoutSaving() {
        // Arrange: usuário existente e orçamento já cadastrado, ignorando maiúsculas/minúsculas.
        var ownerId = UUID.randomUUID();
        var month = YearMonth.of(2026, 11);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(mock(AppUser.class)));
        when(budgetRepository.existsByOwner_IdAndCategoryIgnoreCaseAndBudgetMonth(
                ownerId, "Mercado", month.atDay(1))).thenReturn(true);

        // Act: tenta-se cadastrar a mesma categoria no mesmo mês.
        var exception = assertThrows(ResponseStatusException.class,
                () -> service.create(ownerId,
                        new BudgetRequest("  Mercado  ", new BigDecimal("200.00"), month)));

        // Assert: regra de unicidade vira HTTP 409 e não cria outro registro.
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(budgetRepository, never()).save(any(Budget.class));
    }

    @Test
    void listingAlwaysQueriesOnlyTheRequestedUsersMonth() {
        // Arrange: sem orçamentos, o repositório recebe proprietário e mês selecionados.
        var ownerId = UUID.randomUUID();
        var month = YearMonth.of(2026, 11);
        when(budgetRepository.findAllByOwner_IdAndBudgetMonthOrderByCategoryAsc(ownerId, month.atDay(1)))
                .thenReturn(List.of());

        // Act: lista-se o mês para esse usuário.
        var response = service.findAll(ownerId, month);

        // Assert: não há resultados e a consulta está restrita ao usuário e mês pedidos.
        assertTrue(response.isEmpty());
        verify(budgetRepository).findAllByOwner_IdAndBudgetMonthOrderByCategoryAsc(ownerId, month.atDay(1));
    }

    private Budget budget(UUID ownerId, String category, YearMonth month, String limit) {
        var owner = mock(AppUser.class);
        when(owner.getId()).thenReturn(ownerId);
        var budget = mock(Budget.class);
        when(budget.getOwner()).thenReturn(owner);
        when(budget.getCategory()).thenReturn(category);
        when(budget.getBudgetMonth()).thenReturn(month.atDay(1));
        when(budget.getMonthlyLimit()).thenReturn(new BigDecimal(limit));
        return budget;
    }
}
