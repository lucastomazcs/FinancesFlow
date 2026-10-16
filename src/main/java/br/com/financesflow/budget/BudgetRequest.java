package br.com.financesflow.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.YearMonth;

public record BudgetRequest(
        @NotBlank @Size(max = 60) String category,
        @NotNull @DecimalMin("0.01") BigDecimal monthlyLimit,
        @NotNull YearMonth month
) {
}
