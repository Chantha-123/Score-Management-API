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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.coborrower.data.LoanCoBorrowerData;
import org.apache.fineract.portfolio.loanaccount.data.LoanStatusEnumData;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanCoBorrowerReadPlatformServiceImpl implements LoanCoBorrowerReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;

    @Override
    public List<LoanCoBorrowerData> retrieveForLoan(final Long loanId) {
        this.context.authenticatedUser();
        final LoanCoBorrowerMapper rm = new LoanCoBorrowerMapper();
        final String sql = "select " + rm.schema() + " where cb.loan_id = ? order by cb.id";
        return this.jdbcTemplate.query(sql, rm, loanId); // NOSONAR
    }

    @Override
    public List<LoanCoBorrowerData> retrieveCoBorrowedLoansForClient(final Long clientId) {
        this.context.authenticatedUser();
        final LoanCoBorrowerMapper rm = new LoanCoBorrowerMapper();
        final String sql = "select " + rm.schema() + " where cb.client_id = ? order by l.id desc";
        return this.jdbcTemplate.query(sql, rm, clientId); // NOSONAR
    }

    private static final class LoanCoBorrowerMapper implements RowMapper<LoanCoBorrowerData> {

        public String schema() {
            return " cb.id as id, cb.loan_id as loanId, cb.client_id as clientId, c.display_name as clientName,"
                    + " c.account_no as clientAccountNo, c.mobile_no as clientMobileNo, l.account_no as loanAccountNo,"
                    + " lp.name as loanProductName, l.loan_status_id as loanStatusId, l.principal_amount as principal,"
                    + " l.currency_code as currencyCode, b.id as borrowerClientId, b.display_name as borrowerName"
                    + " from m_loan_co_borrower cb" //
                    + " join m_client c on c.id = cb.client_id" //
                    + " join m_loan l on l.id = cb.loan_id" //
                    + " join m_product_loan lp on lp.id = l.product_id" //
                    + " left join m_client b on b.id = l.client_id";
        }

        @Override
        public LoanCoBorrowerData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final Integer loanStatusId = JdbcSupport.getInteger(rs, "loanStatusId");
            final LoanStatusEnumData loanStatus = loanStatusId == null ? null : LoanEnumerations.status(loanStatusId);
            return new LoanCoBorrowerData(rs.getLong("id"), rs.getLong("loanId"), rs.getLong("clientId"), rs.getString("clientName"),
                    rs.getString("clientAccountNo"), rs.getString("clientMobileNo"), rs.getString("loanAccountNo"),
                    rs.getString("loanProductName"), loanStatus, rs.getBigDecimal("principal"), rs.getString("currencyCode"),
                    JdbcSupport.getLong(rs, "borrowerClientId"), rs.getString("borrowerName"));
        }
    }
}
