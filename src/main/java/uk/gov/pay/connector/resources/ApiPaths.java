package uk.gov.pay.connector.resources;

public interface ApiPaths {

    String API_VERSION_PATH = "/v1";
    
    String GATEWAY_ACCOUNTS_API_PATH = API_VERSION_PATH +"/api/accounts";
    String GATEWAY_ACCOUNT_API_PATH = API_VERSION_PATH +"/api/accounts/{accountId}";
    String GATEWAY_ACCOUNTS_API_EMAIL_NOTIFICATION = API_VERSION_PATH +"/api/accounts/{accountId}/email-notification";
    String GATEWAY_ACCOUNTS_NOTIFICATION_CREDENTIALS = API_VERSION_PATH +"/api/accounts/{accountId}/notification-credentials";

    String FRONTEND_GATEWAY_ACCOUNT_API_PATH = API_VERSION_PATH +"/frontend/accounts/{accountId}";
    String FRONTEND_CHARGE_API_PATH = API_VERSION_PATH +"/frontend/charges/{chargeId}";
    String FRONTEND_CHARGE_STATUS_API_PATH = API_VERSION_PATH +"/frontend/charges/{chargeId}/status";
    String FRONTEND_CHARGE_AUTHORIZE_API_PATH = API_VERSION_PATH +"/frontend/charges/{chargeId}/cards";
    String FRONTEND_CHARGE_CAPTURE_API_PATH = API_VERSION_PATH +"/frontend/charges/{chargeId}/capture";
    String FRONTEND_CHARGE_CANCEL_API_PATH = API_VERSION_PATH +"/frontend/charges/{chargeId}/cancel";

    String FRONTEND_ACCOUNT_CREDENTIALS_API_PATH = API_VERSION_PATH +"/frontend/accounts/{accountId}/credentials";
    String FRONTEND_ACCOUNT_SERVICENAME_API_PATH = API_VERSION_PATH +"/frontend/accounts/{accountId}/servicename";
    String FRONTEND_ACCOUNT_CARDTYPES_API_PATH = API_VERSION_PATH + "/frontend/accounts/{accountId}/card-types";

    String CHARGES_API_PATH = API_VERSION_PATH +"/api/accounts/{accountId}/charges";
    String CHARGE_API_PATH = API_VERSION_PATH +"/api/accounts/{accountId}/charges/{chargeId}";
    String CHARGE_CANCEL_API_PATH = API_VERSION_PATH +"/api/accounts/{accountId}/charges/{chargeId}/cancel";
    String CHARGE_EVENTS_API_PATH = API_VERSION_PATH +"/api/accounts/{accountId}/charges/{chargeId}/events";
    String CHARGES_EXPIRE_CHARGES_TASK_API_PATH = API_VERSION_PATH +"/tasks/expired-charges-sweep";

    String REFUNDS_API_PATH = CHARGE_API_PATH + "/refunds";
    String REFUND_API_PATH = CHARGE_API_PATH + "/refunds/{refundId}";

    String CARD_TYPES_API_PATH = API_VERSION_PATH + "/api/card-types";

    String NOTIFICATIONS_WORLDPAY_API_PATH = API_VERSION_PATH + "/api/notifications/worldpay";
    String NOTIFICATIONS_SMARTPAY_API_PATH = API_VERSION_PATH + "/api/notifications/smartpay";
    String NOTIFICATIONS_SANDBOX_API_PATH = API_VERSION_PATH + "/api/notifications/sandbox";
}
