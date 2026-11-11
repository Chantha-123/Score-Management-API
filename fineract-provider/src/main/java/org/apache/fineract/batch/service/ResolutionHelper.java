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

import com.google.common.base.Splitter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.batch.domain.BatchRequest;
import org.apache.fineract.batch.domain.BatchResponse;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Component;

/**
 * Provides methods to create dependency map among the various batchRequests. It also provides method that takes care of
 * dependency resolution among related requests.
 *
 * @author Rishabh Shukla
 * @see BatchApiServiceImpl
 */
@Component
@RequiredArgsConstructor
public class ResolutionHelper {

    /**
     * Provides a Node like object for the request tree.
     *
     * @author Rishabh shukla
     *
     */
    public static class BatchRequestNode {

        private BatchRequest request;
        private final List<BatchRequestNode> childRequests = new ArrayList<>();

        public BatchRequestNode() {

        }

        public BatchRequest getRequest() {
            return this.request;
        }

        public void setRequest(BatchRequest request) {
            this.request = request;
        }

        public List<BatchRequestNode> getChildRequests() {
            return this.childRequests;
        }

        public void addChildRequest(final BatchRequestNode batchRequest) {
            this.childRequests.add(batchRequest);
        }

    }

    public static final String PARENT_REFERENCE_PREFIX = "$.";
    public static final String ANCESTOR_REFERENCE_PREFIX = "$ref[";
    private static final Pattern ANCESTOR_REFERENCE = Pattern.compile("^\\$ref\\[(\\d+)\\]\\.(.+)$");

    private final FromJsonHelper fromJsonHelper;

    /**
     * Returns a map containing requests that are divided in accordance of dependency relations among them. Each
     * different list is identified with a "Key" which is the "requestId" of the request at topmost level in dependency
     * hierarchy of that particular list.
     *
     * @param batchRequests
     * @return List&lt;ArrayList&lt;BatchRequestNode&gt;&gt;
     */
    public List<BatchRequestNode> getDependingRequests(final List<BatchRequest> batchRequests) {
        final List<BatchRequestNode> rootRequests = new ArrayList<>();

        for (BatchRequest batchRequest : batchRequests) {
            if (batchRequest.getReference() == null) {
                final BatchRequestNode node = new BatchRequestNode();
                node.setRequest(batchRequest);
                rootRequests.add(node);
            } else {
                this.addDependingRequest(batchRequest, rootRequests);
            }
        }

        return rootRequests;
    }

    private void addDependingRequest(final BatchRequest batchRequest, final List<BatchRequestNode> parentRequests) {
        for (BatchRequestNode batchRequestNode : parentRequests) {
            if (batchRequestNode.getRequest().getRequestId().equals(batchRequest.getReference())) {
                final BatchRequestNode dependingRequest = new BatchRequestNode();
                dependingRequest.setRequest(batchRequest);
                batchRequestNode.addChildRequest(dependingRequest);
            } else {
                addDependingRequest(batchRequest, batchRequestNode.getChildRequests());
            }
        }
    }

    /**
     * Returns a BatchRequest after dependency resolution. It takes a request and the response of the request it is
     * dependent upon as its arguments and change the body or relativeUrl of the request according to parent Request.
     *
     * @param request
     * @param parentResponse
     * @return BatchRequest
     */
    public BatchRequest resoluteRequest(final BatchRequest request, final BatchResponse parentResponse) {
        final Map<Long, BatchResponse> ancestorResponses = new HashMap<>();
        if (parentResponse.getRequestId() != null) {
            ancestorResponses.put(parentResponse.getRequestId(), parentResponse);
        }
        return resoluteRequest(request, parentResponse, ancestorResponses);
    }

    /**
     * Resolves dependency expressions in the body and relativeUrl of a request.
     * <ul>
     * <li>{@code $.<jsonPath>} reads from the direct parent response (original behaviour).</li>
     * <li>{@code $ref[<requestId>].<jsonPath>} reads from the response of any ancestor request in the dependency chain
     * (e.g. a loan application referring to both the created client and each created client collateral).</li>
     * </ul>
     *
     * @param request
     *            the request to resolve
     * @param parentResponse
     *            response of the direct parent request
     * @param ancestorResponses
     *            successful responses of every ancestor request (including the parent), keyed by requestId
     * @return the resolved request
     */
    public BatchRequest resoluteRequest(final BatchRequest request, final BatchResponse parentResponse,
            final Map<Long, BatchResponse> ancestorResponses) {

        final Map<Long, ReadContext> contexts = new HashMap<>();
        final ReadContext parentCtx = JsonPath.parse(parentResponse.getBody());

        final JsonElement requestBody = this.fromJsonHelper.parse(request.getBody());
        if (requestBody != null) {
            request.setBody(resolveElement(requestBody, parentCtx, ancestorResponses, contexts).toString());
        }

        String relativeUrl = request.getRelativeUrl();
        if (relativeUrl.contains(PARENT_REFERENCE_PREFIX) || relativeUrl.contains(ANCESTOR_REFERENCE_PREFIX)) {
            String queryParams = "";
            if (relativeUrl.contains("?")) {
                queryParams = relativeUrl.substring(relativeUrl.indexOf("?"));
                relativeUrl = relativeUrl.substring(0, relativeUrl.indexOf("?"));
            }

            final List<String> resolvedSegments = new ArrayList<>();
            for (String segment : Splitter.on('/').split(relativeUrl)) {
                if (isReferenceExpression(segment)) {
                    resolvedSegments.add(readReference(segment, parentCtx, ancestorResponses, contexts).toString());
                } else {
                    resolvedSegments.add(segment);
                }
            }
            request.setRelativeUrl(String.join("/", resolvedSegments) + queryParams);
        }

        return request;
    }

    private JsonElement resolveElement(final JsonElement element, final ReadContext parentCtx,
            final Map<Long, BatchResponse> ancestorResponses, final Map<Long, ReadContext> contexts) {
        if (element.isJsonObject()) {
            final JsonObject resolved = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                resolved.add(entry.getKey(), resolveElement(entry.getValue(), parentCtx, ancestorResponses, contexts));
            }
            return resolved;
        }
        if (element.isJsonArray()) {
            final JsonArray resolved = new JsonArray();
            for (JsonElement item : element.getAsJsonArray()) {
                resolved.add(resolveElement(item, parentCtx, ancestorResponses, contexts));
            }
            return resolved;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && isReferenceExpression(element.getAsString())) {
            final Object value = readReference(element.getAsString(), parentCtx, ancestorResponses, contexts);
            return this.fromJsonHelper.parse(value.toString());
        }
        return element;
    }

    private static boolean isReferenceExpression(final String value) {
        return value.startsWith(PARENT_REFERENCE_PREFIX) || value.startsWith(ANCESTOR_REFERENCE_PREFIX);
    }

    private Object readReference(final String expression, final ReadContext parentCtx, final Map<Long, BatchResponse> ancestorResponses,
            final Map<Long, ReadContext> contexts) {
        if (expression.startsWith(PARENT_REFERENCE_PREFIX)) {
            return parentCtx.read(expression);
        }
        final Matcher matcher = ANCESTOR_REFERENCE.matcher(expression);
        if (!matcher.matches()) {
            throw new GeneralPlatformDomainRuleException("error.msg.batch.reference.invalid",
                    "Invalid batch reference expression `" + expression + "`, expected $ref[<requestId>].<path>", expression);
        }
        final Long requestId = Long.valueOf(matcher.group(1));
        final BatchResponse ancestor = ancestorResponses.get(requestId);
        if (ancestor == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.batch.reference.not.ancestor",
                    "Batch reference `" + expression + "` must point to an ancestor request in the same dependency chain", expression);
        }
        final ReadContext ctx = contexts.computeIfAbsent(requestId, id -> JsonPath.parse(ancestor.getBody()));
        return ctx.read("$." + matcher.group(2));
    }

}
