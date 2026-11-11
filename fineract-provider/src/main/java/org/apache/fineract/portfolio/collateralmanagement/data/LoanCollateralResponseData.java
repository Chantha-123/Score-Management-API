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
package org.apache.fineract.portfolio.collateralmanagement.data;

import java.math.BigDecimal;
import org.apache.fineract.portfolio.loanaccount.data.LoanCollateralManagementData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCollateralManagement;

public final class LoanCollateralResponseData {

    private Long collateralId;

    private BigDecimal quantity;

    private Long clientCollateralId;

    private LoanCollateralResponseData(final Long collateralId, final BigDecimal quantity, final Long clientCollateralId) {
        this.collateralId = collateralId;
        this.quantity = quantity;
        this.clientCollateralId = clientCollateralId;
    }

    public static LoanCollateralResponseData instanceOf(final LoanCollateralManagement loanCollateralManagement) {
        return new LoanCollateralResponseData(loanCollateralManagement.getId(), loanCollateralManagement.getQuantity(),
                loanCollateralManagement.getClientCollateralManagement().getId());
    }

    public LoanCollateralManagementData toCommand() {
        return new LoanCollateralManagementData(this.clientCollateralId, this.quantity, this.collateralId);
    }
}
