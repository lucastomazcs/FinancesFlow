package br.com.financesflow.card;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {
    boolean existsByOwner_IdAndNameIgnoreCase(UUID ownerId, String name);
    java.util.Optional<CreditCard> findByIdAndOwner_Id(UUID id, UUID ownerId);
    List<CreditCard> findAllByOwner_IdOrderByNameAsc(UUID ownerId);
}
