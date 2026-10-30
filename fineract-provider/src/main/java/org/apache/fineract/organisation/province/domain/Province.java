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
package org.apache.fineract.organisation.province.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.organisation.province.api.ProvinceJsonInputParams;

@Entity
@Table(name = "m_province", uniqueConstraints = { @UniqueConstraint(columnNames = { "code" }, name = "uq_province_code") })
public class Province extends AbstractPersistableCustom {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name_khm", nullable = false, length = 100)
    private String nameKhm;

    @Column(name = "name_eng", nullable = false, length = 100)
    private String nameEng;

    protected Province() {}

    private Province(final String code, final String nameKhm, final String nameEng) {
        this.code = code;
        this.nameKhm = nameKhm;
        this.nameEng = nameEng;
    }

    public static Province fromJson(final JsonCommand command) {
        final String code = command.stringValueOfParameterNamed(ProvinceJsonInputParams.CODE.getValue());
        final String nameKhm = command.stringValueOfParameterNamed(ProvinceJsonInputParams.NAME_KHM.getValue());
        final String nameEng = command.stringValueOfParameterNamed(ProvinceJsonInputParams.NAME_ENG.getValue());
        return new Province(code, nameKhm, nameEng);
    }

    public Map<String, Object> update(final JsonCommand command) {
        final Map<String, Object> actualChanges = new LinkedHashMap<>();

        if (command.isChangeInStringParameterNamed(ProvinceJsonInputParams.CODE.getValue(), this.code)) {
            final String newValue = command.stringValueOfParameterNamed(ProvinceJsonInputParams.CODE.getValue());
            actualChanges.put(ProvinceJsonInputParams.CODE.getValue(), newValue);
            this.code = newValue;
        }

        if (command.isChangeInStringParameterNamed(ProvinceJsonInputParams.NAME_KHM.getValue(), this.nameKhm)) {
            final String newValue = command.stringValueOfParameterNamed(ProvinceJsonInputParams.NAME_KHM.getValue());
            actualChanges.put(ProvinceJsonInputParams.NAME_KHM.getValue(), newValue);
            this.nameKhm = newValue;
        }

        if (command.isChangeInStringParameterNamed(ProvinceJsonInputParams.NAME_ENG.getValue(), this.nameEng)) {
            final String newValue = command.stringValueOfParameterNamed(ProvinceJsonInputParams.NAME_ENG.getValue());
            actualChanges.put(ProvinceJsonInputParams.NAME_ENG.getValue(), newValue);
            this.nameEng = newValue;
        }

        return actualChanges;
    }

    public String getCode() {
        return this.code;
    }

    public String getNameKhm() {
        return this.nameKhm;
    }

    public String getNameEng() {
        return this.nameEng;
    }
}
