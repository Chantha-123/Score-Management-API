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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Loan portfolio reports, batch 1: the "(Pentaho)" twins converted to Jasper (see
 * db/changelog/tenant/parts/0039_jasper_loan_portfolio_reports_1.xml). The SQL is not executed here; each template is
 * filled from map rows keyed by its field names and exported.
 */
class JasperLoanPortfolioReports1Test {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 20);
    private static final Pattern FORBIDDEN_SQL = Pattern
            .compile("(?i)\\bnow\\s*\\(|curdate|current_date|sysdate|\\bifnull\\b|\\bdatediff\\b");

    @ParameterizedTest
    @ValueSource(strings = { "active-loans-details-pentaho", "active-loans-summary-pentaho", "active-loans-by-disbursal-period-pentaho",
            "active-loans-in-last-installment-pentaho", "active-loans-in-last-installment-summary-pentaho",
            "active-loans-passed-final-maturity-pentaho", "active-loans-passed-final-maturity-summary-pentaho", "aging-detail-pentaho",
            "aging-summary-arrears-in-weeks-pentaho", "aging-summary-arrears-in-months-pentaho", "client-listing-pentaho",
            "client-loans-listing-pentaho", "expected-payments-by-date-basic-pentaho" })
    void templateCompilesFillsAndExports(final String slug) throws Exception {
        final JasperReport template = compile(slug);
        assertEquals(slug, template.getName(), "jasperReport name must equal the file slug");

        final String sql = template.getQuery().getText();
        assertFalse(sql.contains("${"), slug + " still uses stretchy ${...} substitution");
        assertFalse(sql.contains("$P!{"), slug + " uses raw $P!{...} substitution");
        assertFalse(FORBIDDEN_SQL.matcher(sql).find(), slug + " uses a non-portable function or the database clock");
        assertFalse(sql.contains("\""), slug + " uses double quotes (MySQL string literals or quoted aliases)");
        assertFalse(sql.contains("`"), slug + " uses backticks");
        assertTrue(sql.contains("LIKE CONCAT($P{currentUserHierarchy}, '%')"), slug + " must keep the office hierarchy restriction");

        final JasperPrint print = fill(template, List.of(row(template, 1), row(template, 2)), Map.of());

        assertTrue(print.getPages().size() >= 1);
        final byte[] pdf = JasperExportManager.exportReportToPdf(print);
        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
        assertTrue(csv(print).contains("1,000.50") || !hasAmountField(template), slug + " should print amounts as #,##0.00");
    }

    @Test
    void activeLoansDetailsPrintsGrandTotalOnlyForOneCurrency() throws Exception {
        final JasperReport template = compile("active-loans-details-pentaho");
        final List<Map<String, ?>> rows = List.of(row(template, 1), row(template, 1));

        // 13 summed columns (loan amount and 12 repaid/outstanding/overdue amounts): subtotal, plus grand total for one
        // currency
        assertEquals(26, count(csv(fill(template, rows, Map.of("currencyId", "USD"))), "2,001.00"));
        final String allCurrencies = csv(fill(template, rows, Map.of()));
        assertTrue(allCurrencies.contains("Grand total, 2 loans"));
        assertEquals(13, count(allCurrencies, "2,001.00"), "no grand total across currencies");
        assertTrue(allCurrencies.contains("សុខ ដារ៉ា"), "Khmer client names survive the export");
    }

    @Test
    void summaryShowsParPercentOrInvalidParType() throws Exception {
        final JasperReport template = compile("active-loans-summary-pentaho");
        final Map<String, Object> row = row(template, 1);
        row.put("par_percent", new BigDecimal("12.5"));

        assertTrue(csv(fill(template, List.of(row), Map.of("parType", 2L))).contains("12.50"));
        assertTrue(csv(fill(template, List.of(row), Map.of("parType", 0L))).contains("invalid PAR Type"));
    }

    @Test
    void agingDetailCountsDaysInArrearsFromBusinessDate() throws Exception {
        final JasperReport template = compile("aging-detail-pentaho");
        final Map<String, Object> row = row(template, 1);
        row.put("overdue_since_date", java.sql.Date.valueOf(BUSINESS_DATE.minusDays(45)));
        row.put("weeks_in_arrears_band", "6");
        row.put("days_in_arrears_band", "30 - 60");

        final String csv = csv(fill(template, List.of(row), Map.of()));
        assertTrue(csv.contains(",45,6,30 - 60"), csv);
    }

    @Test
    void agingSummaryTotalsEachCurrency() throws Exception {
        final JasperReport template = compile("aging-summary-arrears-in-weeks-pentaho");
        final Map<String, Object> onSchedule = row(template, 1);
        onSchedule.put("arrears_period", "On Schedule");
        final Map<String, Object> week3 = row(template, 1);
        week3.put("arrears_period", "3");
        week3.put("period_order", 4);

        final String csv = csv(fill(template, List.of(onSchedule, week3), Map.of()));
        assertTrue(csv.contains("Total USD,6,\"2,001.00\""), csv);
    }

    private static boolean hasAmountField(final JasperReport template) {
        for (final JRField field : template.getFields()) {
            if (field.getValueClass() == BigDecimal.class) {
                return true;
            }
        }
        return false;
    }

    /** One representative value per field, by class; variant 2 is another office in another currency. */
    private static Map<String, Object> row(final JasperReport template, final int variant) {
        final Map<String, Object> row = new HashMap<>();
        for (final JRField field : template.getFields()) {
            final String name = field.getName();
            final Class<?> type = field.getValueClass();
            final Object value;
            if (type == String.class) {
                value = text(name, variant);
            } else if (type == Long.class) {
                value = name.equals("office_id") ? (long) variant : 3L;
            } else if (type == Integer.class) {
                value = name.equals("period_order") ? 1 : 12;
            } else if (type == BigDecimal.class) {
                value = variant == 1 ? new BigDecimal("1000.50") : new BigDecimal("250000");
            } else if (type == java.util.Date.class) {
                value = java.sql.Date.valueOf(BUSINESS_DATE.minusDays(10));
            } else {
                throw new IllegalStateException("No sample value for field " + name + " of " + type);
            }
            row.put(name, value);
        }
        return row;
    }

    private static String text(final String name, final int variant) {
        return switch (name) {
            case "currency_code" -> variant == 1 ? "USD" : "KHR";
            case "currency", "currency_name" -> variant == 1 ? "US$" : "៛";
            case "office_name", "selected_office_name" -> variant == 1 ? "Head Office" : "..សាខា ភ្នំពេញ";
            case "client_name" -> "សុខ ដារ៉ា";
            case "arrears_period", "weeks_in_arrears_band" -> "1";
            case "days_in_arrears_band" -> "0 - 30";
            default -> "Sample " + name;
        };
    }

    private JasperReport compile(final String slug) throws Exception {
        try (InputStream jrxml = getClass().getClassLoader().getResourceAsStream("reports/" + slug + ".jrxml")) {
            assertNotNull(jrxml, "reports/" + slug + ".jrxml is missing");
            return JasperCompileManager.compileReport(jrxml);
        }
    }

    private static JasperPrint fill(final JasperReport template, final List<Map<String, ?>> rows, final Map<String, Object> extra)
            throws Exception {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put(JasperReportingProcessService.CURRENT_DATE, java.sql.Date.valueOf(BUSINESS_DATE));
        parameters.put(JasperReportingProcessService.CURRENT_DATE_TIME, java.sql.Timestamp.valueOf(BUSINESS_DATE.atTime(10, 0)));
        parameters.put(JasperReportingProcessService.CURRENT_USER_HIERARCHY, ".");
        parameters.put(JasperReportingProcessService.CURRENT_USER_NAME, "mifos");
        parameters.put(JRParameter.REPORT_LOCALE, Locale.ENGLISH);
        // Same zone as java.sql.Date.valueOf above, as the JDBC data source reads DATE columns in the tenant zone
        parameters.put(JRParameter.REPORT_TIME_ZONE, TimeZone.getDefault());
        parameters.putAll(extra);
        return JasperFillManager.fillReport(template, parameters, new JRMapCollectionDataSource(new ArrayList<>(rows)));
    }

    private static String csv(final JasperPrint print) throws Exception {
        return new String(JasperReportingProcessService.exportCsv(print), StandardCharsets.UTF_8);
    }

    private static int count(final String text, final String token) {
        int count = 0;
        for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + 1)) {
            count++;
        }
        return count;
    }
}
