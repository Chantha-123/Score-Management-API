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
package org.apache.fineract.portfolio.loanproduct.domain;

public enum EarlyPayoffChargeCalculationType {

    INVALID(0, "earlyPayoffChargeCalculationType.invalid"), //
    FLAT(1, "earlyPayoffChargeCalculationType.flat"), //
    PERCENT_OF_TOTAL_PAYOFF(2, "earlyPayoffChargeCalculationType.percentOfTotalPayoff");

    private final Integer value;
    private final String code;

    EarlyPayoffChargeCalculationType(final Integer value, final String code) {
        this.value = value;
        this.code = code;
    }

    public Integer getValue() {
        return this.value;
    }

    public String getCode() {
        return this.code;
    }

    public static EarlyPayoffChargeCalculationType fromInt(final Integer selectedType) {

        if (selectedType == null) {
            return null;
        }

        EarlyPayoffChargeCalculationType calculationType = INVALID;
        switch (selectedType) {
            case 1:
                calculationType = FLAT;
            break;
            case 2:
                calculationType = PERCENT_OF_TOTAL_PAYOFF;
            break;
            default:
                calculationType = INVALID;
            break;
        }
        return calculationType;
    }

    public boolean isFlat() {
        return FLAT.getValue().equals(this.value);
    }

    public boolean isPercentOfTotalPayoff() {
        return PERCENT_OF_TOTAL_PAYOFF.getValue().equals(this.value);
    }
}
