package uk.gov.pay.connector.util;

public class Paths {
    public static final String GATEWAY_ACCOUNT_BY_SERVICE_ID_URL = "/v1/api/service/%s/account/%s";
    public static final String CREATE_CREDENTIALS_BY_SERVICE_ID_URL = GATEWAY_ACCOUNT_BY_SERVICE_ID_URL + "/credentials";
    public static final String PATCH_CREDENTIALS_BY_SERVICE_ID_URL = GATEWAY_ACCOUNT_BY_SERVICE_ID_URL + "/credentials/%s";
    public static final String VALIDATE_CREDENTIALS_BY_SERVICE_ID_URL = GATEWAY_ACCOUNT_BY_SERVICE_ID_URL + "/worldpay/check-credentials";
    public static final String UPDATE_3DS_FLEX_CREDENTIALS_BY_SERVICE_ID_URL = GATEWAY_ACCOUNT_BY_SERVICE_ID_URL + "/3ds-flex-credentials";
    public static final String CREATE_3DS_FLEX_CREDENTIALS_BY_SERVICE_ID_URL = GATEWAY_ACCOUNT_BY_SERVICE_ID_URL + "/3ds-flex-credentials";
    public static final String VALIDATE_3DS_FLEX_CREDENTIALS_BY_SERVICE_ID_URL = GATEWAY_ACCOUNT_BY_SERVICE_ID_URL + "/worldpay/check-3ds-flex-config";
}
