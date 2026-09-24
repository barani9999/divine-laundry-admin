package com.divinelaundry.web;

import com.divinelaundry.service.ReportService;
import com.divinelaundry.service.PdfReportService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@Controller
public class ReportsWebController {
    private static final Map<String, ReportDefinition> REPORTS = Map.ofEntries(
            Map.entry("sales-overview", new ReportDefinition("sales-overview", "Sales Overview", "A complete view of billed sales, collections and outstanding balances.", "overview")),
            Map.entry("daily-sales", new ReportDefinition("daily-sales", "Daily Sales Report", "Compare each business date in the selected period.", "daily")),
            Map.entry("weekly-sales", new ReportDefinition("weekly-sales", "Weekly Sales Report", "Track sales and collection movement by week.", "weekly")),
            Map.entry("monthly-sales", new ReportDefinition("monthly-sales", "Monthly Sales Report", "Review month-by-month business performance.", "monthly")),
            Map.entry("service-sales", new ReportDefinition("service-sales", "Service-wise Sales", "See which services produced sales and volume.", "services")),
            Map.entry("category-sales", new ReportDefinition("category-sales", "Category-wise Sales", "Compare the five operating service categories.", "categories")),
            Map.entry("top-services", new ReportDefinition("top-services", "Top Services", "Rank services by recorded sales volume.", "services")),
            Map.entry("payments", new ReportDefinition("payments", "Payment & Collection", "Review recorded payments and collection status.", "payments")),
            Map.entry("outstanding", new ReportDefinition("outstanding", "Outstanding Report", "Find open balances that still need collection.", "outstanding")),
            Map.entry("customers", new ReportDefinition("customers", "Customer Sales", "Compare customer order value and outstanding balances.", "customers")),
            Map.entry("order-status", new ReportDefinition("order-status", "Order Status Report", "Review sales and balances across current order statuses.", "statuses")),
            Map.entry("orders", new ReportDefinition("orders", "Order History", "Inspect every included order in the selected period.", "orders")));

    private final ReportService reports;
    private final PdfReportService pdfReports;
    private final ZoneId zone;

    public ReportsWebController(ReportService reports, PdfReportService pdfReports,
            @org.springframework.beans.factory.annotation.Value("${app.business-zone:Asia/Kolkata}") String zone) {
        this.reports = reports;
        this.pdfReports = pdfReports;
        this.zone = ZoneId.of(zone);
    }

    @GetMapping("/reports")
    String center(Model model) {
        model.addAttribute("reportCenter", true);
        model.addAttribute("reportCards", REPORTS.values());
        return "reports-center";
    }

    @GetMapping("/reports/{view}")
    String report(@PathVariable String view, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, Model model) {
        ReportDefinition definition = REPORTS.get(view);
        if (definition == null) throw new IllegalArgumentException("Report not found");
        LocalDate today = LocalDate.now(zone);
        LocalDate reportFrom = from == null ? today.withDayOfMonth(1) : from;
        LocalDate reportTo = to == null ? today : to;
        model.addAttribute("report", reports.between(reportFrom, reportTo));
        model.addAttribute("definition", definition);
        model.addAttribute("view", view);
        model.addAttribute("reportPath", "/reports/" + view);
        model.addAttribute("pdfPath", "/reports/" + view + "/pdf");
        model.addAttribute("reportCenter", false);
        return "report-detail";
    }

    @GetMapping(value = "/reports/{view}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> reportPdf(@PathVariable String view,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        ReportDefinition definition = REPORTS.get(view);
        if (definition == null) throw new IllegalArgumentException("Report not found");
        LocalDate today = LocalDate.now(zone);
        LocalDate reportFrom = from == null ? today.withDayOfMonth(1) : from;
        LocalDate reportTo = to == null ? today : to;
        byte[] content = pdfReports.render(definition.title(), definition.section(), reports.between(reportFrom, reportTo));
        String filename = "divine-laundry-" + view + "-" + reportFrom + "-to-" + reportTo + ".pdf";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }

    record ReportDefinition(String slug, String title, String description, String section) {
        public String getPath() { return "/reports/" + slug; }
    }
}