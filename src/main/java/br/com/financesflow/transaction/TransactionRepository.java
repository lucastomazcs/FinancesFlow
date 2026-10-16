package br.com.financesflow.transaction;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    List<Transaction> findAllByOwner_Id(UUID ownerId);

    java.util.Optional<Transaction> findByIdAndOwner_Id(UUID id, UUID ownerId);

    List<Transaction> findByOwner_IdAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
            UUID ownerId,
            LocalDate start,
            LocalDate endExclusive
    );

    List<Transaction> findByOwner_IdAndCategoryIgnoreCaseAndTypeAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
            UUID ownerId,
            String category,
            TransactionType type,
            LocalDate start,
            LocalDate endExclusive
    );
}
