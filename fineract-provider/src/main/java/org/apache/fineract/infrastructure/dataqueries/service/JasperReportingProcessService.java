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

import com.google.gson.Gson;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperPrintManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.query.JRJdbcQueryExecuterFactory;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleHtmlExporterOutput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.dataqueries.api.RunreportsApiResource;
import org.apache.fineract.infrastructure.report.annotation.ReportService;
import org.apache.fineract.infrastructure.report.service.ReportingProcessService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Runs stretchy reports of type "Jasper". The layout and SQL live in {@code reports/<report-name-slug>.jrxml} on the
 * classpath (e.g. "Loan Transaction Receipt" -> reports/loan-transaction-receipt.jrxml). Request parameters
 * {@code R_<name>} are passed as typed Jasper parameters {@code $P{<name>}}, which Jasper binds as JDBC parameters.
 */
@Slf4j
@Service
@ReportService(type = { "Jasper" })
@RequiredArgsConstructor
public class JasperReportingProcessService implements ReportingProcessService {

    public static final String CURRENT_USER_HIERARCHY = "currentUserHierarchy";
    public static final String CURRENT_USER_ID = "currentUserId";
    public static final String CURRENT_USER_NAME = "currentUserName";
    public static final String CURRENT_DATE = "currentDate";
    public static final String CURRENT_DATE_TIME = "currentDateTime";
    public static final String IS_SELF_SERVICE_USER = "isSelfServiceUser";

    private static final String TEMPLATE_LOCATION = "reports/";
    private static final String OUTPUT_TYPE_PARAMETER = "output-type";
    private static final String LOCALE_PARAMETER = "locale";
    private static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String DOCX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String CSV_MEDIA_TYPE = "text/csv; charset=UTF-8";
    private static final String ZOOM_PARAMETER = "zoom";
    private static final String AS_JSON_PARAMETER = "as-json";
    private static final int MAX_PREVIEW_PAGES = 50;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final Map<String, JasperReport> compiledTemplates = new ConcurrentHashMap<>();

    // Set to false in development so .jrxml edits are picked up on the next run without a restart
    @Value("${fineract.report.jasper.template-cache-enabled:true}")
    private boolean templateCacheEnabled = true;

    @Override
    public Response processRequest(final String reportName, final MultivaluedMap<String, String> queryParams) {
        final String templateFileName = templateFileName(reportName);
        final JasperReport template = this.templateCacheEnabled
                ? this.compiledTemplates.computeIfAbsent(templateFileName, this::compileTemplate)
                : compileTemplate(templateFileName);
        final Map<String, Object> parameters = buildParameters(template, queryParams);

        final JasperPrint print = this.jdbcTemplate.execute((ConnectionCallback<JasperPrint>) connection -> {
            try {
                return JasperFillManager.fillReport(template, parameters, connection);
            } catch (final JRException e) {
                throw new PlatformDataIntegrityException("error.msg.report.jasper.fill.failed", "Failed to run report: " + reportName, e);
            }
        });

        final String outputType = StringUtils.defaultIfBlank(queryParams.getFirst(OUTPUT_TYPE_PARAMETER), "PDF").toUpperCase(Locale.ROOT);
        final String fileName = templateFileName.replace(".jrxml", "");
        // Download managers (IDM) intercept file responses and re-request them without the Authorization header;
        // as-json=true wraps the file in JSON so the browser builds the download itself
        final boolean asJson = Boolean.parseBoolean(queryParams.getFirst(AS_JSON_PARAMETER));
        try {
            return switch (outputType) {
                case "XLSX", "XLS" -> fileResponse(exportXlsx(print), XLSX_MEDIA_TYPE, "attachment", fileName + ".xlsx", asJson);
                case "DOCX" -> fileResponse(exportDocx(print), DOCX_MEDIA_TYPE, "attachment", fileName + ".docx", asJson);
                case "CSV" -> fileResponse(exportCsv(print), CSV_MEDIA_TYPE, "attachment", fileName + ".csv", asJson);
                case "HTML" -> Response.ok(exportHtml(print), "text/html; charset=UTF-8").build();
                // Page images inside JSON: viewable in the browser without a PDF response, which download managers
                // intercept
                case "PREVIEW" -> Response
                        .ok(exportPreview(reportName, print, queryParams.getFirst(ZOOM_PARAMETER)), MediaType.APPLICATION_JSON).build();
                default -> fileResponse(JasperExportManager.exportReportToPdf(print), "application/pdf", "inline", fileName + ".pdf",
                        asJson);
            };
        } catch (final JRException | IOException e) {
            throw new PlatformDataIntegrityException("error.msg.report.jasper.export.failed",
                    "Failed to export report " + reportName + " as " + outputType, e);
        }
    }

    /**
     * Report names are free text, so they are reduced to a lowercase slug; this also keeps the lookup inside the
     * reports/ folder.
     */
    static String templateFileName(final String reportName) {
        final String slug = reportName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug + ".jrxml";
    }

    private JasperReport compileTemplate(final String fileName) {
        final String location = TEMPLATE_LOCATION + fileName;
        try (InputStream jrxml = getClass().getClassLoader().getResourceAsStream(location)) {
            if (jrxml == null) {
                throw new PlatformDataIntegrityException("error.msg.report.jasper.template.missing",
                        "Jasper template not found on classpath: " + location);
            }
            log.info("Compiling Jasper template {}", location);
            return JasperCompileManager.compileReport(jrxml);
        } catch (final IOException | JRException e) {
            throw new PlatformDataIntegrityException("error.msg.report.jasper.template.invalid", "Invalid Jasper template: " + location, e);
        }
    }

    Map<String, Object> buildParameters(final JasperReport template, final MultivaluedMap<String, String> queryParams) {
        final Map<String, Object> parameters = new HashMap<>();
        for (final JRParameter parameter : template.getParameters()) {
            if (parameter.isSystemDefined()) {
                continue;
            }
            final String value = queryParams.getFirst("R_" + parameter.getName());
            if (StringUtils.isNotBlank(value)) {
                parameters.put(parameter.getName(), convert(parameter, value.trim()));
            }
        }

        final AppUser currentUser = this.context.authenticatedUser();
        // Lets templates restrict rows to the user's office hierarchy, like ${currentUserHierarchy} in SQL reports
        parameters.put(CURRENT_USER_HIERARCHY, currentUser.getOffice().getHierarchy());
        parameters.put(CURRENT_USER_ID, currentUser.getId());
        parameters.put(CURRENT_USER_NAME, currentUser.getDisplayName());
        // Business date and tenant date-time, like ${currentDate} / NOW() in SQL reports; templates use these instead
        // of
        // CURRENT_DATE so the report follows the business date
        parameters.put(CURRENT_DATE, dateParameterValue(template, CURRENT_DATE, DateUtils.getBusinessLocalDate().atStartOfDay()));
        parameters.put(CURRENT_DATE_TIME, dateParameterValue(template, CURRENT_DATE_TIME, DateUtils.getLocalDateTimeOfTenant()));
        parameters.put(IS_SELF_SERVICE_USER,
                Boolean.parseBoolean(queryParams.getFirst(RunreportsApiResource.IS_SELF_SERVICE_USER_REPORT_PARAMETER)));

        final String locale = queryParams.getFirst(LOCALE_PARAMETER);
        if (StringUtils.isNotBlank(locale)) {
            parameters.put(JRParameter.REPORT_LOCALE, Locale.forLanguageTag(locale.replace('_', '-')));
        }
        // Format with the tenant zone, and read DATE columns in that zone too so they are not shifted by a day
        final ZoneId tenantZone = DateUtils.getDateTimeZoneOfTenant();
        parameters.put(JRParameter.REPORT_TIME_ZONE, TimeZone.getTimeZone(tenantZone));
        parameters.put(JRJdbcQueryExecuterFactory.PROPERTY_TIME_ZONE, tenantZone.getId());
        return parameters;
    }

    /**
     * Matches the class the template declares for the parameter. If not declared as LocalDate/LocalDateTime,
     * currentDate is a java.sql.Date and currentDateTime a java.sql.Timestamp (both are java.util.Date). Jasper ignores
     * map entries the template does not declare.
     */
    static Object dateParameterValue(final JasperReport template, final String name, final LocalDateTime value) {
        final Class<?> type = declaredValueClass(template, name);
        if (type == LocalDate.class) {
            return value.toLocalDate();
        } else if (type == LocalDateTime.class) {
            return value;
        } else if (type == java.sql.Date.class) {
            return java.sql.Date.valueOf(value.toLocalDate());
        } else if (type == java.sql.Timestamp.class || CURRENT_DATE_TIME.equals(name)) {
            return java.sql.Timestamp.valueOf(value);
        }
        return java.sql.Date.valueOf(value.toLocalDate());
    }

    private static Class<?> declaredValueClass(final JasperReport template, final String name) {
        for (final JRParameter parameter : template.getParameters()) {
            if (!parameter.isSystemDefined() && parameter.getName().equals(name)) {
                return parameter.getValueClass();
            }
        }
        return null;
    }

    static Object convert(final JRParameter parameter, final String value) {
        final Class<?> type = parameter.getValueClass();
        if (!isSupportedType(type)) {
            // Passing the String on would only fail at fill time, with an unclear ClassCastException
            throw new PlatformApiDataValidationException(
                    List.of(ApiParameterError.parameterError(
                            "validation.msg.report.parameter.type.unsupported", "Report parameter " + parameter.getName()
                                    + " has unsupported type " + type.getName() + " in the Jasper template",
                            "R_" + parameter.getName(), value)));
        }
        try {
            if (type == Long.class) {
                return Long.valueOf(value);
            } else if (type == Integer.class) {
                return Integer.valueOf(value);
            } else if (type == BigDecimal.class) {
                return new BigDecimal(value);
            } else if (type == Boolean.class) {
                return parseBoolean(value);
            } else if (type == java.util.Date.class || type == java.sql.Date.class) {
                return java.sql.Date.valueOf(LocalDate.parse(value));
            } else if (type == java.sql.Timestamp.class) {
                return value.length() > 10 ? java.sql.Timestamp.valueOf(LocalDateTime.parse(value.replace(' ', 'T')))
                        : java.sql.Timestamp.valueOf(LocalDate.parse(value).atStartOfDay());
            } else if (type == LocalDate.class) {
                return LocalDate.parse(value);
            }
            return value;
        } catch (final RuntimeException e) {
            throw new PlatformApiDataValidationException(List.of(ApiParameterError.parameterError("validation.msg.report.parameter.invalid",
                    "Invalid value for report parameter " + parameter.getName() + ": " + value, "R_" + parameter.getName(), value)));
        }
    }

    private static boolean isSupportedType(final Class<?> type) {
        return type == String.class || type == Object.class || type == Long.class || type == Integer.class || type == BigDecimal.class
                || type == Boolean.class || type == java.util.Date.class || type == java.sql.Date.class || type == java.sql.Timestamp.class
                || type == LocalDate.class;
    }

    // Strict, so a typo is rejected instead of silently becoming false
    private static Boolean parseBoolean(final String value) {
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return Boolean.TRUE;
        } else if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Not a boolean: " + value);
    }

    private static byte[] exportXlsx(final JasperPrint print) throws JRException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
        final SimpleXlsxReportConfiguration configuration = new SimpleXlsxReportConfiguration();
        configuration.setDetectCellType(true);
        configuration.setRemoveEmptySpaceBetweenRows(true);
        configuration.setRemoveEmptySpaceBetweenColumns(true);
        exporter.setConfiguration(configuration);
        exporter.exportReport();
        return out.toByteArray();
    }

    static byte[] exportDocx(final JasperPrint print) throws JRException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final JRDocxExporter exporter = new JRDocxExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
        exporter.exportReport();
        return out.toByteArray();
    }

    static byte[] exportCsv(final JasperPrint print) throws JRException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        // UTF-8 byte order mark, so Excel opens Khmer text correctly
        out.writeBytes(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });
        final JRCsvExporter exporter = new JRCsvExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleWriterExporterOutput(out, "UTF-8"));
        exporter.exportReport();
        return out.toByteArray();
    }

    /**
     * Renders each page to a PNG (same layout engine as the PDF, so Khmer shaping matches) and returns them as base64
     * data URLs in a JSON document.
     */
    static String exportPreview(final String reportName, final JasperPrint print, final String zoomValue) throws JRException, IOException {
        final float zoom = Math.max(1f, Math.min(3f, NumberUtils.toFloat(zoomValue, 2f)));
        final int pageCount = print.getPages().size();
        final List<String> pages = new ArrayList<>();
        for (int i = 0; i < Math.min(pageCount, MAX_PREVIEW_PAGES); i++) {
            final ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write((RenderedImage) JasperPrintManager.printPageToImage(print, i, zoom), "png", png);
            pages.add("data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray()));
        }
        final Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("reportName", reportName);
        preview.put("pageCount", pageCount);
        preview.put("truncated", pageCount > MAX_PREVIEW_PAGES);
        preview.put("pageWidth", print.getPageWidth());
        preview.put("pageHeight", print.getPageHeight());
        preview.put("pages", pages);
        return new Gson().toJson(preview);
    }

    private static String exportHtml(final JasperPrint print) throws JRException {
        final StringBuilder html = new StringBuilder();
        final HtmlExporter exporter = new HtmlExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleHtmlExporterOutput(html));
        exporter.exportReport();
        return html.toString();
    }

    private static Response fileResponse(final byte[] content, final String mediaType, final String disposition, final String fileName,
            final boolean asJson) {
        if (asJson) {
            final Map<String, Object> file = new LinkedHashMap<>();
            file.put("fileName", fileName);
            file.put("mediaType", mediaType);
            file.put("data", Base64.getEncoder().encodeToString(content));
            return Response.ok(new Gson().toJson(file), MediaType.APPLICATION_JSON).build();
        }
        return Response.ok(content, mediaType).header("Content-Disposition", disposition + "; filename=\"" + fileName + "\"").build();
    }
}
