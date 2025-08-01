package uk.gov.pay.connector.util;

public class TestTemplateResourceLoader {
    private static final String TEMPLATE_BASE_NAME = "templates";

    // WORLDPAY

    private static final String WORLDPAY_BASE_NAME = TEMPLATE_BASE_NAME + "/worldpay";

    public static final String WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE = WORLDPAY_BASE_NAME + "/authorisation-success-response.xml";
    public static final String WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE_WITH_INVALID_EXPIRY_YEAR = WORLDPAY_BASE_NAME + "/authorisation-success-response-with-invalid-expiry-year.xml";
    public static final String WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE_WITH_MISSING_EXPIRY_DATE = WORLDPAY_BASE_NAME + "/authorisation-success-response-with-missing-expiry-date.xml";
    public static final String WORLDPAY_EXEMPTION_REQUEST_HONOURED_RESPONSE = WORLDPAY_BASE_NAME + "/exemption-request-honoured-response.xml";
    public static final String WORLDPAY_EXEMPTION_REQUEST_SOFT_DECLINE_RESULT_REJECTED_RESPONSE = WORLDPAY_BASE_NAME + "/exemption-request-soft-decline-result-rejected-response.xml";
    public static final String WORLDPAY_EXEMPTION_REQUEST_SOFT_DECLINE_RESULT_OUT_OF_SCOPE_RESPONSE = WORLDPAY_BASE_NAME + "/exemption-request-soft-decline-result-out-of-scope-response.xml";
    public static final String WORLDPAY_EXEMPTION_REQUEST_DECLINE_RESPONSE = WORLDPAY_BASE_NAME + "/exemption-request-decline-response.xml";
    public static final String WORLDPAY_EXEMPTION_REQUEST_REJECTED_AUTHORISED_RESPONSE = WORLDPAY_BASE_NAME + "/exemption-request-rejected-authorised-response.xml";
    
    public static final String WORLDPAY_AUTHORISATION_CREATE_TOKEN_SUCCESS_RESPONSE_WITH_TRANSACTION_IDENTIFIER = WORLDPAY_BASE_NAME + "/authorisation-create-token-success-response-with-transaction-identifier.xml";

    public static final String WORLDPAY_AUTHORISATION_CREATE_TOKEN_SUCCESS_RESPONSE_WITHOUT_TRANSACTION_IDENTIFIER = WORLDPAY_BASE_NAME + "/authorisation-create-token-success-response-without-transaction-identifier.xml";
    static final String WORLDPAY_AUTHORISATION_ERROR_RESPONSE = WORLDPAY_BASE_NAME + "/authorisation-error-response.xml";
    public static final String WORLDPAY_AUTHORISATION_FAILED_RESPONSE = WORLDPAY_BASE_NAME + "/authorisation-failed-response.xml";
    public static final String WORLDPAY_AUTHORISATION_FAILED_USER_NON_PRESENT_NON_RETRIABLE_RESPONSE =
            WORLDPAY_BASE_NAME + "/authorisation-failed-response-user-not-present-payment-non-retriable.xml";
    static final String WORLDPAY_AUTHORISATION_CANCELLED_RESPONSE = WORLDPAY_BASE_NAME + "/authorisation-cancelled-response.xml";
    public static final String WORLDPAY_AUTHORISATION_PARES_PARSE_ERROR_RESPONSE = WORLDPAY_BASE_NAME + "/authorisation-pares-parse-error-response.xml";
    public static final String WORLDPAY_SPECIAL_CHAR_VALID_AUTHORISE_WORLDPAY_REQUEST_ADDRESS = WORLDPAY_BASE_NAME + "/special-char-valid-authorise-worldpay-request-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_3DS_REQUEST_MIN_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-3ds-request-min-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_3DS_REQUEST_INCLUDING_STATE = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-3ds-request-including-state.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-excluding-3ds.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS_WITH_EMAIL = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-excluding-3ds-with-email.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_WITH_REFERENCE_IN_DESCRIPTION = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-with-reference-in-description.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_FULL_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-full-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITH_IP_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-including-3ds-with-ip-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITHOUT_IP_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-including-3ds-without-ip-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITH_EMAIL = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-including-3ds-with-email.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_STATE = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-including-state.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_MIN_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-min-address.xml";
    
    public static final String WORLDPAY_VALID_AUTHORISE_RECURRING_WORLDPAY_REQUEST_WITH_SCHEME_IDENTIFIER = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-recurring-request-with-scheme-identifier.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_RECURRING_WORLDPAY_REQUEST_WITHOUT_SCHEME_IDENTIFIER = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-recurring-request-without-scheme-identifier.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_WITHOUT_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-without-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_SETUP_AGREEMENT = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-setup-agreement.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST_MIN_DATA = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-apple-pay-min-data.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-apple-pay.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST_WITH_EMAIL = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-apple-pay-with-email.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-google-pay.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST_WITH_REFERENCE_AS_DESCRIPTION = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-apple-pay-with-reference-as-description.xml";

    public static final String WORLDPAY_VALID_AUTHORISE_GOOGLE_PAY_3DS_REQUEST_WITH_DDC_RESULT = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-google-pay-3ds-with-ddc.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST_WITH_EMAIL = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-google-pay-with-email.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITHOUT_IP_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-google-pay-3ds-without-ip-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITH_IP_ADDRESS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-google-pay-3ds-with-ip-address.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITH_EMAIL = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-google-pay-3ds-with-email.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITH_IP_ADDRESS_AND_EMAIL = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-google-pay-3ds-with-ip-address-and-email.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_REQUEST_3DS_FLEX_NON_JS = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-3ds-flex-non-js.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_3DS_REQUEST_EXEMPTION_OPTIMISED = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-3ds-request-exemption-optimised.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_3DS_REQUEST_CORPORATE_EXEMPTION_AUTHORISATION = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-3ds-request-corporate-exemption-authorisation.xml";
    public static final String WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST_WITH_REFERENCE_AS_DESCRIPTION = WORLDPAY_BASE_NAME + "/valid-authorise-worldpay-request-google-pay-with-reference-as-description.xml";

    public static final String WORLDPAY_3DS_RESPONSE = WORLDPAY_BASE_NAME + "/3ds-response.xml";
    public static final String WORLDPAY_3DS_FLEX_RESPONSE = WORLDPAY_BASE_NAME + "/3ds-flex-response.xml";
    public static final String WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST = WORLDPAY_BASE_NAME + "/valid-3ds-response-auth-worldpay-request.xml";
    public static final String WORLDPAY_VALID_3DS_FLEX_RESPONSE_AUTH_WORLDPAY_REQUEST = WORLDPAY_BASE_NAME + "/valid-3ds-flex-response-auth-worldpay-request.xml";

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
    
    public static final String WORLDPAY_VALID_DELETE_TOKEN_REQUEST = WORLDPAY_BASE_NAME + "/valid-delete-token-worldpay-request.xml";
    public static final String WORLDPAY_DELETE_TOKEN_SUCCESS_RESPONSE = WORLDPAY_BASE_NAME + "/delete-token-success-response.xml";
    
    public static final String WORLDPAY_ERROR_RESPONSE = WORLDPAY_BASE_NAME + "/error-response.xml";
    public static final String WORLDPAY_NOTIFICATION = WORLDPAY_BASE_NAME + "/notification.xml";
    
    public static final String WORLDPAY_AUTHORISED_INQUIRY_RESPONSE = WORLDPAY_BASE_NAME + "/inquiry/authorised.xml";
    public static final String WORLDPAY_CAPTURED_INQUIRY_RESPONSE = WORLDPAY_BASE_NAME + "/inquiry/captured.xml";
    public static final String WORLDPAY_CANCELLED_INQUIRY_RESPONSE = WORLDPAY_BASE_NAME + "/inquiry/cancelled.xml";
    public static final String WORLDPAY_REJECTED_INQUIRY_RESPONSE = WORLDPAY_BASE_NAME + "/inquiry/rejected.xml";
    public static final String WORLDPAY_NULL_LAST_EVENT_INQUIRY_RESPONSE = WORLDPAY_BASE_NAME + "/inquiry/null-last-event.xml";
    
    public static final String WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_VALID_RESPONSE = WORLDPAY_BASE_NAME + "/check-credentials/valid.xml";
    public static final String WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_INVALID_MERCHANT_ID_RESPONSE = WORLDPAY_BASE_NAME + "/check-credentials/invalid-merchant-id.xml";
    public static final String WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_UNEXPECTED_ERROR_CODE = WORLDPAY_BASE_NAME + "/check-credentials/unexpected-error-code.xml";

    public static final String STRIPE_AUTHORISATION_FAILED_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/authorisation_failed_response.json";
    public static final String STRIPE_AUTHORISATION_FAILED_RESPONSE_USER_NOT_PRESENT_PAYMENT_NOT_RETRIABLE =
            TEMPLATE_BASE_NAME + "/stripe/create_payment_method_failed_and_non_retriable_for_recurring_payment.json";
    public static final String STRIPE_AUTHORISATION_FAILED_RESPONSE_USER_NOT_PRESENT_PAYMENT_RETRIABLE =
            TEMPLATE_BASE_NAME + "/stripe/create_payment_method_failed_and_retriable_for_recurring_payment.json";
    public static final String STRIPE_NOTIFICATION_BALANCE_AVAILABLE = TEMPLATE_BASE_NAME + "/stripe/balance_available.json";
    public static final String STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/payment_intent_capture_success_response.json";
    public static final String STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/create_payment_intent_success_response.json";
    public static final String STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE_WITH_CHARGE = TEMPLATE_BASE_NAME + "/stripe/create_payment_intent_success_response_with_charge.json";
    public static final String STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE_WITH_CUSTOMER = TEMPLATE_BASE_NAME + "/stripe/create_payment_intent_success_response_with_customer.json";
    public static final String STRIPE_PAYMENT_INTENT_REQUIRES_3DS_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/create_payment_intent_requires_3ds_response.json";
    public static final String STRIPE_PAYMENT_INTENT_AUTHORISATION_REJECTED_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/create_payment_intent_authorisation_rejected_response.json";
    public static final String STRIPE_PAYMENT_INTENT_ERROR_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/create_payment_intent_error_response.json";
    public static final String STRIPE_PAYMENT_INTENT_CANCEL_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/payment_intent_cancel_response.json";
    public static final String STRIPE_GET_PAYMENT_INTENT_WITH_3DS_AUTHORISED_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/get_payment_intent_with_3ds_authorised_success_response.json";
    public static final String STRIPE_GET_PAYMENT_INTENT_WITH_MULTIPLE_CHARGES = TEMPLATE_BASE_NAME + "/stripe/get_payment_intent_with_multiple_charges.json";
    public static final String STRIPE_PAYMENT_INTENT_WITHOUT_BALANCE_TRANSACTION_EXPANDED = TEMPLATE_BASE_NAME + "/stripe/payment_intent_without_balance_transaction_expanded.json";
    public static final String STRIPE_PAYMENT_METHOD_SUCCESS_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/create_payment_method_success_response.json";
    public static final String STRIPE_CUSTOMER_SUCCESS_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/create_customer_success_response.json";
    public static final String STRIPE_TOKEN_SUCCESS_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/create_token_success_response.json";
    public static final String STRIPE_ERROR_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/error_response.json";
    public static final String STRIPE_REFUND_FULL_CHARGE_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/refund_full_charge.json";
    public static final String STRIPE_TRANSFER_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/transfer_success_response.json";
    public static final String STRIPE_PAYOUT_NOTIFICATION = TEMPLATE_BASE_NAME + "/stripe/payout_notification.json";
    public static final String STRIPE_SEARCH_PAYMENT_INTENTS_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/search_query_response.json";
    public static final String STRIPE_NOTIFICATION_PAYMENT_INTENT = TEMPLATE_BASE_NAME + "/stripe/notification_payment_intent.json";
    public static final String STRIPE_NOTIFICATION_PAYMENT_INTENT_PAYMENT_FAILED = TEMPLATE_BASE_NAME + "/stripe/notification_payment_intent_payment_failed.json";
    public static final String STRIPE_NOTIFICATION_ACCOUNT_UPDATED = TEMPLATE_BASE_NAME + "/stripe/account_updated.json";
    public static final String STRIPE_NOTIFICATION_CHARGE_REFUND_UPDATED = TEMPLATE_BASE_NAME + "/stripe/charge_refund_updated.json";
    public static final String STRIPE_NOTIFICATION_CHARGE_DISPUTE = TEMPLATE_BASE_NAME + "/stripe/charge_dispute.json";
    public static final String STRIPE_NOTIFICATION_CHARGE_DISPUTE_LOST_WITH_MULTIPLE_BALANCE_TRANSACTIONS = TEMPLATE_BASE_NAME + "/stripe/charge_dispute_lost_with_multiple_balance_transactions.json";
    public static final String STRIPE_SUBMIT_DISPUTE_EVIDENCE_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/dispute_submit_evidence_response.json";
    public static final String STRIPE_SEARCH_TRANSFERS_FOR_CAPTURED_PAYMENT_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/search_transfers_for_captured_payment_response.json";
    public static final String STRIPE_SEARCH_TRANSFERS_EMPTY_RESPONSE = TEMPLATE_BASE_NAME + "/stripe/search_transfers_empty_response.json";
    

    public static final String SQS_SEND_MESSAGE_RESPONSE = TEMPLATE_BASE_NAME + "/sqs/send-message-response.json";

    public static String load(String location) {
        return TestResourceLoader.loadResource(location);
    }

}
