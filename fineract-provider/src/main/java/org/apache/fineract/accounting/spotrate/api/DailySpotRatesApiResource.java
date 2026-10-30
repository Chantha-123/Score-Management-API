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
package org.apache.fineract.accounting.spotrate.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.spotrate.data.DailySpotRateData;
import org.apache.fineract.accounting.spotrate.service.DailySpotRateReadPlatformService;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/dailyspotrates")
@Component
@Scope("singleton")
@Tag(name = "Daily Spot Rate", description = "Records daily foreign exchange spot rates, buying rates, and selling rates against USD per office and currency.")
@RequiredArgsConstructor
public class DailySpotRatesApiResource {

    private static final Set<String> RESPONSE_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList("id", "officeId", "officeName", "currencyCode", "rateDate", "spotRate", "buyingRate", "sellingRate"));

    private final String resourceNameForPermission = "DAILYSPOTRATE";

    private final PlatformSecurityContext context;
    private final DailySpotRateReadPlatformService readPlatformService;
    private final DefaultToApiJsonSerializer<DailySpotRateData> apiJsonSerializerService;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(tags = { "Daily Spot Rate" }, summary = "List Daily Spot Rates", description = "Returns all daily spot rates. "
            + "Optional filters: officeId, currencyCode, fromDate, toDate.\n\n"
            + "Example Requests:\n\ndailyspotrates\n\ndailyspotrates?officeId=1&currencyCode=KHR\n\n"
            + "dailyspotrates?fromDate=2024-01-01&toDate=2024-01-31")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = DailySpotRateData.class)))) })
    public String retrieveAll(@Context final UriInfo uriInfo,
            @QueryParam("officeId") @Parameter(description = "officeId") final Long officeId,
            @QueryParam("currencyCode") @Parameter(description = "currencyCode") final String currencyCode,
            @QueryParam("fromDate") @Parameter(description = "fromDate (yyyy-MM-dd)") final String fromDateStr,
            @QueryParam("toDate") @Parameter(description = "toDate (yyyy-MM-dd)") final String toDateStr) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermission);

        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        final LocalDate fromDate = fromDateStr != null ? LocalDate.parse(fromDateStr, formatter) : null;
        final LocalDate toDate = toDateStr != null ? LocalDate.parse(toDateStr, formatter) : null;

        final List<DailySpotRateData> dailySpotRates = this.readPlatformService.retrieveAll(officeId, currencyCode, fromDate, toDate);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.apiJsonSerializerService.serialize(settings, dailySpotRates, RESPONSE_DATA_PARAMETERS);
    }

    @GET
    @Path("{dailySpotRateId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(tags = { "Daily Spot Rate" }, summary = "Retrieve a Daily Spot Rate", description = "Example Requests:\n\ndailyspotrates/1")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DailySpotRateData.class))) })
    public String retrieveOne(@PathParam("dailySpotRateId") @Parameter(description = "dailySpotRateId") final Long dailySpotRateId,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermission);

        final DailySpotRateData dailySpotRateData = this.readPlatformService.retrieveById(dailySpotRateId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.apiJsonSerializerService.serialize(settings, dailySpotRateData, RESPONSE_DATA_PARAMETERS);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(tags = {
            "Daily Spot Rate" }, summary = "Create a Daily Spot Rate", description = "Records a new daily spot rate for a given office, currency and date.\n\n"
                    + "Mandatory Fields: officeId, currencyCode, rateDate, spotRate, buyingRate, sellingRate")
    @RequestBody(content = @Content(schema = @Schema(implementation = DailySpotRateData.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))) })
    public String create(@Parameter(hidden = true) final String jsonRequestBody) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().createDailySpotRate().withJson(jsonRequestBody).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.apiJsonSerializerService.serialize(result);
    }

    @PUT
    @Path("{dailySpotRateId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(tags = {
            "Daily Spot Rate" }, summary = "Update a Daily Spot Rate", description = "Updates spotRate, buyingRate and/or sellingRate for an existing record.")
    @RequestBody(content = @Content(schema = @Schema(implementation = DailySpotRateData.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))) })
    public String update(@PathParam("dailySpotRateId") @Parameter(description = "dailySpotRateId") final Long dailySpotRateId,
            @Parameter(hidden = true) final String jsonRequestBody) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateDailySpotRate(dailySpotRateId).withJson(jsonRequestBody)
                .build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.apiJsonSerializerService.serialize(result);
    }
}
