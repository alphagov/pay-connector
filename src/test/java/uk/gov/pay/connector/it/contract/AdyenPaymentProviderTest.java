package uk.gov.pay.connector.it.contract;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.adyen.AdyenPaymentProvider;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.List;
import java.util.Map;

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

    private static final Long AMOUNT = 500L;

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            false);

    private AdyenPaymentProvider adyenPaymentProvider;

    private GatewayAccountEntity gatewayAccountEntity;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

    @Before
    public void setup() {
        envOrThrow("GDS_CONNECTOR_ADYEN_MERCHANT_ACCOUNT_ID_TEST");
        envOrThrow("GDS_CONNECTOR_ADYEN_COMPANY_ACCOUNT_API_KEY_TEST");

        adyenPaymentProvider = app.getInstanceFromGuiceContainer(AdyenPaymentProvider.class);
        gatewayAccountEntity = new GatewayAccountEntity();
        gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("legal_entity_id", "legal-entity-id")) // no store-id
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(ADYEN.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));
        gatewayAccountEntity.setId(123L);
        gatewayAccountEntity.setType(TEST);
    }

    @Test
    public void authoriseAPaymentWithFullBillingAddress() throws GatewayException {
        var fullAddress = new Address(
                "line1",
                "line2",
                "postcode",
                "city",
                "county",
                "GB"
        );
        GatewayResponse gatewayResponse = authorisePayment(fullAddress);
        assertTrue(gatewayResponse.getBaseResponse().isPresent());
    }

    @Test
    public void authoriseAPaymentWithNoBillingAddress() throws GatewayException {
        GatewayResponse gatewayResponse = authorisePayment(null);
        assertTrue(gatewayResponse.getBaseResponse().isPresent());
    }

    @Test
    public void authoriseAPaymentWithPartialBillingAddress() throws GatewayException {
        var partialAddress = new Address("line1",
                "line2",
                null,
                null,
                null,
                null);

        GatewayResponse gatewayResponse = authorisePayment(partialAddress);
        assertTrue(gatewayResponse.getBaseResponse().isPresent());
    }

    private GatewayResponse<BaseAuthoriseResponse> authorisePayment(Address address) throws GatewayException {
        ChargeEntity charge = getCharge();
        CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(charge,
                anAuthCardDetails()
                        .withCardNo("4111112014267661")
                        .withCvc("737")
                        .withAddress(address)
                        .withEndDate(CardExpiryDate.valueOf("12/30"))
                        .build());
        return adyenPaymentProvider.authorise(request, charge);
    }

    private ChargeEntity getCharge() {
        return aValidChargeEntity()
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withAmount(AMOUNT)
                .withTransactionId(randomUUID().toString())
                .withDescription("Adyen payment provider test charge")
                .build();
    }
}
