package uk.gov.pay.connector.it.resources.adyen;


import com.codahale.metrics.MetricRegistry;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.google.inject.Provides;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorModule;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.request.GatewayClientPostRequest;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;


@ExtendWith(DropwizardExtensionsSupport.class)
public class AdyenPaymentAuthorisationTimeoutIT {

    @RegisterExtension
    static WireMockExtension wireMockExtension = WireMockExtension.newInstance()
            .options(wireMockConfig().notifier(new ConsoleNotifier(true)).port(8081))
            .configureStaticDsl(true)
            .build();

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(
            CustomConnectorApp.class,
            ConfigOverride.config("adyen.baseUrls.checkout.test", "http://localhost:8081/v71"));

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension(
            "adyen", app.getLocalPort(), app.getDatabaseTestHelper());

    private ChargeDao chargeDao;

    @BeforeEach
    void setUp() {
        chargeDao = app.getInstanceFromGuiceContainer(ChargeDao.class);
    }

    @Test
    void timed_out_authorisation() {
        var chargeId = testBaseExtension.createNewCharge(ENTERING_CARD_DETAILS);

        app.givenSetup()
                .body(anAuthCardDetails().build())
                .post("/v1/frontend/charges/{chargeId}/cards", chargeId)
                .then()
                .statusCode(402)
                .body("error_identifier", is("GENERIC"))
                .body("message[0]", is("Gateway connection timeout error"));


        var charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION TIMEOUT"));
    }

    public static class CustomConnectorApp extends ConnectorApp {
        @Override
        protected ConnectorModule getModule(ConnectorConfiguration configuration, Environment environment) {
            return new ConnectorModuleWithCustomGatewayClientThrowingException(configuration, environment);
        }
    }

    private static class ConnectorModuleWithCustomGatewayClientThrowingException extends ConnectorModule {
        public ConnectorModuleWithCustomGatewayClientThrowingException(ConnectorConfiguration configuration, Environment environment) {
            super(configuration, environment);
        }

        @Provides
        public GatewayClientFactory provideGatewayClientFactory(ClientFactory clientFactory) {
            return new GatewayClientFactory(clientFactory) {
                @Override
                public GatewayClient createGatewayClient(PaymentGatewayName gateway, MetricRegistry metricRegistry) {
                    return new GatewayClient(null, null) {
                        @Override
                        public Response postRequestFor(GatewayClientPostRequest request) throws GatewayException.GatewayConnectionTimeoutException {
                            throw new GatewayException.GatewayConnectionTimeoutException("Gateway connection timeout error");
                        }
                    };
                }
            };
        }
    }
}
