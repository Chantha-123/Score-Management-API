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
package org.apache.fineract.organisation.village.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.village.data.VillageData;
import org.apache.fineract.organisation.village.exception.VillageNotFoundException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VillageReadPlatformServiceImpl implements VillageReadPlatformService {

    private final JdbcTemplate jdbcTemplate;

    private static final class VillageMapper implements RowMapper<VillageData> {

        public String schema() {
            return " v.id as id, v.commune_id as communeId, c.name_eng as communeNameEng,"
                    + " v.code as code, v.name_khm as nameKhm, v.name_eng as nameEng" + " from m_village v"
                    + " inner join m_commune c on c.id = v.commune_id";
        }

        @Override
        public VillageData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final Long communeId = rs.getLong("communeId");
            final String communeNameEng = rs.getString("communeNameEng");
            final String code = rs.getString("code");
            final String nameKhm = rs.getString("nameKhm");
            final String nameEng = rs.getString("nameEng");
            return new VillageData(id, communeId, communeNameEng, code, nameKhm, nameEng);
        }
    }

    @Override
    public List<VillageData> retrieveAll(final Long communeId) {
        final VillageMapper rm = new VillageMapper();
        final StringBuilder sql = new StringBuilder("select ").append(rm.schema());
        final List<Object> params = new ArrayList<>();

        if (communeId != null) {
            sql.append(" where v.commune_id = ?");
            params.add(communeId);
        }

        sql.append(" ORDER BY v.name_eng ASC");
        return this.jdbcTemplate.query(sql.toString(), rm, params.toArray()); // NOSONAR
    }

    @Override
    public VillageData retrieveById(final Long villageId) {
        try {
            final VillageMapper rm = new VillageMapper();
            final String sql = "select " + rm.schema() + " where v.id = ?";
            return this.jdbcTemplate.queryForObject(sql, rm, new Object[] { villageId });
        } catch (final EmptyResultDataAccessException e) {
            throw new VillageNotFoundException(villageId);
        }
    }
}
