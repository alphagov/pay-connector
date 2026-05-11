package uk.gov.pay.connector.it.contract;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.adyen.AdyenPaymentProvider;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.List;
import java.util.Map;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

/**
 * This is an integration test with Adyen that can be run locally, but is ignored and so it won't be run by CI.
 * In order to make it work you need to set the following environment variables:
 * - GDS_CONNECTOR_ADYEN_MERCHANT_ACCOUNT_ID_TEST: set this to the Gov pay merchant account ID which can be found in Adyen test account dashboard 
 * - GDS_CONNECTOR_ADYEN_COMPANY_ACCOUNT_API_KEY_TEST: set this to the "Payments API Key" for the Adyen test environment
 * To run tests using test runner, add env variables to the DropwizardAppWithPostgresRule parameters as config overrides like so:
 * - ConfigOverride.config("adyen.merchantAccountIds.test", gdsConnectorAdyenMerchantAccountIdTest),
 * - ConfigOverride.config("adyen.apiKeys.companyAccount.test", gdsConnectorAdyenCompanyAccountApiKeyTest)
 * If you get an error referring to duplicated metrics, add app.getAppRule().getEnvironment().metrics().removeMatching(MetricFilter.ALL); to the setup method 
 */

@Disabled
public class AdyenPaymentProviderTest {

    private static final Long AMOUNT = 500L;

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    private AdyenPaymentProvider adyenPaymentProvider;

    private GatewayAccountEntity gatewayAccountEntity;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

    @BeforeEach
    void setup() {
        envOrThrow("GDS_CONNECTOR_ADYEN_MERCHANT_ACCOUNT_ID_TEST");
        envOrThrow("GDS_CONNECTOR_ADYEN_COMPANY_ACCOUNT_API_KEY_TEST");
        
        adyenPaymentProvider = app.getInstanceFromGuiceContainer(AdyenPaymentProvider.class);
        gatewayAccountEntity = new GatewayAccountEntity();
        gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("legal_entity_id", "legal-entity-id"))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(ADYEN.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));
        gatewayAccountEntity.setId(123L);
        gatewayAccountEntity.setType(TEST);
    }

    @Test
    void authoriseAPaymentWithFullBillingAddress() throws GatewayException {
        var fullAddress = new Address(
                "line1",
                "line2",
                "postcode",
                "city",
                "county",
                "GB"
        );
        GatewayResponse gatewayResponse = authorisePayment(fullAddress);
        Assertions.assertTrue(gatewayResponse.getBaseResponse().isPresent());
    }

    @Test
    void authoriseAPaymentWithNoBillingAddress() throws GatewayException {
        GatewayResponse gatewayResponse = authorisePayment(null);
        Assertions.assertTrue(gatewayResponse.getBaseResponse().isPresent());
    }

    @Test
    void authoriseAPaymentWithPartialBillingAddress() throws GatewayException {
        var partialAddress = new Address("line1",
                "line2",
                null,
                null,
                null,
                null);

        GatewayResponse gatewayResponse = authorisePayment(partialAddress);
        Assertions.assertTrue(gatewayResponse.getBaseResponse().isPresent());
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
