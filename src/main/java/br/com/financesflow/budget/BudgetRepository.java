package br.com.financesflow.budget;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    boolean existsByOwner_IdAndCategoryIgnoreCaseAndBudgetMonth(
            UUID ownerId, String category, LocalDate budgetMonth);

    List<Budget> findAllByOwner_IdAndBudgetMonthOrderByCategoryAsc(UUID ownerId, LocalDate budgetMonth);
}
