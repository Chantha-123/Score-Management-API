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

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.organisation.commune.domain.Commune;
import org.apache.fineract.organisation.commune.domain.CommuneRepositoryWrapper;
import org.apache.fineract.organisation.village.api.VillageJsonInputParams;
import org.apache.fineract.organisation.village.command.VillageCommand;
import org.apache.fineract.organisation.village.domain.Village;
import org.apache.fineract.organisation.village.domain.VillageRepository;
import org.apache.fineract.organisation.village.domain.VillageRepositoryWrapper;
import org.apache.fineract.organisation.village.exception.VillageDuplicateException;
import org.apache.fineract.organisation.village.serialization.VillageCommandFromApiJsonDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VillageWritePlatformServiceJpaRepositoryImpl implements VillageWritePlatformService {

    private static final Logger LOG = LoggerFactory.getLogger(VillageWritePlatformServiceJpaRepositoryImpl.class);

    private final VillageRepository villageRepository;
    private final VillageRepositoryWrapper villageRepositoryWrapper;
    private final VillageCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    private final CommuneRepositoryWrapper communeRepositoryWrapper;

    @Transactional
    @Override
    public CommandProcessingResult createVillage(final JsonCommand command) {
        try {
            final VillageCommand villageCommand = this.fromApiJsonDeserializer.commandFromApiJson(command.json());
            villageCommand.validateForCreate();

            final Long communeId = command.longValueOfParameterNamed(VillageJsonInputParams.COMMUNE_ID.getValue());
            final Commune commune = this.communeRepositoryWrapper.findOneWithNotFoundDetection(communeId);

            final String code = villageCommand.getCode();
            this.villageRepository.findByCommuneIdAndCode(communeId, code).ifPresent(existing -> {
                throw new VillageDuplicateException(communeId, code);
            });

            final Village village = Village.fromJson(commune, command);
            this.villageRepository.saveAndFlush(village);

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(village.getId()).build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            final Throwable throwable = dve.getMostSpecificCause();
            handleDataIntegrityIssues(throwable, dve);
            return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult updateVillage(final Long villageId, final JsonCommand command) {
        try {
            final VillageCommand villageCommand = this.fromApiJsonDeserializer.commandFromApiJson(command.json());
            villageCommand.validateForUpdate();

            final Village village = this.villageRepositoryWrapper.findOneWithNotFoundDetection(villageId);

            final Map<String, Object> changesOnly = village.update(command);

            if (!changesOnly.isEmpty()) {
                this.villageRepository.saveAndFlush(village);
            }

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(villageId).with(changesOnly)
                    .build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            final Throwable throwable = dve.getMostSpecificCause();
            handleDataIntegrityIssues(throwable, dve);
            return CommandProcessingResult.empty();
        }
    }

    private void handleDataIntegrityIssues(final Throwable realCause, final NonTransientDataAccessException dve) {
        if (realCause.getMessage().contains("uq_village_commune_code")) {
            throw new PlatformDataIntegrityException("error.msg.village.duplicate.code",
                    "A Village with this code already exists in the commune.");
        }
        LOG.error("Error occurred.", dve);
        throw new PlatformDataIntegrityException("error.msg.village.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource Village: " + realCause.getMessage());
    }
}
