package com.finpulse.service;

import com.finpulse.dto.CreateTransactionRequest;
import com.finpulse.dto.PageResponse;
import com.finpulse.dto.TransactionDto;
import com.finpulse.dto.UpdateTransactionRequest;
import com.finpulse.entity.Category;
import com.finpulse.entity.Transaction;
import com.finpulse.entity.TransactionType;
import com.finpulse.entity.User;
import com.finpulse.exception.BusinessValidationException;
import com.finpulse.exception.ResourceNotFoundException;
import com.finpulse.messaging.EventPublisher;
import com.finpulse.messaging.TransactionCreatedEvent;
import com.finpulse.repository.CategoryRepository;
import com.finpulse.repository.TransactionRepository;
import com.finpulse.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Business logic for user-isolated financial transaction CRUD, filtering, and pagination.
 */
@Service
public class TransactionService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("transactionDate", "amount", "createdAt");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SEARCH_LENGTH = 100;
    private static final int MAX_RECEIPT_URL_LENGTH = 2048;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final DashboardCacheInvalidator dashboardCacheInvalidator;
    private final EventPublisher eventPublisher;

    public TransactionService(
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            DashboardCacheInvalidator dashboardCacheInvalidator,
            EventPublisher eventPublisher
    ) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.dashboardCacheInvalidator = dashboardCacheInvalidator;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionDto> listTransactions(
            UUID userId,
            int page,
            int size,
            String sortBy,
            String sortDir,
            TransactionType type,
            UUID categoryId,
            LocalDate startDate,
            LocalDate endDate,
            String search
    ) {
        if (page < 0) {
            throw new BusinessValidationException("Parameter 'page' tidak boleh kurang dari 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessValidationException("Parameter 'size' harus antara 1 sampai 100");
        }

        String resolvedSortBy = (sortBy == null || sortBy.isBlank()) ? "transactionDate" : sortBy.trim();
        if (!ALLOWED_SORT_FIELDS.contains(resolvedSortBy)) {
            throw new BusinessValidationException(
                    "Parameter 'sortBy' hanya mendukung: transactionDate, amount, createdAt"
            );
        }

        String resolvedSortDir = (sortDir == null || sortDir.isBlank()) ? "DESC" : sortDir.trim().toUpperCase(Locale.ROOT);
        if (!"ASC".equals(resolvedSortDir) && !"DESC".equals(resolvedSortDir)) {
            throw new BusinessValidationException("Parameter 'sortDir' hanya mendukung: ASC, DESC");
        }

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessValidationException("Parameter 'startDate' tidak boleh lebih besar dari 'endDate'");
        }

        if (search != null && search.length() > MAX_SEARCH_LENGTH) {
            throw new BusinessValidationException("Parameter 'search' maksimal 100 karakter");
        }

        Sort.Direction direction = Sort.Direction.valueOf(resolvedSortDir);
        Sort sort = "createdAt".equals(resolvedSortBy)
                ? Sort.by(direction, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
                : Sort.by(direction, resolvedSortBy)
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .and(Sort.by(Sort.Direction.DESC, "id"));

        Specification<Transaction> spec = buildFilterSpecification(
                userId, type, categoryId, startDate, endDate, search
        );

        Page<TransactionDto> pageResult = transactionRepository
                .findAll(spec, PageRequest.of(page, size, sort))
                .map(TransactionDto::from);

        return PageResponse.from(pageResult);
    }

    @Transactional(readOnly = true)
    public TransactionDto getTransactionById(UUID userId, UUID transactionId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaksi tidak ditemukan"));
        return TransactionDto.from(transaction);
    }

    @Transactional
    public TransactionDto createTransaction(UUID userId, CreateTransactionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna tidak ditemukan"));
        Category category = resolveAccessibleCategory(request.categoryId(), userId);
        validateTypeMatchesCategory(request.type(), category);
        BigDecimal normalizedAmount = validateAndScaleAmount(request.amount());
        String normalizedReceiptUrl = normalizeAndValidateReceiptUrl(request.receiptUrl());
        String normalizedDescription = normalizeDescription(request.description());

        Transaction transaction = new Transaction(
                user,
                category,
                request.type(),
                normalizedAmount,
                normalizedDescription,
                request.transactionDate(),
                normalizedReceiptUrl
        );

        Transaction saved = transactionRepository.saveAndFlush(transaction);
        dashboardCacheInvalidator.evictAfterCommit(userId);
        publishExpenseEventIfNeeded(userId, saved);
        return TransactionDto.from(saved);
    }

    @Transactional
    public TransactionDto updateTransaction(UUID userId, UUID transactionId, UpdateTransactionRequest request) {
        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaksi tidak ditemukan"));
        Category category = resolveAccessibleCategory(request.categoryId(), userId);
        validateTypeMatchesCategory(request.type(), category);
        BigDecimal normalizedAmount = validateAndScaleAmount(request.amount());
        String normalizedReceiptUrl = normalizeAndValidateReceiptUrl(request.receiptUrl());
        String normalizedDescription = normalizeDescription(request.description());

        transaction.setCategory(category);
        transaction.setType(request.type());
        transaction.setAmount(normalizedAmount);
        transaction.setDescription(normalizedDescription);
        transaction.setTransactionDate(request.transactionDate());
        transaction.setReceiptUrl(normalizedReceiptUrl);

        Transaction saved = transactionRepository.saveAndFlush(transaction);
        dashboardCacheInvalidator.evictAfterCommit(userId);
        publishExpenseEventIfNeeded(userId, saved);
        return TransactionDto.from(saved);
    }

    @Transactional
    public void deleteTransaction(UUID userId, UUID transactionId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaksi tidak ditemukan"));
        transactionRepository.delete(transaction);
        dashboardCacheInvalidator.evictAfterCommit(userId);
    }

    private void publishExpenseEventIfNeeded(UUID userId, Transaction saved) {
        if (saved.getType() == TransactionType.EXPENSE) {
            eventPublisher.publishTransactionCreatedAfterCommit(new TransactionCreatedEvent(
                    saved.getId(),
                    userId,
                    saved.getCategory().getId(),
                    saved.getType(),
                    saved.getAmount(),
                    saved.getTransactionDate()
            ));
        }
    }

    private Category resolveAccessibleCategory(UUID categoryId, UUID userId) {
        return categoryRepository.findAccessibleByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kategori tidak ditemukan"));
    }

    private void validateTypeMatchesCategory(TransactionType transactionType, Category category) {
        if (category.getType() != transactionType) {
            throw new BusinessValidationException(
                    "Tipe transaksi tidak sesuai dengan tipe kategori yang dipilih"
            );
        }
    }

    private BigDecimal validateAndScaleAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessValidationException("Nominal transaksi harus lebih besar dari 0");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeAndValidateReceiptUrl(String receiptUrl) {
        if (receiptUrl == null || receiptUrl.isBlank()) {
            return null;
        }
        String trimmed = receiptUrl.trim();
        if (trimmed.length() > MAX_RECEIPT_URL_LENGTH) {
            throw new BusinessValidationException("URL struk maksimal 2048 karakter");
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new BusinessValidationException("URL struk tidak valid; wajib berupa URL http:// atau https://");
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || host == null
                || host.isBlank()
                || uri.getUserInfo() != null) {
            throw new BusinessValidationException("URL struk tidak valid; wajib berupa URL http:// atau https://");
        }
        return trimmed;
    }

    private Specification<Transaction> buildFilterSpecification(
            UUID userId,
            TransactionType type,
            UUID categoryId,
            LocalDate startDate,
            LocalDate endDate,
            String search
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), endDate));
            }
            if (search != null && !search.isBlank()) {
                String escaped = search.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                predicates.add(cb.like(
                        cb.lower(cb.coalesce(root.get("description"), "")),
                        "%" + escaped + "%",
                        '\\'
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
