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
package org.apache.fineract.portfolio.loanaccount.coborrower.domain;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;

/**
 * A client named on a loan as co-borrower alongside the main borrower.
 */
@Entity
@Table(name = "m_loan_co_borrower", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "loan_id", "client_id" }, name = "uq_m_loan_co_borrower_loan_client") })
public class LoanCoBorrower extends AbstractAuditableWithUTCDateTimeCustom {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    protected LoanCoBorrower() {}

    private LoanCoBorrower(final Loan loan, final Client client) {
        this.loan = loan;
        this.client = client;
    }

    public static LoanCoBorrower create(final Loan loan, final Client client) {
        return new LoanCoBorrower(loan, client);
    }

    public Loan getLoan() {
        return this.loan;
    }

    public Client getClient() {
        return this.client;
    }
}
