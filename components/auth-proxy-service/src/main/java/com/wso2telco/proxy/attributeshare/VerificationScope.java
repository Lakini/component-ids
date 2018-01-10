/*******************************************************************************
 * Copyright  (c) 2015-2017, WSO2.Telco Inc. (http://www.wso2telco.com) All Rights Reserved.
 *
 * WSO2.Telco Inc. licences this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.wso2telco.proxy.attributeshare;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.authentication.framework.exception.AuthenticationFailedException;

public class VerificationScope extends AbstractAttributeShare {

    private static Log logger = LogFactory.getLog(AttributeShare.class);

    public void mandatoryFeildValidation() {
         /*this method for do the mandatory field validation in attribute verification*/
    }

    public void scopeNClaimMatching() {
       /*this method for do the scope and claim value matching in attribute verification*/
    }

    public void shaAlgortithemValidation() {
        /*this method for do the scope and claim value matching in attribute verification*/
    }

    @Override
    public String attShareDetails(String operatorName, String clientId,String loginhintMsisdn,String msisdn) throws AuthenticationFailedException {
        logger.debug(" verification scope validation for client id : "+clientId);

        return getTrsutedStatus(operatorName, clientId,loginhintMsisdn,msisdn);
    }

}