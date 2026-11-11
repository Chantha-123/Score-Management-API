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
package org.apache.fineract.portfolio.collateral.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.collateral.api.CollateralApiConstants.CollateralJSONinputParams;

/**
 * Immutable command for creating or updating details of a loan collateral (pawned item).
 */
@Getter
@RequiredArgsConstructor
public class CollateralCommand {

    private final Long collateralTypeId;
    private final BigDecimal value;
    private final String description;
    private final BigDecimal bookingAmount;
    private final BigDecimal evaluationAmount;
    private final LocalDate issuedOnDate;
    private final LocalDate expiredOnDate;

    public void validateForCreate() {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();

        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("collateral");

        baseDataValidator.reset().parameter(CollateralJSONinputParams.COLLATERAL_TYPE_ID.getValue()).value(this.collateralTypeId).notNull()
                .integerGreaterThanZero();
        baseDataValidator.reset().parameter(CollateralJSONinputParams.VALUE.getValue()).value(this.value).ignoreIfNull().positiveAmount();
        baseDataValidator.reset().parameter(CollateralJSONinputParams.DESCRIPTION.getValue()).value(this.description).ignoreIfNull()
                .notExceedingLengthOf(500);
        baseDataValidator.reset().parameter(CollateralJSONinputParams.BOOKING_AMOUNT.getValue()).value(this.bookingAmount).notNull()
                .positiveAmount();
        baseDataValidator.reset().parameter(CollateralJSONinputParams.EVALUATION_AMOUNT.getValue()).value(this.evaluationAmount).notNull()
                .positiveAmount();
        baseDataValidator.reset().parameter(CollateralJSONinputParams.ISSUED_ON_DATE.getValue()).value(this.issuedOnDate).notNull();
        validateExpiry(baseDataValidator, this.issuedOnDate, this.expiredOnDate);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateForUpdate() {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();

        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("collateral");

        baseDataValidator.reset().parameter(CollateralJSONinputParams.COLLATERAL_TYPE_ID.getValue()).value(this.collateralTypeId)
                .ignoreIfNull().integerGreaterThanZero();
        baseDataValidator.reset().parameter(CollateralJSONinputParams.VALUE.getValue()).value(this.value).ignoreIfNull().positiveAmount();
        baseDataValidator.reset().parameter(CollateralJSONinputParams.DESCRIPTION.getValue()).value(this.description).ignoreIfNull()
                .notExceedingLengthOf(500);
        baseDataValidator.reset().parameter(CollateralJSONinputParams.BOOKING_AMOUNT.getValue()).value(this.bookingAmount).ignoreIfNull()
                .positiveAmount();
        baseDataValidator.reset().parameter(CollateralJSONinputParams.EVALUATION_AMOUNT.getValue()).value(this.evaluationAmount)
                .ignoreIfNull().positiveAmount();

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    /**
     * Expiry must fall after the issue date. Used on create and, with the merged entity values, after an update.
     */
    public static void validateExpiry(final DataValidatorBuilder baseDataValidator, final LocalDate issuedOnDate,
            final LocalDate expiredOnDate) {
        if (issuedOnDate != null && expiredOnDate != null) {
            baseDataValidator.reset().parameter(CollateralJSONinputParams.EXPIRED_ON_DATE.getValue()).value(expiredOnDate)
                    .validateDateAfter(issuedOnDate);
        }
    }

    public static void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                    dataValidationErrors);
        }
    }
}
