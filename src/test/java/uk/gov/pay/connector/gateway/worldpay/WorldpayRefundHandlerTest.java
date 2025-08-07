package uk.gov.pay.connector.gateway.worldpay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.RefundState.PENDING;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_MERCHANT_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
class WorldpayRefundHandlerTest {

    private final URI WORLDPAY_URL = URI.create("http://worldpay.url");

    private final Map<String, URI> GATEWAY_URL_MAP = Map.of(TEST.toString(), WORLDPAY_URL);

    private ChargeEntityFixture chargeEntityFixture;

    private GatewayAccountEntity gatewayAccountEntity;

    private WorldpayRefundHandler worldpayRefundHandler;

    @Mock
    private GatewayClient refundGatewayClient;

    @Captor
    ArgumentCaptor<GatewayOrder> gatewayOrderCaptor;

    @BeforeEach
    void setup() {
        gatewayAccountEntity = aServiceAccount();
        chargeEntityFixture = aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity);

        worldpayRefundHandler = new WorldpayRefundHandler(refundGatewayClient, GATEWAY_URL_MAP);
    }

    @Test
    void test_refund_request_contains_reference() throws Exception {
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of(
                        ONE_OFF_CUSTOMER_INITIATED, Map.of(
                                CREDENTIALS_MERCHANT_CODE, "MERCHANTCODE",
                                CREDENTIALS_USERNAME, "worldpay-password",
                                CREDENTIALS_PASSWORD, "password")
                ))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build();
        ChargeEntity chargeEntity = chargeEntityFixture
                .withTransactionId("transaction-id")
                .withGatewayAccountCredentialsEntity(credentialsEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

        when(refundGatewayClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        worldpayRefundHandler.refund(RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, gatewayAccountEntity, credentialsEntity));

        String expectedRefundRequest =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                        "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                        "<paymentService version=\"1.4\" merchantCode=\"MERCHANTCODE\">\n" +
                        "    <modify>\n" +
                        "        <orderModification orderCode=\"transaction-id\">\n" +
                        "            <refund reference=\"" + refundEntity.getExternalId() + "\">\n" +
                        "                <amount currencyCode=\"GBP\" exponent=\"2\" value=\"500\"/>\n" +
                        "            </refund>\n" +
                        "        </orderModification>\n" +
                        "    </modify>\n" +
                        "</paymentService>\n" +
                        "";

        verify(refundGatewayClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(WORLDPAY),
                eq("test"),
                gatewayOrderCaptor.capture(),
                anyMap());

        GatewayOrder gatewayOrder = gatewayOrderCaptor.getValue();
        assertThat(gatewayOrder.getPayload(), is(expectedRefundRequest));
        assertThat(gatewayOrder.getOrderRequestType(), is(OrderRequestType.REFUND));
    }

    @Test
    void test_refund_request_contains_reference_for_a_recurring_payment() throws Exception {
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of(
                        RECURRING_CUSTOMER_INITIATED, Map.of(
                                CREDENTIALS_MERCHANT_CODE, "CIT-MERCHANTCODE",
                                CREDENTIALS_USERNAME, "cit-username",
                                CREDENTIALS_PASSWORD, "cit-password"),
                        RECURRING_MERCHANT_INITIATED, Map.of(
                                CREDENTIALS_MERCHANT_CODE, "MIT-MERCHANTCODE",
                                CREDENTIALS_USERNAME, "mit-username",
                                CREDENTIALS_PASSWORD, "mit-password"
                        )
                ))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build();
        ChargeEntity chargeEntity = chargeEntityFixture
                .withTransactionId("transaction-id")
                .withGatewayAccountCredentialsEntity(credentialsEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withAgreementEntity(anAgreementEntity().build())
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

        when(refundGatewayClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        worldpayRefundHandler.refund(RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, gatewayAccountEntity, credentialsEntity));

        String expectedRefundRequest =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                        "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                        "<paymentService version=\"1.4\" merchantCode=\"CIT-MERCHANTCODE\">\n" +
                        "    <modify>\n" +
                        "        <orderModification orderCode=\"transaction-id\">\n" +
                        "            <refund reference=\"" + refundEntity.getExternalId() + "\">\n" +
                        "                <amount currencyCode=\"GBP\" exponent=\"2\" value=\"500\"/>\n" +
                        "            </refund>\n" +
                        "        </orderModification>\n" +
                        "    </modify>\n" +
                        "</paymentService>\n" +
                        "";

        verify(refundGatewayClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(WORLDPAY),
                eq("test"),
                gatewayOrderCaptor.capture(),
                anyMap());

        GatewayOrder gatewayOrder = gatewayOrderCaptor.getValue();
        assertThat(gatewayOrder.getPayload(), is(expectedRefundRequest));
        assertThat(gatewayOrder.getOrderRequestType(), is(OrderRequestType.REFUND));
    }

    @Test
    void test_refund_returns_pending_on_connection_timeout() throws Exception {
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of(
                        ONE_OFF_CUSTOMER_INITIATED, Map.of(
                                CREDENTIALS_MERCHANT_CODE, "MERCHANTCODE",
                                CREDENTIALS_USERNAME, "worldpay-password",
                                CREDENTIALS_PASSWORD, "password")
                ))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build();
        ChargeEntity chargeEntity = chargeEntityFixture
                .withTransactionId("transaction-id")
                .withGatewayAccountCredentialsEntity(credentialsEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

        when(refundGatewayClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayException.GatewayConnectionTimeoutException("Gateway connection timeout error"));

        GatewayRefundResponse response = worldpayRefundHandler.refund(
                RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, gatewayAccountEntity, credentialsEntity));

        assertThat(response.state(), is(PENDING));
    }

    @Test
    void test_refund_returns_error_on_gateway_exception() throws Exception {
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of(
                        ONE_OFF_CUSTOMER_INITIATED, Map.of(
                                CREDENTIALS_MERCHANT_CODE, "MERCHANTCODE",
                                CREDENTIALS_USERNAME, "worldpay-password",
                                CREDENTIALS_PASSWORD, "password")
                ))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build();
        ChargeEntity chargeEntity = chargeEntityFixture
                .withTransactionId("transaction-id")
                .withGatewayAccountCredentialsEntity(credentialsEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

        when(refundGatewayClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        GatewayRefundResponse response = worldpayRefundHandler.refund(
                RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, gatewayAccountEntity, credentialsEntity));

        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getError().isPresent(), is(true));
    }

    private GatewayAccountEntity aServiceAccount() {
        var gatewayAccountEntity = aGatewayAccountEntity()
                .withId(1L)
                .withGatewayName(WORLDPAY.getName())
                .withRequires3ds(false)
                .withType(TEST)
                .build();

        var creds = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of(
                                ONE_OFF_CUSTOMER_INITIATED, Map.of(
                                        CREDENTIALS_MERCHANT_CODE, "a-merchant-code",
                                        CREDENTIALS_USERNAME, "a-username",
                                        CREDENTIALS_PASSWORD, "a-password")))
                        .withGatewayAccountEntity(gatewayAccountEntity)
                        .withPaymentProvider(WORLDPAY.getName())
                        .withState(ACTIVE)
                        .build();

        gatewayAccountEntity.setGatewayAccountCredentials(List.of(creds));

        return gatewayAccountEntity;
    }
}
