package br.com.financesflow.budget;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budgets")
@SecurityRequirement(name = "bearerAuth")
public class BudgetController {

    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody BudgetRequest request) {
        var response = service.create(userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/budgets/" + response.id())).body(response);
    }

    @GetMapping
    public List<BudgetResponse> findAll(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam YearMonth month) {
        return service.findAll(userId(jwt), month);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
