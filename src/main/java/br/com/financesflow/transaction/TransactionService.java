package br.com.financesflow.transaction;

import br.com.financesflow.user.AppUserRepository;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TransactionService {

    private final TransactionRepository repository;
    private final AppUserRepository userRepository;

    public TransactionService(TransactionRepository repository, AppUserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public TransactionResponse create(UUID ownerId, TransactionRequest request) {
        var owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuário do token não encontrado"));
        var transaction = new Transaction(request.description(), request.amount(), request.type(),
                request.category(), request.transactionDate());
        transaction.assignOwner(owner);
        return TransactionResponse.from(repository.save(transaction));
    }

    @Transactional(readOnly = true)
    public TransactionPageResponse findAll(UUID ownerId, YearMonth month, TransactionType type,
                                           String category, int page, int size) {
        Specification<Transaction> specification = (root, query, criteria) ->
                criteria.equal(root.get("owner").get("id"), ownerId);

        if (month != null) {
            var start = month.atDay(1);
            var endExclusive = month.plusMonths(1).atDay(1);
            specification = specification.and((root, query, criteria) -> criteria.and(
                    criteria.greaterThanOrEqualTo(root.get("transactionDate"), start),
                    criteria.lessThan(root.get("transactionDate"), endExclusive)));
        }
        if (type != null) {
            specification = specification.and((root, query, criteria) ->
                    criteria.equal(root.get("type"), type));
        }
        if (category != null && !category.isBlank()) {
            var normalizedCategory = category.trim().toLowerCase(Locale.ROOT);
            specification = specification.and((root, query, criteria) ->
                    criteria.equal(criteria.lower(root.get("category")), normalizedCategory));
        }

        var sorting = Sort.by(Sort.Direction.DESC, "transactionDate")
                .and(Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = repository.findAll(specification, PageRequest.of(page, size, sorting));
        return TransactionPageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public TransactionResponse findById(UUID ownerId, UUID id) {
        return TransactionResponse.from(getTransaction(ownerId, id));
    }

    public TransactionResponse update(UUID ownerId, UUID id, TransactionRequest request) {
        var transaction = getTransaction(ownerId, id);
        transaction.update(request.description(), request.amount(), request.type(),
                request.category(), request.transactionDate());
        return TransactionResponse.from(transaction);
    }

    public void delete(UUID ownerId, UUID id) {
        repository.delete(getTransaction(ownerId, id));
    }

    private Transaction getTransaction(UUID ownerId, UUID id) {
        return repository.findByIdAndOwner_Id(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transação não encontrada: " + id));
    }
}
