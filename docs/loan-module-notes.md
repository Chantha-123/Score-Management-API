# Loan Module — Architecture Notes

Internal reference notes on the loan module's infrastructure, DB schema, and coding flow — covering interest calculation, repayment allocation, accrual accounting, and journal-entry posting. Written from direct code inspection of this fork (branch `mini_pawn_shop_api_v2.0.0`), which extends stock Apache Fineract with custom interest methods.

## 1. Package layout

```
org.apache.fineract.portfolio.loanaccount
├── domain/                      Loan, LoanTransaction, LoanRepaymentScheduleInstallment (JPA entities)
├── domain/transactionprocessor/ repayment allocation strategy pattern
├── loanschedule/domain/         schedule generation engine (interest math lives here)
├── loanschedule/service/        LoanScheduleAssembler — builds terms, picks generator
└── service/                     LoanWritePlatformServiceJpaRepositoryImpl, LoanAccrualWritePlatformServiceImpl

org.apache.fineract.portfolio.loanproduct
└── domain/                      LoanProduct, LoanProductRelatedDetail, InterestMethod

org.apache.fineract.accounting
├── journalentry/service/        AccountingProcessorForLoan (Cash/Accrual variants), AccountingProcessorHelper
└── producttoaccountmapping/     ProductToGLAccountMapping (acc_product_mapping)
```

## 2. Interest calculation — strategy pattern

**Request flow** (create loan / preview schedule):
`LoansApiResource.calculateLoanScheduleOrSubmitLoanApplication()` → `CommandWrapperBuilder` → `LoanApplicationWritePlatformServiceJpaRepositoryImpl` → `LoanScheduleAssembler.assembleLoanTerms()` builds a `LoanApplicationTerms` → `LoanScheduleAssembler.assembleLoanScheduleFrom()` (lines 664-694) picks a generator via `LoanScheduleGeneratorFactory.create(interestMethod)` → `generator.generate(...)`.

**`InterestMethod` enum** (`loanproduct/domain/InterestMethod.java`) — this fork extends stock Fineract's `DECLINING_BALANCE`/`FLAT` with custom `BALLOON`, `SEMI_BALOON`, `AMORTIZATION`, `AMORTIZATION_FEE` (values 0-6).

**`DefaultLoanScheduleGeneratorFactory`** — switch mapping `InterestMethod` → a `LoanScheduleGenerator`:
- `FLAT` → `FlatInterestLoanScheduleGenerator`
- `DECLINING_BALANCE`, `AMORTIZATION`, `BALLOON`, `AMORTIZATION_FEE` → `DecliningBalanceInterestLoanScheduleGenerator`
- `SEMI_BALOON` → `SimiBalloonBalanceInterestLoanScheduleGenerator` (a near copy-paste of the declining-balance generator — this is the established template for adding a new interest method)

Each generator overrides `calculatePrincipalInterestComponentsForPeriod()` (in `AbstractLoanScheduleGenerator`), which per-period delegates to the two real math dispatch points on `LoanApplicationTerms`:
- `calculateTotalInterestForPeriod()` — per-period interest, switch on `interestMethod`
- `calculateTotalPrincipalForPeriod()` — per-period principal, switch on `interestMethod` (and `amortizationMethod`: EQUAL_INSTALLMENTS uses a `pmt`-style formula, EQUAL_PRINCIPAL divides principal evenly)

Core declining-balance formula (`LoanApplicationTerms:1513-1523`):
```
interestDue = outstandingBalance × periodicInterestRate
```
where `periodicInterestRate` derives from `annualNominalInterestRate`, `daysInMonthType`, `daysInYearType`.

**Two-pass equal-amortization mechanism** (`LoanScheduleAssembler.assembleLoanScheduleFrom`, lines 675-688): when `isEqualAmortization()` is true and the method is declining-balance or annuity-fee, the assembler first runs a full `DECLINING_BALANCE` pass just to compute `totalInterestDue`, stores it on `loanApplicationTerms`, then re-runs the real generator — which picks up the precomputed total via the FLAT-like averaging branch in `LoanApplicationTerms:809`.

### Gotchas (verified directly)

1. **`@Enumerated(EnumType.ORDINAL)`** — `LoanProductRelatedDetail.interestMethod` (and `interestPeriodFrequencyType`, `interestCalculationPeriodMethod`) persist the enum's **declaration position**, not its `value` field. New `InterestMethod` constants **must be appended at the end** — never inserted mid-list — or existing stored loan products will silently remap to the wrong method.
2. **Validation gap**: `LoanProductDataValidator.java:263` still validates `interestType` with `.inMinMaxRange(0, 1)`, even though `InterestMethod` supports 0-6. Creating a loan product with `interestType` 3-6 (BALLOON/SEMI_BALOON/AMORTIZATION/AMORTIZATION_FEE) through the standard `POST /loanproducts` API currently fails validation — these products must exist via another path (direct DB seed, or the check needs updating).
3. **DB level**: `m_product_loan.interest_method_enum` / `m_loan.interest_method_enum` are plain `SMALLINT` with no CHECK constraint — the DB happily stores 2-6, only the app-layer validator is behind.

### Checklist — adding/modifying an interest method

1. Append a new constant to `InterestMethod.java` (+ `fromInt()` case) — append only (gotcha #1).
2. Update `LoanProductDataValidator.java:263` range check (and the update-path check ~line 760).
3. Add a case to `LoanEnumerations.interestType()` for API-facing labels.
4. Implement the math: add a case in `LoanApplicationTerms.calculateTotalInterestForPeriod()` / `.calculateTotalPrincipalForPeriod()` / `.calculateTotalInterestCharged()`, or fork a new `LoanScheduleGenerator` subclass (follow `SimiBalloonBalanceInterestLoanScheduleGenerator`).
5. Wire the new method into `DefaultLoanScheduleGeneratorFactory.create()`.
6. No DB migration needed unless the method requires genuinely new fields (none of the existing custom methods did).

## 3. DB schema (loan domain)

Defined in `db/changelog/tenant/parts/0001_initial_schema.xml` (the master Liquibase schema — no separate migration files exist for the custom interest methods; they reuse the stock schema entirely).

| Table | Key columns |
|---|---|
| `m_loan` (line 1995) | `interest_method_enum` (SMALLINT), `amortization_method_enum`, `annual_nominal_interest_rate`, `interest_charged_derived`/`interest_repaid_derived`/`interest_waived_derived`/`interest_outstanding_derived`, `accrued_till` |
| `m_product_loan` (line 3088) | same interest/amortization fields as product defaults; `accounting_type` (SMALLINT, `AccountingRuleType`); `loan_transaction_strategy_id` (FK — selects repayment allocation strategy) |
| `m_loan_repayment_schedule` (line 2498) | `duedate`, `installment`, `principal_amount`/`interest_amount`/`fee_charges_amount`/`penalty_charges_amount` + `*_completed_derived`/`*_waived_derived`/`*_writtenoff_derived` variants, `accrual_interest_derived`/`accrual_fee_charges_derived`/`accrual_penalty_charges_derived`, `completed_derived` |
| `m_loan_transaction` | `transaction_type_enum`, `amount`, `principal_portion_derived`/`interest_portion_derived`/`fee_charges_portion_derived`/`penalty_charges_portion_derived`, `is_reversed` |
| `m_loan_term_variations` (line 2628) | `term_type`, `applicable_date`, `decimal_value`, `date_value`, `is_active` |
| `acc_product_mapping` | `product_id`, `product_type` (LOAN=1), `financial_account_type`, `payment_type`, `charge_id`, `gl_account_id` — unique per (product, type, account-type, payment-type) |
| `acc_gl_journal_entry` | `office_id`, `account_id`, `transaction_id`, `loan_transaction_id`, `type_enum` (DEBIT/CREDIT), `amount`, `reversed`, `entity_type_enum`/`entity_id` |

## 4. Repayment allocation

`LoanRepaymentScheduleTransactionProcessor` (strategy pattern, selected via `m_product_loan.loan_transaction_strategy_id`):
- `FineractStyleLoanRepaymentScheduleTransactionProcessor` (default) — allocates each payment in order **penalties → fees → interest → principal** (`handleTransactionThatIsOnTimePaymentOfInstallment`, lines 116-129). Early and late payments are treated the same as on-time.
- Other strategies exist for different institutions' conventions (`HeavensFamily`, `Creocore`, `RBI`, `EarlyPayment`, `InterestPrincipalPenaltyFeesOrder`, `PrincipalInterestPenaltyFeesOrder`).
- Produces `LoanTransactionToRepaymentScheduleMapping` rows per installment and calls `loanTransaction.updateComponents(principal, interest, fees, penalties)`.

After the transaction is persisted, `LoanWritePlatformServiceJpaRepositoryImpl.postJournalEntries()` (line 2295) runs **synchronously inline** (no async/event bus): builds `Loan.deriveAccountingBridgeData(...)` (a `Map<String,Object>`) and calls `journalEntryWritePlatformService.createJournalEntriesForLoan(accountingBridgeData)`.

## 5. Accrual accounting

`LoanAccrualWritePlatformServiceImpl` (`loanaccount/service/`):
- Two Quartz-style scheduled jobs via `@CronTarget(jobName = JobName.ADD_ACCRUAL_ENTRIES / ADD_PERIODIC_ACCRUAL_ENTRIES)`.
- Only runs for products with `accounting_type = ACCRUAL_PERIODIC` (3) — cash-basis products are filtered out entirely and never generate `ACCRUAL` transactions.
- Computes pro-rata daily interest/fee/penalty from `m_loan_repayment_schedule`, tracks progress via `m_loan.accrued_till`.
- **Inserts the `ACCRUAL` transaction (`transaction_type_enum = 10`) via a raw JDBC `INSERT INTO m_loan_transaction`** (line 269) rather than through the JPA `Loan` aggregate — a deliberate performance shortcut for batch jobs, but it bypasses normal entity-level invariants. Worth remembering before touching accrual-transaction creation.
- Each accrual insert is immediately followed by the same `journalEntryWritePlatformService.createJournalEntriesForLoan(...)` bridge call.

`LoanTransactionType.ACCRUAL = 10` (`LoanTransactionType.java:41`).

## 6. Journal-entry / GL posting bridge

`JournalEntryWritePlatformServiceJpaRepositoryImpl.createJournalEntriesForLoan()` (line 472) reads three accounting-rule booleans off the bridge map, builds a `LoanDTO`, and hands it to `AccountingProcessorForLoanFactory.determineProcessor(loanDTO)` — a strategy pick between `CashBasedAccountingProcessorForLoan` and `AccrualBasedAccountingProcessorForLoan` based on `AccountingRuleType` (`NONE`/`CASH_BASED`/`ACCRUAL_PERIODIC`/`ACCRUAL_UPFRONT`).

**GL account resolution**: `AccountingProcessorHelper.getLinkedGLAccountForLoanProduct()` (line 1090) looks up `ProductToGLAccountMapping` (`acc_product_mapping`, keyed by `product_id` + `product_type=LOAN` + `financial_account_type`), with a payment-type-specific override for Fund Source and a charge-specific override for fee/penalty income accounts.

### Cash-basis (`CashBasedAccountingProcessorForLoan`)

| Transaction | Debit | Credit |
|---|---|---|
| Disbursement | Loan Portfolio | Fund Source |
| Repayment | Fund Source (single total) | Loan Portfolio (principal), Interest on Loans (interest), Income from Fees, Income from Penalties, Overpayment |
| Write-off | Losses Written Off | Loan Portfolio (principal only — interest/fee write-off not booked in cash basis) |
| Recovery repayment | Fund Source | Income from Recovery |

Reversals flip debit ↔ credit.

### Accrual-basis (`AccrualBasedAccountingProcessorForLoan`) — full double-entry lifecycle

Uses three extra GL account types beyond the cash-basis set: `INTEREST_RECEIVABLE`, `FEES_RECEIVABLE`, `PENALTIES_RECEIVABLE`.

1. **At accrual time** (`createJournalEntriesForAccruals`, line 351): **debit** Interest/Fees/Penalties Receivable, **credit** Interest on Loans / Income from Fees / Income from Penalties — income is recognized immediately, before cash is collected.
2. **At repayment time** (`createJournalEntriesForRepaymentsAndWriteOffs`, line 175): interest/fee/penalty portions **credit the Receivable accounts** (clearing what was already recognized at accrual), *not* the income accounts again — this avoids double-counting income between accrual and collection. Principal still credits Loan Portfolio; a single debit hits Fund Source.

## 7. Open questions / not yet explored

- Loan-to-loan transfers and credit balance refund accounting paths (briefly touched, not traced in depth).
- Interest recalculation on repayment (`LoanScheduleAssembler.assembleForInterestRecalculation()`) — confirmed it re-invokes the same generator factory, not deeply traced beyond that.
- `ACCRUAL_UPFRONT` behavior specifically (vs `ACCRUAL_PERIODIC`) — not yet distinguished in the accounting processor code read so far.
