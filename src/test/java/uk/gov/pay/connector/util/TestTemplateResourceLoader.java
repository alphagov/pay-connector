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
    public static final String WORLDPAY_SPECIAL_CHAR_VALID_AUTHORISE_WORLDPAY_REQUEST_ADDRESS = WORLDPAY_BASE_NAME + "/special-char-valid-authorise-worldpay-request-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_3DS_REQUEST_MIN_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-3ds-request-min-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-excluding-3ds.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_FULL_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-full-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-including-3ds.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_MIN_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-min-address.xml";

    public static final String WORLDPAY_3DS_RESPONSE = WORLDPAY_BASE_NAME + "/3ds-response.xml";
    public static final String WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST = WORLDPAY_BASE_NAME + "/valid-3ds-response-auth-worldpay-request.xml";

    public static final String WORLDPAY_CAPTURE_SUCCESS_RESPONSE = WORLDPAY_BASE_NAME + "/authorise-success-response.xml";
    public static final String WORLDPAY_CAPTURE_ERROR_RESPONSE = WORLDPAY_BASE_NAME + "/authorise-error-response.xml";
    public static final String WORLDPAY_SPECIAL_CHAR_VALID_CAPTURE_WORLDPAY_REQUEST = WORLDPAY_BASE_NAME + "/special-char-valid-authorise-worldpay-request.xml";
    public static final String WORLDPAY_VALID_CAPTURE_WORLDPAY_REQUEST = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request.xml";

    public static final String WORLDPAY_CANCEL_SUCCESS_RESPONSE = WORLDPAY_BASE_NAME + "/cancel-success-response.xml";
    public static final String WORLDPAY_CANCEL_ERROR_RESPONSE = WORLDPAY_BASE_NAME + "/cancel-error-response.xml";
    public static final String WORLDPAY_VALID_CANCEL_WORLDPAY_REQUEST = WORLDPAY_BASE_NAME + "/valid-cancel-worldpay-request.xml";

    public static final String WORLDPAY_REFUND_SUCCESS_RESPONSE = WORLDPAY_BASE_NAME + "/refund-success-response.xml";
    public static final String WORLDPAY_REFUND_ERROR_RESPONSE = WORLDPAY_BASE_NAME + "/refund-error-response.xml";
    public static final String WORLDPAY_VALID_REFUND_WORLDPAY_REQUEST = WORLDPAY_BASE_NAME + "/valid-refund-worldpay-request.xml";

    public static final String WORLDPAY_ERROR_RESPONSE = WORLDPAY_BASE_NAME + "/error-response.xml";
    public static final String WORLDPAY_NOTIFICATION = WORLDPAY_BASE_NAME + "/notification.xml";

    // SMARTPAY

    private static final String SMARTPAY_BASE_NAME = TEMPLATE_BASE_NAME + "/smartpay";

    public static final String SMARTPAY_AUTHORISATION_SUCCESS_RESPONSE = SMARTPAY_BASE_NAME + "/authorisation-success-response.xml";
    public static final String SMARTPAY_AUTHORISATION_3DS_REQUIRED_RESPONSE = SMARTPAY_BASE_NAME + "/authorisation-3ds-required-response.xml";
    public static final String SMARTPAY_AUTHORISATION_FAILED_RESPONSE = SMARTPAY_BASE_NAME + "/authorisation-failed-response.xml";
    static final String SMARTPAY_AUTHORISATION_ERROR_RESPONSE = SMARTPAY_BASE_NAME + "/authorisation-error-response.xml";
    public static final String SMARTPAY_SPECIAL_CHAR_VALID_AUTHORISE_SMARTPAY_REQUEST = SMARTPAY_BASE_NAME + "/special-char-valid-authorise-smartpay-request.xml";
    public static final String SMARTPAY_VALID_AUTHORISE_SMARTPAY_REQUEST = SMARTPAY_BASE_NAME + "/valid-authorise-smartpay-request.xml";
    public static final String SMARTPAY_VALID_AUTHORISE_SMARTPAY_REQUEST_MINIMAL = SMARTPAY_BASE_NAME + "/valid-authorise-smartpay-request-minimal.xml";

    static final String SMARTPAY_CANCEL_ERROR_RESPONSE = SMARTPAY_BASE_NAME + "/cancel-error-response.xml";
    public static final String SMARTPAY_CANCEL_SUCCESS_RESPONSE = SMARTPAY_BASE_NAME + "/cancel-success-response.xml";
    public static final String SMARTPAY_VALID_CANCEL_SMARTPAY_REQUEST = SMARTPAY_BASE_NAME + "/valid-cancel-smartpay-request.xml";

    public static final String SMARTPAY_CAPTURE_ERROR_RESPONSE = SMARTPAY_BASE_NAME + "/authorise-error-response.xml";
    public static final String SMARTPAY_CAPTURE_SUCCESS_RESPONSE = SMARTPAY_BASE_NAME + "/authorise-success-response.xml";
    public static final String SMARTPAY_SPECIAL_CHAR_VALID_CAPTURE_SMARTPAY_REQUEST = SMARTPAY_BASE_NAME + "/special-char-valid-authorise-smartpay-request.xml";
    public static final String SMARTPAY_VALID_CAPTURE_SMARTPAY_REQUEST = SMARTPAY_BASE_NAME + "/valid-authorise-smartpay-request.xml";

    public static final String SMARTPAY_MULTIPLE_NOTIFICATIONS = SMARTPAY_BASE_NAME + "/multiple-notifications.json";
    public static final String SMARTPAY_MULTIPLE_NOTIFICATIONS_DIFFERENT_DATES = SMARTPAY_BASE_NAME + "/multiple-notifications-different-dates.xml";
    public static final String SMARTPAY_NOTIFICATION_AUTHORISATION = SMARTPAY_BASE_NAME + "/notification-authorisation.xml";
    public static final String SMARTPAY_NOTIFICATION_CAPTURE_WITH_UNKNOWN_STATUS = SMARTPAY_BASE_NAME + "/notification-authorise-with-unknown-status.xml";
    public static final String SMARTPAY_NOTIFICATION_CAPTURE = SMARTPAY_BASE_NAME + "/notification-authorise.xml";
    public static final String SMARTPAY_NOTIFICATION_REFUND = SMARTPAY_BASE_NAME + "/notification-refund.xml";

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
    public static final String EPDQ_AUTHORISATION_STATUS_REQUEST = EPDQ_BASE_NAME + "/authorisation-status-request.txt";
    public static final String EPDQ_AUTHORISATION_STATUS_DECLINED_RESPONSE = EPDQ_BASE_NAME + "/authorisation-status-declined-response.xml";
    public static final String EPDQ_AUTHORISATION_STATUS_ERROR_RESPONSE = EPDQ_BASE_NAME + "/authorisation-status-error-response.xml";


    public static final String EPDQ_CAPTURE_REQUEST = EPDQ_BASE_NAME + "/authorise-request.txt";
    public static final String EPDQ_CAPTURE_SUCCESS_RESPONSE = EPDQ_BASE_NAME + "/authorise-success-response.xml";
    public static final String EPDQ_CAPTURE_ERROR_RESPONSE = EPDQ_BASE_NAME + "/authorise-error-response.xml";

    public static final String EPDQ_CANCEL_REQUEST = EPDQ_BASE_NAME + "/cancel-request.txt";
    public static final String EPDQ_CANCEL_SUCCESS_RESPONSE = EPDQ_BASE_NAME + "/cancel-success-response.xml";
    static final String EPDQ_CANCEL_WAITING_RESPONSE = EPDQ_BASE_NAME + "/cancel-waiting-response.xml";
    public static final String EPDQ_CANCEL_ERROR_RESPONSE = EPDQ_BASE_NAME + "/cancel-error-response.xml";

    public static final String EPDQ_REFUND_REQUEST = EPDQ_BASE_NAME + "/refund-request.txt";
    public static final String EPDQ_REFUND_SUCCESS_RESPONSE = EPDQ_BASE_NAME + "/refund-success-response.xml";
    public static final String EPDQ_REFUND_ERROR_RESPONSE = EPDQ_BASE_NAME + "/refund-error-response.xml";

    public static final String EPDQ_DELETE_SUCCESS_RESPONSE = EPDQ_BASE_NAME + "/delete-success-response.xml";

    public static final String EPDQ_NOTIFICATION_TEMPLATE = EPDQ_BASE_NAME + "/notification-template.txt";

    public static String load(String location) {
        return fixture(location);
    }

}
