package uk.gov.pay.connector.gateway.worldpay;

import com.google.common.collect.ImmutableMap;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.w3c.dom.Document;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.XPathUtils;

import javax.ws.rs.core.Response;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_MERCHANT_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(JUnitParamsRunner.class)
public class WorldpayCaptureHandlerTest {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    private WorldpayCaptureHandler worldpayCaptureHandler;

    @Mock
    private GatewayClient client;
    @Mock
    private Response response;

    private final Map<String, Object> credentials = Map.of(
            CREDENTIALS_MERCHANT_ID, "MERCHANTCODE",
            CREDENTIALS_USERNAME, "worldpay-password",
            CREDENTIALS_PASSWORD, "password"
    );

    Map<String, Object> recurringCredentials = Map.of(
            CREDENTIALS_MERCHANT_ID, "MERCHANTCODE",
            CREDENTIALS_USERNAME, "worldpay-password",
            CREDENTIALS_PASSWORD, "password",
            RECURRING_MERCHANT_INITIATED, Map.of(
                    CREDENTIALS_MERCHANT_ID, "ECURRING-MERCHANTCODE",
                    CREDENTIALS_USERNAME, "recurring-worldpay-password",
                    CREDENTIALS_PASSWORD, "recurring-password"
            )
    );

    @Before
    public void setup() {
        worldpayCaptureHandler = new WorldpayCaptureHandler(client, ImmutableMap.of(TEST.toString(), URI.create("http://worldpay.test")));
    }

    @Test
    @Parameters({"null", "250"})
    public void shouldCaptureAPaymentSuccessfully(@Nullable Long corporateSurchargeAmount) throws Exception {
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(response.readEntity(String.class)).thenReturn(load("templates/worldpay/capture-success-response.xml"));
        GatewayClient.Response response = new TestResponse(this.response);
        when(client.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(response);

        var chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withPaymentProvider("worldpay")
                        .withCredentials(credentials)
                        .build())
                .build();
        if (corporateSurchargeAmount != null) {
            chargeEntity.setCorporateSurcharge(corporateSurchargeAmount);
        }
        CaptureResponse gatewayResponse = worldpayCaptureHandler.capture(CaptureGatewayRequest.valueOf(chargeEntity));

        assertTrue(gatewayResponse.isSuccessful());
        assertThat(gatewayResponse.state(), is(CaptureResponse.ChargeState.PENDING));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(client).postRequestFor(
                any(URI.class),
                eq(WORLDPAY), eq("test"),
                gatewayOrderArgumentCaptor.capture(),
                anyMap());

        Document document = XPathUtils.getDocumentXmlString(gatewayOrderArgumentCaptor.getValue().getPayload());
        XPath xPath = XPathFactory.newInstance().newXPath();
        assertThat(xPath.evaluate("/paymentService/modify/orderModification/capture/amount/@value", document),
                is(String.valueOf(chargeEntity.getAmount() + chargeEntity.getCorporateSurcharge().orElse(0L))));
    }

    @Test
    public void shouldCaptureARecurringPaymentSuccessfully() throws Exception {

        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(response.readEntity(String.class)).thenReturn(load("templates/worldpay/capture-success-response.xml"));
        GatewayClient.Response response = new TestResponse(this.response);
        when(client.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(response);

        var chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withPaymentProvider("worldpay")
                        .withCredentials(recurringCredentials)
                        .build())
                .build();

        CaptureResponse gatewayResponse = worldpayCaptureHandler.capture(CaptureGatewayRequest.valueOf(chargeEntity));

        assertTrue(gatewayResponse.isSuccessful());
        assertThat(gatewayResponse.state(), is(CaptureResponse.ChargeState.PENDING));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(client).postRequestFor(
                any(URI.class),
                eq(WORLDPAY), eq("test"),
                gatewayOrderArgumentCaptor.capture(),
                anyMap());

        Document document = XPathUtils.getDocumentXmlString(gatewayOrderArgumentCaptor.getValue().getPayload());
        XPath xPath = XPathFactory.newInstance().newXPath();
        assertThat(xPath.evaluate("/paymentService/modify/orderModification/capture/amount/@value", document),
                is(String.valueOf(chargeEntity.getAmount() + chargeEntity.getCorporateSurcharge().orElse(0L))));
    }

    private class TestResponse extends GatewayClient.Response {

        protected TestResponse(Response delegate) {
            super(delegate);
        }
    }

    @Test
    public void shouldErrorIfOrderReferenceNotKnownInCapture() throws Exception {
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(response.readEntity(String.class)).thenReturn(load("templates/worldpay/error-response.xml")
                .replace("{{errorDescription}}", "Order has already been paid"));
        TestResponse testResponse = new TestResponse(this.response);
        when(client.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap())).thenReturn(testResponse);

        CaptureResponse gatewayResponse = worldpayCaptureHandler.capture(getCaptureRequest());

        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.getError().isPresent(), is(true));
        assertThat(gatewayResponse.getError().get().getMessage(), is("Worldpay capture response (error code: 5, error: Order has already been paid)"));
        assertThat(gatewayResponse.getError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldErrorIfWorldpayResponseIsNot200() throws Exception {
        when(client.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        CaptureResponse gatewayResponse = worldpayCaptureHandler.capture(getCaptureRequest());
        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.getError().isPresent(), is(true));
        assertThat(gatewayResponse.getError().get().getMessage(), is("Unexpected HTTP status code 400 from gateway"));
        assertThat(gatewayResponse.getError().get().getErrorType(), is(GATEWAY_ERROR));
    }

    private CaptureGatewayRequest getCaptureRequest() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withPaymentProvider("worldpay")
                        .withCredentials(credentials)
                        .build())
                .build();
        return CaptureGatewayRequest.valueOf(chargeEntity);
    }

    private GatewayAccountEntity aServiceAccount() {
        var gatewayAccountEntity = aGatewayAccountEntity()
                .withId(1L)
                .withGatewayName("worldpay")
                .withRequires3ds(false)
                .withType(TEST)
                .build();

        var creds = aGatewayAccountCredentialsEntity()
                .withCredentials(credentials)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build();

        gatewayAccountEntity.setGatewayAccountCredentials(List.of(creds));

        return gatewayAccountEntity;
    }
}

