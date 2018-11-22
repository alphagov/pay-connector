package uk.gov.pay.connector.gateway.smartpay;

import com.google.common.collect.ImmutableList;
import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.usernotification.model.Notification;
import uk.gov.pay.connector.usernotification.model.Notifications;
import uk.gov.pay.connector.util.AuthUtils;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.io.IOException;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_AUTHORISATION_3DS_REQUIRED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_CAPTURE_SUCCESS_RESPONSE;

@RunWith(MockitoJUnitRunner.class)
public class SmartpayPaymentProviderTest extends BaseSmartpayPaymentProviderTest {

    @Mock
    private GatewayAccountEntity mockGatewayAccountEntity;

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
    public void shouldGetStatusMapper() {
        assertThat(provider.getStatusMapper(), sameInstance(SmartpayStatusMapper.get()));
    }

    @Test
    public void shouldGenerateTransactionId() {
        Assert.assertThat(provider.generateTransactionId().isPresent(), is(false));
    }

    @Test
    public void shouldAlwaysVerifyNotification() {
        Assert.assertThat(provider.verifyNotification(null, mock(GatewayAccountEntity.class)), is(true));
    }

    @Test
    public void shouldSendSuccessfullyAOrderForMerchant() throws Exception {

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .build();

        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails));

        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().isPresent(), CoreMatchers.is(true));
        String transactionId = response.getBaseResponse().get().getTransactionId();
        assertThat(transactionId, is(not(nullValue())));
    }

    @Test
    public void shouldRequire3dsFor3dsRequiredMerchant() throws Exception {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        GatewayAccountEntity gatewayAccountEntity = aServiceAccount();
        gatewayAccountEntity.setRequires3ds(true);
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
        mockSmartpay3dsRequiredOrderSubmitResponse();

        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails));

        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().isPresent(), is(true));
        SmartpayAuthorisationResponse smartpayAuthorisationResponse = (SmartpayAuthorisationResponse) response.getBaseResponse().get();
        assertThat(smartpayAuthorisationResponse.authoriseStatus(), is(REQUIRES_3DS));
        assertThat(smartpayAuthorisationResponse.getMd(), is(not(nullValue())));
        assertThat(smartpayAuthorisationResponse.getIssuerUrl(), is(not(nullValue())));
        assertThat(smartpayAuthorisationResponse.getPaRequest(), is(not(nullValue())));

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

        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise3dsResponse(new Auth3dsResponseGatewayRequest(chargeEntity, auth3dsDetails));

        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().isPresent(), is(true));
        SmartpayAuthorisationResponse smartpayAuthorisationResponse = (SmartpayAuthorisationResponse) response.getBaseResponse().get();
        assertThat(smartpayAuthorisationResponse.authoriseStatus(), is(AUTHORISED));
        assertThat(smartpayAuthorisationResponse.getPspReference(), is(not(nullValue())));
    }

    @Test
    public void parseNotification_shouldReturnErrorIfUnparseableSoapMessage() {
        Either<String, Notifications<Pair<String, Boolean>>> response = provider.parseNotification("not valid soap message");
        assertThat(response.isLeft(), is(true));
        assertThat(response.left().value(), containsString("not valid soap message"));
    }

    @Test
    public void parseNotification_shouldReturnNotificationsIfValidSoapMessage() throws IOException {
        String originalReference = "originalReference";
        String pspReference = "pspReference";
        String merchantReference = "merchantReference";

        Either<String, Notifications<Pair<String, Boolean>>> response = provider.parseNotification(
                notificationPayloadForTransaction(originalReference, pspReference, merchantReference, "notification-capture"));

        assertThat(response.isRight(), is(true));
        ImmutableList<Notification<Pair<String, Boolean>>> notifications = response.right().value().get();

        assertThat(notifications.size(), is(1));

        Notification<Pair<String, Boolean>> smartpayNotification = notifications.get(0);

        assertThat(smartpayNotification.getTransactionId(), is(originalReference));
        assertThat(smartpayNotification.getReference(), is(pspReference));

        Pair<String, Boolean> status = smartpayNotification.getStatus();
        assertThat(status.getLeft(), is("CAPTURE"));
        assertThat(status.getRight(), is(true));
    }

    @Test
    public void shouldTreatAllNotificationsAsVerified() {
        assertThat(provider.verifyNotification(mock(Notification.class), mockGatewayAccountEntity), is(true));
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

    private void mockSmartpaySuccessfulCaptureResponse() {
        mockSmartpayResponse(200, successCaptureResponse());
    }

    private String successAuthoriseResponse() {
        return TestTemplateResourceLoader.load(SMARTPAY_AUTHORISATION_SUCCESS_RESPONSE).replace("{{pspReference}}", "12345678");
    }

    private String successCaptureResponse() {
        return TestTemplateResourceLoader.load(SMARTPAY_CAPTURE_SUCCESS_RESPONSE).replace("{{pspReference}}", "8614440510830227");
    }

    private String notificationPayloadForTransaction(String originalReference, String pspReference, String merchantReference, String fileName) throws IOException {
        return fixture("templates/smartpay/" + fileName + ".json")
                .replace("{{originalReference}}", originalReference)
                .replace("{{pspReference}}", pspReference)
                .replace("{{merchantReference}}", merchantReference);
    }
}
