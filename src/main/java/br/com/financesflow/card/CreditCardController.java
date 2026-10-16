package br.com.financesflow.card;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
@SecurityRequirement(name = "bearerAuth")
public class CreditCardController {

    private final CreditCardService service;

    public CreditCardController(CreditCardService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CreditCardResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody CreditCardRequest request) {
        var response = service.create(userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/cards/" + response.id())).body(response);
    }

    @GetMapping
    public List<CreditCardResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
        return service.findAll(userId(jwt));
    }

    @PostMapping("/{cardId}/purchases")
    public ResponseEntity<CardPurchaseResponse> createPurchase(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable UUID cardId,
                                                               @Valid @RequestBody CardPurchaseRequest request) {
        var response = service.createPurchase(userId(jwt), cardId, request);
        return ResponseEntity.created(URI.create("/api/cards/" + cardId + "/purchases/"
                + response.id())).body(response);
    }

    @GetMapping("/{cardId}/invoices")
    public CardInvoiceResponse findInvoice(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID cardId,
                                           @org.springframework.web.bind.annotation.RequestParam YearMonth month) {
        return service.findInvoice(userId(jwt), cardId, month);
    }

    @PostMapping("/{cardId}/invoices/pay")
    public CardPaymentResponse payInvoice(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable UUID cardId,
                                          @org.springframework.web.bind.annotation.RequestParam YearMonth month) {
        return service.payInvoice(userId(jwt), cardId, month);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
