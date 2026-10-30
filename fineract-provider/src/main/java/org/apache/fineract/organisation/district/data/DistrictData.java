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
package org.apache.fineract.organisation.district.data;

import java.io.Serializable;

public class DistrictData implements Serializable {

    private final Long id;
    private final Long provinceId;
    private final String provinceNameEng;
    private final String code;
    private final String nameKhm;
    private final String nameEng;

    public DistrictData(final Long id, final Long provinceId, final String provinceNameEng, final String code, final String nameKhm,
            final String nameEng) {
        this.id = id;
        this.provinceId = provinceId;
        this.provinceNameEng = provinceNameEng;
        this.code = code;
        this.nameKhm = nameKhm;
        this.nameEng = nameEng;
    }

    public Long getId() {
        return this.id;
    }

    public Long getProvinceId() {
        return this.provinceId;
    }

    public String getProvinceNameEng() {
        return this.provinceNameEng;
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
