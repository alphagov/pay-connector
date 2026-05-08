package uk.gov.pay.connector.it.contract;

import com.codahale.metrics.MetricFilter;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.adyen.AdyenPaymentProvider;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.UUID.randomUUID;
import static junit.framework.TestCase.assertTrue;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

@Ignore
public class AdyenPaymentProviderTest {

    private static final Long chargeAmount = 500L;

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(false);

    private AdyenPaymentProvider adyenPaymentProvider;

    private GatewayAccountEntity gatewayAccountEntity;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

    private final String validCardExpiryDate = ZonedDateTime.now().plusYears(1).format(ofPattern("MM/yy"));

    @Before
    public void setup() {
        // set required env variables
        envOrThrow("GDS_CONNECTOR_ADYEN_MERCHANT_ACCOUNT_ID_TEST");
        envOrThrow("GDS_CONNECTOR_ADYEN_COMPANY_ACCOUNT_API_KEY_TEST");
        app.getEnvironment().metrics().removeMatching(MetricFilter.ALL); // stops the duplicated metric error
        
        

        adyenPaymentProvider = app.getInstanceFromGuiceContainer(AdyenPaymentProvider.class);
        gatewayAccountEntity = new GatewayAccountEntity();
        gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("legal_entity_id","legal-entity-id","store_id", "store-id"))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(ADYEN.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));
        gatewayAccountEntity.setId(123L);
        gatewayAccountEntity.setType(TEST);
    }

    @Test
    public void authoriseAPayment() throws GatewayException {
        GatewayResponse gatewayResponse = authorisePayment();
        assertTrue(gatewayResponse.getBaseResponse().isPresent());
    }

    private GatewayResponse<BaseAuthoriseResponse> authorisePayment() throws GatewayException {
        ChargeEntity charge = getCharge();
        CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(charge,
                anAuthCardDetails()
                        .withCardNo("4111112014267661")
                        .withCvc("737")
                        .withEndDate(CardExpiryDate.valueOf("12/30"))
                        .build());
        return adyenPaymentProvider.authorise(request, charge);
    }

    private ChargeEntity getCharge() {
        return aValidChargeEntity()
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withAmount(chargeAmount)
                .withTransactionId(randomUUID().toString())
                .withDescription("Adyen payment provider test charge")
                .build();
    }
}
