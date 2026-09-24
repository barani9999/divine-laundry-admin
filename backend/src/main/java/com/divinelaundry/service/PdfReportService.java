package com.divinelaundry.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PdfReportService {
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private final String businessName;
    private final ZoneId zone;

    public PdfReportService(@Value("${app.business.name:Divine Laundry}") String businessName,
            @Value("${app.business-zone:Asia/Kolkata}") String zone) {
        this.businessName = businessName;
        this.zone = ZoneId.of(zone);
    }

    public byte[] render(String title, String section, ReportService.ReportData report) {
        PDRectangle pageSize = wide(section)
            ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
            : PDRectangle.A4;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PageState state = newPage(document, pageSize, title, report);
            List<String[]> rows = rowsFor(section, report);
            String[] headers = headersFor(section);
            float[] widths = columnWidths(section, pageSize.getWidth());
            state = table(document, pageSize, state, headers, rows, widths);
            if (rows.isEmpty()) text(state.content(), "No records found for the selected period.", 42, state.y() - 12, REGULAR, 10);
            state.content().close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Could not create the report PDF", error);
        }
    }

    private PageState newPage(PDDocument document, PDRectangle pageSize, String title,
            ReportService.ReportData report) throws IOException {
        PDPage page = new PDPage(pageSize);
        document.addPage(page);
        PDPageContentStream content = new PDPageContentStream(document, page);
        float top = pageSize.getHeight() - 38;
        text(content, businessName, 42, top, BOLD, 18);
        text(content, title, 42, top - 25, BOLD, 14);
        text(content, "Reporting period: " + DATE.format(report.from()) + " - " + DATE.format(report.to()), 42, top - 43, REGULAR, 9);
        text(content, "Generated: " + DATE_TIME.format(Instant.now().atZone(zone)), pageSize.getWidth() - 190, top, REGULAR, 8);
        drawSummary(content, title, report, top - 75);
        return new PageState(content, top - 132, pageSize.getHeight());
    }

    private void drawSummary(PDPageContentStream content, String title, ReportService.ReportData report, float y) throws IOException {
        List<String> values = new ArrayList<>();
        if ("overview".equals(titleSection(title))) {
            values.add("Gross sales " + money(report.grossSales()));
            values.add("Collected " + money(report.collected()));
            values.add("Outstanding " + money(report.outstanding()));
            values.add("Orders " + report.orderCount());
            values.add("Average bill " + money(report.averageBill()));
        } else if ("daily".equals(titleSection(title)) || "weekly".equals(titleSection(title)) || "monthly".equals(titleSection(title))) {
            values.add("Sales " + money(report.grossSales()));
            values.add("Orders " + report.orderCount());
            values.add("Average " + money(report.averagePeriodSales(periods(titleSection(title), report))));
            values.add("Best " + report.bestPeriod(periods(titleSection(title), report)));
        } else if ("services".equals(titleSection(title))) {
            values.add("Service sales " + money(report.grossSales()));
            values.add("Services " + report.services().size());
            values.add("Units " + report.totalUnits().toPlainString());
            values.add("Top " + report.topService());
        } else if ("categories".equals(titleSection(title))) {
            values.add("Category sales " + money(report.grossSales()));
            values.add("Categories " + report.categoriesWithSales());
            values.add("Orders " + report.orderCount());
            values.add("Top " + report.topCategory());
        } else if ("payments".equals(titleSection(title))) {
            values.add("Collected " + money(report.collected()));
            values.add("Payments " + report.paymentCount());
            values.add("Average payment " + money(report.averagePayment()));
            values.add("Outstanding " + money(report.outstanding()));
        } else if ("outstanding".equals(titleSection(title))) {
            values.add("Outstanding " + money(report.outstanding()));
            values.add("Orders with balance " + report.ordersWithBalance());
            values.add("Customers with balance " + report.customersWithBalance());
            values.add("Largest " + money(report.largestOutstanding()));
        } else if ("customers".equals(titleSection(title))) {
            values.add("Customers " + report.customers().size());
            values.add("Orders " + report.orderCount());
            values.add("Gross sales " + money(report.grossSales()));
            values.add("Outstanding " + money(report.outstanding()));
        }
        float x = 42;
        for (String value : values) {
            text(content, value, x, y, BOLD, 9);
            x += 125;
        }
    }

    private PageState table(PDDocument document, PDRectangle pageSize, PageState state,
            String[] headers, List<String[]> rows, float[] widths) throws IOException {
        float y = state.y();
        drawHeader(state.content(), headers, widths, y);
        y -= 18;
        for (String[] row : rows) {
            if (y < 42) {
                state.content().close();
                state = newPage(document, pageSize, "Report continued", new ReportService.ReportData(
                        java.time.LocalDate.MIN, java.time.LocalDate.MIN, List.of(), java.util.Map.of(), java.util.Map.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
                y = state.y();
                drawHeader(state.content(), headers, widths, y);
                y -= 18;
            }
            for (int index = 0; index < row.length; index++) {
                boolean numeric = index > 0 && !headers[index].equalsIgnoreCase("Service")
                        && !headers[index].equalsIgnoreCase("Customer") && !headers[index].equalsIgnoreCase("Category")
                        && !headers[index].equalsIgnoreCase("Period") && !headers[index].equalsIgnoreCase("Date")
                        && !headers[index].equalsIgnoreCase("Payment date");
                float x = columnX(widths, index);
                float valueWidth = widths[index] - 8;
                text(state.content(), fit(row[index], valueWidth, 8), numeric ? x + valueWidth - textWidth(fit(row[index], valueWidth, 8), REGULAR, 8) : x, y, REGULAR, 8);
            }
            y -= 15;
        }
        return new PageState(state.content(), y, pageSize.getHeight());
    }

    private void drawHeader(PDPageContentStream content, String[] headers, float[] widths, float y) throws IOException {
        for (int index = 0; index < headers.length; index++) {
            float x = columnX(widths, index);
            boolean right = index > 0;
            String value = headers[index];
            text(content, value, right ? x + widths[index] - textWidth(value, BOLD, 8) : x, y, BOLD, 8);
        }
    }

    private static float columnX(float[] widths, int index) {
        float x = 42;
        for (int i = 0; i < index; i++) x += widths[i];
        return x;
    }

    private static String[] headersFor(String section) {
        return switch (section) {
            case "daily", "weekly", "monthly" -> new String[] {"PERIOD", "ORDERS", "GROSS SALES", "COLLECTED", "OUTSTANDING", "AVERAGE BILL"};
            case "services" -> new String[] {"SERVICE", "CODE", "CATEGORY", "SALES", "ORDERS", "QUANTITY", "PIECES"};
            case "categories" -> new String[] {"CATEGORY", "SALES", "ORDERS", "QUANTITY", "SHARE"};
            case "payments" -> new String[] {"PAYMENT DATE", "ORDER", "INVOICE", "CUSTOMER", "ORDER TOTAL", "PAYMENT", "STATUS", "REFERENCE"};
            case "outstanding" -> new String[] {"CUSTOMER", "PHONE", "ORDER", "INVOICE", "ORDER TOTAL", "PAID", "OUTSTANDING", "STATUS", "ORDER DATE"};
            case "customers" -> new String[] {"CUSTOMER", "PHONE", "ORDERS", "GROSS SALES", "PAID", "OUTSTANDING", "AVERAGE BILL"};
            case "statuses" -> new String[] {"STATUS", "ORDERS", "GROSS SALES", "PAID", "OUTSTANDING"};
            case "orders" -> new String[] {"ORDER", "INVOICE", "CUSTOMER", "ORDER DATE", "READY BY", "STATUS", "TOTAL", "PAID", "OUTSTANDING"};
            default -> new String[] {"SERVICE", "CODE", "CATEGORY", "SALES", "ORDERS", "QUANTITY", "PIECES"};
        };
    }

    private static float[] columnWidths(String section, float pageWidth) {
        float usable = pageWidth - 84;
        int count = headersFor(section).length;
        float each = usable / count;
        float[] widths = new float[count];
        java.util.Arrays.fill(widths, each);
        return widths;
    }

    private static List<String[]> rowsFor(String section, ReportService.ReportData report) {
        return switch (section) {
            case "daily" -> periodRows(report.daily());
            case "weekly" -> periodRows(report.weekly());
            case "monthly" -> periodRows(report.monthly());
            case "services" -> report.services().stream().map(row -> new String[] {row.name(), row.code(), row.category(), money(row.sales()), String.valueOf(row.orders()), row.quantity().toPlainString(), String.valueOf(row.pieces())}).toList();
            case "categories" -> report.categoriesSales().stream().map(row -> new String[] {row.category(), money(row.sales()), String.valueOf(row.orders()), row.quantity().toPlainString(), row.share() + "%"}).toList();
            case "payments" -> report.payments().stream().map(row -> new String[] {date(row.paidAt()), row.orderNumber(), safe(row.invoiceNumber(), "-"), row.customer(), money(row.orderTotal()), money(row.amount()), row.status().toString(), safe(row.reference(), "-")}).toList();
            case "outstanding" -> report.outstandingRows().stream().map(row -> new String[] {row.order().getCustomer().getName(), row.order().getCustomer().getPhone(), row.order().getOrderNumber(), safe(row.order().getInvoiceNumber(), "-"), money(row.order().getTotal()), money(row.paid()), money(row.outstanding()), row.order().getWorkStatus().toString(), date(row.order().getPlacedAt())}).toList();
            case "customers" -> report.customers().stream().map(row -> new String[] {row.name(), row.phone(), String.valueOf(row.orders()), money(row.sales()), money(row.paid()), money(row.outstanding()), money(row.averageBill())}).toList();
            case "statuses" -> report.statuses().stream().map(row -> new String[] {row.status().toString(), String.valueOf(row.orders()), money(row.sales()), money(row.paid()), money(row.outstanding())}).toList();
            case "orders" -> report.orderRows().stream().map(row -> new String[] {row.order().getOrderNumber(), safe(row.order().getInvoiceNumber(), "-"), row.order().getCustomer().getName(), date(row.order().getPlacedAt()), date(row.order().getDeliveryAt()), row.order().getWorkStatus().toString(), money(row.order().getTotal()), money(row.paid()), money(row.outstanding())}).toList();
            default -> report.services().stream().map(row -> new String[] {row.name(), row.code(), row.category(), money(row.sales()), String.valueOf(row.orders()), row.quantity().toPlainString(), String.valueOf(row.pieces())}).toList();
        };
    }

    private static List<String[]> periodRows(List<ReportService.PeriodRow> rows) {
        return rows.stream().map(row -> new String[] {row.label(), String.valueOf(row.orders()), money(row.sales()), money(row.collected()), money(row.outstanding()), money(row.averageBill())}).toList();
    }

    private static List<ReportService.PeriodRow> periods(String section, ReportService.ReportData report) {
        return switch (section) { case "daily" -> report.daily(); case "weekly" -> report.weekly(); default -> report.monthly(); };
    }

    private String titleSection(String title) {
        if (title.contains("Daily")) return "daily";
        if (title.contains("Weekly")) return "weekly";
        if (title.contains("Monthly")) return "monthly";
        if (title.contains("Service") || title.contains("Top")) return "services";
        if (title.contains("Category")) return "categories";
        if (title.contains("Payment")) return "payments";
        if (title.contains("Outstanding")) return "outstanding";
        if (title.contains("Customer")) return "customers";
        if (title.contains("Status")) return "statuses";
        return "overview";
    }

    private static String date(Instant value) { return value == null ? "-" : DATE.format(value.atZone(ZoneId.of("Asia/Kolkata"))); }
    private static String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.replaceAll("[\\r\\n]", " "); }
    private static String money(BigDecimal value) { return "INR " + (value == null ? "0.00" : value.setScale(2).toPlainString()); }
    private static boolean wide(String section) { return List.of("payments", "outstanding", "orders").contains(section); }
    private static String fit(String value, float width, float size) { String text = safe(value, ""); while (text.length() > 3 && textWidth(text, REGULAR, size) > width) text = text.substring(0, text.length() - 2) + "…"; return text; }
    private static float textWidth(String value, PDFont font, float size) { try { return font.getStringWidth(value) / 1000 * size; } catch (IOException error) { return value.length() * size / 2; } }

    private record PageState(PDPageContentStream content, float y, float pageHeight) {}

    private static void text(PDPageContentStream content, String value, float x, float y, PDFont font, float size) throws IOException {
        content.beginText(); content.setFont(font, size); content.newLineAtOffset(x, y); content.showText(safe(value, "").replace("…", "...")); content.endText();
    }
}
