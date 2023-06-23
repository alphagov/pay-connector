package uk.gov.pay.connector.gatewayaccountcredentials.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.pay.connector.common.exception.ConstraintViolationExceptionMapper;
import uk.gov.pay.connector.common.exception.ValidationExceptionMapper;
import uk.gov.pay.connector.gateway.worldpay.Worldpay3dsFlexCredentialsValidationService;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCredentialsValidationService;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayValidatableCredentials;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.Worldpay3dsFlexCredentialsService;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(DropwizardExtensionsSupport.class)
public class GatewayAccountCredentialsResourceTest {

    private static final GatewayAccountService gatewayAccountService = mock(GatewayAccountService.class);
    private static final Worldpay3dsFlexCredentialsService worldpay3dsFlexCredentialsService = mock(Worldpay3dsFlexCredentialsService.class);
    private static final Worldpay3dsFlexCredentialsValidationService worldpay3dsFlexCredentialsValidationService = mock(Worldpay3dsFlexCredentialsValidationService.class);
    private static final WorldpayCredentialsValidationService worldpayCredentialsValidationService = mock(WorldpayCredentialsValidationService.class);
    private static final GatewayAccountCredentialsRequestValidator gatewayAccountCredentialsRequestValidator = mock(GatewayAccountCredentialsRequestValidator.class);
    private static final GatewayAccountCredentialsDao credentialDao = mock(GatewayAccountCredentialsDao.class);

    public static ResourceExtension resources = ResourceExtension.builder()
            .addResource(new GatewayAccountCredentialsResource(
                    gatewayAccountService,
                    new GatewayAccountCredentialsService(credentialDao),
                    worldpay3dsFlexCredentialsService,
                    worldpay3dsFlexCredentialsValidationService,
                    worldpayCredentialsValidationService,
                    gatewayAccountCredentialsRequestValidator
            ))
            .setRegisterDefaultExceptionMappers(false)
            .addProvider(ConstraintViolationExceptionMapper.class)
            .addProvider(ValidationExceptionMapper.class)
            .build();

    private static final String VALID_ISSUER = "53f0917f101a4428b69d5fb0"; // pragma: allowlist secret`
    private static final String VALID_ORG_UNIT_ID = "57992a087a0c4849895ab8a2"; // pragma: allowlist secret`
    private static final String VALID_JWT_MAC_KEY = "4cabd5d2-0133-4e82-b0e5-2024dbeddaa9"; // pragma: allowlist secret`

    private static final Map<String, String> valid3dsFlexCredentialsPayload = Map.of(
            "issuer", VALID_ISSUER,
            "organisational_unit_id", VALID_ORG_UNIT_ID,
            "jwt_mac_key", VALID_JWT_MAC_KEY);

    private static final Map<String, String> validCheckWorldpayCredentialsPayload = Map.of(
            "username", "valid-user-name",
            "password", "valid-password",
            "merchant_id", "valid-merchant-id"
    );

    private final long accountId = 111;
    private final GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
            .withId(accountId)
            .withGatewayName("worldpay")
            .build();
    private final long stripeAccountId = 333;
    private final GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
            .withPaymentProvider("stripe")
            .withCredentials(Map.of())
            .withState(CREATED)
            .build();
    private final GatewayAccountEntity stripeGatewayAccountEntity = aGatewayAccountEntity()
            .withId(stripeAccountId)
            .withGatewayName("stripe")
            .withGatewayAccountCredentials(List.of(credentialsEntity))
            .build();

    @Test
    void validate3dsCredentialsGatewayAccountNotFound_shouldReturn404() {
        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.empty());

        var payload = valid3dsFlexCredentialsPayload;

        Response response = resources
                .target(format("/v1/api/accounts/%s/worldpay/check-3ds-flex-config", accountId))
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(404));
        assertThat(extractErrorMessagesFromResponse(response).get(0), is("Gateway Account with id [111] not found."));
    }

    @Test
    void organisationalUnitIdNotInCorrectFormat_shouldReturn422() {
        var payload = Map.of(
                "issuer", VALID_ISSUER,
                "organisational_unit_id", "incorrect format",
                "jwt_mac_key", VALID_JWT_MAC_KEY);

        verifyCheckWordlapy3dsCredentialsValidationError(payload, "Field [organisational_unit_id] must be 24 lower-case hexadecimal characters");
    }

    @Test
    void organisationalUnitIdNull_shouldReturn422() {
        var payload = new HashMap<String, String>();
        payload.put("issuer", VALID_ISSUER);
        payload.put("organisational_unit_id", null);
        payload.put("jwt_mac_key", VALID_JWT_MAC_KEY);

        verifyCheckWordlapy3dsCredentialsValidationError(payload, "Field [organisational_unit_id] must be 24 lower-case hexadecimal characters");
    }

    @Test
    void issuerNotInCorrectFormat_shouldReturn422() {
        var payload = Map.of("issuer", "44992i087n0v4849895al9i3",
                "organisational_unit_id", VALID_ORG_UNIT_ID,
                "jwt_mac_key", VALID_JWT_MAC_KEY);

        verifyCheckWordlapy3dsCredentialsValidationError(payload, "Field [issuer] must be 24 lower-case hexadecimal characters");
    }

    @Test
    void issuerNull_shouldReturn422() {
        var payload = new HashMap<String, String>();
        payload.put("issuer", null);
        payload.put("organisational_unit_id", VALID_ORG_UNIT_ID);
        payload.put("jwt_mac_key", VALID_JWT_MAC_KEY);

        verifyCheckWordlapy3dsCredentialsValidationError(payload, "Field [issuer] must be 24 lower-case hexadecimal characters");
    }

    @Test
    void jwtMacKeyNotInCorrectFormat_shouldReturn422() {
        var payload = Map.of("issuer", VALID_ISSUER,
                "organisational_unit_id", VALID_ORG_UNIT_ID,
                "jwt_mac_key", "hihihihi");

        verifyCheckWordlapy3dsCredentialsValidationError(payload, "Field [jwt_mac_key] must be a UUID in its lowercase canonical representation");
    }

    @Test
    void jwtMacKeyNull_shouldReturn422() {
        var payload = new HashMap<String, String>();
        payload.put("issuer", VALID_ISSUER);
        payload.put("organisational_unit_id", VALID_ORG_UNIT_ID);
        payload.put("jwt_mac_key", null);

        verifyCheckWordlapy3dsCredentialsValidationError(payload, "Field [jwt_mac_key] must be a UUID in its lowercase canonical representation");
    }

    private void verifyCheckWordlapy3dsCredentialsValidationError(Map<String, String> payload, String expectedErrorMessage) {
        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.of(gatewayAccountEntity));
        Response response = resources
                .target(format("/v1/api/accounts/%s/worldpay/check-3ds-flex-config", accountId))
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(422));
        assertThat(extractErrorMessagesFromResponse(response).get(0), is(expectedErrorMessage));
    }

    @ParameterizedTest
    @CsvSource({
            "jwt_mac_key, Field [jwt_mac_key] must be a UUID in its lowercase canonical representation",
            "issuer, Field [issuer] must be 24 lower-case hexadecimal characters",
            "organisational_unit_id, Field [organisational_unit_id] must be 24 lower-case hexadecimal characters",
    })
    void update3dsFlexCredentials_missingFieldsReturnCorrectError(String key, String expectedErrorMessage) {
        Map<String, String> payload = new HashMap<>();
        payload.put("issuer", VALID_ISSUER);
        payload.put("organisational_unit_id", VALID_ORG_UNIT_ID);
        payload.put("jwt_mac_key", VALID_JWT_MAC_KEY);
        payload.remove(key);

        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.of(gatewayAccountEntity));
        Response response = resources
                .target(format("/v1/api/accounts/%s/3ds-flex-credentials", accountId))
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(422));
        assertThat(extractErrorMessagesFromResponse(response).get(0), is(expectedErrorMessage));
    }

    @Test
    void update3dsFlexCredentials_nonExistentGatewayAccountReturns404() {
        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.empty());
        Response response = resources
                .target(format("/v1/api/accounts/%s/3ds-flex-credentials", accountId))
                .request()
                .post(Entity.json(valid3dsFlexCredentialsPayload));

        assertThat(response.getStatus(), is(404));
        assertThat(extractErrorMessagesFromResponse(response).get(0), is("Not a Worldpay gateway account"));
    }

    @Test
    void update3dsFlexCredentials_nonWorldpayGatewayAccountReturns404() {
        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.of(stripeGatewayAccountEntity));
        Response response = resources
                .target(format("/v1/api/accounts/%s/3ds-flex-credentials", accountId))
                .request()
                .post(Entity.json(valid3dsFlexCredentialsPayload));

        assertThat(response.getStatus(), is(404));
        assertThat(extractErrorMessagesFromResponse(response).get(0), is("Not a Worldpay gateway account"));
    }

    @Test
    void checkWorldpayCredentials_nonExistentGatewayAccountReturns404() {
        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.empty());
        Response response = resources
                .target(format("/v1/api/accounts/%s/worldpay/check-credentials", accountId))
                .request()
                .post(Entity.json(validCheckWorldpayCredentialsPayload));

        assertThat(response.getStatus(), is(404));
        assertThat(extractErrorMessagesFromResponse(response).get(0), is(format("Gateway Account with id [%s] not found.", accountId)));
    }

    @Test
    void checkWorldpayCredentials_returns422WhenUsernameMissing() {
        var payload = Map.of(
                "username", "",
                "password", "valid-password",
                "merchant_id", "valid-merchant-id"
        );

        Response response = resources
                .target(format("/v1/api/accounts/%s/worldpay/check-credentials", accountId))
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(422));
        assertThat(extractErrorMessagesFromResponse(response).get(0), is("Field [username] is required"));
    }

    @Test
    void checkWorldpayCredentials_returns422WhenPasswordMissing() {
        var payload = Map.of(
                "username", "valid-username",
                "password", "",
                "merchant_id", "valid-merchant-id"
        );

        Response response = resources
                .target(format("/v1/api/accounts/%s/worldpay/check-credentials", accountId))
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(422));
        assertThat(extractErrorMessagesFromResponse(response).get(0), is("Field [password] is required"));
    }

    @Test
    void checkWorldpayCredentials_returns422WhenMerchantIdMissing() {
        var payload = Map.of(
                "username", "valid-username",
                "password", "valid-password",
                "merchant_id", ""
        );

        Response response = resources
                .target(format("/v1/api/accounts/%s/worldpay/check-credentials", accountId))
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(422));
        assertThat(extractErrorMessagesFromResponse(response).get(0), is("Field [merchant_id] is required"));
    }

    @Test
    void checkWorldpayCredentials_returnsValid() {
        WorldpayValidatableCredentials worldpayValidatableCredentials = new WorldpayValidatableCredentials(
                validCheckWorldpayCredentialsPayload.get("merchant_id"),
                validCheckWorldpayCredentialsPayload.get("username"),
                validCheckWorldpayCredentialsPayload.get("password"));

        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.of(gatewayAccountEntity));
        when(worldpayCredentialsValidationService.validateCredentials(gatewayAccountEntity, worldpayValidatableCredentials)).thenReturn(true);

        Response response = resources
                .target(format("/v1/api/accounts/%s/worldpay/check-credentials", accountId))
                .request()
                .post(Entity.json(validCheckWorldpayCredentialsPayload));

        verify(worldpayCredentialsValidationService).validateCredentials(gatewayAccountEntity, worldpayValidatableCredentials);

        assertThat(response.getStatus(), is(200));
        Map<String, Object> responseBody = response.readEntity(new GenericType<HashMap>() {
        });
        assertThat(responseBody, hasEntry("result", "valid"));
    }

    @Test
    void checkWorldpayCredentials_returnsInvalid() {
        WorldpayValidatableCredentials worldpayValidatableCredentials = new WorldpayValidatableCredentials(
                validCheckWorldpayCredentialsPayload.get("merchant_id"),
                validCheckWorldpayCredentialsPayload.get("username"),
                validCheckWorldpayCredentialsPayload.get("password"));

        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.of(gatewayAccountEntity));
        when(worldpayCredentialsValidationService.validateCredentials(gatewayAccountEntity, worldpayValidatableCredentials)).thenReturn(false);

        Response response = resources
                .target(format("/v1/api/accounts/%s/worldpay/check-credentials", accountId))
                .request()
                .post(Entity.json(validCheckWorldpayCredentialsPayload));

        verify(worldpayCredentialsValidationService).validateCredentials(gatewayAccountEntity, worldpayValidatableCredentials);

        assertThat(response.getStatus(), is(200));
        Map<String, Object> responseBody = response.readEntity(new GenericType<HashMap>() {
        });
        assertThat(responseBody, hasEntry("result", "invalid"));
    }

    @Test
    void createGatewayAccountCredentialsGatewayAccountNotFound_shouldReturn404() {
        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.empty());

        Response response = resources
                .target(format("/v1/api/accounts/%s/credentials", accountId))
                .request()
                .post(Entity.json(Map.of("payment_provider", "worldpay")));

        assertThat(response.getStatus(), is(404));
        assertThat(extractErrorMessagesFromResponse(response).get(0), is("Gateway Account with id [111] not found."));
    }

    @Test
    void createGatewayAccountCredentialsMissingBody_shouldReturn422() {
        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.of(gatewayAccountEntity));

        Response response = resources
                .target(format("/v1/api/accounts/%s/credentials", accountId))
                .request()
                .post(null);

        assertThat(response.getStatus(), is(422));
    }

    @Test
    void patchGatewayAccountCredentialsGatewayAccountNotFound_shouldReturn404() {
        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.empty());

        var payload = Collections.singletonList(
                Map.of("op", "replace",
                        "path", "last_updated_by_user_external_id",
                        "value", "a-user-id")
        );
        Response response = resources
                .target(format("/v1/api/accounts/%s/credentials/222", accountId))
                .request()
                .method("PATCH", Entity.json(payload));

        assertThat(response.getStatus(), is(404));
        assertThat(extractErrorMessagesFromResponse(response).get(0), is("Gateway Account with id [111] not found."));
    }

    private List extractErrorMessagesFromResponse(Response response) {
        var responseBody = response.readEntity(new GenericType<HashMap>() {
        });
        return (List) responseBody.get("message");
    }
}
