package uk.gov.pay.connector.gateway.adyen.handler;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.adyen.request.AdyenDeleteStoredPaymentDetailsRequest;
import uk.gov.pay.connector.gateway.model.request.DeleteStoredPaymentDetailsGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.records.AdyenMerchantAccountHelper;

import java.util.Map;

import static uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory.STORED_PAYMENT_METHOD_ID;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getApiKeyHeader;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getDeleteStoredPaymentMethodUrl;

public class AdyenDeleteStoredPaymentHandler {
    
    private final GatewayClient gatewayClient;
    private final AdyenGatewayConfig adyenGatewayConfig;
    private final AdyenMerchantAccountHelper adyenMerchantAccountHelper;


    public AdyenDeleteStoredPaymentHandler(GatewayClient gatewayClient,
                                           ConnectorConfiguration connectorConfig) {
        this.gatewayClient = gatewayClient;
        this.adyenGatewayConfig = connectorConfig.getAdyenGatewayConfig();
        adyenMerchantAccountHelper = new AdyenMerchantAccountHelper(connectorConfig);

    }

    public void deleteStoredPaymentDetails(DeleteStoredPaymentDetailsGatewayRequest request) throws GatewayException {
        String storedPaymentMethodId = request.getRecurringAuthToken().get(STORED_PAYMENT_METHOD_ID);
        if (storedPaymentMethodId == null || storedPaymentMethodId.isBlank()) {
            throw new IllegalArgumentException("Adyen recurring auth token is missing storedPaymentMethodId");
        }

        var deleteRequest = new AdyenDeleteStoredPaymentDetailsRequest(
                getDeleteStoredPaymentMethodUrl(adyenGatewayConfig, request.isLive(), storedPaymentMethodId),
                getApiKeyHeader(adyenGatewayConfig, request.isLive()),
                Map.of(
                        "merchantAccount", adyenMerchantAccountHelper.getMerchantAccount(request.isLive()),
                        "shopperReference", request.getAgreementExternalId()
                ),
                request.getGatewayAccountType());

        gatewayClient.deleteRequestFor(deleteRequest);
    }
}
