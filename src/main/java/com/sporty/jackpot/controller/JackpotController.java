package com.sporty.jackpot.controller;

import com.sporty.jackpot.dto.JackpotRequest;
import com.sporty.jackpot.dto.JackpotUpdateRequest;
import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.JackpotContribution;
import com.sporty.jackpot.service.JackpotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jackpots")
@Tag(name = "Jackpots", description = "Administer jackpots and observe their pools")
public class JackpotController {

    private final JackpotService jackpotService;

    public JackpotController(JackpotService jackpotService) {
        this.jackpotService = jackpotService;
    }

    @GetMapping
    @Operation(summary = "List all jackpots",
            description = "Each jackpot with its current pool and its contribution and reward configuration.")
    public List<Jackpot> all() {
        return jackpotService.findAll();
    }

    @PostMapping
    @Operation(summary = "Create a jackpot",
            description = """
                    The id is assigned by the service and returned in the `Location` header. The pool \
                    starts at `initialPoolAmount` and returns there whenever the jackpot is awarded.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Invalid configuration", content = @Content)
    })
    public ResponseEntity<Jackpot> create(@Valid @RequestBody JackpotRequest request) {
        Jackpot jackpot = jackpotService.create(request);
        return ResponseEntity.created(URI.create("/api/jackpots/" + jackpot.getId())).body(jackpot);
    }

    @GetMapping("/{jackpotId}")
    @Operation(summary = "Get one jackpot")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The jackpot"),
            @ApiResponse(responseCode = "404", description = "No such jackpot", content = @Content)
    })
    public Jackpot byId(
            @Parameter(description = "Jackpot id", example = "33333333-3333-3333-3333-333333333333")
            @PathVariable UUID jackpotId) {
        return jackpotService.findById(jackpotId);
    }

    @PutMapping("/{jackpotId}")
    @Operation(summary = "Rename and reconfigure a jackpot",
            description = """
                    Replaces the name and both configurations. The pool and the initial amount are \
                    untouched, and contributions already made keep the amounts they were computed \
                    with — only future bets see the new configuration.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated jackpot"),
            @ApiResponse(responseCode = "400", description = "Invalid configuration", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such jackpot", content = @Content)
    })
    public Jackpot update(
            @Parameter(description = "Jackpot id") @PathVariable UUID jackpotId,
            @Valid @RequestBody JackpotUpdateRequest request) {
        return jackpotService.update(jackpotId, request);
    }

    @DeleteMapping("/{jackpotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a jackpot",
            description = """
                    Only possible while nothing has contributed to it — contribution rows are the \
                    record of real bets and must not be orphaned.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "404", description = "No such jackpot", content = @Content),
            @ApiResponse(responseCode = "409", description = "The jackpot has contributions", content = @Content)
    })
    public void delete(@Parameter(description = "Jackpot id") @PathVariable UUID jackpotId) {
        jackpotService.delete(jackpotId);
    }

    @GetMapping("/{jackpotId}/contributions")
    @Operation(summary = "List a jackpot's contributions",
            description = """
                    Newest first. Each row records the bet, the user, the stake, how much of it was \
                    contributed, and the pool amount right after that contribution.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The contributions"),
            @ApiResponse(responseCode = "404", description = "No such jackpot", content = @Content)
    })
    public List<JackpotContribution> contributions(
            @Parameter(description = "Jackpot id", example = "33333333-3333-3333-3333-333333333333")
            @PathVariable UUID jackpotId) {
        return jackpotService.findContributions(jackpotId);
    }
}
