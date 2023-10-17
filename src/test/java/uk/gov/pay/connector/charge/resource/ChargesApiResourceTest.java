package uk.gov.pay.connector.charge.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeExpiryService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.ConstraintViolationExceptionMapper;
import uk.gov.pay.connector.common.exception.ValidationExceptionMapper;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;
import uk.gov.pay.connector.util.JsonMappingExceptionMapper;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(DropwizardExtensionsSupport.class)
public class ChargesApiResourceTest {

    private static final ChargeService chargeService = mock(ChargeService.class);
    private static final ChargeExpiryService chargeExpiryService = mock(ChargeExpiryService.class);
    private static final GatewayAccountService gatewayAccountService = mock(GatewayAccountService.class);
    private static final UserNotificationService userNotificationService = mock(UserNotificationService.class);

    public static ResourceExtension resources = ResourceExtension.builder()
            .addResource(new ChargesApiResource(chargeService, chargeExpiryService, gatewayAccountService, userNotificationService))
            .setRegisterDefaultExceptionMappers(false)
            .addProvider(ConstraintViolationExceptionMapper.class)
            .addProvider(JsonMappingExceptionMapper.class)
            .addProvider(ValidationExceptionMapper.class)
            .build();

    @Test
    void createCharge_invalidAuthorisationMode_shouldReturn400() {
        var payload = Map.of(
                "amount", 100,
                "reference", "ref",
                "description", "desc",
                "return_url", "http://service.url/success-page/",
                "authorisation_mode", "foo"
        );

        Response response = resources
                .target("/v1/api/accounts/1/charges")
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(400));
        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(errorResponse.getMessages().get(0), startsWith("Cannot deserialize value of type `uk.gov.service.payments.commons.model.AuthorisationMode`"));
        assertThat(errorResponse.getIdentifier(), is(ErrorIdentifier.GENERIC));
    }

    @Test
    void createCharge_idempotencyKeyAboveMaxLength_shouldReturn422() {
        var payload = Map.of(
                "amount", 100,
                "reference", "ref",
                "description", "desc",
                "authorisation_mode", "agreement",
                "agreement_id", "agreement12345677890123456"
        );

        Response response = resources
                .target("/v1/api/accounts/1/charges")
                .request()
                .header("Idempotency-Key", "a".repeat(256))
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(422));
        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(errorResponse.getMessages(), contains("Header [Idempotency-Key] can have a size between 1 and 255"));
        assertThat(errorResponse.getIdentifier(), is(ErrorIdentifier.GENERIC));
    }

    @Test
    void createCharge_idempotencyKeyEmpty_shouldReturn422() {
        var payload = Map.of(
                "amount", 100,
                "reference", "ref",
                "description", "desc",
                "authorisation_mode", "agreement",
                "agreement_id", "agreement12345677890123456"
        );

        Response response = resources
                .target("/v1/api/accounts/1/charges")
                .request()
                .header("Idempotency-Key", "")
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(422));
        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(errorResponse.getMessages(), contains("Header [Idempotency-Key] can have a size between 1 and 255"));
        assertThat(errorResponse.getIdentifier(), is(ErrorIdentifier.GENERIC));
    }

    @Test
    void resendConfirmationEmail_accountNotFound_shouldReturn404() {
        var accountId = 1234L;
        var chargeId = "charge-id";

        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.empty());

        Response response = resources
                .target(String.format("/v1/api/accounts/%d/charges/%s/resend-confirmation-email", accountId, chargeId))
                .request()
                .post(Entity.json(Collections.emptyMap()));

        assertThat(response.getStatus(), is(404));
    }

    @Test
    void resendConfirmationEmail_chargeNotFound_shouldReturn404() {
        var accountId = 1234L;
        var chargeId = "charge-id";
        var account = mock(GatewayAccountEntity.class);

        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.of(account));
        when(chargeService.findCharge(chargeId, accountId)).thenReturn(Optional.empty());

        Response response = resources
                .target(String.format("/v1/api/accounts/%d/charges/%s/resend-confirmation-email", accountId, chargeId))
                .request()
                .post(Entity.json(Collections.emptyMap()));

        assertThat(response.getStatus(), is(404));
    }

    @Test
    void resendConfirmationEmail_emailNotSent_shouldReturn402() {
        var accountId = 1234L;
        var chargeId = "charge-id";
        var account = mock(GatewayAccountEntity.class);
        var charge = mock(Charge.class);

        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.of(account));
        when(chargeService.findCharge(chargeId, accountId)).thenReturn(Optional.of(charge));
        when(userNotificationService.sendPaymentConfirmedEmailSynchronously(charge, account, true))
                .thenReturn(Optional.empty());

        Response response = resources
                .target(String.format("/v1/api/accounts/%d/charges/%s/resend-confirmation-email", accountId, chargeId))
                .request()
                .post(Entity.json(Collections.emptyMap()));

        assertThat(response.getStatus(), is(402));
    }

    @Test
    void resendConfirmationEmail_success_shouldReturn204() {
        var accountId = 1234L;
        var chargeId = "charge-id";
        var account = mock(GatewayAccountEntity.class);
        var charge = mock(Charge.class);

        when(gatewayAccountService.getGatewayAccount(accountId)).thenReturn(Optional.of(account));
        when(chargeService.findCharge(chargeId, accountId)).thenReturn(Optional.of(charge));
        when(userNotificationService.sendPaymentConfirmedEmailSynchronously(charge, account, true))
                .thenReturn(Optional.of("Email sent"));

        Response response = resources
                .target(String.format("/v1/api/accounts/%d/charges/%s/resend-confirmation-email", accountId, chargeId))
                .request()
                .post(Entity.json(Collections.emptyMap()));

        assertThat(response.getStatus(), is(204));
    }
}
