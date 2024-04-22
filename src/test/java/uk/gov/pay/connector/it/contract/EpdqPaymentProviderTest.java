package uk.gov.pay.connector.it.contract;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.ChargeQueryGatewayRequest;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.TestClientFactory;

import javax.ws.rs.client.Client;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userEmail;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

@Disabled("Ignoring as this test is failing in Jenkins because it's failing to locate the certificates - PP-1707")
@ExtendWith(MockitoExtension.class)
class EpdqPaymentProviderTest {

    private static final String ADDRESS_LINE_1 = "The Money Pool";
    private static final String ADDRESS_LINE_2 = "1 Gold Way";
    private static final String ADDRESS_CITY = "London";
    private static final String ADDRESS_POSTCODE = "DO11 4RS";
    private static final String ADDRESS_COUNTRY = "GB";

    private String url = "https://mdepayments.epdq.co.uk/ncol/test";
    private String merchantId = envOrThrow("GDS_CONNECTOR_EPDQ_MERCHANT_ID");
    private String username = envOrThrow("GDS_CONNECTOR_EPDQ_USER");
    private String password = envOrThrow("GDS_CONNECTOR_EPDQ_PASSWORD");
    private String shaInPassphrase = envOrThrow("GDS_CONNECTOR_EPDQ_SHA_IN_PASSPHRASE");
    private ChargeEntity chargeEntity;
    private GatewayAccountEntity gatewayAccountEntity;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;
    private EpdqPaymentProvider paymentProvider;

    @Mock
    private MetricRegistry mockMetricRegistry;

    @Mock
    private Histogram mockHistogram;

    @Mock
    private Environment mockEnvironment;

    @Mock
    private LinksConfig mockLinksConfig;

    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;

    @Mock
    private GatewayConfig mockGatewayConfig;

    @Mock
    private GatewayClientFactory mockGatewayClientFactory;

    @BeforeEach
    void setUp() {
        when(mockConnectorConfiguration.getLinks()).thenReturn(mockLinksConfig);
        when(mockConnectorConfiguration.getGatewayConfigFor(EPDQ)).thenReturn(mockGatewayConfig);
        when(mockLinksConfig.getFrontendUrl()).thenReturn("http://frontendUrl");
        when(mockGatewayConfig.getUrls()).thenReturn(Map.of(TEST.toString(), url));
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);

        Client client = TestClientFactory.createJerseyClient();
        GatewayClient gatewayClient = new GatewayClient(client,
                mockMetricRegistry);

        when(mockGatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class),
                any(GatewayOperation.class),
                any())).thenReturn(gatewayClient);

        paymentProvider = new EpdqPaymentProvider();
    }

    @Test
    void shouldRefundSuccessfully() throws Exception {
        setUpAndCheckThatEpdqIsUp();

        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixture().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request, chargeEntity);
        assertThat(response.isSuccessful(), is(true));

        String transactionId = response.getBaseResponse().get().getTransactionId();
        assertThat(transactionId, is(not(nullValue())));
        CaptureGatewayRequest captureRequest = buildCaptureRequest(chargeEntity, transactionId);
        CaptureResponse captureResponse = paymentProvider.capture(captureRequest);
        assertThat(captureResponse.isSuccessful(), is(true));

        RefundGatewayRequest refundGatewayRequest = buildRefundRequest(chargeEntity, (chargeEntity.getAmount() - 100));
        GatewayRefundResponse refundResponse = paymentProvider.refund(refundGatewayRequest);
        assertThat(refundResponse.isSuccessful(), is(true));
    }

    @Test
    void shouldQueryPaymentStatusSuccessfully() throws Exception {
        setUpAndCheckThatEpdqIsUp();

        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixture().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request, chargeEntity);
        assertThat(response.isSuccessful(), is(true));

        ChargeQueryGatewayRequest chargeQueryGatewayRequest = ChargeQueryGatewayRequest.valueOf(Charge.from(chargeEntity), chargeEntity.getGatewayAccount(), chargeEntity.getGatewayAccountCredentialsEntity());
        ChargeQueryResponse chargeQueryResponse = paymentProvider.queryPaymentStatus(chargeQueryGatewayRequest);
        assertThat(chargeQueryResponse.getMappedStatus(), is(Optional.of(ChargeStatus.AUTHORISATION_SUCCESS)));
        assertThat(chargeQueryResponse.foundCharge(), is(true));
    }

    @Test
    void shouldReturnQueryResponseWhenChargeNotFound() throws Exception {
        setUpAndCheckThatEpdqIsUp();
        ChargeQueryGatewayRequest chargeQueryGatewayRequest = ChargeQueryGatewayRequest.valueOf(Charge.from(chargeEntity), chargeEntity.getGatewayAccount(), chargeEntity.getGatewayAccountCredentialsEntity());
        ChargeQueryResponse chargeQueryResponse = paymentProvider.queryPaymentStatus(chargeQueryGatewayRequest);
        assertThat(chargeQueryResponse.getMappedStatus(), is(Optional.empty()));
        assertThat(chargeQueryResponse.foundCharge(), is(false));
    }

    private static AuthCardDetailsFixture authCardDetailsFixture() {
        return AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(new Address(
                        ADDRESS_LINE_1,
                        ADDRESS_LINE_2,
                        ADDRESS_POSTCODE,
                        ADDRESS_CITY,
                        null,
                        ADDRESS_COUNTRY
                ));
    }

    private void setUpAndCheckThatEpdqIsUp() {
        epdqSetupWithStatusCheck(false, false);
    }

    private void epdqSetupWithStatusCheck(boolean require3ds, boolean requires3ds2) {
        try {
            new URL(url).openConnection().connect();
            Map<String, Object> validEpdqCredentials = ImmutableMap.of(
                    "merchant_id", merchantId,
                    "username", username,
                    "password", password,
                    "sha_in_passphrase", shaInPassphrase);

            gatewayAccountEntity = new GatewayAccountEntity();
            gatewayAccountEntity.setId(123L);
            gatewayAccountEntity.setType(TEST);
            gatewayAccountEntity.setRequires3ds(require3ds);
            gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                    .withCredentials(validEpdqCredentials)
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withPaymentProvider(EPDQ.getName())
                    .build();

            gatewayAccountEntity.setGatewayAccountCredentials(
                    List.of(gatewayAccountCredentialsEntity));

            if (requires3ds2) {
                gatewayAccountEntity.setIntegrationVersion3ds(2);
                gatewayAccountEntity.setSendPayerIpAddressToGateway(true);
            }

            chargeEntity = aValidChargeEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withTransactionId(randomUUID().toString())
                    .build();

        } catch (IOException ex) {
            fail();
        }
    }

    private static CaptureGatewayRequest buildCaptureRequest(ChargeEntity chargeEntity, String transactionId) {
        chargeEntity.setGatewayTransactionId(transactionId);
        return CaptureGatewayRequest.valueOf(chargeEntity);
    }

    private RefundGatewayRequest buildRefundRequest(ChargeEntity chargeEntity, Long refundAmount) {
        return RefundGatewayRequest.valueOf(Charge.from(chargeEntity), new RefundEntity(refundAmount, userExternalId, userEmail, chargeEntity.getExternalId()),
                gatewayAccountEntity, gatewayAccountCredentialsEntity);
    }
}
