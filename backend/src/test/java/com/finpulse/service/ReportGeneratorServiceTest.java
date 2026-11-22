package com.finpulse.service;

import com.finpulse.dto.BudgetDto;
import com.finpulse.entity.Category;
import com.finpulse.entity.ReportFormat;
import com.finpulse.entity.Transaction;
import com.finpulse.entity.TransactionType;
import com.finpulse.entity.User;
import com.finpulse.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGeneratorServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetService budgetService;

    private ReportGeneratorService generatorService;

    private UUID userId;
    private List<Transaction> sampleTransactions;
    private List<BudgetDto> sampleBudgets;

    @BeforeEach
    void setUp() {
        generatorService = new ReportGeneratorService(transactionRepository, budgetService);
        userId = UUID.randomUUID();

        User user = new User("report@finpulse.app", "hash", "Report User");
        user.setId(userId);
        Category food = new Category(null, "Makanan, Minuman", TransactionType.EXPENSE, "utensils", "#F59E0B", true);
        food.setId(UUID.randomUUID());
        Category salary = new Category(null, "Gaji", TransactionType.INCOME, "wallet", "#10B981", true);
        salary.setId(UUID.randomUUID());

        Transaction tx1 = new Transaction(
                user, salary, TransactionType.INCOME, new BigDecimal("10000000.00"),
                "Gaji Bulanan", LocalDate.of(2026, 11, 1), null
        );
        Transaction tx2 = new Transaction(
                user, food, TransactionType.EXPENSE, new BigDecimal("150000.00"),
                "=HYPERLINK(\"http://evil.example\",\"Click\")", LocalDate.of(2026, 11, 5), null
        );
        Transaction tx3 = new Transaction(
                user, food, TransactionType.EXPENSE, new BigDecimal("75000.00"),
                "+SUM(A1:A2)", LocalDate.of(2026, 11, 6), null
        );
        Transaction tx4 = new Transaction(
                user, food, TransactionType.EXPENSE, new BigDecimal("50000.00"),
                "-1+2", LocalDate.of(2026, 11, 7), null
        );
        Transaction tx5 = new Transaction(
                user, food, TransactionType.EXPENSE, new BigDecimal("25000.00"),
                "@cmd", LocalDate.of(2026, 11, 8), null
        );
        sampleTransactions = List.of(tx1, tx2, tx3, tx4, tx5);

        sampleBudgets = List.of(new BudgetDto(
                UUID.randomUUID(), food.getId(), food.getName(), food.getIcon(), food.getColor(),
                11, 2026, new BigDecimal("2000000.00"), new BigDecimal("300000.00"),
                new BigDecimal("1700000.00"), new BigDecimal("15.00"), false,
                Instant.now(), Instant.now()
        ));
    }

    @Test
    void shouldGenerateCsvWithUtf8BomRfc4180EscapingAndFormulaInjectionProtection() {
        when(transactionRepository.findAllByUserIdAndDateRangeOrderByDateAsc(
                userId, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)
        )).thenReturn(sampleTransactions);
        when(budgetService.listBudgets(userId, 11, 2026)).thenReturn(sampleBudgets);

        ReportGeneratorService.GeneratedReport report =
                generatorService.generate(userId, 11, 2026, ReportFormat.CSV);

        assertThat(report.fileName()).isEqualTo("finpulse-report-2026-11.csv");
        assertThat(report.contentType()).isEqualTo(ReportGeneratorService.CSV_CONTENT_TYPE);

        byte[] bytes = report.bytes();
        assertThat(bytes.length).isGreaterThan(3);
        // UTF-8 BOM check
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);

        String csvText = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(csvText).contains("FinPulse - Laporan Keuangan Bulanan,Nov 2026");
        assertThat(csvText).contains("Total Pemasukan,10000000.00");
        assertThat(csvText).contains("Total Pengeluaran,300000.00");
        assertThat(csvText).contains("\"Makanan, Minuman\"");
        // Formula injection cells must be prefixed with single quote
        assertThat(csvText).contains("\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"Click\"\")\"");
        assertThat(csvText).contains("'+SUM(A1:A2)");
        assertThat(csvText).contains("'-1+2");
        assertThat(csvText).contains("'@cmd");
        assertThat(generatorService.sanitizeCsvFormula("\tmalicious")).isEqualTo("'\tmalicious");
        assertThat(generatorService.sanitizeCsvFormula("\rmalicious")).isEqualTo("'\rmalicious");
    }

    @Test
    void shouldGenerateValidPdfDocumentWithHeaderMagicBytes() {
        when(transactionRepository.findAllByUserIdAndDateRangeOrderByDateAsc(
                userId, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)
        )).thenReturn(sampleTransactions);
        when(budgetService.listBudgets(userId, 11, 2026)).thenReturn(sampleBudgets);

        ReportGeneratorService.GeneratedReport report =
                generatorService.generate(userId, 11, 2026, ReportFormat.PDF);

        assertThat(report.fileName()).isEqualTo("finpulse-report-2026-11.pdf");
        assertThat(report.contentType()).isEqualTo(ReportGeneratorService.PDF_CONTENT_TYPE);
        assertThat(report.bytes().length).isGreaterThan(500);

        String pdfHeader = new String(report.bytes(), 0, 5, StandardCharsets.US_ASCII);
        assertThat(pdfHeader).isEqualTo("%PDF-");
    }

    @Test
    void shouldNeutralizeFormulaInjectionPrecededBySpacesOrNewlines() {
        assertThat(generatorService.sanitizeCsvFormula(" =CMD|'/C calc'!A0"))
                .isEqualTo("' =CMD|'/C calc'!A0");
        assertThat(generatorService.sanitizeCsvFormula("   +SUM(A1:A2)"))
                .isEqualTo("'   +SUM(A1:A2)");
        assertThat(generatorService.sanitizeCsvFormula("  -1+2"))
                .isEqualTo("'  -1+2");
        assertThat(generatorService.sanitizeCsvFormula("  @cmd"))
                .isEqualTo("'  @cmd");
        assertThat(generatorService.sanitizeCsvFormula("\n=HYPERLINK(\"http://evil\")"))
                .isEqualTo("'\n=HYPERLINK(\"http://evil\")");
        assertThat(generatorService.escapeCsvCell(" =HYPERLINK(\"http://evil\",\"x\")"))
                .isEqualTo("\"' =HYPERLINK(\"\"http://evil\"\",\"\"x\"\")\"");
        assertThat(generatorService.sanitizeCsvFormula("Normal description")).isEqualTo("Normal description");
    }

    @Test
    void shouldGeneratePdfWithoutCrashForNullLongAndUnicodeDescriptionsOrEmptyData() {
        User user = new User("unicode@finpulse.app", "hash", "Unicode User");
        user.setId(userId);
        Category food = new Category(null, "Makanan & Minuman", TransactionType.EXPENSE, "utensils", "#F59E0B", true);
        food.setId(UUID.randomUUID());

        Transaction txNullDesc = new Transaction(
                user, food, TransactionType.EXPENSE, new BigDecimal("50000.00"),
                null, LocalDate.of(2026, 11, 2), null
        );
        Transaction txLongUnicode = new Transaction(
                user, food, TransactionType.EXPENSE, new BigDecimal("125000.00"),
                "Kopi & Roti — café 日本語 ".repeat(20), LocalDate.of(2026, 11, 3), null
        );

        when(transactionRepository.findAllByUserIdAndDateRangeOrderByDateAsc(
                userId, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)
        )).thenReturn(List.of(txNullDesc, txLongUnicode));
        when(budgetService.listBudgets(userId, 11, 2026)).thenReturn(List.of());

        ReportGeneratorService.GeneratedReport report =
                generatorService.generate(userId, 11, 2026, ReportFormat.PDF);
        assertThat(new String(report.bytes(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // Also verify empty transactions & empty budgets
        when(transactionRepository.findAllByUserIdAndDateRangeOrderByDateAsc(
                userId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)
        )).thenReturn(List.of());
        when(budgetService.listBudgets(userId, 10, 2026)).thenReturn(List.of());

        ReportGeneratorService.GeneratedReport emptyReport =
                generatorService.generate(userId, 10, 2026, ReportFormat.PDF);
        assertThat(new String(emptyReport.bytes(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
