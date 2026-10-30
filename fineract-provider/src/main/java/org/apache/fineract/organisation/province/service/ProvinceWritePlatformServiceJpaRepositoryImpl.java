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

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.organisation.province.command.ProvinceCommand;
import org.apache.fineract.organisation.province.domain.Province;
import org.apache.fineract.organisation.province.domain.ProvinceRepository;
import org.apache.fineract.organisation.province.domain.ProvinceRepositoryWrapper;
import org.apache.fineract.organisation.province.exception.ProvinceDuplicateException;
import org.apache.fineract.organisation.province.serialization.ProvinceCommandFromApiJsonDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProvinceWritePlatformServiceJpaRepositoryImpl implements ProvinceWritePlatformService {

    private static final Logger LOG = LoggerFactory.getLogger(ProvinceWritePlatformServiceJpaRepositoryImpl.class);

    private final ProvinceRepository provinceRepository;
    private final ProvinceRepositoryWrapper provinceRepositoryWrapper;
    private final ProvinceCommandFromApiJsonDeserializer fromApiJsonDeserializer;

    @Transactional
    @Override
    public CommandProcessingResult createProvince(final JsonCommand command) {
        try {
            final ProvinceCommand provinceCommand = this.fromApiJsonDeserializer.commandFromApiJson(command.json());
            provinceCommand.validateForCreate();

            final String code = provinceCommand.getCode();
            this.provinceRepository.findByCode(code).ifPresent(existing -> {
                throw new ProvinceDuplicateException(code);
            });

            final Province province = Province.fromJson(command);
            this.provinceRepository.saveAndFlush(province);

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(province.getId()).build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            final Throwable throwable = dve.getMostSpecificCause();
            handleDataIntegrityIssues(throwable, dve);
            return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult updateProvince(final Long provinceId, final JsonCommand command) {
        try {
            final ProvinceCommand provinceCommand = this.fromApiJsonDeserializer.commandFromApiJson(command.json());
            provinceCommand.validateForUpdate();

            final Province province = this.provinceRepositoryWrapper.findOneWithNotFoundDetection(provinceId);

            final Map<String, Object> changesOnly = province.update(command);

            if (!changesOnly.isEmpty()) {
                this.provinceRepository.saveAndFlush(province);
            }

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(provinceId).with(changesOnly)
                    .build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            final Throwable throwable = dve.getMostSpecificCause();
            handleDataIntegrityIssues(throwable, dve);
            return CommandProcessingResult.empty();
        }
    }

    private void handleDataIntegrityIssues(final Throwable realCause, final NonTransientDataAccessException dve) {
        if (realCause.getMessage().contains("uq_province_code")) {
            throw new PlatformDataIntegrityException("error.msg.province.duplicate.code", "A Province with this code already exists.");
        }
        LOG.error("Error occurred.", dve);
        throw new PlatformDataIntegrityException("error.msg.province.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource Province: " + realCause.getMessage());
    }
}
