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
package org.apache.fineract.accounting.spotrate.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.spotrate.data.DailySpotRateData;
import org.apache.fineract.accounting.spotrate.exception.DailySpotRateNotFoundException;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DailySpotRateReadPlatformServiceImpl implements DailySpotRateReadPlatformService {

    private final JdbcTemplate jdbcTemplate;

    private static final class DailySpotRateMapper implements RowMapper<DailySpotRateData> {

        public String schema() {
            return " dsr.id as id, dsr.office_id as officeId, o.name as officeName, dsr.currency_code as currencyCode,"
                    + " dsr.rate_date as rateDate, dsr.spot_rate as spotRate, dsr.buying_rate as buyingRate,"
                    + " dsr.selling_rate as sellingRate" + " from acc_daily_spot_rate dsr"
                    + " inner join m_office o on o.id = dsr.office_id";
        }

        @Override
        public DailySpotRateData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final Long officeId = rs.getLong("officeId");
            final String officeName = rs.getString("officeName");
            final String currencyCode = rs.getString("currencyCode");
            final LocalDate rateDate = JdbcSupport.getLocalDate(rs, "rateDate");
            final BigDecimal spotRate = rs.getBigDecimal("spotRate");
            final BigDecimal buyingRate = rs.getBigDecimal("buyingRate");
            final BigDecimal sellingRate = rs.getBigDecimal("sellingRate");
            return new DailySpotRateData(id, officeId, officeName, currencyCode, rateDate, spotRate, buyingRate, sellingRate);
        }
    }

    @Override
    public List<DailySpotRateData> retrieveAll(final Long officeId, final String currencyCode, final LocalDate fromDate,
            final LocalDate toDate) {
        final DailySpotRateMapper rm = new DailySpotRateMapper();
        final StringBuilder sql = new StringBuilder("select ").append(rm.schema());

        final List<Object> params = new ArrayList<>();
        boolean whereAdded = false;

        if (officeId != null) {
            sql.append(" where dsr.office_id = ?");
            params.add(officeId);
            whereAdded = true;
        }

        if (currencyCode != null) {
            sql.append(whereAdded ? " and" : " where").append(" dsr.currency_code = ?");
            params.add(currencyCode);
            whereAdded = true;
        }

        if (fromDate != null) {
            sql.append(whereAdded ? " and" : " where").append(" dsr.rate_date >= ?");
            params.add(fromDate);
            whereAdded = true;
        }

        if (toDate != null) {
            sql.append(whereAdded ? " and" : " where").append(" dsr.rate_date <= ?");
            params.add(toDate);
        }

        sql.append(" ORDER BY dsr.rate_date DESC, o.name ASC");

        return this.jdbcTemplate.query(sql.toString(), rm, params.toArray()); // NOSONAR
    }

    @Override
    public DailySpotRateData retrieveById(final Long dailySpotRateId) {
        try {
            final DailySpotRateMapper rm = new DailySpotRateMapper();
            final String sql = "select " + rm.schema() + " where dsr.id = ?";
            return this.jdbcTemplate.queryForObject(sql, rm, new Object[] { dailySpotRateId });
        } catch (final EmptyResultDataAccessException e) {
            throw new DailySpotRateNotFoundException(dailySpotRateId);
        }
    }
}
