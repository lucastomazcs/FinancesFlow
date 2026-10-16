package br.com.financesflow.report;

import br.com.financesflow.card.CardPurchaseRepository;
import br.com.financesflow.card.InstallmentMath;
import br.com.financesflow.transaction.Transaction;
import br.com.financesflow.transaction.TransactionRepository;
import br.com.financesflow.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthlyReportService {

    private final TransactionRepository transactionRepository;
    private final CardPurchaseRepository cardPurchaseRepository;

    public MonthlyReportService(TransactionRepository transactionRepository,
                                CardPurchaseRepository cardPurchaseRepository) {
        this.transactionRepository = transactionRepository;
        this.cardPurchaseRepository = cardPurchaseRepository;
    }

    @Transactional(readOnly = true)
    public MonthlyReportResponse generate(java.util.UUID ownerId, YearMonth month) {
        var start = month.atDay(1);
        var endExclusive = month.plusMonths(1).atDay(1);

        var transactions = transactionRepository
                .findByOwner_IdAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                        ownerId, start, endExclusive);

        var totalIncome = totalByType(transactions, TransactionType.INCOME);
        var transactionExpenses = totalByType(transactions, TransactionType.EXPENSE);
        var cardExpenses = cardPurchaseRepository
                .findAllByCard_Owner_IdAndInvoiceMonthLessThanEqual(ownerId, start)
                .stream()
                .map(purchase -> InstallmentMath.installmentNumberFor(purchase, month) == 0
                        ? BigDecimal.ZERO
                        : InstallmentMath.amountFor(purchase,
                                InstallmentMath.installmentNumberFor(purchase, month)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalExpenses = transactionExpenses.add(cardExpenses);
        var balance = totalIncome.subtract(totalExpenses);

        return new MonthlyReportResponse(month, totalIncome, totalExpenses, balance);
    }

    private BigDecimal totalByType(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(transaction -> transaction.getType() == type)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
