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
package org.apache.fineract.portfolio.loanaccount.coborrower.service;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.coborrower.command.LoanCoBorrowerCommand;
import org.apache.fineract.portfolio.loanaccount.coborrower.domain.LoanCoBorrower;
import org.apache.fineract.portfolio.loanaccount.coborrower.domain.LoanCoBorrowerRepository;
import org.apache.fineract.portfolio.loanaccount.coborrower.domain.LoanCoBorrowerRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.coborrower.exception.InvalidLoanCoBorrowerException;
import org.apache.fineract.portfolio.loanaccount.coborrower.exception.LoanCoBorrowerDuplicateException;
import org.apache.fineract.portfolio.loanaccount.coborrower.serialization.LoanCoBorrowerCommandFromApiJsonDeserializer;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanCoBorrowerWritePlatformServiceJpaRepositoryImpl implements LoanCoBorrowerWritePlatformService {

    private static final Logger LOG = LoggerFactory.getLogger(LoanCoBorrowerWritePlatformServiceJpaRepositoryImpl.class);

    private final LoanCoBorrowerRepository repository;
    private final LoanCoBorrowerRepositoryWrapper repositoryWrapper;
    private final LoanCoBorrowerCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final ClientRepositoryWrapper clientRepositoryWrapper;

    @Transactional
    @Override
    public CommandProcessingResult createLoanCoBorrower(final Long loanId, final JsonCommand command) {
        try {
            final LoanCoBorrowerCommand coBorrowerCommand = this.fromApiJsonDeserializer.commandFromApiJson(command.json());
            coBorrowerCommand.validateForCreate();

            final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);
            assertLoanAcceptsChanges(loan);

            final Long clientId = coBorrowerCommand.getClientId();
            final Client client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);

            if (clientId.equals(loan.getClientId())) {
                throw new InvalidLoanCoBorrowerException("is.loan.borrower", "The borrower cannot also be a co-borrower of the same loan.",
                        loanId, clientId);
            }
            if (!client.isActive()) {
                throw new InvalidLoanCoBorrowerException("client.not.active", "Client with identifier " + clientId + " is not active.",
                        loanId, clientId);
            }
            if (this.repository.existsByLoanIdAndClientId(loanId, clientId)) {
                throw new LoanCoBorrowerDuplicateException(loanId, clientId);
            }

            final LoanCoBorrower coBorrower = LoanCoBorrower.create(loan, client);
            this.repository.saveAndFlush(coBorrower);

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(coBorrower.getId())
                    .withLoanId(loanId).withClientId(clientId).withOfficeId(loan.getOfficeId()).build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(dve);
            return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult deleteLoanCoBorrower(final Long loanId, final Long coBorrowerId, final JsonCommand command) {
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);
        assertLoanAcceptsChanges(loan);

        final LoanCoBorrower coBorrower = this.repositoryWrapper.findOneWithNotFoundDetection(loanId, coBorrowerId);
        final Long clientId = coBorrower.getClient().getId();
        this.repository.delete(coBorrower);

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(coBorrowerId).withLoanId(loanId)
                .withClientId(clientId).withOfficeId(loan.getOfficeId()).build();
    }

    /**
     * Co-borrowers can be changed while the loan is pending, approved or active - not once it is
     * closed/rejected/withdrawn.
     */
    private void assertLoanAcceptsChanges(final Loan loan) {
        if (!loan.status().isActiveOrAwaitingApprovalOrDisbursal()) {
            throw new InvalidLoanCoBorrowerException("loan.not.open",
                    "Co-borrowers can only be changed on a pending, approved or active loan.", loan.getId(), null);
        }
    }

    private void handleDataIntegrityIssues(final NonTransientDataAccessException dve) {
        final Throwable realCause = dve.getMostSpecificCause();
        if (realCause.getMessage() != null && realCause.getMessage().contains("uq_m_loan_co_borrower_loan_client")) {
            throw new PlatformDataIntegrityException("error.msg.loan.coborrower.duplicate",
                    "This client is already a co-borrower of the loan.");
        }
        LOG.error("Error occurred.", dve);
        throw new PlatformDataIntegrityException("error.msg.loan.coborrower.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource Loan Co-Borrower: " + realCause.getMessage());
    }
}
