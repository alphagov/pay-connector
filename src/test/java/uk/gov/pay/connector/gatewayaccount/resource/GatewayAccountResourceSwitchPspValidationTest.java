package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.pay.connector.gatewayaccount.GatewayAccountSwitchPaymentProviderRequest;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountSwitchPaymentProviderService;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.rules.ResourceTestRuleWithCustomExceptionMappersBuilder;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

@ExtendWith(DropwizardExtensionsSupport.class)
class GatewayAccountResourceSwitchPspValidationTest {

    private static ObjectMapper objectMapper = new ObjectMapper();
    private static GatewayAccountDao gatewayAccountDao = mock(GatewayAccountDao.class);
    private static GatewayAccountCredentialsDao gatewayAccountCredentialsDao = mock(GatewayAccountCredentialsDao.class);
    private static GatewayAccountService gatewayAccountService = mock(GatewayAccountService.class);

    private GatewayAccountEntity gatewayAccountEntity;
    private GatewayAccountSwitchPaymentProviderRequest request;

    private static final ResourceExtension resources = ResourceTestRuleWithCustomExceptionMappersBuilder
            .getBuilder()
            .addResource(new GatewayAccountResource(gatewayAccountService, null,null, null,
                    new GatewayAccountSwitchPaymentProviderService(gatewayAccountDao, gatewayAccountCredentialsDao)))
            .build();

    @BeforeEach
    void setUp() {
        gatewayAccountEntity = aGatewayAccountEntity().withProviderSwitchEnabled(true).build();
        request = new GatewayAccountSwitchPaymentProviderRequest(randomUuid(), randomUuid());
    }

    @Test
    void shouldReturn400WhenNoActiveCredentialFound() throws JsonProcessingException {
        var switchToExtId = randomUuid();
        var gatewayAccountCredentialsEntity1 = aGatewayAccountCredentialsEntity()
                .withExternalId(randomUuid())
                .withState(VERIFIED_WITH_LIVE_PAYMENT)
                .build();
        var gatewayAccountCredentialsEntity2 = aGatewayAccountCredentialsEntity()
                .withExternalId(switchToExtId)
                .withState(RETIRED)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity1, gatewayAccountCredentialsEntity2));

        String payload = objectMapper.writeValueAsString(Map.of("user_external_id", "some-user-external-id",
                "gateway_account_credential_external_id", switchToExtId));

        when(gatewayAccountService.getGatewayAccount(1)).thenReturn(Optional.of(gatewayAccountEntity));

        Response response = resources.client()
                .target("/v1/api/accounts/1/switch-psp")
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(400));

        String errorMessage = response.readEntity(JsonNode.class).get("message").get(0).textValue();
        assertThat(errorMessage, is("Account credential with ACTIVE state not found."));
    }

    @Test
    void shouldReturn400WhenCredentialIsMissing() throws JsonProcessingException {
        var switchToExtId = randomUuid();
        var gatewayAccountCredentialsEntity1 = aGatewayAccountCredentialsEntity()
                .withExternalId(randomUuid())
                .withState(VERIFIED_WITH_LIVE_PAYMENT)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity1));

        String payload = objectMapper.writeValueAsString(Map.of("user_external_id", "some-user-external-id",
                "gateway_account_credential_external_id", switchToExtId));

        when(gatewayAccountService.getGatewayAccount(1)).thenReturn(Optional.of(gatewayAccountEntity));

        Response response = resources.client()
                .target("/v1/api/accounts/1/switch-psp")
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(400));

        String errorMessage = response.readEntity(JsonNode.class).get("message").get(0).textValue();
        assertThat(errorMessage, is("Account has no credential to switch to/from"));
    }

    @Test
    void shouldReturn400WhenCredentialsNonExistent() throws JsonProcessingException {
        var switchToExtId = randomUuid();
        var gatewayAccountCredentialsEntity1 = aGatewayAccountCredentialsEntity()
                .withExternalId(randomUuid())
                .withState(VERIFIED_WITH_LIVE_PAYMENT)
                .build();
        var gatewayAccountCredentialsEntity2 = aGatewayAccountCredentialsEntity()
                .withExternalId(randomUuid())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity1, gatewayAccountCredentialsEntity2));

        String payload = objectMapper.writeValueAsString(Map.of("user_external_id", "some-user-external-id",
                "gateway_account_credential_external_id", switchToExtId));

        when(gatewayAccountService.getGatewayAccount(1)).thenReturn(Optional.of(gatewayAccountEntity));

        Response response = resources.client()
                .target("/v1/api/accounts/1/switch-psp")
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(404));

        String errorMessage = response.readEntity(JsonNode.class).get("message").get(0).textValue();
        assertThat(errorMessage, is("Account credential with id [" + switchToExtId + "] not found."));
    }

    @Test
    void shouldReturn400WhenCredentialNotCorrectState() throws JsonProcessingException {
        var switchToExtId = randomUuid();
        var gatewayAccountCredentialsEntity1 = aGatewayAccountCredentialsEntity()
                .withExternalId(randomUuid())
                .withState(ACTIVE)
                .build();
        var gatewayAccountCredentialsEntity2 = aGatewayAccountCredentialsEntity()
                .withExternalId(switchToExtId)
                .withState(RETIRED)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity1, gatewayAccountCredentialsEntity2));

        String payload = objectMapper.writeValueAsString(Map.of("user_external_id", "some-user-external-id",
                "gateway_account_credential_external_id", switchToExtId));

        when(gatewayAccountService.getGatewayAccount(1)).thenReturn(Optional.of(gatewayAccountEntity));

        Response response = resources.client()
                .target("/v1/api/accounts/1/switch-psp")
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(400));

        String errorMessage = response.readEntity(JsonNode.class).get("message").get(0).textValue();
        assertThat(errorMessage, is("Credential with id [" + switchToExtId + "] is not in correct state."));
    }

    @Test
    void shouldReturn404WhenGatewayAccountIsNonExistent() throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of("user_external_id", "some-user-external-id",
                "gateway_account_credential_external_id", randomUuid()));

        when(gatewayAccountService.getGatewayAccount(1)).thenReturn(Optional.empty());

        Response response = resources.client()
                .target("/v1/api/accounts/1/switch-psp")
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(404));

        String errorMessage = response.readEntity(JsonNode.class).get("message").get(0).textValue();
        assertThat(errorMessage, is("The gateway account id [1] does not exist."));
    }

    @Test
    void shouldReturn400WhenSwitchPaymentProviderIsNotEnabled() throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of("user_external_id", "some-user-external-id",
                "gateway_account_credential_external_id", randomUuid()));

        gatewayAccountEntity.setProviderSwitchEnabled(false);
        when(gatewayAccountService.getGatewayAccount(1)).thenReturn(Optional.of(gatewayAccountEntity));

        Response response = resources.client()
                .target("/v1/api/accounts/1/switch-psp")
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(400));

        String errorMessage = response.readEntity(JsonNode.class).get("message").get(0).textValue();
        assertThat(errorMessage, is("Account is not configured to switch PSP or already switched PSP."));
    }

    @Test
    void shouldNotThrowNullPointerWhenNoPayloadPresent() throws JsonProcessingException {
        String payload = "";

        gatewayAccountEntity.setProviderSwitchEnabled(true);
        when(gatewayAccountService.getGatewayAccount(1)).thenReturn(Optional.of(gatewayAccountEntity));

        Response response = resources.client()
                .target("/v1/api/accounts/1/switch-psp")
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(422));

        String errorMessage = response.readEntity(JsonNode.class).get("message").get(0).textValue();
        assertThat(errorMessage, is("must not be null"));
    }
}
