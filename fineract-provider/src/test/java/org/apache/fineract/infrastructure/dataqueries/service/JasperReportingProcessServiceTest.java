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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import javax.ws.rs.core.MultivaluedMap;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JRDesignParameter;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.dataqueries.api.RunreportsApiResource;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class JasperReportingProcessServiceTest {

    private static final String PARAMETER_TEMPLATE = """
            <jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports" name="parameters" pageWidth="595" pageHeight="842"
                          columnWidth="555" leftMargin="20" rightMargin="20" topMargin="20" bottomMargin="20">
                <parameter name="currentDate" class="java.time.LocalDate"/>
                <parameter name="currentDateTime" class="java.util.Date"/>
                <parameter name="isSelfServiceUser" class="java.lang.Boolean"/>
                <parameter name="active" class="java.lang.Boolean"/>
                <parameter name="officeId" class="java.lang.Long">
                    <defaultValueExpression><![CDATA[-1L]]></defaultValueExpression>
                </parameter>
                <detail>
                    <band height="20">
                        <textField>
                            <reportElement x="0" y="0" width="555" height="20"/>
                            <textFieldExpression><![CDATA[$P{currentDate} + " " + $P{isSelfServiceUser} + " " + $P{active} + " " + $P{officeId}]]></textFieldExpression>
                        </textField>
                    </band>
                </detail>
            </jasperReport>
            """;

    @Test
    void reportNameMapsToSafeTemplateFileName() {
        assertEquals("loan-transaction-receipt.jrxml", JasperReportingProcessService.templateFileName("Loan Transaction Receipt"));
        assertEquals("etc-passwd.jrxml", JasperReportingProcessService.templateFileName("../../etc/passwd"));
    }

    @Test
    void loanTransactionReceiptTemplateRendersPdf() throws Exception {
        final byte[] pdf = renderReceipt("reports/loan-transaction-receipt.jrxml", "Sok Dara");

        assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF"));
    }

    @Test
    void loanTransactionReceiptFallsBackToKhmerFontForKhmerNames() throws Exception {
        final byte[] pdf = renderReceipt("reports/loan-transaction-receipt.jrxml", "សុខ ដារ៉ា");

        final String content = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(content.contains("DejaVu"), "Latin text should use DejaVu Sans");
        assertTrue(content.contains("Battambang"), "Khmer client name should fall back to Battambang");
    }

    @Test
    void khmerLoanTransactionReceiptEmbedsKhmerFont() throws Exception {
        final byte[] pdf = renderReceipt("reports/loan-transaction-receipt-khmer.jrxml", "សុខ ដារ៉ា");

        assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF"));
        assertTrue(new String(pdf, StandardCharsets.ISO_8859_1).contains("Battambang"), "Battambang font should be embedded");
    }

    @Test
    void khmerReceiptPreviewReturnsPageImagesAndDocxExports() throws Exception {
        final JasperPrint print = fillReceipt("reports/loan-transaction-receipt-khmer.jrxml", "សុខ ដារ៉ា");

        final String preview = JasperReportingProcessService.exportPreview("Loan Transaction Receipt Khmer", print, "1");
        assertTrue(preview.contains("\"pageCount\":1"), preview.substring(0, Math.min(200, preview.length())));
        assertTrue(preview.contains("data:image/png;base64,"), "Preview should carry a PNG per page");

        final byte[] docx = JasperReportingProcessService.exportDocx(print);
        assertEquals("PK", new String(docx, 0, 2, StandardCharsets.US_ASCII), "DOCX is a zip container");
    }

    @Test
    void receiptExportsUtf8Csv() throws Exception {
        final JasperPrint print = fillReceipt("reports/loan-transaction-receipt-khmer.jrxml", "សុខ ដារ៉ា");

        final byte[] csv = JasperReportingProcessService.exportCsv(print);
        assertEquals((byte) 0xEF, csv[0], "CSV should start with a UTF-8 byte order mark");
        assertTrue(new String(csv, StandardCharsets.UTF_8).contains("សុខ ដារ៉ា"), "Khmer text should survive as UTF-8");
    }

    @Test
    void booleanParametersAreConvertedStrictly() {
        final JRDesignParameter parameter = parameter("active", Boolean.class);

        assertEquals(Boolean.TRUE, JasperReportingProcessService.convert(parameter, "true"));
        assertEquals(Boolean.TRUE, JasperReportingProcessService.convert(parameter, "1"));
        assertEquals(Boolean.FALSE, JasperReportingProcessService.convert(parameter, "FALSE"));
        assertThrows(PlatformApiDataValidationException.class, () -> JasperReportingProcessService.convert(parameter, "yes please"));
    }

    @Test
    void unsupportedParameterTypeIsRejectedAsValidationError() {
        final PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                () -> JasperReportingProcessService.convert(parameter("rate", Double.class), "1.5"));

        assertEquals("validation.msg.report.parameter.type.unsupported", e.getErrors().get(0).getUserMessageGlobalisationCode());
        assertEquals("R_rate", e.getErrors().get(0).getParameterName());
        assertEquals("1.5", JasperReportingProcessService.convert(parameter("note", String.class), "1.5"));
    }

    @Test
    void buildParametersSuppliesBusinessDateUserAndSelfServiceFlag() throws Exception {
        final LocalDate businessDate = LocalDate.of(2026, 10, 20);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Bangkok", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, businessDate)));
        try {
            final JasperReport template = JasperCompileManager.compileReport(new ByteArrayInputStream(PARAMETER_TEMPLATE.getBytes(UTF_8)));
            final MultivaluedMap<String, String> queryParams = new MultivaluedStringMap();
            queryParams.putSingle("R_active", "true");
            queryParams.putSingle("R_officeId", " ");
            queryParams.putSingle(RunreportsApiResource.IS_SELF_SERVICE_USER_REPORT_PARAMETER, "true");

            final Map<String, Object> parameters = service().buildParameters(template, queryParams);

            assertEquals(businessDate, parameters.get(JasperReportingProcessService.CURRENT_DATE), "declared as LocalDate");
            assertTrue(parameters.get(JasperReportingProcessService.CURRENT_DATE_TIME) instanceof java.sql.Timestamp);
            assertEquals(Boolean.TRUE, parameters.get(JasperReportingProcessService.IS_SELF_SERVICE_USER));
            assertEquals(Boolean.TRUE, parameters.get("active"));
            assertEquals(".1.", parameters.get(JasperReportingProcessService.CURRENT_USER_HIERARCHY));
            assertFalse(parameters.containsKey("officeId"), "blank values are skipped so the template default applies");

            final JasperPrint print = JasperFillManager.fillReport(template, parameters, new JREmptyDataSource(1));
            final String csv = new String(JasperReportingProcessService.exportCsv(print), UTF_8);
            assertTrue(csv.contains("2026-10-20 true true -1"), csv);

            // Templates that do not declare currentDate get a java.sql.Date, which suits java.util.Date declarations
            // too
            final JasperReport receipt;
            try (InputStream jrxml = getClass().getClassLoader().getResourceAsStream("reports/loan-transaction-receipt.jrxml")) {
                receipt = JasperCompileManager.compileReport(jrxml);
            }
            assertEquals(java.sql.Date.valueOf(businessDate),
                    service().buildParameters(receipt, new MultivaluedStringMap()).get(JasperReportingProcessService.CURRENT_DATE));
        } finally {
            ThreadLocalContextUtil.clearTenant();
        }
    }

    @Test
    void noTemplateUsesRawParameterSubstitution() throws Exception {
        for (final Resource template : templates()) {
            final String jrxml = new String(template.getInputStream().readAllBytes(), UTF_8);
            // $P!{} is pasted into the SQL unbound; templates must use $P{} so values are JDBC parameters
            assertFalse(jrxml.contains("$P!{"), template.getFilename() + " uses $P!{...} (SQL injection risk), use $P{...}");
        }
    }

    @Test
    void everyTemplateCompiles() throws Exception {
        for (final Resource template : templates()) {
            try (InputStream jrxml = template.getInputStream()) {
                assertNotNull(JasperCompileManager.compileReport(jrxml), template.getFilename());
            }
        }
    }

    private static Resource[] templates() throws Exception {
        final Resource[] templates = new PathMatchingResourcePatternResolver().getResources("classpath*:reports/*.jrxml");
        assertTrue(templates.length > 0, "No Jasper templates found on the classpath");
        return templates;
    }

    private static JRDesignParameter parameter(final String name, final Class<?> type) {
        final JRDesignParameter parameter = new JRDesignParameter();
        parameter.setName(name);
        parameter.setValueClass(type);
        return parameter;
    }

    private static JasperReportingProcessService service() {
        final Office office = Mockito.mock(Office.class);
        Mockito.when(office.getHierarchy()).thenReturn(".1.");
        final AppUser user = Mockito.mock(AppUser.class);
        Mockito.when(user.getOffice()).thenReturn(office);
        Mockito.when(user.getId()).thenReturn(1L);
        Mockito.when(user.getDisplayName()).thenReturn("mifos");
        final PlatformSecurityContext context = Mockito.mock(PlatformSecurityContext.class);
        Mockito.when(context.authenticatedUser()).thenReturn(user);
        return new JasperReportingProcessService(null, context);
    }

    private byte[] renderReceipt(final String templateLocation, final String clientName) throws Exception {
        return JasperExportManager.exportReportToPdf(fillReceipt(templateLocation, clientName));
    }

    private JasperPrint fillReceipt(final String templateLocation, final String clientName) throws Exception {
        final JasperReport template;
        try (InputStream jrxml = getClass().getClassLoader().getResourceAsStream(templateLocation)) {
            template = JasperCompileManager.compileReport(jrxml);
        }

        final Map<String, Object> row = new HashMap<>();
        row.put("transaction_id", 42L);
        row.put("transaction_date", java.sql.Date.valueOf("2026-10-20"));
        row.put("amount", new BigDecimal("150000"));
        row.put("principal_portion", new BigDecimal("120000"));
        row.put("interest_portion", new BigDecimal("30000"));
        row.put("fee_portion", BigDecimal.ZERO);
        row.put("penalty_portion", BigDecimal.ZERO);
        row.put("outstanding_balance", new BigDecimal("880000"));
        row.put("transaction_type", "Repayment");
        row.put("loan_account_no", "000000017");
        row.put("currency_code", "KHR");
        row.put("currency_name", "រៀល");
        row.put("currency_digits", 0);
        row.put("client_name", clientName);
        row.put("client_account_no", "000000005");
        row.put("office_name", "Head Office");
        row.put("payment_type", "Cash");

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("transactionId", 42L);
        parameters.put(JasperReportingProcessService.CURRENT_USER_NAME, "mifos");
        parameters.put(JRParameter.REPORT_LOCALE, Locale.ENGLISH);
        parameters.put(JRParameter.REPORT_TIME_ZONE, TimeZone.getTimeZone("Asia/Bangkok"));
        // Always supplied by the service but not declared by the receipt: Jasper must ignore them
        parameters.put(JasperReportingProcessService.CURRENT_DATE, java.sql.Date.valueOf("2026-10-20"));
        parameters.put(JasperReportingProcessService.IS_SELF_SERVICE_USER, Boolean.FALSE);

        final JasperPrint print = JasperFillManager.fillReport(template, parameters,
                new JRMapCollectionDataSource(List.<Map<String, ?>>of(row)));

        assertEquals(1, print.getPages().size());
        return print;
    }
}
