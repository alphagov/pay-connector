package uk.gov.pay.connector.gateway.worldpay;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.core.setup.Environment;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.charge.service.Worldpay3dsFlexJwtService;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.worldpay.exception.NotAWorldpayGatewayAccountException;
import uk.gov.pay.connector.gateway.worldpay.exception.ThreeDsFlexDdcServiceUnavailableException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
class Worldpay3dsFlexCredentialsValidationServiceTest {

    private static final String DDC_TOKEN = "aDdcToken";

    private Worldpay3dsFlexCredentialsValidationService service;

    private Map<String, String> threeDsFlexDdcUrls = Map.of(
            "test", "https://a/test/url",
            "live", "https://a/live/url");

    private GatewayAccountEntity gatewayAccountEntity;

    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

    @Mock
    private ClientFactory clientFactory;

    @Mock
    private Environment environment;

    @Mock
    private Worldpay3dsFlexJwtService worldpay3dsFlexJwtService;

    @Mock
    private ConnectorConfiguration connectorConfiguration;

    @Mock
    private Invocation.Builder invocationBuilder;

    @Mock
    private Response response;

    @Mock
    private Client client;

    @Mock
    private WebTarget webTarget;

    @BeforeEach
    void setup() {
        mockConnectorConfiguration();
        mockJerseyClient();
        service = new Worldpay3dsFlexCredentialsValidationService(clientFactory, environment,
                worldpay3dsFlexJwtService, connectorConfiguration);
    }

    @ParameterizedTest
    @ValueSource(strings = {"test", "live"})
    void should_return_true_if_account_credentials_are_valid_for_a_gateway_account(String type) {
        gatewayAccountEntity = aGatewayAccountEntity()
                .withType(GatewayAccountType.fromString(type))
                .withGatewayName(WORLDPAY.getName())
                .build();
        addGatewayAccountCredentials(gatewayAccountEntity);


        Worldpay3dsFlexCredentials flexCredentials = getValid3dsFlexCredentials();

        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(client.target(threeDsFlexDdcUrls.get(type))).thenReturn(webTarget);
        when(worldpay3dsFlexJwtService.generateDdcToken(eq(GatewayAccount.valueOf(gatewayAccountEntity)),
                eq(flexCredentials), any(Instant.class), eq(WORLDPAY.getName()))).thenReturn(DDC_TOKEN);
        var expectedFormData = new MultivaluedHashMap<String, String>() {{
            add("JWT", DDC_TOKEN);
        }};
        when(invocationBuilder.post(argThat(new EntityMatcher(Entity.form(expectedFormData))))).thenReturn(response);

        assertTrue(service.validateCredentials(gatewayAccountEntity, flexCredentials));
    }

    @ParameterizedTest
    @ValueSource(strings = {"test", "live"})
    void should_return_true_false_account_credentials_are_invalid_for_a_live_gateway_account(String type) {
        gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName(WORLDPAY.getName())
                .withType(GatewayAccountType.fromString(type))
                .build();
        addGatewayAccountCredentials(gatewayAccountEntity);

        var invalidIssuer = "54i0917n10va4428b69l5id0";
        var invalidOrgUnitId = "57992i087n0v4849895alid2";
        var invalidJwtMacKey = "4inva5l2-0133-4i82-d0e5-2024dbeddaa9";
        var flexCredentials = new Worldpay3dsFlexCredentials(invalidIssuer, invalidOrgUnitId, invalidJwtMacKey, false, false);

        when(response.getStatus()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(client.target(threeDsFlexDdcUrls.get(type))).thenReturn(webTarget);
        when(worldpay3dsFlexJwtService.generateDdcToken(eq(GatewayAccount.valueOf(gatewayAccountEntity)),
                eq(flexCredentials), any(Instant.class), eq(WORLDPAY.getName()))).thenReturn(DDC_TOKEN);
        var expectedFormData = new MultivaluedHashMap<String, String>() {{
            add("JWT", DDC_TOKEN);
        }};
        when(invocationBuilder.post(argThat(new EntityMatcher(Entity.form(expectedFormData))))).thenReturn(response);

        assertFalse(service.validateCredentials(gatewayAccountEntity, flexCredentials));
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 204, 303, 404, 500})
    void should_throw_exception_if_response_from_3ds_flex_ddc_endpoint_is_not_200_or_400(int responseCode) {
        gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName(WORLDPAY.getName())
                .withType(LIVE)
                .build();
        addGatewayAccountCredentials(gatewayAccountEntity);

        Worldpay3dsFlexCredentials flexCredentials = getValid3dsFlexCredentials();

        when(response.getStatus()).thenReturn(responseCode);
        when(client.target(threeDsFlexDdcUrls.get("live"))).thenReturn(webTarget);
        when(worldpay3dsFlexJwtService.generateDdcToken(eq(GatewayAccount.valueOf(gatewayAccountEntity)), eq(flexCredentials),
                any(Instant.class), eq(WORLDPAY.getName()))).thenReturn(DDC_TOKEN);
        var expectedFormData = new MultivaluedHashMap<String, String>() {{
            add("JWT", DDC_TOKEN);
        }};
        when(invocationBuilder.post(argThat(new EntityMatcher(Entity.form(expectedFormData))))).thenReturn(response);

        var exception = assertThrows(ThreeDsFlexDdcServiceUnavailableException.class,
                () -> service.validateCredentials(gatewayAccountEntity, flexCredentials));
        assertEquals(exception.getResponse().getStatus(), HttpStatus.SC_SERVICE_UNAVAILABLE);
        assertThat(exception, is(instanceOf(WebApplicationException.class)));
    }

    @Test
    void should_throw_exception_if_processingException_thrown_when_communicating_with_3ds_flex_ddc_endpoint() {
        gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName(WORLDPAY.getName())
                .withType(LIVE)
                .build();
        addGatewayAccountCredentials(gatewayAccountEntity);

        Worldpay3dsFlexCredentials flexCredentials = getValid3dsFlexCredentials();

        when(client.target(threeDsFlexDdcUrls.get("live"))).thenReturn(webTarget);
        when(worldpay3dsFlexJwtService.generateDdcToken(eq(GatewayAccount.valueOf(gatewayAccountEntity)), eq(flexCredentials),
                any(Instant.class), eq(WORLDPAY.getName()))).thenReturn(DDC_TOKEN);
        var expectedFormData = new MultivaluedHashMap<String, String>() {{
            add("JWT", DDC_TOKEN);
        }};
        when(invocationBuilder.post(argThat(new EntityMatcher(Entity.form(expectedFormData)))))
                .thenThrow(new ProcessingException("Some I/O failure"));

        var exception = assertThrows(ThreeDsFlexDdcServiceUnavailableException.class,
                () -> service.validateCredentials(gatewayAccountEntity, flexCredentials));
        assertEquals(exception.getResponse().getStatus(), HttpStatus.SC_SERVICE_UNAVAILABLE);
        assertThat(exception, is(instanceOf(WebApplicationException.class)));
    }

    @Test
    void should_throw_exception_if_gateway_account_is_not_a_worldpay_account() {
        gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName(STRIPE.getName())
                .build();

        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
                .aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        assertThrows(NotAWorldpayGatewayAccountException.class, () -> service.validateCredentials(gatewayAccountEntity, getValid3dsFlexCredentials()));
    }

    @Nested
    class ServiceIsSwitchingToWorldpayFromStripe {
        GatewayAccountEntity liveGatewayAccountEntity;
        GatewayAccountCredentialsEntity activeStripeCredentialsEntity;
        Worldpay3dsFlexCredentials flexCredentials;

        @BeforeEach
        void setup() {
            liveGatewayAccountEntity = aGatewayAccountEntity()
                    .withType(LIVE)
                    .withProviderSwitchEnabled(true)
                    .build();
            activeStripeCredentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(liveGatewayAccountEntity)
                    .withPaymentProvider(STRIPE.getName())
                    .withState(ACTIVE)
                    .build();
            flexCredentials = getValid3dsFlexCredentials();
        }

        @ParameterizedTest
        @EnumSource(value = GatewayAccountCredentialState.class, names = {"CREATED", "ENTERED", "VERIFIED_WITH_LIVE_PAYMENT"})
        void should_validate_when_valid_credential_state_exists(GatewayAccountCredentialState state) {

            GatewayAccountCredentialsEntity worldpayCredentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(liveGatewayAccountEntity)
                    .withPaymentProvider(WORLDPAY.getName())
                    .withState(state)
                    .build();

            liveGatewayAccountEntity.setGatewayAccountCredentials(List.of(activeStripeCredentialsEntity, worldpayCredentialsEntity));

            when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
            when(client.target(threeDsFlexDdcUrls.get("live"))).thenReturn(webTarget);
            when(worldpay3dsFlexJwtService.generateDdcToken(eq(GatewayAccount.valueOf(liveGatewayAccountEntity)),
                    eq(flexCredentials), any(Instant.class), eq(WORLDPAY.getName()))).thenReturn(DDC_TOKEN);
            var expectedFormData = new MultivaluedHashMap<String, String>() {{
                add("JWT", DDC_TOKEN);
            }};
            when(invocationBuilder.post(argThat(new EntityMatcher(Entity.form(expectedFormData))))).thenReturn(response);

            assertTrue(service.validateCredentials(liveGatewayAccountEntity, flexCredentials));
        }

        @Test
        void should_throw_when_invalid_credential_state_exists() {
            GatewayAccountCredentialsEntity worldpayCredentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(liveGatewayAccountEntity)
                    .withPaymentProvider(WORLDPAY.getName())
                    .withState(RETIRED)
                    .build();

            liveGatewayAccountEntity.setGatewayAccountCredentials(List.of(activeStripeCredentialsEntity, worldpayCredentialsEntity));

            assertThrows(NotAWorldpayGatewayAccountException.class,
                    () -> service.validateCredentials(liveGatewayAccountEntity, flexCredentials));
        }

        @Test
        void should_throw_when_provider_switch_is_not_enabled() {
            liveGatewayAccountEntity.setProviderSwitchEnabled(false);
            
            GatewayAccountCredentialsEntity worldpayCredentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(liveGatewayAccountEntity)
                    .withPaymentProvider(WORLDPAY.getName())
                    .withState(CREATED)
                    .build();

            liveGatewayAccountEntity.setGatewayAccountCredentials(List.of(activeStripeCredentialsEntity, worldpayCredentialsEntity));

            assertThrows(NotAWorldpayGatewayAccountException.class,
                    () -> service.validateCredentials(liveGatewayAccountEntity, flexCredentials));
        }

        @Test
        void should_throw_when_not_a_worldpay_account() {
            liveGatewayAccountEntity.setProviderSwitchEnabled(false);
            liveGatewayAccountEntity.setGatewayAccountCredentials(List.of(activeStripeCredentialsEntity));

            assertThrows(NotAWorldpayGatewayAccountException.class,
                    () -> service.validateCredentials(liveGatewayAccountEntity, flexCredentials));
        }
    }

    private Worldpay3dsFlexCredentials getValid3dsFlexCredentials() {
        var validIssuer = "53f0917f101a4428b69d5fb0"; //pragma: allowlist secret
        var validOrgUnitId = "57992a087a0c4849895ab8a2";
        var validJwtMacKey = "4cabd5d2-0133-4e82-b0e5-2024dbeddaa9";
        return new Worldpay3dsFlexCredentials(validIssuer, validOrgUnitId, validJwtMacKey, false, false);
    }

    private void mockJerseyClient() {
        MetricRegistry metricRegistry = mock(MetricRegistry.class);
        when(environment.metrics()).thenReturn(metricRegistry);
        when(clientFactory.createWithDropwizardClient(any(PaymentGatewayName.class), any(MetricRegistry.class)))
                .thenReturn(client);
        lenient().when(webTarget.request()).thenReturn(invocationBuilder);
    }

    private void mockConnectorConfiguration() {
        WorldpayConfig worldpayConfig = mock(WorldpayConfig.class);
        when(connectorConfiguration.getWorldpayConfig()).thenReturn(worldpayConfig);
        when(worldpayConfig.getThreeDsFlexDdcUrls()).thenReturn(threeDsFlexDdcUrls);
    }

    private void addGatewayAccountCredentials(GatewayAccountEntity gatewayAccountEntity) {
        gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of())
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));
    }
}
