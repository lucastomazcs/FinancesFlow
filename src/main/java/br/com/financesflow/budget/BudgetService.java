package br.com.financesflow.budget;

import br.com.financesflow.card.CardPurchaseRepository;
import br.com.financesflow.card.InstallmentMath;
import br.com.financesflow.transaction.TransactionRepository;
import br.com.financesflow.transaction.TransactionType;
import br.com.financesflow.user.AppUserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final CardPurchaseRepository cardPurchaseRepository;
    private final AppUserRepository userRepository;

    public BudgetService(BudgetRepository budgetRepository, TransactionRepository transactionRepository,
                         CardPurchaseRepository cardPurchaseRepository, AppUserRepository userRepository) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
        this.cardPurchaseRepository = cardPurchaseRepository;
        this.userRepository = userRepository;
    }

    public BudgetResponse create(UUID ownerId, BudgetRequest request) {
        var owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuário do token não encontrado"));
        var category = request.category().trim();
        var budgetMonth = request.month().atDay(1);

        if (budgetRepository.existsByOwner_IdAndCategoryIgnoreCaseAndBudgetMonth(
                ownerId, category, budgetMonth)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe um orçamento para essa categoria e mês");
        }

        var budget = budgetRepository.save(
                new Budget(owner, category, budgetMonth, request.monthlyLimit()));
        return toResponse(budget);
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> findAll(UUID ownerId, YearMonth month) {
        return budgetRepository.findAllByOwner_IdAndBudgetMonthOrderByCategoryAsc(
                        ownerId, month.atDay(1))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private BudgetResponse toResponse(Budget budget) {
        var start = budget.getBudgetMonth();
        var endExclusive = start.plusMonths(1);
        var expenses = transactionRepository
                .findByOwner_IdAndCategoryIgnoreCaseAndTypeAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                        budget.getOwner().getId(), budget.getCategory(), TransactionType.EXPENSE,
                        start, endExclusive);
        var transactionExpenses = expenses.stream()
                .map(transaction -> transaction.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var month = YearMonth.from(budget.getBudgetMonth());
        var cardExpenses = cardPurchaseRepository
                .findAllByCard_Owner_IdAndCategoryIgnoreCaseAndInvoiceMonthLessThanEqual(
                        budget.getOwner().getId(), budget.getCategory(), start)
                .stream()
                .map(purchase -> InstallmentMath.installmentNumberFor(purchase, month) == 0
                        ? BigDecimal.ZERO
                        : InstallmentMath.amountFor(purchase,
                                InstallmentMath.installmentNumberFor(purchase, month)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var spent = transactionExpenses.add(cardExpenses);
        var remaining = budget.getMonthlyLimit().subtract(spent);

        return new BudgetResponse(budget.getId(), budget.getCategory(),
                YearMonth.from(budget.getBudgetMonth()), budget.getMonthlyLimit(), spent,
                remaining, spent.compareTo(budget.getMonthlyLimit()) > 0);
    }
}
