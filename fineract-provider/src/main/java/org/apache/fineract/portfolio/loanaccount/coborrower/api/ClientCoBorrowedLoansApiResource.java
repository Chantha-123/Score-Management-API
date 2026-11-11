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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.coborrower.data.LoanCoBorrowerData;
import org.apache.fineract.portfolio.loanaccount.coborrower.service.LoanCoBorrowerReadPlatformService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/clients/{clientId}/coborrowedloans")
@Component
@Scope("singleton")
@Tag(name = "Loan Co-Borrowers", description = "Loans on which a client is named as co-borrower.")
@RequiredArgsConstructor
public class ClientCoBorrowedLoansApiResource {

    private final PlatformSecurityContext context;
    private final LoanCoBorrowerReadPlatformService readPlatformService;
    private final DefaultToApiJsonSerializer<LoanCoBorrowerData> apiJsonSerializerService;
    private final ApiRequestParameterHelper apiRequestParameterHelper;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List loans a client co-borrows", description = "Example Requests:\n\nclients/1/coborrowedloans")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LoanCoBorrowerData.class)))) })
    public String retrieveAll(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission("LOANCOBORROWER");

        final List<LoanCoBorrowerData> loans = this.readPlatformService.retrieveCoBorrowedLoansForClient(clientId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.apiJsonSerializerService.serialize(settings, loans, LoanCoBorrowersApiResource.RESPONSE_DATA_PARAMETERS);
    }
}
