package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;

import java.util.List;
import java.util.Map;

import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class GatewayAccountResourceSwitchPspIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    class ByGatewayAccountId {
        @Test
        public void shouldSwitchPaymentProvider() throws JsonProcessingException {
            String gatewayAccountId = "1000024";
            String activeExtId = randomUuid();
            String switchToExtId = randomUuid();

            AddGatewayAccountCredentialsParams activeParams =
                    AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder
                            .anAddGatewayAccountCredentialsParams()
                            .withGatewayAccountId(Long.valueOf(gatewayAccountId))
                            .withCredentials(Map.of())
                            .withExternalId(activeExtId)
                            .withState(ACTIVE)
                            .build();

            AddGatewayAccountCredentialsParams switchToParams =
                    AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder
                            .anAddGatewayAccountCredentialsParams()
                            .withGatewayAccountId(Long.valueOf(gatewayAccountId))
                            .withCredentials(Map.of())
                            .withExternalId(switchToExtId)
                            .withState(VERIFIED_WITH_LIVE_PAYMENT)
                            .withPaymentProvider("stripe")
                            .build();

            app.getDatabaseTestHelper().addGatewayAccount(
                    anAddGatewayAccountParams()
                            .withAccountId(gatewayAccountId)
                            .withPaymentGateway("stripe")
                            .withServiceName("a cool service")
                            .withProviderSwitchEnabled(true)
                            .withGatewayAccountCredentials(List.of(activeParams, switchToParams))
                            .build());

            String payload = objectMapper.writeValueAsString(Map.of("user_external_id", "some-user-external-id",
                    "gateway_account_credential_external_id", switchToExtId));

            app.givenSetup()
                    .body(payload)
                    .post("/v1/api/accounts/" + gatewayAccountId + "/switch-psp")
                    .then()
                    .statusCode(OK.getStatusCode());

            Map<String, Object> account = app.getDatabaseTestHelper().getGatewayAccount(Long.valueOf(gatewayAccountId));
            assertThat((Integer) account.get("integration_version_3ds"), is(2));
            assertThat((Boolean) account.get("provider_switch_enabled"), is(false));

            Map<String, Object> retiredCredentials = app.getDatabaseTestHelper().getGatewayAccountCredentialByExternalId(activeExtId);
            assertThat(retiredCredentials.get("state").toString(), is(RETIRED.name()));

            Map<String, Object> activeCredentials = app.getDatabaseTestHelper().getGatewayAccountCredentialByExternalId(switchToExtId);
            assertThat(activeCredentials.get("state").toString(), is(ACTIVE.name()));
        }
    }
}
