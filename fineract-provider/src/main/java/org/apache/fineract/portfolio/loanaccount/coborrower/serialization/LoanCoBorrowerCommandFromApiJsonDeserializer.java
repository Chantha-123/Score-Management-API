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
package org.apache.fineract.portfolio.loanaccount.coborrower.serialization;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.serialization.AbstractFromApiJsonDeserializer;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.coborrower.api.LoanCoBorrowerJsonInputParams;
import org.apache.fineract.portfolio.loanaccount.coborrower.command.LoanCoBorrowerCommand;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class LoanCoBorrowerCommandFromApiJsonDeserializer extends AbstractFromApiJsonDeserializer<LoanCoBorrowerCommand> {

    private final FromJsonHelper fromApiJsonHelper;

    @Override
    public LoanCoBorrowerCommand commandFromApiJson(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, LoanCoBorrowerJsonInputParams.getAllValues());

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final Long clientId = this.fromApiJsonHelper.extractLongNamed(LoanCoBorrowerJsonInputParams.CLIENT_ID.getValue(), element);

        return new LoanCoBorrowerCommand(clientId);
    }
}
