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
package org.apache.fineract.accounting.spotrate.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.accounting.spotrate.api.DailySpotRateJsonInputParams;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.organisation.office.domain.Office;

@Entity
@Table(name = "acc_daily_spot_rate", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "office_id", "currency_code", "rate_date" }, name = "uq_daily_spot_rate_office_currency_date") })
public class DailySpotRate extends AbstractPersistableCustom {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id", nullable = false)
    private Office office;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "spot_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal spotRate;

    @Column(name = "buying_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal buyingRate;

    @Column(name = "selling_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal sellingRate;

    protected DailySpotRate() {}

    private DailySpotRate(final Office office, final String currencyCode, final LocalDate rateDate, final BigDecimal spotRate,
            final BigDecimal buyingRate, final BigDecimal sellingRate) {
        this.office = office;
        this.currencyCode = currencyCode;
        this.rateDate = rateDate;
        this.spotRate = spotRate;
        this.buyingRate = buyingRate;
        this.sellingRate = sellingRate;
    }

    public static DailySpotRate fromJson(final Office office, final JsonCommand command) {
        final String currencyCode = command.stringValueOfParameterNamed(DailySpotRateJsonInputParams.CURRENCY_CODE.getValue());
        final LocalDate rateDate = command.localDateValueOfParameterNamed(DailySpotRateJsonInputParams.RATE_DATE.getValue());
        final BigDecimal spotRate = command.bigDecimalValueOfParameterNamed(DailySpotRateJsonInputParams.SPOT_RATE.getValue());
        final BigDecimal buyingRate = command.bigDecimalValueOfParameterNamed(DailySpotRateJsonInputParams.BUYING_RATE.getValue());
        final BigDecimal sellingRate = command.bigDecimalValueOfParameterNamed(DailySpotRateJsonInputParams.SELLING_RATE.getValue());
        return new DailySpotRate(office, currencyCode, rateDate, spotRate, buyingRate, sellingRate);
    }

    public Map<String, Object> update(final JsonCommand command) {
        final Map<String, Object> actualChanges = new LinkedHashMap<>();

        if (command.isChangeInBigDecimalParameterNamed(DailySpotRateJsonInputParams.SPOT_RATE.getValue(), this.spotRate)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed(DailySpotRateJsonInputParams.SPOT_RATE.getValue());
            actualChanges.put(DailySpotRateJsonInputParams.SPOT_RATE.getValue(), newValue);
            this.spotRate = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(DailySpotRateJsonInputParams.BUYING_RATE.getValue(), this.buyingRate)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed(DailySpotRateJsonInputParams.BUYING_RATE.getValue());
            actualChanges.put(DailySpotRateJsonInputParams.BUYING_RATE.getValue(), newValue);
            this.buyingRate = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(DailySpotRateJsonInputParams.SELLING_RATE.getValue(), this.sellingRate)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed(DailySpotRateJsonInputParams.SELLING_RATE.getValue());
            actualChanges.put(DailySpotRateJsonInputParams.SELLING_RATE.getValue(), newValue);
            this.sellingRate = newValue;
        }

        return actualChanges;
    }

    public Office getOffice() {
        return this.office;
    }

    public String getCurrencyCode() {
        return this.currencyCode;
    }

    public LocalDate getRateDate() {
        return this.rateDate;
    }

    public BigDecimal getSpotRate() {
        return this.spotRate;
    }

    public BigDecimal getBuyingRate() {
        return this.buyingRate;
    }

    public BigDecimal getSellingRate() {
        return this.sellingRate;
    }
}
