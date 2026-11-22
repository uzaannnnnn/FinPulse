package com.finpulse.service;

import com.finpulse.dto.BudgetDto;
import com.finpulse.entity.ReportFormat;
import com.finpulse.entity.Transaction;
import com.finpulse.entity.TransactionType;
import com.finpulse.repository.TransactionRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Generates monthly financial reports in CSV (UTF-8 BOM, RFC 4180 + formula injection safe) and PDF (OpenPDF) formats.
 */
@Service
public class ReportGeneratorService {

    public static final String CSV_CONTENT_TYPE = "text/csv; charset=UTF-8";
    public static final String PDF_CONTENT_TYPE = "application/pdf";

    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter PERIOD_FORMATTER =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final TransactionRepository transactionRepository;
    private final BudgetService budgetService;

    public ReportGeneratorService(
            TransactionRepository transactionRepository,
            BudgetService budgetService
    ) {
        this.transactionRepository = transactionRepository;
        this.budgetService = budgetService;
    }

    public record GeneratedReport(String fileName, String contentType, byte[] bytes) {
    }

    @Transactional(readOnly = true)
    public GeneratedReport generate(UUID userId, int month, int year, ReportFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("Format laporan tidak boleh kosong");
        }
        YearMonth period = YearMonth.of(year, month);
        LocalDate startDate = period.atDay(1);
        LocalDate endDate = period.atEndOfMonth();

        List<Transaction> transactions = transactionRepository
                .findAllByUserIdAndDateRangeOrderByDateAsc(userId, startDate, endDate);
        List<BudgetDto> budgets = budgetService.listBudgets(userId, month, year);

        BigDecimal totalIncome = ZERO;
        BigDecimal totalExpense = ZERO;
        for (Transaction tx : transactions) {
            BigDecimal amount = tx.getAmount() != null ? tx.getAmount() : ZERO;
            if (tx.getType() == TransactionType.INCOME) {
                totalIncome = totalIncome.add(amount);
            } else if (tx.getType() == TransactionType.EXPENSE) {
                totalExpense = totalExpense.add(amount);
            }
        }
        totalIncome = totalIncome.setScale(2, RoundingMode.HALF_UP);
        totalExpense = totalExpense.setScale(2, RoundingMode.HALF_UP);
        BigDecimal balance = totalIncome.subtract(totalExpense).setScale(2, RoundingMode.HALF_UP);

        if (format == ReportFormat.CSV) {
            String fileName = String.format("finpulse-report-%04d-%02d.csv", year, month);
            byte[] csvBytes = buildCsvBytes(period, totalIncome, totalExpense, balance, budgets, transactions);
            return new GeneratedReport(fileName, CSV_CONTENT_TYPE, csvBytes);
        }

        String fileName = String.format("finpulse-report-%04d-%02d.pdf", year, month);
        byte[] pdfBytes = buildPdfBytes(period, totalIncome, totalExpense, balance, budgets, transactions);
        return new GeneratedReport(fileName, PDF_CONTENT_TYPE, pdfBytes);
    }

    byte[] buildCsvBytes(
            YearMonth period,
            BigDecimal totalIncome,
            BigDecimal totalExpense,
            BigDecimal balance,
            List<BudgetDto> budgets,
            List<Transaction> transactions
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(csvRow("FinPulse - Laporan Keuangan Bulanan", period.format(PERIOD_FORMATTER))).append("\r\n");
        sb.append(csvRow("Total Pemasukan", totalIncome.toPlainString())).append("\r\n");
        sb.append(csvRow("Total Pengeluaran", totalExpense.toPlainString())).append("\r\n");
        sb.append(csvRow("Sisa Saldo", balance.toPlainString())).append("\r\n");
        sb.append(csvRow("Jumlah Transaksi", String.valueOf(transactions.size()))).append("\r\n");
        sb.append("\r\n");

        if (!budgets.isEmpty()) {
            sb.append(csvRow("PROGRES ANGGARAN")).append("\r\n");
            sb.append(csvRow("Kategori", "Batas Anggaran", "Terpakai", "Sisa", "Utilisasi (%)", "Status")).append("\r\n");
            for (BudgetDto budget : budgets) {
                sb.append(csvRow(
                        budget.categoryName(),
                        budget.amountLimit().toPlainString(),
                        budget.spentAmount().toPlainString(),
                        budget.remainingAmount().toPlainString(),
                        budget.utilizationPercentage().toPlainString(),
                        budget.isOverBudget() ? "OVER_BUDGET" : "AMAN"
                )).append("\r\n");
            }
            sb.append("\r\n");
        }

        sb.append(csvRow("RINCIAN TRANSAKSI")).append("\r\n");
        sb.append(csvRow("Tanggal", "Kategori", "Tipe", "Nominal", "Deskripsi")).append("\r\n");
        for (Transaction tx : transactions) {
            sb.append(csvRow(
                    tx.getTransactionDate() != null ? tx.getTransactionDate().format(DATE_FORMATTER) : "",
                    tx.getCategory() != null ? tx.getCategory().getName() : "",
                    tx.getType() != null ? tx.getType().name() : "",
                    tx.getAmount() != null ? tx.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00",
                    tx.getDescription() != null ? tx.getDescription() : ""
            )).append("\r\n");
        }

        byte[] contentBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + contentBytes.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(contentBytes, 0, result, UTF8_BOM.length, contentBytes.length);
        return result;
    }

    String csvRow(String... cells) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(escapeCsvCell(cells[i]));
        }
        return row.toString();
    }

    String escapeCsvCell(String rawValue) {
        String value = rawValue == null ? "" : rawValue;
        String safe = sanitizeCsvFormula(value);
        boolean needsQuotes = safe.contains(",")
                || safe.contains("\"")
                || safe.contains("\n")
                || safe.contains("\r");
        if (!needsQuotes) {
            return safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    String sanitizeCsvFormula(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        if (isFormulaTrigger(first) || first == '\t' || first == '\r' || first == '\n') {
            return "'" + value;
        }
        String strippedLeading = value.stripLeading();
        if (!strippedLeading.isEmpty() && isFormulaTrigger(strippedLeading.charAt(0))) {
            return "'" + value;
        }
        return value;
    }

    private boolean isFormulaTrigger(char ch) {
        return ch == '=' || ch == '+' || ch == '-' || ch == '@';
    }

    byte[] buildPdfBytes(
            YearMonth period,
            BigDecimal totalIncome,
            BigDecimal totalExpense,
            BigDecimal balance,
            List<BudgetDto> budgets,
            List<Transaction> transactions
    ) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 40, 40);
        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(15, 23, 42));
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 11, new Color(71, 85, 105));
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(15, 23, 42));
            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(30, 41, 59));
            Font boldBodyFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(15, 23, 42));

            Paragraph title = new Paragraph("FinPulse - Laporan Keuangan Bulanan", titleFont);
            title.setSpacingAfter(4f);
            document.add(title);

            Paragraph subtitle = new Paragraph("Periode: " + period.format(PERIOD_FORMATTER), subtitleFont);
            subtitle.setSpacingAfter(14f);
            document.add(subtitle);

            // KPI Summary Table
            Paragraph kpiHeader = new Paragraph("Ringkasan KPI", sectionFont);
            kpiHeader.setSpacingAfter(6f);
            document.add(kpiHeader);

            PdfPTable kpiTable = new PdfPTable(4);
            kpiTable.setWidthPercentage(100);
            kpiTable.setSpacingAfter(14f);
            addHeaderCell(kpiTable, "Total Pemasukan", tableHeaderFont);
            addHeaderCell(kpiTable, "Total Pengeluaran", tableHeaderFont);
            addHeaderCell(kpiTable, "Sisa Saldo", tableHeaderFont);
            addHeaderCell(kpiTable, "Jumlah Transaksi", tableHeaderFont);

            addBodyCell(kpiTable, formatIdr(totalIncome), boldBodyFont, Element.ALIGN_RIGHT);
            addBodyCell(kpiTable, formatIdr(totalExpense), boldBodyFont, Element.ALIGN_RIGHT);
            addBodyCell(kpiTable, formatIdr(balance), boldBodyFont, Element.ALIGN_RIGHT);
            addBodyCell(kpiTable, String.valueOf(transactions.size()), boldBodyFont, Element.ALIGN_RIGHT);
            document.add(kpiTable);

            // Budget Progress Table
            Paragraph budgetHeader = new Paragraph("Progres Anggaran", sectionFont);
            budgetHeader.setSpacingAfter(6f);
            document.add(budgetHeader);

            if (budgets.isEmpty()) {
                Paragraph emptyBudgets = new Paragraph("Tidak ada anggaran terdaftar untuk periode ini.", bodyFont);
                emptyBudgets.setSpacingAfter(14f);
                document.add(emptyBudgets);
            } else {
                PdfPTable budgetTable = new PdfPTable(new float[]{2.5f, 1.8f, 1.8f, 1.3f, 1.4f});
                budgetTable.setWidthPercentage(100);
                budgetTable.setSpacingAfter(14f);
                addHeaderCell(budgetTable, "Kategori", tableHeaderFont);
                addHeaderCell(budgetTable, "Batas", tableHeaderFont);
                addHeaderCell(budgetTable, "Terpakai", tableHeaderFont);
                addHeaderCell(budgetTable, "Utilisasi", tableHeaderFont);
                addHeaderCell(budgetTable, "Status", tableHeaderFont);

                for (BudgetDto budget : budgets) {
                    addBodyCell(budgetTable, budget.categoryName(), bodyFont, Element.ALIGN_LEFT);
                    addBodyCell(budgetTable, formatIdr(budget.amountLimit()), bodyFont, Element.ALIGN_RIGHT);
                    addBodyCell(budgetTable, formatIdr(budget.spentAmount()), bodyFont, Element.ALIGN_RIGHT);
                    addBodyCell(budgetTable, budget.utilizationPercentage().toPlainString() + "%", bodyFont, Element.ALIGN_RIGHT);
                    addBodyCell(budgetTable, budget.isOverBudget() ? "Over Budget" : "Aman", bodyFont, Element.ALIGN_CENTER);
                }
                document.add(budgetTable);
            }

            // Transactions Table
            Paragraph txHeader = new Paragraph("Rincian Transaksi", sectionFont);
            txHeader.setSpacingAfter(6f);
            document.add(txHeader);

            if (transactions.isEmpty()) {
                document.add(new Paragraph("Tidak ada transaksi pada periode ini.", bodyFont));
            } else {
                PdfPTable txTable = new PdfPTable(new float[]{1.4f, 2.0f, 1.2f, 1.8f, 3.0f});
                txTable.setWidthPercentage(100);
                addHeaderCell(txTable, "Tanggal", tableHeaderFont);
                addHeaderCell(txTable, "Kategori", tableHeaderFont);
                addHeaderCell(txTable, "Tipe", tableHeaderFont);
                addHeaderCell(txTable, "Nominal", tableHeaderFont);
                addHeaderCell(txTable, "Deskripsi", tableHeaderFont);

                for (Transaction tx : transactions) {
                    addBodyCell(
                            txTable,
                            tx.getTransactionDate() != null ? tx.getTransactionDate().format(DATE_FORMATTER) : "-",
                            bodyFont,
                            Element.ALIGN_LEFT
                    );
                    addBodyCell(
                            txTable,
                            tx.getCategory() != null ? tx.getCategory().getName() : "-",
                            bodyFont,
                            Element.ALIGN_LEFT
                    );
                    addBodyCell(
                            txTable,
                            tx.getType() != null ? tx.getType().name() : "-",
                            bodyFont,
                            Element.ALIGN_CENTER
                    );
                    addBodyCell(
                            txTable,
                            formatIdr(tx.getAmount() != null ? tx.getAmount() : ZERO),
                            bodyFont,
                            Element.ALIGN_RIGHT
                    );
                    addBodyCell(
                            txTable,
                            tx.getDescription() != null ? tx.getDescription() : "-",
                            bodyFont,
                            Element.ALIGN_LEFT
                    );
                }
                document.add(txTable);
            }
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }

        return outputStream.toByteArray();
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(16, 185, 129));
        cell.setPadding(6f);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setPadding(5f);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private String formatIdr(BigDecimal amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "Rp " + nf.format(amount != null ? amount : ZERO);
    }
}
