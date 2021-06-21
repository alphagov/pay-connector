package uk.gov.pay.connector.gatewayaccountcredentials.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.common.exception.ValidationException;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchOp;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchRequest;
import uk.gov.pay.connector.common.validator.JsonPatchRequestValidator;
import uk.gov.pay.connector.common.validator.PatchPathOperation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentialsRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentialsRequest.PAYMENT_PROVIDER_FIELD_NAME;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;

public class GatewayAccountCredentialsRequestValidator {

    private final Map<String, List<String>> providerCredentialFields;
    private final Joiner COMMA_JOINER = Joiner.on(", ");

    public static final String FIELD_CREDENTIALS = "credentials";
    public static final String FIELD_LAST_UPDATED_BY_USER = "last_updated_by_user_external_id";
    public static final String FIELD_STATE = "state";

    @Inject
    public GatewayAccountCredentialsRequestValidator(ConnectorConfiguration configuration) {
        // validator hooked into dependency injection as it needs access to top level connector configuration
        providerCredentialFields = newHashMap();
        providerCredentialFields.put("worldpay", configuration.getWorldpayConfig().getCredentials());
        providerCredentialFields.put("smartpay", configuration.getSmartpayConfig().getCredentials());
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

    public void validatePatch(JsonNode patchRequest, String paymentProvider) {
        Map<PatchPathOperation, Consumer<JsonPatchRequest>> operationValidators = Map.of(
                new PatchPathOperation(FIELD_CREDENTIALS, JsonPatchOp.REPLACE), (operation) -> validateReplaceCredentialsOperation(operation, paymentProvider),
                new PatchPathOperation(FIELD_LAST_UPDATED_BY_USER, JsonPatchOp.REPLACE), JsonPatchRequestValidator::throwIfValueNotString,
                new PatchPathOperation(FIELD_STATE, JsonPatchOp.REPLACE), this::validateReplaceStateOperation
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
        JsonPatchRequestValidator.throwIfValueNotString(request);

        if (!request.valueAsString().equals(VERIFIED_WITH_LIVE_PAYMENT.name())) {
            throw new ValidationException(Collections.singletonList(format("Operation with path [%s] can only be used to update state to [%s]",
                    request.getPath(), VERIFIED_WITH_LIVE_PAYMENT.name())));
        }
    }
}
