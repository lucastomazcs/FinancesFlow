package br.com.financesflow.report;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthlyReportResponse(
        YearMonth month,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal balance
) {
}
