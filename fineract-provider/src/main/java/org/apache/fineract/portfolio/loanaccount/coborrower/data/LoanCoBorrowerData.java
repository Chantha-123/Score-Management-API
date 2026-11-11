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
package org.apache.fineract.portfolio.loanaccount.coborrower.data;

import java.io.Serializable;
import java.math.BigDecimal;
import org.apache.fineract.portfolio.loanaccount.data.LoanStatusEnumData;

/**
 * Co-borrower row. Used both for "co-borrowers of a loan" and "loans a client co-borrows".
 */
@SuppressWarnings("unused")
public final class LoanCoBorrowerData implements Serializable {

    private final Long id;
    private final Long loanId;
    private final Long clientId;
    private final String clientName;
    private final String clientAccountNo;
    private final String clientMobileNo;
    private final String loanAccountNo;
    private final String loanProductName;
    private final LoanStatusEnumData loanStatus;
    private final BigDecimal principal;
    private final String currencyCode;
    private final Long borrowerClientId;
    private final String borrowerName;

    public LoanCoBorrowerData(final Long id, final Long loanId, final Long clientId, final String clientName, final String clientAccountNo,
            final String clientMobileNo, final String loanAccountNo, final String loanProductName, final LoanStatusEnumData loanStatus,
            final BigDecimal principal, final String currencyCode, final Long borrowerClientId, final String borrowerName) {
        this.id = id;
        this.loanId = loanId;
        this.clientId = clientId;
        this.clientName = clientName;
        this.clientAccountNo = clientAccountNo;
        this.clientMobileNo = clientMobileNo;
        this.loanAccountNo = loanAccountNo;
        this.loanProductName = loanProductName;
        this.loanStatus = loanStatus;
        this.principal = principal;
        this.currencyCode = currencyCode;
        this.borrowerClientId = borrowerClientId;
        this.borrowerName = borrowerName;
    }

    public Long getId() {
        return this.id;
    }

    public Long getLoanId() {
        return this.loanId;
    }

    public Long getClientId() {
        return this.clientId;
    }
}
