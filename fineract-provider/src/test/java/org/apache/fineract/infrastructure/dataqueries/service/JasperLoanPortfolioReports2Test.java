/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.dataqueries.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Test;

/**
 * Loan portfolio reports converted from their "(Pentaho)" stretchy twins: each template compiles, fills from map rows
 * that use the template's field names and exports to PDF and CSV, and its SQL is bound and portable.
 */
class JasperLoanPortfolioReports2Test {

    private static final List<String> TEMPLATES = List.of("funds-disbursed-between-dates-summary-pentaho",
            "funds-disbursed-between-dates-summary-by-office-pentaho", "loans-awaiting-disbursal-pentaho",
            "loans-awaiting-disbursal-summary-pentaho", "loans-awaiting-disbursal-summary-by-month-pentaho",
            "loans-pending-approval-pentaho", "obligation-met-loans-details-pentaho", "obligation-met-loans-summary-pentaho",
            "portfolio-at-risk-pentaho", "portfolio-at-risk-by-branch-pentaho", "rescheduled-loans-pentaho", "written-off-loans-pentaho");

    @Test
    void sqlIsBoundAndPortable() throws Exception {
        for (final String name : TEMPLATES) {
            final String jrxml;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("reports/" + name + ".jrxml")) {
                assertNotNull(in, name);
                jrxml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            final int start = jrxml.indexOf("<queryString>");
            final int end = jrxml.indexOf("</queryString>");
            assertTrue(start > 0 && end > start, name + " has a query");
            final String sql = jrxml.substring(start, end);
            final String lower = sql.toLowerCase(Locale.ROOT);
            assertFalse(sql.contains("${"), name + " uses ${");
            assertFalse(sql.contains("$P!{"), name + " uses $P!{");
            assertFalse(lower.contains("now()"), name + " uses NOW()");
            assertFalse(lower.contains("curdate"), name + " uses curdate()");
            assertFalse(lower.contains("current_date"), name + " uses CURRENT_DATE");
            assertFalse(lower.contains("ifnull"), name + " uses ifnull");
            assertFalse(lower.contains("datediff"), name + " uses datediff");
            assertFalse(sql.contains("\""), name + " uses a double-quoted literal or identifier");
            assertFalse(sql.contains("`"), name + " uses backticks");
            assertTrue(sql.contains("LIKE CONCAT($P{currentUserHierarchy}, '%')"), name + " keeps the hierarchy restriction");
            assertTrue(jrxml.contains("name=\"" + name + "\""), name + " report name equals the slug");
        }
    }

    @Test
    void fundsDisbursedSummaryFillsAndExports() throws Exception {
        final Map<String, Object> khr = new LinkedHashMap<>();
        khr.put("filter_fund", null);
        khr.put("fund_name", "មូលនិធិ A");
        khr.put("currency_code", "KHR");
        khr.put("currency", "៛");
        khr.put("disbursed_amount", new BigDecimal("4000000.0000"));
        final Map<String, Object> usd = new LinkedHashMap<>();
        usd.put("fund_name", "-");
        usd.put("currency_code", "USD");
        usd.put("currency", "$");
        usd.put("disbursed_amount", new BigDecimal("1000.5000"));

        final String csv = fillAndExport("funds-disbursed-between-dates-summary-pentaho", List.of(khr, usd),
                Map.of("fundId", -1L, "currencyId", "-1"));
        assertTrue(csv.contains("1,000.50"), csv);
        assertTrue(csv.contains("4,000,000.00"), csv);
    }

    @Test
    void fundsDisbursedSummaryByOfficeFillsAndExports() throws Exception {
        final Map<String, Object> head = officeRow(1L, "Head Office", "USD");
        head.put("fund_name", "Fund A");
        head.put("disbursed_amount", new BigDecimal("1000.50"));
        final Map<String, Object> branch = officeRow(2L, "..សាខា ភ្នំពេញ", "USD");
        branch.put("fund_name", "-");
        branch.put("disbursed_amount", new BigDecimal("250.00"));

        final String csv = fillAndExport("funds-disbursed-between-dates-summary-by-office-pentaho", List.of(head, branch),
                Map.of("officeId", 1L, "currencyId", "USD", "fundId", -1L));
        assertTrue(csv.contains("1,000.50"), csv);
        assertTrue(csv.contains("1,250.50"), csv);
    }

    @Test
    void loansAwaitingDisbursalFillsAndExports() throws Exception {
        final Map<String, Object> row = loanRow();
        row.put("fund_name", null);
        row.put("principal", new BigDecimal("1500.00"));
        row.put("term_frequency", 12);
        row.put("term_frequency_period", "Months");
        row.put("annual_interest_rate", new BigDecimal("18.000000"));
        row.put("approved_on", Date.valueOf("2026-10-10"));
        row.put("expected_disbursal_on", Date.valueOf("2026-10-25"));
        row.put("loan_purpose", "Agriculture");

        final String csv = fillAndExport("loans-awaiting-disbursal-pentaho", List.of(row), Map.of("officeId", 1L, "currencyId", "-1"));
        assertTrue(csv.contains("1,500.00"), csv);
        assertTrue(csv.contains("12 Months"), csv);
        // Expected disbursal 25 Oct, business date 20 Oct
        assertTrue(csv.contains(",5,"), csv);
    }

    @Test
    void loansAwaitingDisbursalSummaryFillsAndExports() throws Exception {
        final Map<String, Object> first = officeRow(1L, "Head Office", "USD");
        first.put("product_name", "Micro Loan");
        first.put("fund_name", null);
        first.put("principal", new BigDecimal("1500.00"));
        final Map<String, Object> second = officeRow(1L, "Head Office", "USD");
        second.put("product_name", "ឥណទានកសិកម្ម");
        second.put("fund_name", "Fund A");
        second.put("principal", new BigDecimal("500.00"));

        final String csv = fillAndExport("loans-awaiting-disbursal-summary-pentaho", List.of(first, second),
                Map.of("officeId", 1L, "currencyId", "USD", "loanProductId", 3L));
        assertTrue(csv.contains("1,500.00"), csv);
        assertTrue(csv.contains("2,000.00"), csv);
    }

    @Test
    void loansAwaitingDisbursalSummaryByMonthFillsAndExports() throws Exception {
        final Map<String, Object> row = officeRow(1L, "Head Office", "USD");
        row.put("product_name", "Micro Loan");
        row.put("disbursal_year", new BigDecimal("2026"));
        row.put("disbursal_month", new BigDecimal("11"));
        row.put("principal", new BigDecimal("1500.00"));

        final String csv = fillAndExport("loans-awaiting-disbursal-summary-by-month-pentaho", List.of(row), Map.of("officeId", 1L));
        assertTrue(csv.contains("November"), csv);
        assertTrue(csv.contains("2026"), csv);
        assertTrue(csv.contains("1,500.00"), csv);
    }

    @Test
    void loansPendingApprovalFillsAndExports() throws Exception {
        final Map<String, Object> row = loanRow();
        row.put("currency_code", "KHR");
        row.put("currency", "KHR");
        row.put("loan_amount", new BigDecimal("4000000.00"));
        row.put("term_frequency", 6);
        row.put("term_frequency_period", "Months");
        row.put("annual_interest_rate", new BigDecimal("24"));
        row.put("submitted_on", Date.valueOf("2026-10-10"));
        row.put("loan_purpose", null);

        final String csv = fillAndExport("loans-pending-approval-pentaho", List.of(row), Map.of("officeId", 1L, "loanOfficerId", -10L));
        assertTrue(csv.contains("4,000,000.00"), csv);
        // Submitted 10 Oct, business date 20 Oct
        assertTrue(csv.contains(",10,"), csv);
    }

    @Test
    void obligationMetLoansDetailsFillsAndExports() throws Exception {
        final Map<String, Object> row = loanRow();
        row.put("fund_name", "Fund A");
        row.put("loan_amount", new BigDecimal("1000.00"));
        row.put("total_repaid", new BigDecimal("1180.00"));
        row.put("annual_interest_rate", new BigDecimal("18"));
        row.put("disbursed_on", Date.valueOf("2025-10-01"));
        row.put("closed_on", Date.valueOf("2026-10-01"));
        row.put("principal_repaid", new BigDecimal("1000.00"));
        row.put("interest_repaid", new BigDecimal("170.00"));
        row.put("fees_repaid", new BigDecimal("10.00"));
        row.put("penalties_repaid", BigDecimal.ZERO);
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("officeId", 1L);
        parameters.put("currencyId", "USD");
        parameters.put("obligDateType", 1L);
        parameters.put("startDate", Date.valueOf("2026-10-01"));
        parameters.put("endDate", Date.valueOf("2026-10-31"));

        final String csv = fillAndExport("obligation-met-loans-details-pentaho", List.of(row), parameters);
        assertTrue(csv.contains("1,180.00"), csv);
        assertTrue(csv.contains("170.00"), csv);
    }

    @Test
    void obligationMetLoansSummaryFillsAndExports() throws Exception {
        final Map<String, Object> head = officeRow(1L, "Head Office", "USD");
        head.put("client_count", 2L);
        head.put("loan_count", 3L);
        head.put("loan_amount", new BigDecimal("3000.00"));
        head.put("principal_repaid", new BigDecimal("3000.00"));
        head.put("interest_repaid", new BigDecimal("450.25"));
        head.put("fees_repaid", BigDecimal.ZERO);
        head.put("penalties_repaid", BigDecimal.ZERO);
        head.put("interest_waived", BigDecimal.ZERO);
        head.put("fees_waived", BigDecimal.ZERO);
        head.put("penalties_waived", BigDecimal.ZERO);
        final Map<String, Object> branch = officeRow(2L, "..សាខា ភ្នំពេញ", "USD");
        branch.put("client_count", 1L);
        branch.put("loan_count", 1L);
        branch.put("loan_amount", new BigDecimal("500.00"));
        branch.put("principal_repaid", new BigDecimal("500.00"));
        branch.put("interest_repaid", new BigDecimal("50.00"));

        final String csv = fillAndExport("obligation-met-loans-summary-pentaho", List.of(head, branch),
                Map.of("officeId", 1L, "currencyId", "USD"));
        assertTrue(csv.contains("450.25"), csv);
        assertTrue(csv.contains("3,500.00"), csv);
    }

    @Test
    void portfolioAtRiskFillsAndExports() throws Exception {
        final Map<String, Object> row = parRow(1L, "Head Office", "8000.00", "400.00");
        row.put("interest_outstanding", new BigDecimal("900.00"));
        row.put("interest_overdue", new BigDecimal("50.00"));

        final String csv = fillAndExport("portfolio-at-risk-pentaho", List.of(row), Map.of("officeId", 1L, "parType", 1L));
        assertTrue(csv.contains("8,000.00"), csv);
        assertTrue(csv.contains("5.00"), csv);
    }

    @Test
    void portfolioAtRiskByBranchFillsAndExports() throws Exception {
        final Map<String, Object> head = parRow(1L, "Head Office", "1000.00", "100.00");
        final Map<String, Object> branch = parRow(2L, "..សាខា ភ្នំពេញ", "3000.00", "0.00");

        final String csv = fillAndExport("portfolio-at-risk-by-branch-pentaho", List.of(head, branch),
                Map.of("officeId", 1L, "currencyId", "USD", "parType", 1L));
        assertTrue(csv.contains("4,000.00"), csv);
        // Total PAR is recomputed from the summed amounts: 100 / 4000
        assertTrue(csv.contains("2.50"), csv);
    }

    @Test
    void rescheduledLoansFillsAndExports() throws Exception {
        final String csv = fillAndExport("rescheduled-loans-pentaho", List.of(transactionRow("ពន្យារពេល")), Map.of("officeId", 1L));
        assertTrue(csv.contains("Rescheduled Principal"), csv);
        assertTrue(csv.contains("750.00"), csv);
    }

    @Test
    void writtenOffLoansFillsAndExports() throws Exception {
        final List<Map<String, ?>> rows = List.of(transactionRow("Client left the area"), transactionRow(null));

        final String csv = fillAndExport("written-off-loans-pentaho", rows,
                Map.of("officeId", 1L, "currencyId", "USD", "loanProductId", 2L));
        assertTrue(csv.contains("Written-Off Principal"), csv);
        assertTrue(csv.contains("1,500.00"), csv);
    }

    private static Map<String, Object> officeRow(final Long officeId, final String officeName, final String currencyCode) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("selected_office_name", "Head Office");
        row.put("office_id", officeId);
        row.put("office_name", officeName);
        row.put("currency_code", currencyCode);
        row.put("currency", currencyCode);
        return row;
    }

    private static Map<String, Object> loanRow() {
        final Map<String, Object> row = officeRow(1L, "Head Office", "USD");
        row.put("client_account_no", "000000001");
        row.put("client_name", "សុខ សៅ");
        row.put("loan_account_no", "000000011");
        row.put("product_name", "Micro Loan");
        row.put("loan_officer_name", "Dara");
        return row;
    }

    private static Map<String, Object> parRow(final Long officeId, final String officeName, final String outstanding,
            final String overdue) {
        final BigDecimal outstandingAmount = new BigDecimal(outstanding);
        final BigDecimal overdueAmount = new BigDecimal(overdue);
        final Map<String, Object> row = officeRow(officeId, officeName, "USD");
        row.put("par_type_name", "Principal Only");
        row.put("principal_outstanding", outstandingAmount);
        row.put("principal_overdue", overdueAmount);
        row.put("interest_outstanding", BigDecimal.ZERO);
        row.put("interest_overdue", BigDecimal.ZERO);
        row.put("fees_outstanding", BigDecimal.ZERO);
        row.put("fees_overdue", BigDecimal.ZERO);
        row.put("penalties_outstanding", BigDecimal.ZERO);
        row.put("penalties_overdue", BigDecimal.ZERO);
        row.put("par_numerator", overdueAmount);
        row.put("par_denominator", outstandingAmount);
        row.put("par_percent", overdueAmount.multiply(BigDecimal.valueOf(100)).divide(outstandingAmount, 2, RoundingMode.HALF_UP));
        return row;
    }

    private static Map<String, Object> transactionRow(final String reason) {
        final Map<String, Object> row = loanRow();
        row.put("loan_officer_name", "-");
        row.put("disbursed_on", Date.valueOf("2026-01-15"));
        row.put("transaction_date", Date.valueOf("2026-10-05"));
        row.put("loan_amount", new BigDecimal("1000.00"));
        row.put("principal_portion", new BigDecimal("750.00"));
        row.put("interest_portion", new BigDecimal("30.00"));
        row.put("fees_portion", BigDecimal.ZERO);
        row.put("penalties_portion", BigDecimal.ZERO);
        row.put("reason", reason);
        return row;
    }

    private String fillAndExport(final String name, final List<Map<String, ?>> rows, final Map<String, Object> reportParameters)
            throws Exception {
        final JasperReport template;
        try (InputStream jrxml = getClass().getClassLoader().getResourceAsStream("reports/" + name + ".jrxml")) {
            assertNotNull(jrxml, name);
            template = JasperCompileManager.compileReport(jrxml);
        }
        assertEquals(name, template.getName());
        final Map<String, Object> parameters = new HashMap<>(reportParameters);
        parameters.put(JasperReportingProcessService.CURRENT_DATE, Date.valueOf("2026-10-20"));
        parameters.put(JasperReportingProcessService.CURRENT_DATE_TIME, Timestamp.valueOf("2026-10-20 10:00:00"));
        parameters.put(JasperReportingProcessService.CURRENT_USER_HIERARCHY, ".");
        parameters.put(JasperReportingProcessService.CURRENT_USER_NAME, "mifos");
        parameters.put(JRParameter.REPORT_LOCALE, Locale.ENGLISH);
        parameters.put(JRParameter.REPORT_TIME_ZONE, TimeZone.getTimeZone("Asia/Bangkok"));

        final JasperPrint print = JasperFillManager.fillReport(template, parameters, new JRMapCollectionDataSource(rows));
        assertFalse(print.getPages().isEmpty(), name);
        final byte[] pdf = JasperExportManager.exportReportToPdf(print);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII), name);
        return new String(JasperReportingProcessService.exportCsv(print), StandardCharsets.UTF_8);
    }
}
