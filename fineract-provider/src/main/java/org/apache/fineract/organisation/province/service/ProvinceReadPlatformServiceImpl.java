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
package org.apache.fineract.organisation.province.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.province.data.ProvinceData;
import org.apache.fineract.organisation.province.exception.ProvinceNotFoundException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProvinceReadPlatformServiceImpl implements ProvinceReadPlatformService {

    private final JdbcTemplate jdbcTemplate;

    private static final class ProvinceMapper implements RowMapper<ProvinceData> {

        public String schema() {
            return " p.id as id, p.code as code, p.name_khm as nameKhm, p.name_eng as nameEng" + " from m_province p";
        }

        @Override
        public ProvinceData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final String code = rs.getString("code");
            final String nameKhm = rs.getString("nameKhm");
            final String nameEng = rs.getString("nameEng");
            return new ProvinceData(id, code, nameKhm, nameEng);
        }
    }

    @Override
    public List<ProvinceData> retrieveAll() {
        final ProvinceMapper rm = new ProvinceMapper();
        final String sql = "select " + rm.schema() + " ORDER BY p.name_eng ASC";
        return this.jdbcTemplate.query(sql, rm); // NOSONAR
    }

    @Override
    public ProvinceData retrieveById(final Long provinceId) {
        try {
            final ProvinceMapper rm = new ProvinceMapper();
            final String sql = "select " + rm.schema() + " where p.id = ?";
            return this.jdbcTemplate.queryForObject(sql, rm, new Object[] { provinceId });
        } catch (final EmptyResultDataAccessException e) {
            throw new ProvinceNotFoundException(provinceId);
        }
    }
}
