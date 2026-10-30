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
package org.apache.fineract.accounting.spotrate.serialization;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.accounting.spotrate.api.DailySpotRateJsonInputParams;
import org.apache.fineract.accounting.spotrate.command.DailySpotRateCommand;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.serialization.AbstractFromApiJsonDeserializer;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class DailySpotRateCommandFromApiJsonDeserializer extends AbstractFromApiJsonDeserializer<DailySpotRateCommand> {

    private final FromJsonHelper fromApiJsonHelper;

    @Override
    public DailySpotRateCommand commandFromApiJson(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        final Set<String> supportedParameters = DailySpotRateJsonInputParams.getAllValues();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, supportedParameters);

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final Long id = this.fromApiJsonHelper.extractLongNamed(DailySpotRateJsonInputParams.ID.getValue(), element);
        final Long officeId = this.fromApiJsonHelper.extractLongNamed(DailySpotRateJsonInputParams.OFFICE_ID.getValue(), element);
        final String currencyCode = this.fromApiJsonHelper.extractStringNamed(DailySpotRateJsonInputParams.CURRENCY_CODE.getValue(),
                element);
        final LocalDate rateDate = this.fromApiJsonHelper.extractLocalDateNamed(DailySpotRateJsonInputParams.RATE_DATE.getValue(), element);
        final BigDecimal spotRate = this.fromApiJsonHelper
                .extractBigDecimalWithLocaleNamed(DailySpotRateJsonInputParams.SPOT_RATE.getValue(), element);
        final BigDecimal buyingRate = this.fromApiJsonHelper
                .extractBigDecimalWithLocaleNamed(DailySpotRateJsonInputParams.BUYING_RATE.getValue(), element);
        final BigDecimal sellingRate = this.fromApiJsonHelper
                .extractBigDecimalWithLocaleNamed(DailySpotRateJsonInputParams.SELLING_RATE.getValue(), element);

        return new DailySpotRateCommand(id, officeId, currencyCode, rateDate, spotRate, buyingRate, sellingRate);
    }
}
