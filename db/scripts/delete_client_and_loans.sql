-- ============================================================
-- DELETE CLIENT ACCOUNT, LOAN ACCOUNTS, AND ALL RELATED DATA
-- Apache Fineract Database Cleanup Script
--
-- Tables covered:
--   Client:  m_client, m_client_charge, m_client_transaction,
--            m_client_charge_paid_by, m_client_address,
--            m_client_identifier, m_family_members,
--            m_client_non_person, m_client_transfer_details,
--            m_client_attendance, m_client_collateral_management,
--            m_group_client, m_note (client)
--
--   Loan:    m_loan, m_loan_transaction, m_loan_repayment_schedule,
--            m_loan_repayment_schedule_history,
--            m_loan_charge, m_loan_charge_paid_by,
--            m_loan_installment_charge, m_loan_overdue_installment_charge,
--            m_loan_disbursement_detail, m_loan_tranche_disbursement_charge,
--            m_loan_tranche_charges, m_loan_term_variations,
--            m_loan_officer_assignment_history, m_loan_topup,
--            m_loan_recalculation_details,
--            m_loan_interest_recalculation_additional_details,
--            m_loan_reschedule_request,
--            m_loan_reschedule_request_term_variations_mapping,
--            m_loan_collateral, m_loan_collateral_management,
--            m_repayment_with_post_dated_checks,
--            m_loan_transaction_repayment_schedule_mapping,
--            m_guarantor, m_guarantor_funding_details,
--            m_guarantor_transaction,
--            m_portfolio_account_associations,
--            acc_gl_journal_entry (loan transaction entries),
--            m_note (loan + loan transaction)
--
-- Usage:
--   Set @client_id to the target client's primary key before running.
--   Run inside a transaction so you can ROLLBACK if anything looks wrong.
--
-- Deletion order strictly respects FK dependency chains.
-- ============================================================

START TRANSACTION;

SET @client_id = 1; -- <-- CHANGE THIS to the target client ID

-- ============================================================
-- PHASE 1: Notes for this client's loan transactions
--   Must precede loan-transaction deletion (FK: loan_transaction_id).
-- ============================================================

DELETE n
FROM   m_note n
INNER  JOIN m_loan_transaction lt ON lt.id = n.loan_transaction_id
WHERE  lt.loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 2: Guarantor transaction records
--   FK chain: m_guarantor_transaction → m_guarantor_funding_details
--             → m_guarantor → m_loan
--   Also references m_loan_transaction (nullable FK) — cleared here
--   before loan transactions are deleted.
-- ============================================================

DELETE gt
FROM   m_guarantor_transaction gt
INNER  JOIN m_guarantor_funding_details gfd ON gfd.id = gt.guarantor_fund_detail_id
INNER  JOIN m_guarantor g                   ON g.id  = gfd.guarantor_id
WHERE  g.loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 3: Accounting journal entries for loan transactions
--   FK: acc_gl_journal_entry.loan_transaction_id → m_loan_transaction
-- ============================================================

DELETE FROM acc_gl_journal_entry
WHERE  loan_transaction_id IN (
    SELECT id FROM m_loan_transaction
    WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id)
);

-- ============================================================
-- PHASE 4: Loan charge paid-by records
--   FK: m_loan_charge_paid_by.loan_transaction_id → m_loan_transaction
-- ============================================================

DELETE lcpb
FROM   m_loan_charge_paid_by lcpb
INNER  JOIN m_loan_transaction lt ON lt.id = lcpb.loan_transaction_id
WHERE  lt.loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 5: Transaction-to-schedule mappings
--   FK: m_loan_transaction_repayment_schedule_mapping.loan_transaction_id
--       → m_loan_transaction
-- ============================================================

DELETE trsm
FROM   m_loan_transaction_repayment_schedule_mapping trsm
INNER  JOIN m_loan_transaction lt ON lt.id = trsm.loan_transaction_id
WHERE  lt.loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 6: Loan transactions
--   (all child FKs cleared in phases 1-5)
-- ============================================================

DELETE FROM m_loan_transaction
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 7: Guarantor funding details & guarantors
--   (guarantor_transaction deleted in phase 2)
--   Note: m_guarantor_funding_details → m_portfolio_account_associations
--         (cleared here before portfolio associations are deleted)
-- ============================================================

DELETE FROM m_guarantor_funding_details
WHERE  guarantor_id IN (
    SELECT id FROM m_guarantor
    WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id)
);

DELETE FROM m_guarantor
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 8: Additional interest recalculation details
--   FK: → m_loan_repayment_schedule
-- ============================================================

DELETE lira
FROM   m_loan_interest_recalculation_additional_details lira
INNER  JOIN m_loan_repayment_schedule lrs ON lrs.id = lira.loan_repayment_schedule_id
WHERE  lrs.loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 9: Overdue installment charges
--   FK: → m_loan_charge (→ m_loan) AND → m_loan_repayment_schedule
-- ============================================================

DELETE loic
FROM   m_loan_overdue_installment_charge loic
INNER  JOIN m_loan_charge lc ON lc.id = loic.loan_charge_id
WHERE  lc.loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 10: Installment charges
--   FK: → m_loan_charge AND → m_loan_repayment_schedule
-- ============================================================

DELETE lic
FROM   m_loan_installment_charge lic
INNER  JOIN m_loan_charge lc ON lc.id = lic.loan_charge_id
WHERE  lc.loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 11: Repayment schedule installments
-- ============================================================

DELETE FROM m_loan_repayment_schedule
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 12: Loan reschedule history
--   FK: → m_loan AND → m_loan_reschedule_request
--   Must be deleted before m_loan_reschedule_request.
-- ============================================================

DELETE FROM m_loan_repayment_schedule_history
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 13: Reschedule request → term variation mappings
--   FK: → m_loan_reschedule_request AND → m_loan_term_variations
--   Must be deleted before both referenced tables.
-- ============================================================

DELETE rrtvm
FROM   m_loan_reschedule_request_term_variations_mapping rrtvm
INNER  JOIN m_loan_reschedule_request lrr ON lrr.id = rrtvm.loan_reschedule_request_id
WHERE  lrr.loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 14: Loan reschedule requests
-- ============================================================

DELETE FROM m_loan_reschedule_request
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 15: Loan tranche disbursement charges
--   FK: → m_loan_charge AND → m_loan_disbursement_detail
--   Must be deleted before both referenced tables.
-- ============================================================

DELETE ltdc
FROM   m_loan_tranche_disbursement_charge ltdc
INNER  JOIN m_loan_charge lc ON lc.id = ltdc.loan_charge_id
WHERE  lc.loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 16: Loan charges
-- ============================================================

DELETE FROM m_loan_charge
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 17: Loan disbursement details
-- ============================================================

DELETE FROM m_loan_disbursement_detail
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 18: Loan collateral management
--   FK: → m_loan AND → m_client_collateral_management
--   Must be deleted before m_client_collateral_management.
-- ============================================================

DELETE FROM m_loan_collateral_management
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 19: Loan collateral (legacy)
-- ============================================================

DELETE FROM m_loan_collateral
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 20: Post-dated checks (repayment instruments)
-- ============================================================

DELETE FROM m_repayment_with_post_dated_checks
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 21: Loan term variations
--   Self-referential (parent_id FK within same table).
--   Delete children before parents using a JOIN instead of
--   a same-table subquery (MySQL restriction).
-- ============================================================

-- 21a. Children
DELETE child
FROM   m_loan_term_variations child
INNER  JOIN m_loan_term_variations parent ON parent.id = child.parent_id
WHERE  parent.loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- 21b. Root records
DELETE FROM m_loan_term_variations
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 22: Remaining loan-level satellite tables
-- ============================================================

-- Loan tranche charges
DELETE FROM m_loan_tranche_charges
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- Loan officer assignment history
DELETE FROM m_loan_officer_assignment_history
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- Loan top-up linkage
DELETE FROM m_loan_topup
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- Loan interest recalculation configuration
DELETE FROM m_loan_recalculation_details
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 23: Portfolio account associations
--   FK: loan_account_id / linked_loan_account_id → m_loan
--   Also used by m_guarantor_funding_details (cleared in phase 7).
-- ============================================================

DELETE FROM m_portfolio_account_associations
WHERE  loan_account_id        IN (SELECT id FROM m_loan WHERE client_id = @client_id)
   OR  linked_loan_account_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 24: Notes for loans
--   (loan_transaction notes already cleared in phase 1)
-- ============================================================

DELETE FROM m_note
WHERE  loan_id IN (SELECT id FROM m_loan WHERE client_id = @client_id);

-- ============================================================
-- PHASE 25: Loan accounts
--   All child FKs have been cleared in phases 1-24.
-- ============================================================

DELETE FROM m_loan
WHERE  client_id = @client_id;

-- ============================================================
-- PHASE 26: Client collateral management
--   m_loan_collateral_management (FK) cleared in phase 18.
-- ============================================================

DELETE FROM m_client_collateral_management
WHERE  client_id = @client_id;

-- ============================================================
-- PHASE 27: Client charge chain
--   m_client_charge_paid_by → m_client_transaction + m_client_charge
-- ============================================================

-- 27a. Charge paid-by pivot
DELETE ccpb
FROM   m_client_charge_paid_by ccpb
INNER  JOIN m_client_transaction ct ON ct.id = ccpb.client_transaction_id
WHERE  ct.client_id = @client_id;

-- 27b. Client charges
DELETE FROM m_client_charge
WHERE  client_id = @client_id;

-- 27c. Client transactions
DELETE FROM m_client_transaction
WHERE  client_id = @client_id;

-- ============================================================
-- PHASE 28: Remaining client satellite tables
-- ============================================================

DELETE FROM m_client_address
WHERE  client_id = @client_id;

DELETE FROM m_client_identifier
WHERE  client_id = @client_id;

DELETE FROM m_family_members
WHERE  client_id = @client_id;

DELETE FROM m_client_non_person
WHERE  client_id = @client_id;

DELETE FROM m_client_transfer_details
WHERE  client_id = @client_id;

DELETE FROM m_client_attendance
WHERE  client_id = @client_id;

-- ============================================================
-- PHASE 29: Client notes
-- ============================================================

DELETE FROM m_note
WHERE  client_id = @client_id;

-- ============================================================
-- PHASE 30: Group membership join table
-- ============================================================

DELETE FROM m_group_client
WHERE  client_id = @client_id;

-- ============================================================
-- PHASE 31: Client record
--   All child FKs cleared in phases 26-30.
-- ============================================================

DELETE FROM m_client
WHERE  id = @client_id;

-- ============================================================
-- Verify nothing was missed — expected: 0 rows
-- ============================================================
SELECT COUNT(*) AS remaining_loans    FROM m_loan       WHERE client_id = @client_id;
SELECT COUNT(*) AS remaining_charges  FROM m_client_charge WHERE client_id = @client_id;
SELECT COUNT(*) AS remaining_client   FROM m_client      WHERE id = @client_id;

-- COMMIT;   -- Uncomment after reviewing the verification counts above
ROLLBACK;    -- Safety: remove this line (and uncomment COMMIT) once verified
