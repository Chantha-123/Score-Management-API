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
package org.apache.fineract.portfolio.loanaccount.batchprincipalpaydown.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;

/**
 * Links a {@link LoanTransaction} of type BATCH_PRINCIPAL_PAYDOWN to the {@code m_loan_repayment_schedule_history}
 * version that was archived immediately before the paydown regenerated the schedule, so that an undo can locate the
 * exact schedule shape to restore.
 */
@Entity
@Table(name = "m_loan_batch_principal_paydown", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "loan_transaction_id" }, name = "uq_lbpp_transaction") })
public class LoanBatchPrincipalPaydownAudit extends AbstractPersistableCustom {

    @OneToOne(optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @OneToOne(optional = false)
    @JoinColumn(name = "loan_transaction_id", nullable = false)
    private LoanTransaction loanTransaction;

    @Column(name = "schedule_history_version", nullable = false)
    private Integer scheduleHistoryVersion;

    protected LoanBatchPrincipalPaydownAudit() {}

    private LoanBatchPrincipalPaydownAudit(final Loan loan, final LoanTransaction loanTransaction, final Integer scheduleHistoryVersion) {
        this.loan = loan;
        this.loanTransaction = loanTransaction;
        this.scheduleHistoryVersion = scheduleHistoryVersion;
    }

    public static LoanBatchPrincipalPaydownAudit instance(final Loan loan, final LoanTransaction loanTransaction,
            final Integer scheduleHistoryVersion) {
        return new LoanBatchPrincipalPaydownAudit(loan, loanTransaction, scheduleHistoryVersion);
    }

    public Loan getLoan() {
        return this.loan;
    }

    public LoanTransaction getLoanTransaction() {
        return this.loanTransaction;
    }

    public Integer getScheduleHistoryVersion() {
        return this.scheduleHistoryVersion;
    }

}
