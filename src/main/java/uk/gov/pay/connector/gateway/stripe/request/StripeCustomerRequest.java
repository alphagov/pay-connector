package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;

import java.util.Map;

import static java.util.Map.entry;

public class StripeCustomerRequest extends StripePostRequest {

    private static final String GOVUK_PAY_AGREEMENT_EXTERNAL_ID = "govuk_pay_agreement_external_id";
    
    private final String name;
    private final String description;
    private final String agreementExternalId;

    private StripeCustomerRequest(
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            AuthCardDetails authCardDetails,
            AgreementEntity agreement,
            GatewayCredentials credentials) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        name = authCardDetails.getCardHolder();
        description = agreement.getDescription();
        agreementExternalId = agreement.getExternalId();
    }

    public static StripeCustomerRequest of(CardAuthorisationGatewayRequest request, StripeGatewayConfig config, AgreementEntity agreement) {
        return new StripeCustomerRequest(
                request.getGatewayAccount(),
                request.getGovUkPayPaymentId(),
                config,
                request.getAuthCardDetails(),
                agreement,
                request.getGatewayCredentials()
        );
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    protected Map<String, String> params() {
        return Map.ofEntries(
                entry("name", name),
                entry("description", description),
                entry(String.format("metadata[%s]", GOVUK_PAY_AGREEMENT_EXTERNAL_ID), agreementExternalId));
    }

    @Override
    protected String urlPath() {
        return "/v1/customers";
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.AUTHORISE;
    }

    @Override
    protected String idempotencyKeyType() {
        return "customer";
    }

}
