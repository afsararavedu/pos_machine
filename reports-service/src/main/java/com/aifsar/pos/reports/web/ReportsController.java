package com.aifsar.pos.reports.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.aifsar.pos.common.security.CurrentPrincipal;
import com.aifsar.pos.reports.domain.TransactionView;
import com.aifsar.pos.reports.service.ReportService;
import com.aifsar.pos.reports.service.ReportService.Summary;
import com.aifsar.pos.reports.service.ReportService.Timeline;
import com.aifsar.pos.reports.service.ReportService.TransactionFilter;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN and AUDITOR see every merchant; MERCHANT is automatically scoped to its own merchantId.
 * Dates are business dates in the configured zone (Asia/Kolkata by default).
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(),
                    p.getTotalPages());
        }
    }

    private final ReportService reports;
    private final ZoneId zone;

    public ReportsController(ReportService reports,
                             @Value("${reports.business-zone:Asia/Kolkata}")
                             String zone) {
        this.reports = reports;
        this.zone = ZoneId.of(zone);
    }

    @GetMapping("/transactions")
    public PageResponse<TransactionView> transactions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) String terminalId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return PageResponse.of(reports.search(filter(status, merchantId, terminalId, from, to), page, size,
                CurrentPrincipal.from(jwt)));
    }

    /** Full lifecycle of one payment: current state + every event in order (audit trail). */
    @GetMapping("/transactions/{paymentId}")
    public Timeline timeline(@PathVariable UUID paymentId, @AuthenticationPrincipal Jwt jwt) {
        return reports.timeline(paymentId, CurrentPrincipal.from(jwt));
    }

    /** Daily (or ranged) totals: approval rate, approved amount, declines, reversals, duplicates blocked. */
    @GetMapping("/summary")
    public Summary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String merchantId,
            @AuthenticationPrincipal Jwt jwt) {
        LocalDate start = from != null ? from : LocalDate.now(zone);
        LocalDate end = to != null ? to : start;
        return reports.summary(startOf(start), startOf(end.plusDays(1)), merchantId, CurrentPrincipal.from(jwt));
    }

    /** Payments still INITIATED / TIMEOUT_PENDING_REVERSAL after N minutes: candidates for reconciliation. */
    @GetMapping("/attention")
    public List<TransactionView> attention(@RequestParam(defaultValue = "5") long olderThanMinutes,
                                           @AuthenticationPrincipal Jwt jwt) {
        return reports.needingAttention(Duration.ofMinutes(olderThanMinutes), CurrentPrincipal.from(jwt));
    }

    /** CSV export (max 5,000 rows per call; page through for more). */
    @GetMapping(value = "/transactions/export.csv", produces = "text/csv")
    public void export(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response) throws IOException {
        Page<TransactionView> rows = reports.search(filter(status, merchantId, null, from, to), page, 5000,
                CurrentPrincipal.from(jwt));
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=transactions.csv");
        PrintWriter out = response.getWriter();
        out.println("payment_id,created_at,merchant_id,terminal_id,stan,rrn,masked_pan,entry_mode,amount_minor,"
                + "currency,status,response_code,auth_code,duplicates_blocked");
        for (TransactionView v : rows.getContent()) {
            out.println(String.join(",", v.getPaymentId().toString(), String.valueOf(v.getCreatedAt()),
                    v.getMerchantId(), v.getTerminalId(), v.getStan(), v.getRrn(), v.getMaskedPan(),
                    v.getEntryMode(), Long.toString(v.getAmountMinor()), v.getCurrency(), v.getStatus(),
                    nz(v.getResponseCode()), nz(v.getAuthCode()), Integer.toString(v.getDuplicateAttempts())));
        }
        out.flush();
    }

    private TransactionFilter filter(String status, String merchantId, String terminalId, LocalDate from,
                                     LocalDate to) {
        return new TransactionFilter(status, merchantId, terminalId,
                from == null ? null : startOf(from), to == null ? null : startOf(to.plusDays(1)));
    }

    private Instant startOf(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
