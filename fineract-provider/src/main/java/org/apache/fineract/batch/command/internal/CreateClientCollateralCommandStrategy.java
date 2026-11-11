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
package org.apache.fineract.batch.command.internal;

import com.google.common.base.Splitter;
import java.util.List;
import javax.ws.rs.core.UriInfo;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.batch.command.CommandStrategy;
import org.apache.fineract.batch.domain.BatchRequest;
import org.apache.fineract.batch.domain.BatchResponse;
import org.apache.fineract.portfolio.collateralmanagement.api.ClientCollateralManagementApiResource;
import org.springframework.stereotype.Component;

/**
 * Handles {@code POST clients/{clientId}/collaterals} inside a batch by delegating to
 * {@link ClientCollateralManagementApiResource}, so the normal command pipeline (permissions, audit, maker-checker,
 * validation) applies. The response carries {@code resourceId} (the client collateral id) and {@code clientId}.
 */
@Component
@RequiredArgsConstructor
public class CreateClientCollateralCommandStrategy implements CommandStrategy {

    private final ClientCollateralManagementApiResource clientCollateralManagementApiResource;

    @Override
    public BatchResponse execute(final BatchRequest request, @SuppressWarnings("unused") UriInfo uriInfo) {
        final BatchResponse response = new BatchResponse();
        response.setRequestId(request.getRequestId());
        response.setHeaders(request.getHeaders());

        final List<String> pathParameters = Splitter.on('/').splitToList(request.getRelativeUrl());
        final Long clientId = Long.parseLong(pathParameters.get(1));

        final String responseBody = clientCollateralManagementApiResource.addCollateral(clientId, request.getBody());

        response.setStatusCode(200);
        response.setBody(responseBody);
        return response;
    }
}
