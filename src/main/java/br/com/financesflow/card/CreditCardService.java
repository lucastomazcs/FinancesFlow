package br.com.financesflow.card;

import br.com.financesflow.user.AppUserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CreditCardService {

    private final CreditCardRepository cardRepository;
    private final CardPurchaseRepository purchaseRepository;
    private final AppUserRepository userRepository;
    private final CardInvoicePaymentRepository paymentRepository;

    public CreditCardService(CreditCardRepository cardRepository, CardPurchaseRepository purchaseRepository,
                             AppUserRepository userRepository, CardInvoicePaymentRepository paymentRepository) {
        this.cardRepository = cardRepository;
        this.purchaseRepository = purchaseRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
    }

    public CreditCardResponse create(UUID ownerId, CreditCardRequest request) {
        var owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuário do token não encontrado"));
        var name = request.name().trim();
        if (cardRepository.existsByOwner_IdAndNameIgnoreCase(ownerId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe um cartão com esse nome");
        }

        var card = cardRepository.save(new CreditCard(owner, name, request.creditLimit(),
                request.closingDay(), request.dueDay()));
        return toResponse(card);
    }

    @Transactional(readOnly = true)
    public List<CreditCardResponse> findAll(UUID ownerId) {
        return cardRepository.findAllByOwner_IdOrderByNameAsc(ownerId).stream()
                .map(this::toResponse)
                .toList();
    }

    public CardPurchaseResponse createPurchase(UUID ownerId, UUID cardId, CardPurchaseRequest request) {
        var card = findOwnedCard(ownerId, cardId);
        var minimumForInstallments = BigDecimal.valueOf(request.installments(), 2);
        if (request.amount().compareTo(minimumForInstallments) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O valor precisa permitir pelo menos R$ 0,01 por parcela");
        }
        var purchaseDate = request.purchaseDate();
        var invoiceMonth = purchaseDate.getDayOfMonth() >= card.getClosingDay()
                ? purchaseDate.plusMonths(1).withDayOfMonth(1)
                : purchaseDate.withDayOfMonth(1);
        var availableLimit = availableLimit(card);
        if (request.amount().compareTo(availableLimit) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Compra excede o limite disponível do cartão");
        }
        var purchase = purchaseRepository.save(new CardPurchase(card, request.description().trim(),
                request.category().trim(), request.amount(), purchaseDate, invoiceMonth,
                request.installments()));
        return toPurchaseResponse(purchase, request.amount(), request.installments(), invoiceMonth);
    }

    @Transactional(readOnly = true)
    public CardInvoiceResponse findInvoice(UUID ownerId, UUID cardId, java.time.YearMonth month) {
        var card = findOwnedCard(ownerId, cardId);
        return buildInvoice(card, month);
    }

    public CardPaymentResponse payInvoice(UUID ownerId, UUID cardId, YearMonth month) {
        var card = findOwnedCard(ownerId, cardId);
        if (paymentRepository.findByCard_IdAndInvoiceMonth(cardId, month.atDay(1)).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Fatura já está paga");
        }
        var invoice = buildInvoice(card, month);
        if (invoice.total().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível pagar uma fatura vazia");
        }
        var payment = paymentRepository.save(new CardInvoicePayment(card, month.atDay(1), invoice.total()));
        return new CardPaymentResponse(cardId, month, payment.getAmount(), payment.getPaidAt());
    }

    private CardInvoiceResponse buildInvoice(CreditCard card, YearMonth month) {
        var cardId = card.getId();
        var invoiceMonth = month.atDay(1);
        var purchases = purchaseRepository
                .findAllByCard_IdAndInvoiceMonthLessThanEqualOrderByPurchaseDateAsc(cardId, invoiceMonth)
                .stream()
                .map(purchase -> {
                    var number = InstallmentMath.installmentNumberFor(purchase, month);
                    return number == 0 ? null : toInvoiceItem(purchase, number);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        var total = purchases.stream().map(CardInvoiceItemResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var payment = paymentRepository.findByCard_IdAndInvoiceMonth(cardId, invoiceMonth);
        return new CardInvoiceResponse(card.getId(), card.getName(), month,
                month.atDay(card.getDueDay()), total, payment.isPresent(),
                payment.map(CardInvoicePayment::getPaidAt).orElse(null),
                purchases);
    }

    private CreditCard findOwnedCard(UUID ownerId, UUID cardId) {
        return cardRepository.findByIdAndOwner_Id(cardId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cartão não encontrado"));
    }

    private CardInvoiceItemResponse toInvoiceItem(CardPurchase purchase, int installmentNumber) {
        return new CardInvoiceItemResponse(purchase.getId(), purchase.getDescription(),
                purchase.getCategory(), InstallmentMath.amountFor(purchase, installmentNumber),
                installmentNumber, purchase.getInstallmentCount(), purchase.getPurchaseDate());
    }

    private CardPurchaseResponse toPurchaseResponse(CardPurchase purchase, BigDecimal amount,
                                                    int installments, LocalDate invoiceMonth) {
        return new CardPurchaseResponse(purchase.getId(), purchase.getDescription(),
                purchase.getCategory(), amount, installments, purchase.getPurchaseDate(), invoiceMonth);
    }

    private CreditCardResponse toResponse(CreditCard card) {
        return new CreditCardResponse(card.getId(), card.getName(), card.getCreditLimit(),
                availableLimit(card), card.getClosingDay(), card.getDueDay(), card.getCreatedAt());
    }

    private BigDecimal availableLimit(CreditCard card) {
        Set<LocalDate> paidMonths = paymentRepository.findAllByCard_Id(card.getId()).stream()
                .map(CardInvoicePayment::getInvoiceMonth)
                .collect(Collectors.toSet());
        var usedCredit = purchaseRepository.findAllByCard_Id(card.getId()).stream()
                .map(purchase -> {
                    var remaining = BigDecimal.ZERO;
                    var firstInvoice = YearMonth.from(purchase.getInvoiceMonth());
                    for (int installment = 1; installment <= purchase.getInstallmentCount(); installment++) {
                        var installmentMonth = firstInvoice.plusMonths(installment - 1L);
                        if (!paidMonths.contains(installmentMonth.atDay(1))) {
                            remaining = remaining.add(InstallmentMath.amountFor(purchase, installment));
                        }
                    }
                    return remaining;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return card.getCreditLimit().subtract(usedCredit);
    }
}
