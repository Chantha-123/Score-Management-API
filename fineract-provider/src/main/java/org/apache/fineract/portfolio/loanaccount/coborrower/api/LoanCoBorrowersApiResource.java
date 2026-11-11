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
package org.apache.fineract.portfolio.loanaccount.coborrower.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.coborrower.data.LoanCoBorrowerData;
import org.apache.fineract.portfolio.loanaccount.coborrower.service.LoanCoBorrowerReadPlatformService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/loans/{loanId}/coborrowers")
@Component
@Scope("singleton")
@Tag(name = "Loan Co-Borrowers", description = "Clients jointly named on a loan alongside the main borrower. Repayments and accounting stay on the main borrower; the loan is also listed on each co-borrower's client page.")
@RequiredArgsConstructor
public class LoanCoBorrowersApiResource {

    public static final Set<String> RESPONSE_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList("id", "loanId", "clientId", "clientName", "clientAccountNo", "clientMobileNo", "loanAccountNo", "loanProductName",
                    "loanStatus", "principal", "currencyCode", "borrowerClientId", "borrowerName"));

    private final String resourceNameForPermission = "LOANCOBORROWER";

    private final PlatformSecurityContext context;
    private final LoanCoBorrowerReadPlatformService readPlatformService;
    private final DefaultToApiJsonSerializer<LoanCoBorrowerData> apiJsonSerializerService;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List co-borrowers of a loan", description = "Example Requests:\n\nloans/1/coborrowers")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LoanCoBorrowerData.class)))) })
    public String retrieveAll(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId, @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(this.resourceNameForPermission);

        final List<LoanCoBorrowerData> coBorrowers = this.readPlatformService.retrieveForLoan(loanId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.apiJsonSerializerService.serialize(settings, coBorrowers, RESPONSE_DATA_PARAMETERS);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Add a co-borrower to a loan", description = "Links an existing active client (not the borrower) to the loan.\n\n"
            + "Mandatory Fields: clientId")
    @RequestBody(content = @Content(schema = @Schema(implementation = LoanCoBorrowerData.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))) })
    public String create(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId,
            @Parameter(hidden = true) final String jsonRequestBody) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().createLoanCoBorrower(loanId).withJson(jsonRequestBody).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.apiJsonSerializerService.serialize(result);
    }

    @DELETE
    @Path("{coBorrowerId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Remove a co-borrower from a loan")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))) })
    public String delete(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId,
            @PathParam("coBorrowerId") @Parameter(description = "coBorrowerId") final Long coBorrowerId) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteLoanCoBorrower(loanId, coBorrowerId).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.apiJsonSerializerService.serialize(result);
    }
}
