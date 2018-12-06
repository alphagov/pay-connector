package uk.gov.pay.connector.gateway.smartpay;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.paymentprocessor.service.PaymentProviderAuthorisationResponse;
import uk.gov.pay.connector.util.AuthUtils;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_AUTHORISATION_3DS_REQUIRED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_AUTHORISATION_SUCCESS_RESPONSE;

@RunWith(MockitoJUnitRunner.class)
public class SmartpayPaymentProviderTest extends BaseSmartpayPaymentProviderTest {

    @Before
    public void setup() {
        super.setup();
        mockSmartpaySuccessfulOrderSubmitResponse();
    }

    @Test
    public void shouldGetPaymentProviderName() {
        assertThat(provider.getPaymentGatewayName().getName(), is("smartpay"));
    }

    @Test
    public void shouldGenerateTransactionId() {
        Assert.assertThat(provider.generateTransactionId().isPresent(), is(false));
    }

    @Test
    public void shouldSendSuccessfullyAOrderForMerchant() {

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .build();

        PaymentProviderAuthorisationResponse response = provider.authorise(new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails));

        assertThat(response.getTransactionId().isPresent(), is(true));
    }

    @Test
    public void shouldRequire3dsFor3dsRequiredMerchant() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        GatewayAccountEntity gatewayAccountEntity = aServiceAccount();
        gatewayAccountEntity.setRequires3ds(true);
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
        mockSmartpay3dsRequiredOrderSubmitResponse();

        PaymentProviderAuthorisationResponse response = provider.authorise(new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails));

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(REQUIRES_3DS));
        assertTrue(response.getAuth3dsDetailsEntity().isPresent());
        assertNotNull(response.getAuth3dsDetailsEntity().get().getMd());
        assertNotNull(response.getAuth3dsDetailsEntity().get().getIssuerUrl());
        assertNotNull(response.getAuth3dsDetailsEntity().get().getPaRequest());

    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldThrow_IfTryingToAuthoriseAnApplePayPayment() {
        provider.authoriseApplePay(null);
    }

    @Test
    public void shouldSuccess3DSAuthorisation() {
        GatewayAccountEntity gatewayAccountEntity = aServiceAccount();
        gatewayAccountEntity.setRequires3ds(true);
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
        Auth3dsDetails auth3dsDetails = AuthUtils.buildAuth3dsDetails();
        auth3dsDetails.setMd("Some smart text here");

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(new Auth3dsResponseGatewayRequest(chargeEntity, auth3dsDetails));

        assertTrue(response.isSuccessful());
    }

    private void mockSmartpaySuccessfulOrderSubmitResponse() {
        mockSmartpayResponse(200, successAuthoriseResponse());
    }

    private void mockSmartpay3dsRequiredOrderSubmitResponse() {
        mockSmartpayResponse(200, successAuthorise3dsrequiredResponse());
    }

    private String successAuthorise3dsrequiredResponse() {
        return TestTemplateResourceLoader.load(SMARTPAY_AUTHORISATION_3DS_REQUIRED_RESPONSE).replace("{{pspReference}}", "12345678");
    }

    private String successAuthoriseResponse() {
        return TestTemplateResourceLoader.load(SMARTPAY_AUTHORISATION_SUCCESS_RESPONSE).replace("{{pspReference}}", "12345678");
    }

}
