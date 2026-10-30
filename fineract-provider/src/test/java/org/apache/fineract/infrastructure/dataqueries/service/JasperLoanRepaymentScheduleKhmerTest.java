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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Test;

class JasperLoanRepaymentScheduleKhmerTest {

    private static final String TEMPLATE = "reports/loan-repayment-schedule-khmer.jrxml";

    @Test
    void reportNameMapsToTemplate() {
        assertEquals("loan-repayment-schedule-khmer.jrxml",
                JasperReportingProcessService.templateFileName("Loan Repayment Schedule Khmer"));
    }

    @Test
    void sqlIsPortableAndBound() throws Exception {
        final String sql = compile().getQuery().getText();

        assertFalse(sql.contains("${"));
        assertFalse(sql.contains("$P!{"));
        final String upper = sql.toUpperCase(Locale.ROOT);
        for (final String function : List.of("NOW()", "CURDATE()", "CURRENT_DATE", "IFNULL", "DATEDIFF")) {
            assertFalse(upper.contains(function), "uses " + function);
        }
        assertFalse(sql.contains("\""));
        assertFalse(sql.contains("`"));
        assertTrue(sql.contains("LIKE CONCAT($P{currentUserHierarchy}, '%')"));
    }

    /**
     * Same figures as the ITD-0002 paper form: 2,000 over 12 months, 1.5% interest and a 0.5% service fee per month.
     */
    @Test
    void printsTheItd0002DecliningSchedule() throws Exception {
        final List<Map<String, ?>> rows = new ArrayList<>();
        LocalDate from = LocalDate.of(2026, 8, 12);
        BigDecimal outstanding = new BigDecimal("2000.00");
        for (int installment = 1; installment <= 12; installment++) {
            final LocalDate due = from.plusMonths(1);
            final BigDecimal principal = installment == 12 ? outstanding : new BigDecimal("166.67");
            rows.add(row(installment, from, due, principal,
                    outstanding.multiply(new BigDecimal("0.015")).setScale(2, java.math.RoundingMode.HALF_UP)));
            outstanding = outstanding.subtract(principal);
            from = due;
        }

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("loanId", 1L);
        parameters.put(JasperReportingProcessService.CURRENT_USER_HIERARCHY, ".");
        parameters.put(JRParameter.REPORT_LOCALE, new Locale("km"));
        parameters.put(JRParameter.REPORT_TIME_ZONE, TimeZone.getTimeZone("Asia/Bangkok"));
        final JasperPrint print = JasperFillManager.fillReport(compile(), parameters, new JRMapCollectionDataSource(rows));

        assertEquals(1, print.getPages().size(), "12 installments fit on one A4 page");
        final List<String> texts = texts(print);
        // Header
        assertTrue(texts.contains("ប៉ុក លឹមហ៊ាង"));
        assertTrue(texts.contains("ថ្មី ក្រឡា កំពង់សៀម កំពង់ចាម"));
        assertTrue(texts.contains("2,000 ដុល្លារ"));
        assertTrue(texts.contains("1.5% ប្រចាំខែ"));
        assertTrue(texts.contains("0.50% ប្រចាំខែ"));
        assertTrue(texts.contains("12 ខែ"));
        assertTrue(texts.contains("រំលស់ប្រាក់ដើម"));
        // First installment: 166.67 + 30.00 + 20.00 = 216.67, with the full 2,000 still owed
        assertTrue(texts.contains("216.67"));
        assertTrue(texts.contains("2,000.00"));
        assertTrue(texts.contains("12-09-2026"));
        // Last installment: interest on 166.63 owed, total 166.63 + 2.50 + 20.00
        assertTrue(texts.contains("189.13"));
        assertTrue(texts.contains("31"), "days between 12-07 and 12-08");

        final byte[] pdf = JasperExportManager.exportReportToPdf(print);
        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
        assertTrue(new String(pdf, StandardCharsets.ISO_8859_1).contains("Battambang"));
    }

    private static Map<String, Object> row(final int installment, final LocalDate from, final LocalDate due, final BigDecimal principal,
            final BigDecimal interest) {
        final Map<String, Object> row = new HashMap<>();
        row.put("installment", installment);
        row.put("fromdate", java.sql.Date.valueOf(from));
        row.put("duedate", java.sql.Date.valueOf(due));
        row.put("principal", principal);
        row.put("interest", interest);
        row.put("fee", new BigDecimal("20.00"));
        row.put("loan_principal", new BigDecimal("2000.00"));
        row.put("disbursement_date", java.sql.Date.valueOf("2026-06-09"));
        row.put("loan_account_no", "009-0029-0031424-29");
        row.put("currency_digits", 2);
        row.put("interest_rate", new BigDecimal("1.500000"));
        row.put("term_frequency", 12);
        row.put("interest_period_name", "ខែ");
        row.put("term_period_name", "ខែ");
        row.put("repayment_method", "រំលស់ប្រាក់ដើម");
        row.put("currency_name", "ដុល្លារ");
        row.put("currency_symbol", "$");
        row.put("fee_rate", new BigDecimal("0.500000"));
        row.put("client_account_no", "0109");
        row.put("client_name", "ប៉ុក លឹមហ៊ាង");
        row.put("address", "ថ្មី ក្រឡា កំពង់សៀម កំពង់ចាម");
        return row;
    }

    private static JasperReport compile() throws Exception {
        try (InputStream jrxml = JasperLoanRepaymentScheduleKhmerTest.class.getClassLoader().getResourceAsStream(TEMPLATE)) {
            return JasperCompileManager.compileReport(jrxml);
        }
    }

    private static List<String> texts(final JasperPrint print) {
        final List<String> texts = new ArrayList<>();
        for (final JRPrintPage page : print.getPages()) {
            collect(page.getElements(), texts);
        }
        return texts;
    }

    private static void collect(final List<JRPrintElement> elements, final List<String> texts) {
        for (final JRPrintElement element : elements) {
            if (element instanceof JRPrintText) {
                texts.add(((JRPrintText) element).getFullText());
            } else if (element instanceof JRPrintFrame) {
                collect(((JRPrintFrame) element).getElements(), texts);
            }
        }
    }
}
