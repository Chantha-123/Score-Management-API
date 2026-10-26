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
package org.apache.fineract.portfolio.loanaccount.batchprincipalpaydown.service;

import java.math.MathContext;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.batchprincipalpaydown.BatchPrincipalPaydownApiConstants;
import org.apache.fineract.portfolio.loanaccount.batchprincipalpaydown.data.LoanBatchPrincipalPaydownDataValidator;
import org.apache.fineract.portfolio.loanaccount.batchprincipalpaydown.domain.LoanBatchPrincipalPaydownAudit;
import org.apache.fineract.portfolio.loanaccount.batchprincipalpaydown.domain.LoanBatchPrincipalPaydownAuditRepository;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.LoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanScheduleDTO;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.DefaultPaymentPeriodsInOneYearCalculator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanApplicationTerms;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleGeneratorFactory;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.PaymentPeriodsInOneYearCalculator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleHistoryReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleHistoryWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanBatchPrincipalPaydownWritePlatformServiceImpl implements LoanBatchPrincipalPaydownWritePlatformService {

    private final LoanBatchPrincipalPaydownDataValidator dataValidator;
    private final LoanBatchPrincipalPaydownAuditRepository auditRepository;
    private final LoanAssembler loanAssembler;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final LoanUtilService loanUtilService;
    private final LoanScheduleGeneratorFactory loanScheduleFactory;
    private final LoanRepaymentScheduleTransactionProcessorFactory loanRepaymentScheduleTransactionProcessorFactory;
    private final LoanScheduleHistoryReadPlatformService loanScheduleHistoryReadPlatformService;
    private final LoanScheduleHistoryWritePlatformService loanScheduleHistoryWritePlatformService;
    private final LoanAccountDomainService loanAccountDomainService;
    private final PaymentDetailWritePlatformService paymentDetailWritePlatformService;

    @Transactional
    @Override
    public CommandProcessingResult batchPrincipalPaydown(final Long loanId, final JsonCommand command) {

        final Loan loan = this.loanAssembler.assembleFrom(loanId);
        this.dataValidator.validateForCreate(command, loan);

        final MonetaryCurrency currency = loan.getCurrency();
        final LocalDate transactionDate = command
                .localDateValueOfParameterNamed(BatchPrincipalPaydownApiConstants.transactionDateParamName);
        final Money principalAmount = Money.of(currency,
                command.bigDecimalValueOfParameterNamed(BatchPrincipalPaydownApiConstants.principalAmountParamName));
        final String noteText = command.stringValueOfParameterNamed(BatchPrincipalPaydownApiConstants.noteParamName);
        final String txnExternalId = command.stringValueOfParameterNamedAllowingNull(BatchPrincipalPaydownApiConstants.externalIdParamName);

        // Archive the current (unmutated) schedule first, so undo has an exact restore point.
        final Integer scheduleHistoryVersion = this.loanScheduleHistoryReadPlatformService.fetchCurrentVersionNumber(loan.getId()) + 1;
        this.loanScheduleHistoryWritePlatformService.createAndSaveLoanScheduleArchive(loan.getRepaymentScheduleInstallments(), loan, null);

        // 1. Accrued interest up to the payment date (foreclosure-style day-count proration).
        final LoanRepaymentScheduleInstallment foreCloseDetail = loan.fetchLoanForeclosureDetail(transactionDate);
        final Money accruedInterest = foreCloseDetail.getInterestCharged(currency);

        // 2. Truncate the in-progress installment to the payment date so it becomes the last retained period.
        final LoanRepaymentScheduleInstallment currentInstallment = loan.getRepaymentScheduleInstallments().stream()
                .filter(installment -> !installment.isObligationsMet()).findFirst()
                .orElseThrow(() -> new NoSuchElementException("No outstanding installment found for loan " + loanId));
        final int remainingInstallmentCount = (int) loan.getRepaymentScheduleInstallments().stream()
                .filter(installment -> installment.getInstallmentNumber() > currentInstallment.getInstallmentNumber()).count();
        currentInstallment.updateDueDate(transactionDate);
        currentInstallment.updateInterestCharged(accruedInterest.getAmount());
        // The truncated installment's principal must equal exactly principalAmount (not the original scheduled
        // principal, and not zero) so the incoming transaction (accruedInterest + principalAmount) is fully
        // absorbed by this one closed-out installment. If it were anything else, the repayment allocator would
        // either leave money unapplied or spill the remainder into the newly regenerated future installments,
        // double-counting principalAmount against the balance already reduced via setPrincipal(...) below.
        currentInstallment.updatePrincipal(principalAmount.getAmount());

        // 3. Reduce the outstanding balance and reamortize the remaining installments from the payment date.
        final Money newOutstandingPrincipal = Money.of(currency, loan.getSummary().getTotalPrincipalOutstanding()).minus(principalAmount);
        final ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, null);
        final LoanApplicationTerms loanApplicationTerms = loan.constructLoanApplicationTerms(scheduleGeneratorDTO);
        final MathContext mc = new MathContext(8, MoneyHelper.getRoundingMode());

        // rescheduleNextInstallments() replays the retained (already-paid + truncated) installments first, and its
        // own "last installment" true-up (LoanApplicationTerms#adjustPrincipalIfLastRepaymentPeriod /
        // adjustInterestIfLastRepaymentPeriod) compares cumulative principal/interest paid SINCE DISBURSEMENT
        // against loanApplicationTerms.getPrincipal(), firing when the (loan-wide, absolute) periodNumber reaches
        // actualNumberOfRepayments. Both of those totals must therefore stay the loan's TRUE original totals, or
        // that true-up fires at the wrong installment and the regenerated tail ends up one (or more) installments
        // short/long. So: derive the correct EMI for the reduced remaining balance/count in isolation first -
        // pmtForInstallment() caches it as a side effect via setFixedEmiAmount() - then restore principal/
        // numberOfRepayments to the loan's real totals before generating, so the generator's cumulative
        // bookkeeping stays correct and simply reuses the pre-computed EMI instead of deriving a wrong one from
        // the restored totals. Only equal-installment (fixed EMI) amortization uses that cached value; equal-
        // principal amortization recomputes its per-period share from the (correctly restored) totals directly.
        final Money originalTotalPrincipal = loanApplicationTerms.getPrincipal();
        final Integer originalTotalNumberOfRepayments = loanApplicationTerms.fetchNumberOfRepaymentsAfterExceptions();
        if (loanApplicationTerms.getAmortizationMethod().isEqualInstallment()) {
            final PaymentPeriodsInOneYearCalculator paymentPeriodsInOneYearCalculator = new DefaultPaymentPeriodsInOneYearCalculator();
            loanApplicationTerms.setPrincipal(newOutstandingPrincipal);
            loanApplicationTerms.updateNumberOfRepayments(remainingInstallmentCount);
            loanApplicationTerms.pmtForInstallment(paymentPeriodsInOneYearCalculator, newOutstandingPrincipal, 1, mc);
            loanApplicationTerms.setPrincipal(originalTotalPrincipal);
            loanApplicationTerms.updateNumberOfRepayments(originalTotalNumberOfRepayments);
        }

        final LoanScheduleGenerator loanScheduleGenerator = this.loanScheduleFactory.create(loanApplicationTerms.getInterestMethod());
        final LoanRepaymentScheduleTransactionProcessor loanRepaymentScheduleTransactionProcessor = this.loanRepaymentScheduleTransactionProcessorFactory
                .determineProcessor(loan.transactionProcessingStrategy());
        final LoanScheduleDTO loanSchedule = loanScheduleGenerator.rescheduleNextInstallments(mc, loanApplicationTerms, loan,
                loanApplicationTerms.getHolidayDetailDTO(), loanRepaymentScheduleTransactionProcessor, transactionDate.plusDays(1));

        // Update the future installments in place on the SAME persisted rows rather than calling
        // loan.updateLoanSchedule(Collection), which clears-and-rebuilds the installment collection: since that
        // drops the old installment entities and inserts brand-new ones, it deletes rows that
        // m_loan_transaction_repayment_schedule_mapping / m_loan_overdue_installment_charge may still reference
        // via RESTRICT foreign keys (e.g. an installment that already received an early/advance payment), causing
        // a constraint violation on flush. Mutating the existing rows keeps their ids stable, so no delete happens.
        final Map<Integer, LoanRepaymentScheduleInstallment> regeneratedByInstallmentNumber = loanSchedule.getInstallments().stream()
                .filter(installment -> installment.getInstallmentNumber() > currentInstallment.getInstallmentNumber())
                .collect(Collectors.toMap(LoanRepaymentScheduleInstallment::getInstallmentNumber, installment -> installment));
        for (final LoanRepaymentScheduleInstallment existingInstallment : loan.getRepaymentScheduleInstallments()) {
            final LoanRepaymentScheduleInstallment regenerated = regeneratedByInstallmentNumber
                    .get(existingInstallment.getInstallmentNumber());
            if (regenerated == null) {
                continue;
            }
            existingInstallment.updateFromDate(regenerated.getFromDate());
            existingInstallment.updateDueDate(regenerated.getDueDate());
            existingInstallment.updatePrincipal(regenerated.getPrincipal(currency).getAmount());
            existingInstallment.updateInterestCharged(regenerated.getInterestCharged(currency).getAmount());
        }
        // Refresh maturity-date fields now that installment due dates have shifted; totals (LoanSummary) are
        // recalculated as a normal side effect of the makeRepayment() call below, which replays the full
        // repayment allocation and always refreshes them at the end.
        loan.updateLoanScheduleDependentDerivedFields();
        loan.recalculateAllCharges();
        this.loanRepositoryWrapper.saveAndFlush(loan);

        // 4. Collect the accrued interest + principal batch through the exact normal-repayment path.
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("locale", command.locale());
        changes.put("dateFormat", command.dateFormat());
        changes.put(BatchPrincipalPaydownApiConstants.transactionDateParamName,
                command.stringValueOfParameterNamed(BatchPrincipalPaydownApiConstants.transactionDateParamName));
        changes.put(BatchPrincipalPaydownApiConstants.principalAmountParamName,
                command.stringValueOfParameterNamed(BatchPrincipalPaydownApiConstants.principalAmountParamName));

        final PaymentDetail paymentDetail = this.paymentDetailWritePlatformService.createAndPersistPaymentDetail(command, changes);
        final Money totalCollected = accruedInterest.plus(principalAmount);
        final CommandProcessingResultBuilder commandProcessingResultBuilder = new CommandProcessingResultBuilder();

        final LoanTransaction loanTransaction = this.loanAccountDomainService.makeRepayment(LoanTransactionType.BATCH_PRINCIPAL_PAYDOWN,
                loan, commandProcessingResultBuilder, transactionDate, totalCollected.getAmount(), paymentDetail, noteText, txnExternalId,
                false, false, null, false);

        // 5. Link the new transaction to the archived schedule version, so an undo knows exactly what to restore.
        this.auditRepository
                .saveAndFlush(LoanBatchPrincipalPaydownAudit.instance(loan, loanTransaction, scheduleHistoryVersion));

        return commandProcessingResultBuilder.withCommandId(command.commandId()).withLoanId(loanId).with(changes).build();
    }

}
