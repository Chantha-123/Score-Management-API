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
package org.apache.fineract.portfolio.loanaccount.coborrower.command;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.loanaccount.coborrower.api.LoanCoBorrowerJsonInputParams;

@Getter
@RequiredArgsConstructor
public class LoanCoBorrowerCommand {

    private final Long clientId;

    public void validateForCreate() {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loanCoBorrower");

        baseDataValidator.reset().parameter(LoanCoBorrowerJsonInputParams.CLIENT_ID.getValue()).value(this.clientId).notNull()
                .longGreaterThanZero();

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                    dataValidationErrors);
        }
    }
}
