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
package org.apache.fineract.organisation.district.command;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.organisation.district.api.DistrictJsonInputParams;

@RequiredArgsConstructor
@Getter
public class DistrictCommand {

    private final Long id;
    private final Long provinceId;
    private final String code;
    private final String nameKhm;
    private final String nameEng;

    public void validateForCreate() {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("District");

        baseDataValidator.reset().parameter(DistrictJsonInputParams.PROVINCE_ID.getValue()).value(this.provinceId).notNull()
                .longGreaterThanZero();
        baseDataValidator.reset().parameter(DistrictJsonInputParams.CODE.getValue()).value(this.code).notBlank().notExceedingLengthOf(20);
        baseDataValidator.reset().parameter(DistrictJsonInputParams.NAME_KHM.getValue()).value(this.nameKhm).notBlank()
                .notExceedingLengthOf(100);
        baseDataValidator.reset().parameter(DistrictJsonInputParams.NAME_ENG.getValue()).value(this.nameEng).notBlank()
                .notExceedingLengthOf(100);

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                    dataValidationErrors);
        }
    }

    public void validateForUpdate() {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("District");

        baseDataValidator.reset().parameter(DistrictJsonInputParams.CODE.getValue()).value(this.code).ignoreIfNull().notBlank()
                .notExceedingLengthOf(20);
        baseDataValidator.reset().parameter(DistrictJsonInputParams.NAME_KHM.getValue()).value(this.nameKhm).ignoreIfNull().notBlank()
                .notExceedingLengthOf(100);
        baseDataValidator.reset().parameter(DistrictJsonInputParams.NAME_ENG.getValue()).value(this.nameEng).ignoreIfNull().notBlank()
                .notExceedingLengthOf(100);

        baseDataValidator.reset().anyOfNotNull(this.code, this.nameKhm, this.nameEng);

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                    dataValidationErrors);
        }
    }
}
