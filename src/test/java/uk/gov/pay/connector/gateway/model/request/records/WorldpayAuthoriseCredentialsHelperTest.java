package uk.gov.pay.connector.gateway.model.request.records;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

class WorldpayAuthoriseCredentialsHelperTest {

    private final WorldpayAuthoriseCredentialsHelper worldpayAuthoriseCredentialsHelper = new WorldpayAuthoriseCredentialsHelper();
    
    @Test
    void returnsOneOffCredentials(){
        String merchantCode = "a-merchant-code";
        String username = "a-username";
        String password = "a-password"; //        pragma: allowlist secret
        GatewayAccountEntity gatewayAccountEntity = GatewayAccountEntityFixture.aGatewayAccountEntity().build();
        GatewayAccountCredentialsEntityFixture gatewayAccountCredentialsEntityFixture = aGatewayAccountCredentialsEntity()
                .withState(ACTIVE)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withCredentials(Map.of(
                    ONE_OFF_CUSTOMER_INITIATED, Map.of(
                        CREDENTIALS_MERCHANT_CODE, merchantCode,
                        CREDENTIALS_USERNAME, username,
                        CREDENTIALS_PASSWORD, password)));

        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = gatewayAccountCredentialsEntityFixture.build();
        
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withDescription("DESCRIPTION")
                .withReference(ServicePaymentReference.of("REFERENCE"))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .build();

        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = CardAuthorisationGatewayRequest.valueOf(chargeEntity, null);

        WorldpayMerchantCodeCredentials expected = new WorldpayMerchantCodeCredentials(merchantCode, username, password);

        WorldpayMerchantCodeCredentials actual = worldpayAuthoriseCredentialsHelper.getOneOffCredentials(cardAuthorisationGatewayRequest);
        
        assertThat(actual, is(expected));
    }
}
