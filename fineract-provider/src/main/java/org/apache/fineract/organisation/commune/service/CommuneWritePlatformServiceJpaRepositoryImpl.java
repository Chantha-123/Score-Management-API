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

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.organisation.commune.api.CommuneJsonInputParams;
import org.apache.fineract.organisation.commune.command.CommuneCommand;
import org.apache.fineract.organisation.commune.domain.Commune;
import org.apache.fineract.organisation.commune.domain.CommuneRepository;
import org.apache.fineract.organisation.commune.domain.CommuneRepositoryWrapper;
import org.apache.fineract.organisation.commune.exception.CommuneDuplicateException;
import org.apache.fineract.organisation.commune.serialization.CommuneCommandFromApiJsonDeserializer;
import org.apache.fineract.organisation.district.domain.District;
import org.apache.fineract.organisation.district.domain.DistrictRepositoryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommuneWritePlatformServiceJpaRepositoryImpl implements CommuneWritePlatformService {

    private static final Logger LOG = LoggerFactory.getLogger(CommuneWritePlatformServiceJpaRepositoryImpl.class);

    private final CommuneRepository communeRepository;
    private final CommuneRepositoryWrapper communeRepositoryWrapper;
    private final CommuneCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    private final DistrictRepositoryWrapper districtRepositoryWrapper;

    @Transactional
    @Override
    public CommandProcessingResult createCommune(final JsonCommand command) {
        try {
            final CommuneCommand communeCommand = this.fromApiJsonDeserializer.commandFromApiJson(command.json());
            communeCommand.validateForCreate();

            final Long districtId = command.longValueOfParameterNamed(CommuneJsonInputParams.DISTRICT_ID.getValue());
            final District district = this.districtRepositoryWrapper.findOneWithNotFoundDetection(districtId);

            final String code = communeCommand.getCode();
            this.communeRepository.findByDistrictIdAndCode(districtId, code).ifPresent(existing -> {
                throw new CommuneDuplicateException(districtId, code);
            });

            final Commune commune = Commune.fromJson(district, command);
            this.communeRepository.saveAndFlush(commune);

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(commune.getId()).build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            final Throwable throwable = dve.getMostSpecificCause();
            handleDataIntegrityIssues(throwable, dve);
            return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult updateCommune(final Long communeId, final JsonCommand command) {
        try {
            final CommuneCommand communeCommand = this.fromApiJsonDeserializer.commandFromApiJson(command.json());
            communeCommand.validateForUpdate();

            final Commune commune = this.communeRepositoryWrapper.findOneWithNotFoundDetection(communeId);

            final Map<String, Object> changesOnly = commune.update(command);

            if (!changesOnly.isEmpty()) {
                this.communeRepository.saveAndFlush(commune);
            }

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(communeId).with(changesOnly)
                    .build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            final Throwable throwable = dve.getMostSpecificCause();
            handleDataIntegrityIssues(throwable, dve);
            return CommandProcessingResult.empty();
        }
    }

    private void handleDataIntegrityIssues(final Throwable realCause, final NonTransientDataAccessException dve) {
        if (realCause.getMessage().contains("uq_commune_district_code")) {
            throw new PlatformDataIntegrityException("error.msg.commune.duplicate.code",
                    "A Commune with this code already exists in the district.");
        }
        LOG.error("Error occurred.", dve);
        throw new PlatformDataIntegrityException("error.msg.commune.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource Commune: " + realCause.getMessage());
    }
}
