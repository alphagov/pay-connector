package uk.gov.pay.connector.gatewayaccountcredentials.resource;

import com.fasterxml.jackson.databind.JsonNode;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentialsRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.service.payments.commons.api.exception.ValidationException;
import uk.gov.service.payments.commons.api.validation.JsonPatchRequestValidator;
import uk.gov.service.payments.commons.api.validation.PatchPathOperation;
import uk.gov.service.payments.commons.model.jsonpatch.JsonPatchOp;
import uk.gov.service.payments.commons.model.jsonpatch.JsonPatchRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.util.stream.Collectors.toUnmodifiableList;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentialsRequest.PAYMENT_PROVIDER_FIELD_NAME;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
import static uk.gov.service.payments.commons.api.validation.JsonPatchRequestValidator.throwIfValueNotString;

public class GatewayAccountCredentialsRequestValidator {

    private static final Pattern WORLDPAY_MERCHANT_ID_PATTERN = Pattern.compile("[0-9a-f]{15}");
    private static final List<String> WORLDPAY_CREDENTIALS_KEYS = List.of("merchant_code", "username", "password");
    private static final List<String> STRIPE_CREDENTIALS_KEYS = List.of("stripe_account_id");
    private static final List<String> EPDQ_CREDENTIALS_KEYS = List.of("merchant_id", "username", "password",
            "sha_in_passphrase", "sha_out_passphrase");

    public static final String FIELD_CREDENTIALS = "credentials";
    public static final String FIELD_CREDENTIALS_WORLDPAY_ONE_OFF_CUSTOMER_INITIATED = "credentials/worldpay/one_off_customer_initiated";
    public static final String FIELD_CREDENTIALS_WORLDPAY_RECURRING_CUSTOMER_INITIATED = "credentials/worldpay/recurring_customer_initiated";
    public static final String FIELD_CREDENTIALS_WORLDPAY_RECURRING_MERCHANT_INITIATED = "credentials/worldpay/recurring_merchant_initiated";
    public static final String FIELD_LAST_UPDATED_BY_USER = "last_updated_by_user_external_id";
    public static final String FIELD_STATE = "state";
    public static final String FIELD_GATEWAY_MERCHANT_ID = "gateway_merchant_id";
    public static final String GATEWAY_MERCHANT_ID_PATH = "credentials/gateway_merchant_id";

    public void validateCreate(GatewayAccountCredentialsRequest gatewayAccountCredentialsRequest) {
        if (gatewayAccountCredentialsRequest.getPaymentProvider() == null) {
            throw new ValidationException(List.of("Field(s) missing: [" + PAYMENT_PROVIDER_FIELD_NAME + ']'));
        }

        var paymentGatewayName = PaymentGatewayName.valueFrom(gatewayAccountCredentialsRequest.getPaymentProvider());

        if (!(paymentGatewayName == WORLDPAY || paymentGatewayName == STRIPE)) {
            throw new ValidationException(List.of("Operation not supported for payment provider '"
                    + paymentGatewayName.getName() + "'"));
        }

        Map<String, String> credentials = gatewayAccountCredentialsRequest.getCredentialsAsMap();
        if (credentials != null) {
            if (paymentGatewayName == WORLDPAY) {
                throw new ValidationException(List.of("Field [credentials] is not supported for payment provider Worldpay"));
            }
            
            List<String> missingCredentialsFields = getMissingCredentialsFields(credentials, paymentGatewayName, FIELD_CREDENTIALS);
            if (!missingCredentialsFields.isEmpty()) {
                throw new ValidationException(List.of("Field(s) missing: [" + String.join(", ", missingCredentialsFields) + ']'));
            }
        }
    }

    public void validatePatch(JsonNode patchRequest, String paymentProvider, GatewayCredentials credentials) {
        var paymentGatewayName = PaymentGatewayName.valueFrom(paymentProvider);

        Map<PatchPathOperation, Consumer<JsonPatchRequest>> operationValidators = Map.of(
                new PatchPathOperation(FIELD_CREDENTIALS, JsonPatchOp.REPLACE),
                        operation -> validateReplaceCredentialsOperation(operation, paymentGatewayName,
                                FIELD_CREDENTIALS),

                new PatchPathOperation(FIELD_CREDENTIALS_WORLDPAY_ONE_OFF_CUSTOMER_INITIATED, JsonPatchOp.REPLACE),
                        operation -> validateReplaceCredentialsOperation(operation, paymentGatewayName,
                                FIELD_CREDENTIALS_WORLDPAY_ONE_OFF_CUSTOMER_INITIATED),

                new PatchPathOperation(FIELD_CREDENTIALS_WORLDPAY_RECURRING_CUSTOMER_INITIATED, JsonPatchOp.REPLACE),
                        operation -> validateReplaceCredentialsOperation(operation, paymentGatewayName,
                                FIELD_CREDENTIALS_WORLDPAY_RECURRING_CUSTOMER_INITIATED),

                new PatchPathOperation(FIELD_CREDENTIALS_WORLDPAY_RECURRING_MERCHANT_INITIATED, JsonPatchOp.REPLACE),
                        operation -> validateReplaceCredentialsOperation(operation, paymentGatewayName,
                                FIELD_CREDENTIALS_WORLDPAY_RECURRING_MERCHANT_INITIATED),

                new PatchPathOperation(FIELD_LAST_UPDATED_BY_USER, JsonPatchOp.REPLACE),
                        JsonPatchRequestValidator::throwIfValueNotString,

                new PatchPathOperation(FIELD_STATE, JsonPatchOp.REPLACE), this::validateReplaceStateOperation,

                new PatchPathOperation(GATEWAY_MERCHANT_ID_PATH, JsonPatchOp.REPLACE),
                        request -> validateGatewayMerchantId(request, paymentProvider, credentials)
        );

        var patchRequestValidator = new JsonPatchRequestValidator(operationValidators);
        patchRequestValidator.validate(patchRequest);
    }

    private void validateReplaceCredentialsOperation(JsonPatchRequest request, PaymentGatewayName paymentProvider, String path) {
        Map<String, String> credentialsMap = request.valueAsObject();
        List<String> missingCredentialsFields = getMissingCredentialsFields(credentialsMap, paymentProvider, path);
        if (!missingCredentialsFields.isEmpty()) {
            throw new ValidationException(List.of(format("Value for path [%s] is missing field(s): [%s]", path,
                    String.join(", ", missingCredentialsFields))));
        }
    }

    private List<String> getMissingCredentialsFields(Map<String, String> credentials, PaymentGatewayName paymentProvider, String path) {
        return getRequiredCredentialsFields(paymentProvider, path)
                .stream()
                .filter(requiredField -> !credentials.containsKey(requiredField))
                .collect(toUnmodifiableList());
    }

    private List<String> getRequiredCredentialsFields(PaymentGatewayName paymentProvider, String path) {
        switch (paymentProvider) {
            case WORLDPAY:
                if (FIELD_CREDENTIALS.equals(path)) {
                    throw new UnsupportedOperationException(format("Path [%s] is not supported for updating Worldpay credentials", path));
                }
                return WORLDPAY_CREDENTIALS_KEYS;
            case STRIPE:
                return STRIPE_CREDENTIALS_KEYS;
            case EPDQ:
                return EPDQ_CREDENTIALS_KEYS;
            case SMARTPAY:
            case SANDBOX:
            default:
                throw new UnsupportedOperationException("Cannot perform operation for payment provider [ " + paymentProvider.getName() + ']');
        }
    }

    private void validateReplaceStateOperation(JsonPatchRequest request) {
        throwIfValueNotString(request);
        if (!request.valueAsString().equals(VERIFIED_WITH_LIVE_PAYMENT.name())) {
            throw new ValidationException(Collections.singletonList(format("Operation with path [%s] can only be used to update state to [%s]",
                    request.getPath(), VERIFIED_WITH_LIVE_PAYMENT.name())));
        }
    }

    private void validateGatewayMerchantId(JsonPatchRequest request, String paymentProvider,
                                           GatewayCredentials gatewayCredentials) {
        if (!WORLDPAY.getName().equals(paymentProvider)) {
            throw new ValidationException(List.of("Gateway Merchant ID is not applicable for payment provider '" + paymentProvider + "'."));
        }
        if (!gatewayCredentials.hasCredentials()) {
            throw new ValidationException(List.of("Account credentials are required to set a Gateway Merchant ID."));
        }

        throwIfValueNotString(request);
        throwIfNotValidMerchantId(request.valueAsString(), GATEWAY_MERCHANT_ID_PATH);
    }

    private void throwIfNotValidMerchantId(String value, String field) {
        Matcher matcher = WORLDPAY_MERCHANT_ID_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new ValidationException(List.of(format("Field [%s] value [%s] does not match that expected for a "
                    + "Worldpay Merchant ID; should be 15 characters and within range [0-9a-f]", field, value)));
        }
    }

}
