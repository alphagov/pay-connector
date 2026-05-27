package uk.gov.pay.connector.it.resources.adyen;

import com.adyen.model.management.PaymentMethodSetupInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.lang.String.format;
import static org.apache.http.HttpStatus.SC_BAD_GATEWAY;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_CREATE_INDIVIDUAL_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_CREATE_LEGAL_ENTITY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_PAYMENT_METHOD_REQUEST;

public class AdyenAccountResourceIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    
    @Test
    void requestingTestAccountShouldCreateAdyenEntitiesAndGatewayAccount() {
        var serviceId = RandomIdGenerator.randomUuid();
        var serviceName = "A local council";
        var legalEntityId = "LE00000000000000000000001";
        var merchantId = "adyen-test-merchant-account-id";
        var termsOfServiceDocumentId = "doc123";
        var paymentTypes = List.of(
                PaymentMethodSetupInfo.TypeEnum.VISA,
                PaymentMethodSetupInfo.TypeEnum.MC,
                PaymentMethodSetupInfo.TypeEnum.AMEX,
                PaymentMethodSetupInfo.TypeEnum.JCB,
                PaymentMethodSetupInfo.TypeEnum.MAESTRO,
                PaymentMethodSetupInfo.TypeEnum.DISCOVER,
                PaymentMethodSetupInfo.TypeEnum.DINERS,
                PaymentMethodSetupInfo.TypeEnum.CUP, // China Union Pay
                PaymentMethodSetupInfo.TypeEnum.PAYBYBANK,
                PaymentMethodSetupInfo.TypeEnum.APPLEPAY,
                PaymentMethodSetupInfo.TypeEnum.GOOGLEPAY);
        
        app.getAdyenKycMockClient().mockCreateLegalEntity();
        app.getAdyenKycMockClient().mockCreateBusinessLine();
        app.getAdyenKycMockClient().mockCreateIndividual();
        app.getAdyenKycMockClient().mockUpdateLegalEntity(legalEntityId);
        app.getAdyenKycMockClient().mockCreateTransferInstrument();
        
        app.getAdyenManagementMockClient().mockCreateStore();
        app.getAdyenManagementMockClient().mockRequestPaymentMethod(merchantId);
        
        app.getAdyenBalancePlatformMockClient().mockCreateAccountHolder();
        app.getAdyenBalancePlatformMockClient().mockCreateBalanceAccount();
        
        app.getAdyenKycMockClient().mockGetTermsOfServiceDocument(legalEntityId);
        app.getAdyenKycMockClient().mockAcceptTermsOfService(legalEntityId, termsOfServiceDocumentId);
        app.getAdyenKycMockClient().mockGeneratePciQuestionnaire(legalEntityId);
        app.getAdyenKycMockClient().mockSignPciTemplates(legalEntityId);
        
        app.givenSetup()
                .body(Map.of("service_name", serviceName))
                .post(format("/v1/api/service/%s/request-adyen-test-account", serviceId))
                .then().statusCode(SC_OK)
                .body("gateway_account_id", is(notNullValue()))
                .body("legal_entity_id", is(legalEntityId))
                .body("store_id" , is("STORE_ID_123"))
                .body("account_holder_id", is("AH3227C223222H5J4DCLW9VBV"))
                .body("balance_account_id", is("BA0000000000000000000001"));
        
        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/legalEntities"))
                .withRequestBody(equalToJson(TestTemplateResourceLoader
                        .load(ADYEN_CREATE_LEGAL_ENTITY_REQUEST)
                        .replace("{{serviceName}}", serviceName))));
        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/businessLines")));
        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/legalEntities"))
                .withRequestBody(equalToJson(TestTemplateResourceLoader
                        .load(ADYEN_CREATE_INDIVIDUAL_REQUEST))));
        app.getAdyenWireMockServer().verify(patchRequestedFor(urlEqualTo(format("/legalEntities/%s", legalEntityId))));
        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/transferInstruments")));
        
        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/stores")));
        verifyPaymentMethodsRequest(paymentTypes, merchantId);
        
        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/accountHolders")));
        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/balanceAccounts")));
        
        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo(format("/legalEntities/%s/termsOfService", legalEntityId))));
        app.getAdyenWireMockServer().verify(patchRequestedFor(urlEqualTo(format("/legalEntities/%s/termsOfService/%s", legalEntityId, termsOfServiceDocumentId))));
        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo(format("/legalEntities/%s/pciQuestionnaires/generatePciTemplates", legalEntityId))));
        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo(format("/legalEntities/%s/pciQuestionnaires/signPciTemplates", legalEntityId))));
        
        app.givenSetup().get(format("/v1/api/service/%s/account/test", serviceId))
                .then()
                .statusCode(SC_OK)
                .body("service_id", is(serviceId))
                .body("payment_provider", is(ADYEN.getName()))
                .body("type", is(TEST.toString()))
                .body("service_name", is(serviceName))
                .body("gateway_account_credentials", hasSize(1))
                .body("gateway_account_credentials[0].payment_provider", is(ADYEN.getName()))
                .body("gateway_account_credentials[0].state", is(ACTIVE.toString()))
                .body("gateway_account_credentials[0].credentials.legal_entity_id", is(legalEntityId))
                .body("gateway_account_credentials[0].credentials.store_id", is("STORE_ID_123"))
                .body("gateway_account_credentials[0].credentials.account_holder_id", is("AH3227C223222H5J4DCLW9VBV"))
                .body("gateway_account_credentials[0].credentials.balance_account_id", is("BA0000000000000000000001"))
                .body("gateway_account_credentials[0].external_id", is(notNullValue(String.class)));
    }

    private void verifyPaymentMethodsRequest(List<PaymentMethodSetupInfo.TypeEnum> paymentTypes, String merchantId) {
        for (PaymentMethodSetupInfo.TypeEnum paymentType : paymentTypes) {
            app.getAdyenWireMockServer()
                    .verify(postRequestedFor(urlEqualTo((format("/merchants/%s/paymentMethodSettings", merchantId))))
                            .withRequestBody(equalToJson(TestTemplateResourceLoader
                                    .load(ADYEN_PAYMENT_METHOD_REQUEST)
                                    .replace("{{type}}", paymentType.toString()))));
        }
        
        app.getAdyenWireMockServer().verify(paymentTypes.size(), 
                postRequestedFor(urlEqualTo((format("/merchants/%s/paymentMethodSettings", merchantId)))));
    }

    @Test
    void requestingTestAccountShouldReturnBadGatewayWhenAdyenRespondsWithError() {
        var serviceId = RandomIdGenerator.randomUuid();

        app.getAdyenKycMockClient().mockError("/legalEntities");
        
        app.givenSetup()
                .body(Map.of("service_name", "Existing service"))
                .post(format("/v1/api/service/%s/request-adyen-test-account", serviceId))
                .then().statusCode(SC_BAD_GATEWAY);

        app.getAdyenWireMockServer().verify(1, postRequestedFor(urlEqualTo("/legalEntities")));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/businessLines")));
        app.getAdyenWireMockServer().verify(0, patchRequestedFor(urlEqualTo(format("/legalEntities/%s", any(String.class)))));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/transferInstruments")));
        
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/stores")));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo((format("/merchants/%s/paymentMethodSettings", any(String.class))))));
        
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/accountHolders")));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/balanceAccounts")));
        
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo(format("/legalEntities/%s/termsOfService", any(String.class)))));
        app.getAdyenWireMockServer().verify(0, patchRequestedFor(urlEqualTo(format("/legalEntities/%s/termsOfService/%s", any(String.class),  any(String.class)))));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo(format("/legalEntities/%s/pciQuestionnaires/generatePciTemplates", any(String.class)))));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo(format("/legalEntities/%s/pciQuestionnaires/signPciTemplates", any(String.class)))));
    }

    @Test
    void requestingTestAccountShouldReturnConflictWhenTestAccountExistsForService() {
        var serviceId = RandomIdGenerator.randomUuid();

        app.getDatabaseFixtures()
                .aTestAccount()
                .withServiceId(serviceId)
                .withPaymentProvider(ADYEN.getName())
                .insert();

        app.givenSetup()
                .body(Map.of("service_name", "Existing service"))
                .post(format("/v1/api/service/%s/request-adyen-test-account", serviceId))
                .then().statusCode(SC_CONFLICT);

        verifyNoRequests();
    }

    private static void verifyNoRequests() {
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/legalEntities")));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/businessLines")));
        app.getAdyenWireMockServer().verify(0, patchRequestedFor(urlEqualTo(format("/legalEntities/%s", any(String.class)))));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/transferInstruments")));

        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/stores")));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo((format("/merchants/%s/paymentMethodSettings", any(String.class))))));

        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/accountHolders")));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/balanceAccounts")));

        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo(format("/legalEntities/%s/termsOfService", any(String.class)))));
        app.getAdyenWireMockServer().verify(0, patchRequestedFor(urlEqualTo(format("/legalEntities/%s/termsOfService/%s", any(String.class),  any(String.class)))));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo(format("/legalEntities/%s/pciQuestionnaires/generatePciTemplates", any(String.class)))));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo(format("/legalEntities/%s/pciQuestionnaires/signPciTemplates", any(String.class)))));
    }

    @Test
    void switchTestAccountShouldReturnBadGatewayWhenAdyenRespondsWithError() {
        var serviceId = RandomIdGenerator.randomUuid();

        app.getDatabaseFixtures()
                .aTestAccount()
                .withServiceId(serviceId)
                .withPaymentProvider(STRIPE.getName())
                .insert();

        app.getAdyenKycMockClient().mockError("/legalEntities");

        app.givenSetup()
                .body(Map.of("service_name", "Existing service"))
                .post(format("/v1/api/service/%s/switch-to-adyen-test-account", serviceId))
                .then().statusCode(SC_BAD_GATEWAY);

        app.getAdyenWireMockServer().verify(1, postRequestedFor(urlEqualTo("/legalEntities")));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/businessLines")));
        app.getAdyenWireMockServer().verify(0, patchRequestedFor(urlEqualTo(format("/legalEntities/%s", any(String.class)))));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/transferInstruments")));

        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/stores")));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo((format("/merchants/%s/paymentMethodSettings", any(String.class))))));

        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/accountHolders")));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo("/balanceAccounts")));

        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo(format("/legalEntities/%s/termsOfService", any(String.class)))));
        app.getAdyenWireMockServer().verify(0, patchRequestedFor(urlEqualTo(format("/legalEntities/%s/termsOfService/%s", any(String.class),  any(String.class)))));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo(format("/legalEntities/%s/pciQuestionnaires/generatePciTemplates", any(String.class)))));
        app.getAdyenWireMockServer().verify(0, postRequestedFor(urlEqualTo(format("/legalEntities/%s/pciQuestionnaires/signPciTemplates", any(String.class)))));
    }

    @Test
    void switchTestAccountShouldReturnBadRequestWhenProviderIsNotStripe() {
        var serviceId = RandomIdGenerator.randomUuid();

        app.getDatabaseFixtures()
                .aTestAccount()
                .withServiceId(serviceId)
                .withPaymentProvider(WORLDPAY.getName())
                .insert();

        app.givenSetup()
                .body(Map.of("service_name", "Existing service"))
                .post(format("/v1/api/service/%s/switch-to-adyen-test-account", serviceId))
                .then().statusCode(SC_BAD_REQUEST);

        verifyNoRequests();
    }

    @Test
    void switchTestAccountShouldReturnBadRequestWhenStripeTestAccountDoesNotExist() {
        var serviceId = RandomIdGenerator.randomUuid();
        
        app.givenSetup()
                .body(Map.of("service_name", "Existing service"))
                .post(format("/v1/api/service/%s/switch-to-adyen-test-account", serviceId))
                .then().statusCode(SC_BAD_REQUEST);

        verifyNoRequests();
    }
}
