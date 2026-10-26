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
package org.apache.fineract.portfolio.loanaccount.batchprincipalpaydown;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.fineract.portfolio.paymentdetail.PaymentDetailConstants;

public final class BatchPrincipalPaydownApiConstants {

    private BatchPrincipalPaydownApiConstants() {

    }

    public static final String ENTITY_NAME = "LOAN";
    public static final String BATCHPRINCIPALPAYDOWN_ACTION_NAME = "BATCHPRINCIPALPAYDOWN";

    public static final String localeParamName = "locale";
    public static final String dateFormatParamName = "dateFormat";
    public static final String transactionDateParamName = "transactionDate";
    public static final String principalAmountParamName = "principalAmount";
    public static final String noteParamName = "note";
    public static final String externalIdParamName = "externalId";

    public static final Set<String> CREATE_REQUEST_DATA_PARAMETERS = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList(localeParamName, dateFormatParamName, transactionDateParamName,
                    principalAmountParamName, noteParamName, externalIdParamName, PaymentDetailConstants.paymentTypeParamName,
                    PaymentDetailConstants.accountNumberParamName, PaymentDetailConstants.checkNumberParamName,
                    PaymentDetailConstants.routingCodeParamName, PaymentDetailConstants.receiptNumberParamName,
                    PaymentDetailConstants.bankNumberParamName)));
}
