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
package org.apache.fineract.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.batch.domain.BatchRequest;
import org.apache.fineract.batch.domain.BatchResponse;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.Test;

class ResolutionHelperTest {

    private final ResolutionHelper resolutionHelper = new ResolutionHelper(new FromJsonHelper());

    private static BatchResponse response(long requestId, String body) {
        final BatchResponse response = new BatchResponse();
        response.setRequestId(requestId);
        response.setStatusCode(200);
        response.setBody(body);
        return response;
    }

    private static BatchRequest request(long requestId, long reference, String relativeUrl, String body) {
        final BatchRequest request = new BatchRequest();
        request.setRequestId(requestId);
        request.setReference(reference);
        request.setRelativeUrl(relativeUrl);
        request.setMethod("POST");
        request.setBody(body);
        return request;
    }

    @Test
    void parentReferenceStillResolvesInUrlAndBody() {
        final BatchResponse parent = response(1L, "{\"clientId\":11,\"resourceId\":11}");
        final BatchRequest child = request(2L, 1L, "clients/$.clientId?command=activate",
                "{\"activationDate\":\"01 November 2026\",\"clientId\":\"$.clientId\"}");

        final BatchRequest resolved = resolutionHelper.resoluteRequest(child, parent);

        assertEquals("clients/11?command=activate", resolved.getRelativeUrl());
        final JsonObject body = JsonParser.parseString(resolved.getBody()).getAsJsonObject();
        assertEquals(11L, body.get("clientId").getAsLong());
        assertEquals("01 November 2026", body.get("activationDate").getAsString());
    }

    @Test
    void ancestorReferencesResolveInsideNestedArrays() {
        final BatchResponse client = response(1L, "{\"clientId\":11,\"resourceId\":11}");
        final BatchResponse collateral1 = response(3L, "{\"clientId\":11,\"resourceId\":31}");
        final BatchResponse collateral2 = response(4L, "{\"clientId\":11,\"resourceId\":32}");
        final Map<Long, BatchResponse> ancestors = new HashMap<>();
        ancestors.put(1L, client);
        ancestors.put(3L, collateral1);
        ancestors.put(4L, collateral2);

        final BatchRequest loan = request(5L, 4L, "loans",
                "{\"clientId\":\"$ref[1].clientId\",\"collateral\":[{\"clientCollateralId\":\"$ref[3].resourceId\",\"quantity\":1},"
                        + "{\"clientCollateralId\":\"$ref[4].resourceId\",\"quantity\":2}],\"note\":\"costs $.50\","
                        + "\"tags\":[\"a\",7]}");

        final BatchRequest resolved = resolutionHelper.resoluteRequest(loan, collateral2, ancestors);

        final JsonObject body = JsonParser.parseString(resolved.getBody()).getAsJsonObject();
        assertEquals(11L, body.get("clientId").getAsLong());
        assertEquals(31L, body.getAsJsonArray("collateral").get(0).getAsJsonObject().get("clientCollateralId").getAsLong());
        assertEquals(32L, body.getAsJsonArray("collateral").get(1).getAsJsonObject().get("clientCollateralId").getAsLong());
        assertEquals(2, body.getAsJsonArray("collateral").get(1).getAsJsonObject().get("quantity").getAsInt());
        // free text that merely contains "$." is left untouched
        assertEquals("costs $.50", body.get("note").getAsString());
        // arrays of primitives are preserved
        assertEquals(2, body.getAsJsonArray("tags").size());
    }

    @Test
    void ancestorReferenceResolvesInUrl() {
        final Map<Long, BatchResponse> ancestors = new HashMap<>();
        ancestors.put(5L, response(5L, "{\"loanId\":77,\"resourceId\":77}"));
        final BatchResponse parent = response(6L, "{\"loanId\":77,\"resourceId\":77}");
        ancestors.put(6L, parent);

        final BatchRequest disburse = request(7L, 6L, "loans/$ref[5].loanId?command=disburse", "{\"transactionAmount\":1000}");

        assertEquals("loans/77?command=disburse", resolutionHelper.resoluteRequest(disburse, parent, ancestors).getRelativeUrl());
    }

    @Test
    void referenceToNonAncestorIsRejected() {
        final BatchResponse parent = response(1L, "{\"clientId\":11}");
        final BatchRequest child = request(2L, 1L, "loans", "{\"clientId\":\"$ref[9].clientId\"}");

        assertThrows(GeneralPlatformDomainRuleException.class, () -> resolutionHelper.resoluteRequest(child, parent));
    }
}
