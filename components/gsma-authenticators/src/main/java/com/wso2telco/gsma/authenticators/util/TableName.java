package com.wso2telco.gsma.authenticators.util;

import com.wso2telco.gsma.authenticators.model.SPConsent;

public enum TableName {

    CLIENT_STATUS("clientstatus"),

    AUTHENTICATED_LOGIN("authenticated_login"),

    REG_STATUS("regstatus"),

    ALLOWED_AUTHENTICATORS_MNO("allowed_authenticators_mno"),

    ALLOWED_AUTHENTICATORS_SP("allowed_authenticators_sp"),

    CONSENT("consent"),
    SCOPE_PARAMETER("scope_parameter"),

    SP_CONFIGURATION("sp_configuration"),

    CONSENT_HISTORY("consent_history");

    private final String text;

    /**
     * @param text
     */
    private TableName(final String text) {
        this.text = text;
    }

    /* (non-Javadoc)
     * @see java.lang.Enum#toString()
     */
    @Override
    public String toString() {
        return text;
    }


}
