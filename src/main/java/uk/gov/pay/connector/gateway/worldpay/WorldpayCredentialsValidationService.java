package uk.gov.pay.connector.gateway.worldpay;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.worldpay.exception.UnexpectedValidateCredentialsResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayValidatableCredentials;

import javax.inject.Named;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getWorldpayCredentialsCheckAuthHeader;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayInquiryRequestBuilder;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;
import static uk.gov.service.payments.logging.LoggingKeys.REMOTE_HTTP_STATUS;

public class WorldpayCredentialsValidationService implements WorldpayGatewayResponseGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldpayCredentialsValidationService.class);

    private final Map<String, URI> gatewayUrlMap;
    private final GatewayClient gatewayClient;

    @Inject
    public WorldpayCredentialsValidationService(@Named("WorldpayGatewayUrlMap") Map<String, URI> gatewayUrlMap,
                                                @Named("WorldpayValidateCredentialsGatewayClient") GatewayClient gatewayClient) {
        this.gatewayUrlMap = gatewayUrlMap;
        this.gatewayClient = gatewayClient;
    }

    public boolean validateCredentials(GatewayAccountEntity gatewayAccountEntity, WorldpayValidatableCredentials worldpayValidatableCredentials) {
        GatewayOrder order = aWorldpayInquiryRequestBuilder()
                .withTransactionId("an-order-id-that-will-not-exist")
                .withMerchantCode(worldpayValidatableCredentials.getMerchantId())
                .build();

        try {
            GatewayClient.Response response = gatewayClient.postRequestFor(
                    gatewayUrlMap.get(gatewayAccountEntity.getType()),
                    PaymentGatewayName.WORLDPAY,
                    gatewayAccountEntity.getType(),
                    order,
                    Collections.emptyList(),
                    getWorldpayCredentialsCheckAuthHeader(worldpayValidatableCredentials)
            );

            // There is no Worldpay endpoint to explicitly check credentials, so we make a query for a non-existent 
            // transaction and expect:
            // - a 401 if username/password are incorrect
            // - a 200 with error code 5 (transaction not found) in the response if all credentials are correct
            // - a 200 with error code 4 (security violation) if merchant code is incorrect
            WorldpayQueryResponse worldpayQueryResponse = unmarshallResponse(response, WorldpayQueryResponse.class);
            switch (worldpayQueryResponse.getErrorCode()) {
                case "5":
                    LOGGER.info(format("Worldpay credentials for gateway account %s passed validation: received error code 5 (%s), " +
                                            "which means credentials are correct",
                                    gatewayAccountEntity.getId(), worldpayQueryResponse.getErrorMessage()),
                            kv(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId()),
                            kv(GATEWAY_ACCOUNT_TYPE, gatewayAccountEntity.getType()),
                            kv(PROVIDER, PaymentGatewayName.WORLDPAY.getName()));
                    return true;
                case "4":
                    LOGGER.info(format("Worldpay credentials for gateway account %s failed validation: received error code 4 (%s), " +
                                            "which means username and password are likely correct but merchant code may be incorrect",
                                    gatewayAccountEntity.getId(), worldpayQueryResponse.getErrorMessage()),
                            kv(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId()),
                            kv(GATEWAY_ACCOUNT_TYPE, gatewayAccountEntity.getType()),
                            kv(PROVIDER, PaymentGatewayName.WORLDPAY.getName()));
                    return false;
                default:
                    LOGGER.error(format("Error validating Worldpay credentials for gateway account %s: unexpected error code %s (%s)",
                                    gatewayAccountEntity.getId(), worldpayQueryResponse.getErrorCode(), worldpayQueryResponse.getErrorMessage()),
                            kv(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId()),
                            kv(GATEWAY_ACCOUNT_TYPE, gatewayAccountEntity.getType()),
                            kv(PROVIDER, PaymentGatewayName.WORLDPAY.getName()));
                    throw new UnexpectedValidateCredentialsResponse();
            }
        } catch (GatewayException.GatewayErrorException e) {
            return e.getStatus().map(status -> {
                if (status == SC_UNAUTHORIZED) {
                    LOGGER.info(format("Worldpay credentials for gateway account %s failed validation: received HTTP 401, " +
                                            "which means username and password are likely incorrect",
                                    gatewayAccountEntity.getId()),
                            kv(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId()),
                            kv(GATEWAY_ACCOUNT_TYPE, gatewayAccountEntity.getType()),
                            kv(PROVIDER, PaymentGatewayName.WORLDPAY.getName()),
                            kv(REMOTE_HTTP_STATUS, SC_UNAUTHORIZED));
                    return false;
                }
                throw new UnexpectedValidateCredentialsResponse();
            }).orElseThrow(UnexpectedValidateCredentialsResponse::new);
        } catch (GatewayException e) {
            throw new UnexpectedValidateCredentialsResponse();
        }
    }
}
