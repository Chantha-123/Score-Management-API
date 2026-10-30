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
package org.apache.fineract.portfolio.loanaccount.batchprincipalpaydown.data;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.batchprincipalpaydown.BatchPrincipalPaydownApiConstants;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LoanBatchPrincipalPaydownDataValidator {

    private final FromJsonHelper fromJsonHelper;

    @Autowired
    public LoanBatchPrincipalPaydownDataValidator(final FromJsonHelper fromJsonHelper) {
        this.fromJsonHelper = fromJsonHelper;
    }

    public void validateForCreate(final JsonCommand command, final Loan loan) {

        final String json = command.json();
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeToken = new TypeToken<java.util.Map<String, Object>>() {}.getType();
        this.fromJsonHelper.checkForUnsupportedParameters(typeToken, json,
                BatchPrincipalPaydownApiConstants.CREATE_REQUEST_DATA_PARAMETERS);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder dataValidatorBuilder = new DataValidatorBuilder(dataValidationErrors)
                .resource("loan.batchprincipalpaydown");

        if (!loan.status().isActive()) {
            dataValidatorBuilder.reset().failWithCodeNoParameterAddedToErrorCode("loan.is.not.active", "Loan is not active");
        }

        final LocalDate transactionDate = command
                .localDateValueOfParameterNamed(BatchPrincipalPaydownApiConstants.transactionDateParamName);
        dataValidatorBuilder.reset().parameter(BatchPrincipalPaydownApiConstants.transactionDateParamName).value(transactionDate).notNull();
        if (transactionDate != null) {
            if (transactionDate.isAfter(DateUtils.getBusinessLocalDate())) {
                dataValidatorBuilder.reset().parameter(BatchPrincipalPaydownApiConstants.transactionDateParamName)
                        .failWithCode("in.the.future", "Transaction date cannot be in the future");
            }
            if (loan.getDisbursementDate() != null && transactionDate.isBefore(loan.getDisbursementDate())) {
                dataValidatorBuilder.reset().parameter(BatchPrincipalPaydownApiConstants.transactionDateParamName)
                        .failWithCode("before.loan.disbursement.date", "Transaction date cannot be before the loan disbursement date");
            }

            final long unpaidInstallmentsUpToDate = loan.getRepaymentScheduleInstallments().stream()
                    .filter(installment -> !installment.isObligationsMet() && !installment.getDueDate().isAfter(transactionDate)).count();
            if (unpaidInstallmentsUpToDate > 1) {
                dataValidatorBuilder.reset().parameter(BatchPrincipalPaydownApiConstants.transactionDateParamName).failWithCode(
                        "multiple.overdue.installments",
                        "Batch principal paydown requires at most one outstanding installment as of the transaction date; "
                                + "clear overdue installments first");
            }
        }

        final BigDecimal principalAmount = command
                .bigDecimalValueOfParameterNamed(BatchPrincipalPaydownApiConstants.principalAmountParamName);
        dataValidatorBuilder.reset().parameter(BatchPrincipalPaydownApiConstants.principalAmountParamName).value(principalAmount).notNull()
                .positiveAmount();

        if (principalAmount != null) {
            final BigDecimal totalPrincipalOutstanding = loan.getSummary().getTotalPrincipalOutstanding();
            if (totalPrincipalOutstanding == null || principalAmount.compareTo(totalPrincipalOutstanding) > 0) {
                dataValidatorBuilder.reset().parameter(BatchPrincipalPaydownApiConstants.principalAmountParamName)
                        .failWithCode("exceeds.outstanding.principal", "Principal amount cannot exceed the outstanding principal");
            }
        }

        final String note = command.stringValueOfParameterNamed(BatchPrincipalPaydownApiConstants.noteParamName);
        dataValidatorBuilder.reset().parameter(BatchPrincipalPaydownApiConstants.noteParamName).value(note).ignoreIfNull()
                .notExceedingLengthOf(1000);

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

}
