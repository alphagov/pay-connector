package uk.gov.pay.connector.gatewayaccount.resource;

import com.google.common.base.Joiner;
import com.google.inject.Inject;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.common.exception.ValidationException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentialsRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentialsRequest.PAYMENT_PROVIDER_FIELD_NAME;

public class GatewayAccountCredentialsRequestValidator {

    private final Map<String, List<String>> providerCredentialFields;
    private final Joiner COMMA_JOINER = Joiner.on(", ");

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
        return providerCredentialFields.get(paymentProvider).stream()
                .filter(requiredField -> !credentials.containsKey(requiredField))
                .collect(Collectors.toList());
    }
}
