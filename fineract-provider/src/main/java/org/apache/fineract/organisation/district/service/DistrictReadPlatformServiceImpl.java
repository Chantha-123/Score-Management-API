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
package org.apache.fineract.organisation.district.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.district.data.DistrictData;
import org.apache.fineract.organisation.district.exception.DistrictNotFoundException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DistrictReadPlatformServiceImpl implements DistrictReadPlatformService {

    private final JdbcTemplate jdbcTemplate;

    private static final class DistrictMapper implements RowMapper<DistrictData> {

        public String schema() {
            return " d.id as id, d.province_id as provinceId, p.name_eng as provinceNameEng,"
                    + " d.code as code, d.name_khm as nameKhm, d.name_eng as nameEng" + " from m_district d"
                    + " inner join m_province p on p.id = d.province_id";
        }

        @Override
        public DistrictData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final Long provinceId = rs.getLong("provinceId");
            final String provinceNameEng = rs.getString("provinceNameEng");
            final String code = rs.getString("code");
            final String nameKhm = rs.getString("nameKhm");
            final String nameEng = rs.getString("nameEng");
            return new DistrictData(id, provinceId, provinceNameEng, code, nameKhm, nameEng);
        }
    }

    @Override
    public List<DistrictData> retrieveAll(final Long provinceId) {
        final DistrictMapper rm = new DistrictMapper();
        final StringBuilder sql = new StringBuilder("select ").append(rm.schema());
        final List<Object> params = new ArrayList<>();

        if (provinceId != null) {
            sql.append(" where d.province_id = ?");
            params.add(provinceId);
        }

        sql.append(" ORDER BY d.name_eng ASC");
        return this.jdbcTemplate.query(sql.toString(), rm, params.toArray()); // NOSONAR
    }

    @Override
    public DistrictData retrieveById(final Long districtId) {
        try {
            final DistrictMapper rm = new DistrictMapper();
            final String sql = "select " + rm.schema() + " where d.id = ?";
            return this.jdbcTemplate.queryForObject(sql, rm, new Object[] { districtId });
        } catch (final EmptyResultDataAccessException e) {
            throw new DistrictNotFoundException(districtId);
        }
    }
}
