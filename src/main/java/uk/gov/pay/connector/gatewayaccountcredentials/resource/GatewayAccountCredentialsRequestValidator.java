package uk.gov.pay.connector.gatewayaccountcredentials.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentialsRequest;
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
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentialsRequest.PAYMENT_PROVIDER_FIELD_NAME;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
import static uk.gov.service.payments.commons.api.validation.JsonPatchRequestValidator.throwIfValueNotString;

public class GatewayAccountCredentialsRequestValidator {

    private final Map<String, List<String>> providerCredentialFields;
    private final Joiner COMMA_JOINER = Joiner.on(", ");
    private static final Pattern WORLDPAY_MERCHANT_ID_PATTERN = Pattern.compile("[0-9a-f]{15}");

    public static final String FIELD_CREDENTIALS = "credentials";
    public static final String FIELD_CREDENTIALS_WORLDPAY_ONE_OFF_CUSTOMER_INITIATED = "credentials/worldpay/one_off_customer_initiated";
    public static final String FIELD_CREDENTIALS_WORLDPAY_RECURRING_CUSTOMER_INITIATED = "credentials/worldpay/recurring_customer_initiated";
    public static final String FIELD_CREDENTIALS_WORLDPAY_RECURRING_MERCHANT_INITIATED = "credentials/worldpay/recurring_merchant_initiated";
    public static final String FIELD_LAST_UPDATED_BY_USER = "last_updated_by_user_external_id";
    public static final String FIELD_STATE = "state";
    public static final String FIELD_GATEWAY_MERCHANT_ID = "gateway_merchant_id";
    public static final String GATEWAY_MERCHANT_ID_PATH = "credentials/gateway_merchant_id";

    @Inject
    public GatewayAccountCredentialsRequestValidator(ConnectorConfiguration configuration) {
        // validator hooked into dependency injection as it needs access to top level connector configuration
        providerCredentialFields = newHashMap();
        providerCredentialFields.put("worldpay", configuration.getWorldpayConfig().getCredentials());
        providerCredentialFields.put("epdq", configuration.getEpdqConfig().getCredentials());
        providerCredentialFields.put("stripe", configuration.getStripeConfig().getCredentials());
    }

    private void validatePaymentProvider(String paymentProvider) {
        if (paymentProvider == null) {
            throw new ValidationException(Collections.singletonList(format("Field(s) missing: [%s]", PAYMENT_PROVIDER_FIELD_NAME)));
        }
        if (!(paymentProvider.equals(WORLDPAY.getName()) || paymentProvider.equals(STRIPE.getName()))) {
            throw new ValidationException(Collections.singletonList(format("Operation not supported for payment provider '%s'", paymentProvider)));
        }
    }

    private void validateCredentialsForPaymentProvider(Map<String, String> credentials, String paymentProvider) {
        if (credentials != null) {
            List<String> missingCredentialsFields = getMissingCredentialsFields(credentials, paymentProvider);
            if (!missingCredentialsFields.isEmpty()) {
                throw new ValidationException(Collections.singletonList(format("Field(s) missing: [%s]", COMMA_JOINER.join(missingCredentialsFields))));
            }
        }
    }

    public void validateCreate(GatewayAccountCredentialsRequest gatewayAccountCredentialsRequest) {
        validatePaymentProvider(gatewayAccountCredentialsRequest.getPaymentProvider());
        validateCredentialsForPaymentProvider(gatewayAccountCredentialsRequest.getCredentialsAsMap(), gatewayAccountCredentialsRequest.getPaymentProvider());
    }

    public List<String> getMissingCredentialsFields(Map<String, String> credentials, String paymentProvider) {
        if (!providerCredentialFields.containsKey(paymentProvider)) {
            throw new UnsupportedOperationException(format("Cannot perform operation for payment provider [%s]", paymentProvider));
        }
        return providerCredentialFields.get(paymentProvider).stream()
                .filter(requiredField -> !credentials.containsKey(requiredField))
                .collect(Collectors.toList());
    }

    public void validatePatch(JsonNode patchRequest, String paymentProvider, Map<String, Object> credentials) {
        Map<PatchPathOperation, Consumer<JsonPatchRequest>> operationValidators = Map.of(
                new PatchPathOperation(FIELD_CREDENTIALS, JsonPatchOp.REPLACE), operation -> validateReplaceCredentialsOperation(operation, paymentProvider),
                new PatchPathOperation(FIELD_CREDENTIALS_WORLDPAY_ONE_OFF_CUSTOMER_INITIATED, JsonPatchOp.REPLACE), operation -> validateReplaceCredentialsOperation(operation, paymentProvider),
                new PatchPathOperation(FIELD_CREDENTIALS_WORLDPAY_RECURRING_CUSTOMER_INITIATED, JsonPatchOp.REPLACE), operation -> validateReplaceCredentialsOperation(operation, paymentProvider),
                new PatchPathOperation(FIELD_CREDENTIALS_WORLDPAY_RECURRING_MERCHANT_INITIATED, JsonPatchOp.REPLACE), operation -> validateReplaceCredentialsOperation(operation, paymentProvider),
                new PatchPathOperation(FIELD_LAST_UPDATED_BY_USER, JsonPatchOp.REPLACE), JsonPatchRequestValidator::throwIfValueNotString,
                new PatchPathOperation(FIELD_STATE, JsonPatchOp.REPLACE), this::validateReplaceStateOperation,
                new PatchPathOperation(GATEWAY_MERCHANT_ID_PATH, JsonPatchOp.REPLACE), request -> validateGatewayMerchantId(request, paymentProvider, credentials)
        );
        var patchRequestValidator = new JsonPatchRequestValidator(operationValidators);
        patchRequestValidator.validate(patchRequest);
    }

    private void validateReplaceCredentialsOperation(JsonPatchRequest request, String paymentProvider) {
        Map<String, String> credentialsMap = request.valueAsObject();
        List<String> missingCredentialsFields = getMissingCredentialsFields(credentialsMap, paymentProvider);
        if (!missingCredentialsFields.isEmpty()) {
            throw new ValidationException(Collections.singletonList(format("Value for path [%s] is missing field(s): [%s]",
                    FIELD_CREDENTIALS, COMMA_JOINER.join(missingCredentialsFields))));
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
                                           Map<String, Object> credentials) {
        if (!WORLDPAY.getName().equals(paymentProvider)) {
            throw new ValidationException(List.of(format("Gateway '%s' does not support digital wallets.", paymentProvider)));
        }
        if (credentials.isEmpty()) {
            throw new ValidationException(List.of("Account credentials are required to set a Gateway Merchant ID."));
        }

        throwIfValueNotString(request);
        throwIfNotValidMerchantId(request.valueAsString(), GATEWAY_MERCHANT_ID_PATH);
    }

    private void throwIfNotValidMerchantId(String value, String field) {
        Matcher matcher = WORLDPAY_MERCHANT_ID_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new ValidationException(Collections.singletonList(format("Field [%s] value [%s] does not match that expected for a Worldpay Merchant ID; should be 15 characters and within range [0-9a-f]", field, value)));
        }
    }
}
