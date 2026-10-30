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
package org.apache.fineract.organisation.commune.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.commune.data.CommuneData;
import org.apache.fineract.organisation.commune.exception.CommuneNotFoundException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommuneReadPlatformServiceImpl implements CommuneReadPlatformService {

    private final JdbcTemplate jdbcTemplate;

    private static final class CommuneMapper implements RowMapper<CommuneData> {

        public String schema() {
            return " c.id as id, c.district_id as districtId, d.name_eng as districtNameEng,"
                    + " c.code as code, c.name_khm as nameKhm, c.name_eng as nameEng" + " from m_commune c"
                    + " inner join m_district d on d.id = c.district_id";
        }

        @Override
        public CommuneData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final Long districtId = rs.getLong("districtId");
            final String districtNameEng = rs.getString("districtNameEng");
            final String code = rs.getString("code");
            final String nameKhm = rs.getString("nameKhm");
            final String nameEng = rs.getString("nameEng");
            return new CommuneData(id, districtId, districtNameEng, code, nameKhm, nameEng);
        }
    }

    @Override
    public List<CommuneData> retrieveAll(final Long districtId) {
        final CommuneMapper rm = new CommuneMapper();
        final StringBuilder sql = new StringBuilder("select ").append(rm.schema());
        final List<Object> params = new ArrayList<>();

        if (districtId != null) {
            sql.append(" where c.district_id = ?");
            params.add(districtId);
        }

        sql.append(" ORDER BY c.name_eng ASC");
        return this.jdbcTemplate.query(sql.toString(), rm, params.toArray()); // NOSONAR
    }

    @Override
    public CommuneData retrieveById(final Long communeId) {
        try {
            final CommuneMapper rm = new CommuneMapper();
            final String sql = "select " + rm.schema() + " where c.id = ?";
            return this.jdbcTemplate.queryForObject(sql, rm, new Object[] { communeId });
        } catch (final EmptyResultDataAccessException e) {
            throw new CommuneNotFoundException(communeId);
        }
    }
}
