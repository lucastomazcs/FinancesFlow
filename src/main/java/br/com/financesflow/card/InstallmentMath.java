package br.com.financesflow.card;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public final class InstallmentMath {

    private InstallmentMath() {
    }

    public static int installmentNumberFor(CardPurchase purchase, YearMonth invoiceMonth) {
        var firstMonth = YearMonth.from(purchase.getInvoiceMonth());
        var offset = ChronoUnit.MONTHS.between(firstMonth, invoiceMonth);
        return offset >= 0 && offset < purchase.getInstallmentCount() ? (int) offset + 1 : 0;
    }

    public static BigDecimal amountFor(CardPurchase purchase, int installmentNumber) {
        var count = purchase.getInstallmentCount();
        if (installmentNumber == count) {
            var regularInstallment = purchase.getAmount()
                    .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            return purchase.getAmount().subtract(regularInstallment
                    .multiply(BigDecimal.valueOf(count - 1L)));
        }
        return purchase.getAmount()
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
