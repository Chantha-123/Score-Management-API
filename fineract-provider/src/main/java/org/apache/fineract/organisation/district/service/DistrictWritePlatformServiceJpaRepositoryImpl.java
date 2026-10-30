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

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.organisation.district.api.DistrictJsonInputParams;
import org.apache.fineract.organisation.district.command.DistrictCommand;
import org.apache.fineract.organisation.district.domain.District;
import org.apache.fineract.organisation.district.domain.DistrictRepository;
import org.apache.fineract.organisation.district.domain.DistrictRepositoryWrapper;
import org.apache.fineract.organisation.district.exception.DistrictDuplicateException;
import org.apache.fineract.organisation.district.serialization.DistrictCommandFromApiJsonDeserializer;
import org.apache.fineract.organisation.province.domain.Province;
import org.apache.fineract.organisation.province.domain.ProvinceRepositoryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DistrictWritePlatformServiceJpaRepositoryImpl implements DistrictWritePlatformService {

    private static final Logger LOG = LoggerFactory.getLogger(DistrictWritePlatformServiceJpaRepositoryImpl.class);

    private final DistrictRepository districtRepository;
    private final DistrictRepositoryWrapper districtRepositoryWrapper;
    private final DistrictCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    private final ProvinceRepositoryWrapper provinceRepositoryWrapper;

    @Transactional
    @Override
    public CommandProcessingResult createDistrict(final JsonCommand command) {
        try {
            final DistrictCommand districtCommand = this.fromApiJsonDeserializer.commandFromApiJson(command.json());
            districtCommand.validateForCreate();

            final Long provinceId = command.longValueOfParameterNamed(DistrictJsonInputParams.PROVINCE_ID.getValue());
            final Province province = this.provinceRepositoryWrapper.findOneWithNotFoundDetection(provinceId);

            final String code = districtCommand.getCode();
            this.districtRepository.findByProvinceIdAndCode(provinceId, code).ifPresent(existing -> {
                throw new DistrictDuplicateException(provinceId, code);
            });

            final District district = District.fromJson(province, command);
            this.districtRepository.saveAndFlush(district);

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(district.getId()).build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            final Throwable throwable = dve.getMostSpecificCause();
            handleDataIntegrityIssues(throwable, dve);
            return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult updateDistrict(final Long districtId, final JsonCommand command) {
        try {
            final DistrictCommand districtCommand = this.fromApiJsonDeserializer.commandFromApiJson(command.json());
            districtCommand.validateForUpdate();

            final District district = this.districtRepositoryWrapper.findOneWithNotFoundDetection(districtId);

            final Map<String, Object> changesOnly = district.update(command);

            if (!changesOnly.isEmpty()) {
                this.districtRepository.saveAndFlush(district);
            }

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(districtId).with(changesOnly)
                    .build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            final Throwable throwable = dve.getMostSpecificCause();
            handleDataIntegrityIssues(throwable, dve);
            return CommandProcessingResult.empty();
        }
    }

    private void handleDataIntegrityIssues(final Throwable realCause, final NonTransientDataAccessException dve) {
        if (realCause.getMessage().contains("uq_district_province_code")) {
            throw new PlatformDataIntegrityException("error.msg.district.duplicate.code",
                    "A District with this code already exists in the province.");
        }
        LOG.error("Error occurred.", dve);
        throw new PlatformDataIntegrityException("error.msg.district.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource District: " + realCause.getMessage());
    }
}
