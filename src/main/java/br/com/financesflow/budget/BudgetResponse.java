package br.com.financesflow.budget;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        String category,
        YearMonth month,
        BigDecimal monthlyLimit,
        BigDecimal spent,
        BigDecimal remaining,
        boolean overLimit
) {
}
