package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;

import java.util.Map;

import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class GatewayAccountResourceSwitchPspIT extends GatewayAccountResourceTestBase {
    
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void shouldSwitchPaymentProvider() throws JsonProcessingException {
        String gatewayAccountId = "1000024";
        String activeExtId = randomUuid();
        String switchToExtId = randomUuid();
        databaseTestHelper.addGatewayAccount(
                anAddGatewayAccountParams()
                .withAccountId(gatewayAccountId)
                .withPaymentGateway("stripe")
                .withServiceName("a cool service")
                .withProviderSwitchEnabled(true)
                .build());

        AddGatewayAccountCredentialsParams activeParams =
            AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder
                    .anAddGatewayAccountCredentialsParams()
                    .withGatewayAccountId(Long.valueOf(gatewayAccountId))
                    .withCredentials(Map.of())
                    .withExternalId(activeExtId)
                    .withState(ACTIVE)
                    .build();
        databaseTestHelper.insertGatewayAccountCredentials(activeParams);

        AddGatewayAccountCredentialsParams switchToParams =
                AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder
                        .anAddGatewayAccountCredentialsParams()
                        .withGatewayAccountId(Long.valueOf(gatewayAccountId))
                        .withCredentials(Map.of())
                        .withExternalId(switchToExtId)
                        .withState(VERIFIED_WITH_LIVE_PAYMENT)
                        .withPaymentProvider("stripe")
                        .build();
        databaseTestHelper.insertGatewayAccountCredentials(switchToParams);

        String payload = objectMapper.writeValueAsString(Map.of("user_external_id", "some-user-external-id",
                "gateway_account_credential_external_id", switchToExtId));

        givenSetup()
                .body(payload)
                .post("/v1/api/accounts/" + gatewayAccountId + "/switch-psp")
                .then()
                .statusCode(OK.getStatusCode());

        Map<String, Object> account = databaseTestHelper.getGatewayAccount(Long.valueOf(gatewayAccountId));
        assertThat((Integer)account.get("integration_version_3ds"), is(2));
        assertThat((Boolean)account.get("provider_switch_enabled"), is(false));

        Map<String, Object> retiredCredentials = databaseTestHelper.getGatewayAccountCredentialByExternalId(activeExtId);
        assertThat(retiredCredentials.get("state").toString(), is(RETIRED.name()));

        Map<String, Object> activeCredentials = databaseTestHelper.getGatewayAccountCredentialByExternalId(switchToExtId);
        assertThat(activeCredentials.get("state").toString(), is(ACTIVE.name()));
    }
}
