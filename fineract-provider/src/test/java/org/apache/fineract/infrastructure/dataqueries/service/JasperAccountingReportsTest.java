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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JasperAccountingReportsTest {

    private static final String TRIAL_BALANCE = "reports/trial-balance.jrxml";
    private static final String INCOME_STATEMENT = "reports/income-statement.jrxml";
    private static final String BALANCE_SHEET = "reports/balance-sheet.jrxml";
    private static final String GENERAL_LEDGER = "reports/generalledgerreport.jrxml";

    @ParameterizedTest
    @ValueSource(strings = { TRIAL_BALANCE, INCOME_STATEMENT, BALANCE_SHEET, GENERAL_LEDGER })
    void sqlIsPortableAndBound(final String location) throws Exception {
        final String sql = compile(location).getQuery().getText();

        assertFalse(sql.contains("${"), location + " still has stretchy ${...} placeholders");
        assertFalse(sql.contains("$P!{"), location + " uses raw $P!{...} substitution");
        final String upper = sql.toUpperCase(Locale.ROOT);
        for (final String function : List.of("NOW()", "CURDATE()", "CURRENT_DATE", "SYSDATE", "IFNULL", "DATE_SUB", "INTERVAL")) {
            assertFalse(upper.contains(function), location + " uses " + function);
        }
        assertFalse(sql.contains("\""), location + " has a double-quoted string literal or identifier");
        assertFalse(sql.contains("`"), location + " has backticks");
        assertTrue(sql.contains("LIKE CONCAT($P{currentUserHierarchy}, '%')"), location + " must restrict to the user's office hierarchy");
    }

    @Test
    void trialBalanceGroupsByCurrencyAndAccountTypeWithTotals() throws Exception {
        final List<Map<String, ?>> rows = new ArrayList<>();
        rows.add(trialBalanceRow("USD", 1, "Assets", "10100", "សាច់ប្រាក់ (Cash)", "1000.50", null));
        rows.add(trialBalanceRow("USD", 2, "Liabilities", "20100", "Savings deposits", null, "400.25"));
        rows.add(trialBalanceRow("USD", 3, "Equity", "30100", "Share capital", null, "200.00"));
        rows.add(trialBalanceRow("USD", 4, "Income", "40100", "Interest income", null, "600.25"));
        rows.add(trialBalanceRow("USD", 5, "Expenses", "50100", "Salaries", "200.00", null));
        rows.add(trialBalanceRow("KHR", 1, "Assets", "10100", "Cash", "4000000", null));
        rows.add(trialBalanceRow("KHR", 3, "Equity", "30100", "Share capital", null, "4000000"));

        final JasperPrint print = fill(TRIAL_BALANCE, rows, Map.of("officeId", 1L));

        final String csv = exportPdfAndCsv(print);
        assertTrue(csv.contains("Total Assets"), csv);
        assertTrue(csv.contains("1,200.50"), "USD debit and credit totals: " + csv);
        assertTrue(csv.contains("4,000,000.00"), "KHR is totalled separately: " + csv);
        assertTrue(csv.contains("សាច់ប្រាក់"), "Khmer account names survive: " + csv);
    }

    @Test
    void incomeStatementShowsNetIncomePerCurrency() throws Exception {
        final List<Map<String, ?>> rows = new ArrayList<>();
        rows.add(statementRow("USD", 4, "Income", "40100", "Interest income", "1500.00"));
        rows.add(statementRow("USD", 4, "Income", "40200", "Fee income", "250.00"));
        rows.add(statementRow("USD", 5, "Expenses", "50100", "Salaries", "1000.00"));

        final String csv = exportPdfAndCsv(fill(INCOME_STATEMENT, rows, Map.of("officeId", 2L)));

        assertTrue(csv.contains("1,750.00"), "total income: " + csv);
        assertTrue(csv.contains("Net income (USD)"), csv);
        assertTrue(csv.contains("750.00"), "net income: " + csv);
    }

    @Test
    void balanceSheetComparesAssetsWithLiabilitiesAndEquity() throws Exception {
        final List<Map<String, ?>> rows = new ArrayList<>();
        rows.add(statementRow("USD", 1, "Assets", "10100", "Cash", "5000.00"));
        rows.add(statementRow("USD", 2, "Liabilities", "20100", "Savings deposits", "3000.00"));
        rows.add(statementRow("USD", 3, "Equity", "30100", "Share capital", "1500.00"));

        final String csv = exportPdfAndCsv(fill(BALANCE_SHEET, rows, Map.of("endDate", java.sql.Date.valueOf("2026-10-31"))));

        assertTrue(csv.contains("5,000.00"), "total assets: " + csv);
        assertTrue(csv.contains("4,500.00"), "liabilities + equity: " + csv);
        assertTrue(csv.contains("500.00"), "difference: " + csv);
    }

    @Test
    void generalLedgerShowsOpeningRunningAndClosingBalance() throws Exception {
        final List<Map<String, ?>> rows = new ArrayList<>();
        rows.add(ledgerRow("2026-10-02", 101L, null, "Loan disbursement", "40100-Loan portfolio", "200.00", "0", "200.00"));
        rows.add(ledgerRow("2026-10-05", 105L, 105L, "Manual adjustment", "50100-Salaries", "0", "50.00", "-50.00"));

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("GLAccountNO", 7L);
        parameters.put("startDate", java.sql.Date.valueOf("2026-10-01"));
        final String csv = exportPdfAndCsv(fill(GENERAL_LEDGER, rows, parameters));

        assertTrue(csv.contains("Opening balance"), csv);
        assertTrue(csv.contains("1,200.00"), "running balance after the first row: " + csv);
        assertTrue(csv.contains("1,150.00"), "running / closing balance: " + csv);
        assertTrue(csv.contains("System"), "system entries are labelled: " + csv);
    }

    @Test
    void generalLedgerOpeningBalanceCutOffMatchesStretchySql() throws Exception {
        final JasperReport template = compile(GENERAL_LEDGER);
        final Map<String, Object> parameters = baseParameters();
        parameters.put("startDate", java.sql.Date.valueOf("2026-10-01"));
        final JasperPrint print = JasperFillManager.fillReport(template, parameters,
                new JRMapCollectionDataSource(List.of(ledgerRow("2026-10-02", 1L, null, "x", "y", "1", "0", "1"))));
        assertEquals(1, print.getPages().size());
        // DATE_SUB(startDate, INTERVAL 2 DAY) in the stretchy SQL
        assertEquals(java.sql.Date.valueOf("2026-09-29"), parameters.get("openingBalanceDate"));
    }

    private static Map<String, Object> trialBalanceRow(final String currency, final int classification, final String type,
            final String glCode, final String name, final String debit, final String credit) {
        final Map<String, Object> row = new HashMap<>();
        row.put("selected_office_name", "Head Office");
        row.put("currency_code", currency);
        row.put("classification_enum", classification);
        row.put("account_type", type);
        row.put("gl_code", glCode);
        row.put("account_name", name);
        row.put("debit_balance", debit == null ? null : new BigDecimal(debit));
        row.put("credit_balance", credit == null ? null : new BigDecimal(credit));
        return row;
    }

    private static Map<String, Object> statementRow(final String currency, final int classification, final String type, final String glCode,
            final String name, final String balance) {
        final Map<String, Object> row = new HashMap<>();
        row.put("selected_office_name", "សាខា ភ្នំពេញ");
        row.put("currency_code", currency);
        row.put("classification_enum", classification);
        row.put("account_type", type);
        row.put("gl_code", glCode);
        row.put("account_name", name);
        row.put("balance", new BigDecimal(balance));
        return row;
    }

    private static Map<String, Object> ledgerRow(final String date, final Long firstEntryId, final Long manualEntryId,
            final String description, final String contraAccount, final String debit, final String credit, final String movement) {
        final Map<String, Object> row = new HashMap<>();
        row.put("selected_office_name", "Head Office");
        row.put("report_header", "10100-Cash");
        row.put("entry_date", java.sql.Date.valueOf(date));
        row.put("description", description);
        row.put("account_name", contraAccount);
        row.put("manual_entry_id", manualEntryId);
        row.put("first_entry_id", firstEntryId);
        row.put("debit_amount", new BigDecimal(debit));
        row.put("credit_amount", new BigDecimal(credit));
        row.put("opening_balance", new BigDecimal("1000.00"));
        row.put("movement", new BigDecimal(movement));
        return row;
    }

    private JasperReport compile(final String location) throws Exception {
        try (InputStream jrxml = getClass().getClassLoader().getResourceAsStream(location)) {
            return JasperCompileManager.compileReport(jrxml);
        }
    }

    private JasperPrint fill(final String location, final List<Map<String, ?>> rows, final Map<String, Object> reportParameters)
            throws Exception {
        final Map<String, Object> parameters = baseParameters();
        parameters.putAll(reportParameters);
        final JasperPrint print = JasperFillManager.fillReport(compile(location), parameters, new JRMapCollectionDataSource(rows));
        assertFalse(print.getPages().isEmpty(), location + " produced no pages");
        return print;
    }

    private static Map<String, Object> baseParameters() {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put(JasperReportingProcessService.CURRENT_DATE, java.sql.Date.valueOf("2026-10-20"));
        parameters.put(JasperReportingProcessService.CURRENT_DATE_TIME, java.sql.Timestamp.valueOf("2026-10-20 10:00:00"));
        parameters.put(JasperReportingProcessService.CURRENT_USER_NAME, "mifos");
        parameters.put(JasperReportingProcessService.CURRENT_USER_HIERARCHY, ".");
        parameters.put(JRParameter.REPORT_LOCALE, Locale.ENGLISH);
        parameters.put(JRParameter.REPORT_TIME_ZONE, TimeZone.getTimeZone("Asia/Bangkok"));
        return parameters;
    }

    private static String exportPdfAndCsv(final JasperPrint print) throws Exception {
        final byte[] pdf = JasperExportManager.exportReportToPdf(print);
        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
        return new String(JasperReportingProcessService.exportCsv(print), StandardCharsets.UTF_8);
    }
}
