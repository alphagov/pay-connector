package uk.gov.pay.connector.util;

import static io.dropwizard.testing.FixtureHelpers.fixture;

public class TestTemplateResourceLoader {
    private static final String TEMPLATE_BASE_NAME = "templates";

    // WORLDPAY

    private static final String WORLDPAY_BASE_NAME = TEMPLATE_BASE_NAME + "/worldpay";

    public static final String WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE = WORLDPAY_BASE_NAME + "/authorisation-success-response.xml";
    static final String WORLDPAY_AUTHORISATION_ERROR_RESPONSE = WORLDPAY_BASE_NAME + "/authorisation-error-response.xml";
    public static final String WORLDPAY_AUTHORISATION_FAILED_RESPONSE = WORLDPAY_BASE_NAME + "/authorisation-failed-response.xml";
    static final String WORLDPAY_AUTHORISATION_CANCELLED_RESPONSE = WORLDPAY_BASE_NAME + "/authorisation-cancelled-response.xml";
    public static final String WORLDPAY_AUTHORISATION_PARES_PARSE_ERROR_RESPONSE = WORLDPAY_BASE_NAME + "/authorisation-pares-parse-error-response.xml";
    public static final String WORLDPAY_SPECIAL_CHAR_VALID_AUTHORISE_WORLDPAY_REQUEST_ADDRESS = WORLDPAY_BASE_NAME + "/special-char-valid-authorise-worldpay-request-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_3DS_REQUEST_MIN_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-3ds-request-min-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-excluding-3ds.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_FULL_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-full-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-including-3ds.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_MIN_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-min-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_WITHOUT_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-without-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST_MIN_DATA = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-apple-pay-min-data.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-apple-pay.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-google-pay.xml";

    public static final String WORLDPAY_3DS_RESPONSE = WORLDPAY_BASE_NAME + "/3ds-response.xml";
    public static final String WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST = WORLDPAY_BASE_NAME + "/valid-3ds-response-auth-worldpay-request.xml";

    public static final String WORLDPAY_CAPTURE_SUCCESS_RESPONSE = WORLDPAY_BASE_NAME + "/capture-success-response.xml";
    public static final String WORLDPAY_CAPTURE_ERROR_RESPONSE = WORLDPAY_BASE_NAME + "/capture-error-response.xml";
    public static final String WORLDPAY_SPECIAL_CHAR_VALID_CAPTURE_WORLDPAY_REQUEST = WORLDPAY_BASE_NAME + "/special-char-valid-capture-worldpay-request.xml";
    public static final String WORLDPAY_VALID_CAPTURE_WORLDPAY_REQUEST = WORLDPAY_BASE_NAME + "/valid-capture-worldpay-request.xml";

    public static final String WORLDPAY_CANCEL_SUCCESS_RESPONSE = WORLDPAY_BASE_NAME + "/cancel-success-response.xml";
    public static final String WORLDPAY_CANCEL_ERROR_RESPONSE = WORLDPAY_BASE_NAME + "/cancel-error-response.xml";
    public static final String WORLDPAY_VALID_CANCEL_WORLDPAY_REQUEST = WORLDPAY_BASE_NAME + "/valid-cancel-worldpay-request.xml";

    public static final String WORLDPAY_REFUND_SUCCESS_RESPONSE = WORLDPAY_BASE_NAME + "/refund-success-response.xml";
    public static final String WORLDPAY_REFUND_ERROR_RESPONSE = WORLDPAY_BASE_NAME + "/refund-error-response.xml";
    public static final String WORLDPAY_VALID_REFUND_WORLDPAY_REQUEST = WORLDPAY_BASE_NAME + "/valid-refund-worldpay-request.xml";

    public static final String WORLDPAY_ERROR_RESPONSE = WORLDPAY_BASE_NAME + "/error-response.xml";
    public static final String WORLDPAY_NOTIFICATION = WORLDPAY_BASE_NAME + "/notification.xml";
    
    public static final String WORLDPAY_AUTHORISED_INQUIRY_RESPONSE = WORLDPAY_BASE_NAME + "/inquiry/authorised.xml";
    public static final String WORLDPAY_CAPTURED_INQUIRY_RESPONSE = WORLDPAY_BASE_NAME + "/inquiry/captured.xml";
    public static final String WORLDPAY_CANCELLED_INQUIRY_RESPONSE = WORLDPAY_BASE_NAME + "/inquiry/cancelled.xml";
    public static final String WORLDPAY_REJECTED_INQUIRY_RESPONSE = WORLDPAY_BASE_NAME + "/inquiry/rejected.xml";

    // SMARTPAY

    private static final String SMARTPAY_BASE_NAME = TEMPLATE_BASE_NAME + "/smartpay";

    public static final String SMARTPAY_AUTHORISATION_SUCCESS_RESPONSE = SMARTPAY_BASE_NAME + "/authorisation-success-response.xml";
    public static final String SMARTPAY_AUTHORISATION_3DS_REQUIRED_RESPONSE = SMARTPAY_BASE_NAME + "/authorisation-3ds-required-response.xml";
    public static final String SMARTPAY_AUTHORISATION_FAILED_RESPONSE = SMARTPAY_BASE_NAME + "/authorisation-failed-response.xml";
    static final String SMARTPAY_AUTHORISATION_ERROR_RESPONSE = SMARTPAY_BASE_NAME + "/authorisation-error-response.xml";
    public static final String SMARTPAY_SPECIAL_CHAR_VALID_AUTHORISE_SMARTPAY_REQUEST = SMARTPAY_BASE_NAME + "/special-char-valid-authorise-smartpay-request.xml";
    public static final String SMARTPAY_VALID_AUTHORISE_SMARTPAY_REQUEST = SMARTPAY_BASE_NAME + "/valid-authorise-smartpay-request.xml";
    public static final String SMARTPAY_VALID_AUTHORISE_SMARTPAY_REQUEST_MINIMAL = SMARTPAY_BASE_NAME + "/valid-authorise-smartpay-request-minimal.xml";
    public static final String SMARTPAY_VALID_AUTHORISE_SMARTPAY_REQUEST_WITHOUT_ADDRESS = SMARTPAY_BASE_NAME + "/valid-authorise-smartpay-request-without-address.xml";
    public static final String SMARTPAY_SPECIAL_CHAR_VALID_AUTHORISE_SMARTPAY_3DS_REQUEST = SMARTPAY_BASE_NAME + "/special-char-valid-authorise-smartpay-3ds-request.xml";
    public static final String SMARTPAY_VALID_AUTHORISE_SMARTPAY_3DS_REQUEST = SMARTPAY_BASE_NAME + "/valid-authorise-smartpay-3ds-request.xml";
    public static final String SMARTPAY_VALID_AUTHORISE_SMARTPAY_3DS_REQUEST_MINIMAL = SMARTPAY_BASE_NAME + "/valid-authorise-smartpay-3ds-request-minimal.xml";
    public static final String SMARTPAY_VALID_AUTHORISE_SMARTPAY_3DS_REQUEST_WITHOUT_ADDRESS = SMARTPAY_BASE_NAME + "/valid-authorise-smartpay-3ds-request-without-address.xml";

    static final String SMARTPAY_CANCEL_ERROR_RESPONSE = SMARTPAY_BASE_NAME + "/cancel-error-response.xml";
    public static final String SMARTPAY_CANCEL_SUCCESS_RESPONSE = SMARTPAY_BASE_NAME + "/cancel-success-response.xml";
    public static final String SMARTPAY_VALID_CANCEL_SMARTPAY_REQUEST = SMARTPAY_BASE_NAME + "/valid-cancel-smartpay-request.xml";

    public static final String SMARTPAY_CAPTURE_ERROR_RESPONSE = SMARTPAY_BASE_NAME + "/capture-error-response.xml";
    public static final String SMARTPAY_CAPTURE_SUCCESS_RESPONSE = SMARTPAY_BASE_NAME + "/capture-success-response.xml";
    public static final String SMARTPAY_SPECIAL_CHAR_VALID_CAPTURE_SMARTPAY_REQUEST = SMARTPAY_BASE_NAME + "/special-char-valid-capture-smartpay-request.xml";
    public static final String SMARTPAY_VALID_CAPTURE_SMARTPAY_REQUEST = SMARTPAY_BASE_NAME + "/valid-capture-smartpay-request.xml";

    public static final String SMARTPAY_MULTIPLE_NOTIFICATIONS = SMARTPAY_BASE_NAME + "/multiple-notifications.json";
    public static final String SMARTPAY_MULTIPLE_NOTIFICATIONS_DIFFERENT_DATES = SMARTPAY_BASE_NAME + "/multiple-notifications-different-dates.json";
    public static final String SMARTPAY_NOTIFICATION_AUTHORISATION = SMARTPAY_BASE_NAME + "/notification-authorisation.json";
    public static final String SMARTPAY_NOTIFICATION_CAPTURE_WITH_UNKNOWN_STATUS = SMARTPAY_BASE_NAME + "/notification-capture-with-unknown-status.json";
    public static final String SMARTPAY_NOTIFICATION_CAPTURE = SMARTPAY_BASE_NAME + "/notification-capture.json";
    public static final String SMARTPAY_NOTIFICATION_REFUND = SMARTPAY_BASE_NAME + "/notification-refund.json";

    public static final String SMARTPAY_REFUND_ERROR_RESPONSE = SMARTPAY_BASE_NAME + "/refund-error-response.xml";
    public static final String SMARTPAY_REFUND_SUCCESS_RESPONSE = SMARTPAY_BASE_NAME + "/refund-success-response.xml";
    public static final String SMARTPAY_VALID_REFUND_SMARTPAY_REQUEST = SMARTPAY_BASE_NAME + "/valid-refund-smartpay-request.xml";

    // EPDQ
    private static final String EPDQ_BASE_NAME = TEMPLATE_BASE_NAME + "/epdq";
    public static final String EPDQ_AUTHORISATION_SUCCESS_RESPONSE = EPDQ_BASE_NAME + "/authorisation-success-response.xml";
    public static final String EPDQ_AUTHORISATION_SUCCESS_3D_RESPONSE = EPDQ_BASE_NAME + "/authorisation-success-3d-response.xml";
    public static final String EPDQ_AUTHORISATION_ERROR_RESPONSE = EPDQ_BASE_NAME + "/authorisation-error-response.xml";
    public static final String EPDQ_AUTHORISATION_FAILED_RESPONSE = EPDQ_BASE_NAME + "/authorisation-failed-response.xml";
    public static final String EPDQ_AUTHORISATION_WAITING_EXTERNAL_RESPONSE = EPDQ_BASE_NAME + "/authorisation-waiting-external-response.xml";
    public static final String EPDQ_AUTHORISATION_WAITING_RESPONSE = EPDQ_BASE_NAME + "/authorisation-waiting-response.xml";
    public static final String EPDQ_AUTHORISATION_OTHER_RESPONSE = EPDQ_BASE_NAME + "/authorisation-other-response.xml";
    public static final String EPDQ_AUTHORISATION_REQUEST = EPDQ_BASE_NAME + "/authorisation-request.txt";
    public static final String EPDQ_AUTHORISATION_3DS_REQUEST = EPDQ_BASE_NAME + "/authorisation-3ds-request.txt";
    public static final String EPDQ_AUTHORISATION_STATUS_REQUEST = EPDQ_BASE_NAME + "/authorisation-status-request.txt";
    public static final String EPDQ_AUTHORISATION_STATUS_DECLINED_RESPONSE = EPDQ_BASE_NAME + "/authorisation-status-declined-response.xml";
    public static final String EPDQ_AUTHORISATION_STATUS_ERROR_RESPONSE = EPDQ_BASE_NAME + "/authorisation-status-error-response.xml";
    public static final String EPDQ_UNKNOWN_RESPONSE = EPDQ_BASE_NAME + "/unknown-status-response.xml";


    public static final String EPDQ_CAPTURE_REQUEST = EPDQ_BASE_NAME + "/capture-request.txt";
    public static final String EPDQ_CAPTURE_SUCCESS_RESPONSE = EPDQ_BASE_NAME + "/capture-success-response.xml";
    public static final String EPDQ_CAPTURE_ERROR_RESPONSE = EPDQ_BASE_NAME + "/capture-error-response.xml";

    public static final String EPDQ_CANCEL_REQUEST = EPDQ_BASE_NAME + "/cancel-request.txt";
    public static final String EPDQ_CANCEL_SUCCESS_RESPONSE = EPDQ_BASE_NAME + "/cancel-success-response.xml";
    static final String EPDQ_CANCEL_WAITING_RESPONSE = EPDQ_BASE_NAME + "/cancel-waiting-response.xml";
    public static final String EPDQ_CANCEL_ERROR_RESPONSE = EPDQ_BASE_NAME + "/cancel-error-response.xml";

    public static final String EPDQ_REFUND_REQUEST = EPDQ_BASE_NAME + "/refund-request.txt";
    public static final String EPDQ_REFUND_SUCCESS_RESPONSE = EPDQ_BASE_NAME + "/refund-success-response.xml";
    public static final String EPDQ_REFUND_ERROR_RESPONSE = EPDQ_BASE_NAME + "/refund-error-response.xml";

    public static final String EPDQ_DELETE_SUCCESS_RESPONSE = EPDQ_BASE_NAME + "/delete-success-response.xml";

    public static final String EPDQ_NOTIFICATION_TEMPLATE = EPDQ_BASE_NAME + "/notification-template.txt";

    public static final String STRIPE_AUTHORISATION_SUCCESS_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/authorisation_success_response.json";
    public static final String STRIPE_AUTHORISATION_FAILED_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/authorisation_failed_response.json";
    public static final String STRIPE_CREATE_TOKEN_SUCCESS_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/create_token_response.json";
    public static final String STRIPE_CREATE_SOURCES_SUCCESS_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/create_sources_response.json";
    public static final String STRIPE_CREATE_SOURCES_3DS_REQUIRED_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/create_sources_3ds_required_response.json";
    public static final String STRIPE_CREATE_3DS_SOURCES_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/create_3ds_sources_response.json";
    public static final String STRIPE_CAPTURE_SUCCESS_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/capture_success_response.json";
    public static final String STRIPE_CAPTURE_SUCCESS_RESPONSE_DESTINATION_CHARGE = TEMPLATE_BASE_NAME + "/stripe/capture_success_response_destination_charge.json";
    public static final String STRIPE_ERROR_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/error_response.json";
    public static final String STRIPE_ERROR_RESPONSE_GENERAL = TEMPLATE_BASE_NAME + "/stripe/error_response_general.json";
    public static final String STRIPE_CANCEL_CHARGE_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/cancel_charge_response.json";
    public static final String STRIPE_REFUND_FULL_CHARGE_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/refund_full_charge.json";
    public static final String STRIPE_REFUND_FULL_DESTINATION_CHARGE_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/refund_full_destination_charge.json";
    public static final String STRIPE_REFUND_ERROR_GREATER_AMOUNT_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/refund_error_greater_amount.json";
    public static final String STRIPE_REFUND_ERROR_ALREADY_REFUNDED_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/refund_error_charge_already_refunded.json";
    public static final String STRIPE_TRANSFER_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/transfer_success_response.json";

    public static final String STRIPE_NOTIFICATION_3DS_SOURCE = TEMPLATE_BASE_NAME + "/stripe/notification_3ds_source.json";

    public static final String SQS_SEND_MESSAGE_RESPONSE = TEMPLATE_BASE_NAME + "/sqs/send-message-response.xml";
    public static final String SQS_ERROR_RESPONSE = TEMPLATE_BASE_NAME + "/sqs/error-response.xml";

    public static String load(String location) {
        return fixture(location);
    }

}
