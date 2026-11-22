package com.finpulse.controller;

import com.finpulse.dto.CreateTransactionRequest;
import com.finpulse.dto.PageResponse;
import com.finpulse.dto.ReceiptUploadResponse;
import com.finpulse.dto.TransactionDto;
import com.finpulse.dto.UpdateTransactionRequest;
import com.finpulse.entity.TransactionType;
import com.finpulse.security.FinPulseUserPrincipal;
import com.finpulse.service.StorageService;
import com.finpulse.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST endpoints for user-scoped financial transaction CRUD, pagination, filtering, and receipt uploads.
 */
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "CRUD transaksi keuangan, filter/paginasi, dan upload foto struk")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;
    private final StorageService storageService;

    public TransactionController(TransactionService transactionService, StorageService storageService) {
        this.transactionService = transactionService;
        this.storageService = storageService;
    }

    @GetMapping
    @Operation(
            operationId = "listTransactions",
            summary = "Ambil daftar transaksi milik pengguna dengan paginasi dan filter"
    )
    public ResponseEntity<PageResponse<TransactionDto>> listTransactions(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "sortBy", defaultValue = "transactionDate") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "DESC") String sortDir,
            @RequestParam(name = "type", required = false) TransactionType type,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "search", required = false) String search
    ) {
        PageResponse<TransactionDto> response = transactionService.listTransactions(
                principal.getId(), page, size, sortBy, sortDir, type, categoryId, startDate, endDate, search
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(
            operationId = "getTransactionById",
            summary = "Ambil detail transaksi milik pengguna"
    )
    public ResponseEntity<TransactionDto> getTransactionById(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(transactionService.getTransactionById(principal.getId(), id));
    }

    @PostMapping
    @Operation(
            operationId = "createTransaction",
            summary = "Buat transaksi pemasukan atau pengeluaran baru"
    )
    public ResponseEntity<TransactionDto> createTransaction(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        TransactionDto created = transactionService.createTransaction(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(
            operationId = "updateTransaction",
            summary = "Perbarui transaksi milik pengguna"
    )
    public ResponseEntity<TransactionDto> updateTransaction(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTransactionRequest request
    ) {
        return ResponseEntity.ok(transactionService.updateTransaction(principal.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(
            operationId = "deleteTransaction",
            summary = "Hapus transaksi milik pengguna"
    )
    public ResponseEntity<Void> deleteTransaction(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @PathVariable UUID id
    ) {
        transactionService.deleteTransaction(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "uploadTransactionReceipt",
            summary = "Upload foto struk transaksi ke Supabase Storage"
    )
    public ResponseEntity<ReceiptUploadResponse> uploadTransactionReceipt(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        ReceiptUploadResponse uploaded = storageService.uploadReceipt(principal.getId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
    }
}
