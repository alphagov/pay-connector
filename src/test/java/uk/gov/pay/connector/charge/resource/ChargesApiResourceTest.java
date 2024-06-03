package uk.gov.pay.connector.charge.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeExpiryService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.ConstraintViolationExceptionMapper;
import uk.gov.pay.connector.common.exception.ValidationExceptionMapper;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;
import uk.gov.pay.connector.util.JsonMappingExceptionMapper;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Nested
    @DisplayName("Given a create charge request")
    class CreateCharge {
        @Nested
        @DisplayName("When account id is provided")
        class WhenAccountIdIsProvided {
            @Test
            @DisplayName("Then 400 is returned if authorisation mode is invalid")
            void createCharge_invalidAuthorisationMode_shouldReturn400() {
                var payload = Map.of(
                        "amount", 100,
                        "reference", "ref",
                        "description", "desc",
                        "return_url", "http://service.url/success-page/",
                        "authorisation_mode", "foo"
                );

                try (Response response = resources
                        .target("/v1/api/accounts/1/charges")
                        .request()
                        .post(Entity.json(payload))) {

                    assertThat(response.getStatus(), is(400));
                    var errorResponse = response.readEntity(ErrorResponse.class);
                    assertThat(errorResponse.getMessages().get(0), startsWith("Cannot deserialize value of type `uk.gov.service.payments.commons.model.AuthorisationMode`"));
                    assertThat(errorResponse.getIdentifier(), is(ErrorIdentifier.GENERIC));
                }
            }

            @DisplayName("Then 422 is returned if idempotency key is above maximum length")
            @Test
            void createCharge_idempotencyKeyAboveMaxLength_shouldReturn422() {
                var payload = Map.of(
                        "amount", 100,
                        "reference", "ref",
                        "description", "desc",
                        "authorisation_mode", "agreement",
                        "agreement_id", "agreement12345677890123456"
                );

                try (Response response = resources
                        .target("/v1/api/accounts/1/charges")
                        .request()
                        .header("Idempotency-Key", "a".repeat(256))
                        .post(Entity.json(payload))) {

                    assertThat(response.getStatus(), is(422));
                    var errorResponse = response.readEntity(ErrorResponse.class);
                    assertThat(errorResponse.getMessages(), contains("Header [Idempotency-Key] can have a size between 1 and 255"));
                    assertThat(errorResponse.getIdentifier(), is(ErrorIdentifier.GENERIC));
                }
            }

            @DisplayName("Then 422 is returned if idempotency key is empty")
            @Test
            void createCharge_idempotencyKeyEmpty_shouldReturn422() {
                var payload = Map.of(
                        "amount", 100,
                        "reference", "ref",
                        "description", "desc",
                        "authorisation_mode", "agreement",
                        "agreement_id", "agreement12345677890123456"
                );

                try (Response response = resources
                        .target("/v1/api/accounts/1/charges")
                        .request()
                        .header("Idempotency-Key", "")
                        .post(Entity.json(payload))) {

                    assertThat(response.getStatus(), is(422));
                    var errorResponse = response.readEntity(ErrorResponse.class);
                    assertThat(errorResponse.getMessages(), contains("Header [Idempotency-Key] can have a size between 1 and 255"));
                    assertThat(errorResponse.getIdentifier(), is(ErrorIdentifier.GENERIC));
                }
            }
        }
    }
    

    @Nested
    @DisplayName("Given a resend confirmation email request")
    class ResendConfirmationEmail {
        private final String aChargeId = "charge-id";
        private final GatewayAccountEntity mockGatewayAccountEntity = mock(GatewayAccountEntity.class);
        private final Charge mockCharge = mock(Charge.class);
        
        @Nested
        @DisplayName("When account id is provided")
        class ByAccountId {
            private final Long anAccountId = 1234L;
            
            @Test
            @DisplayName("Then 404 is returned if account is not found")
            void resendConfirmationEmail_accountNotFound_shouldReturn404() {
                when(gatewayAccountService.getGatewayAccount(anAccountId)).thenReturn(Optional.empty());

                try (Response response = resources
                        .target(String.format("/v1/api/accounts/%d/charges/%s/resend-confirmation-email", anAccountId, aChargeId))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertThat(response.getStatus(), is(404));
                }
            }

            @Test
            @DisplayName("Then 404 is returned if charge is not found")
            void resendConfirmationEmail_chargeNotFound_shouldReturn404() {
                when(gatewayAccountService.getGatewayAccount(anAccountId)).thenReturn(Optional.of(mockGatewayAccountEntity));
                when(chargeService.findCharge(aChargeId, anAccountId)).thenReturn(Optional.empty());

                try (Response response = resources
                        .target(String.format("/v1/api/accounts/%d/charges/%s/resend-confirmation-email", anAccountId, aChargeId))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertThat(response.getStatus(), is(404));
                }
            }

            @Test
            @DisplayName("Then 402 is returned if email is not sent")
            void resendConfirmationEmail_emailNotSent_shouldReturn402() {
                when(gatewayAccountService.getGatewayAccount(anAccountId)).thenReturn(Optional.of(mockGatewayAccountEntity));
                when(chargeService.findCharge(aChargeId, anAccountId)).thenReturn(Optional.of(mockCharge));
                when(userNotificationService.sendPaymentConfirmedEmailSynchronously(mockCharge, mockGatewayAccountEntity, true))
                        .thenReturn(Optional.empty());

                try (Response response = resources
                        .target(String.format("/v1/api/accounts/%d/charges/%s/resend-confirmation-email", anAccountId, aChargeId))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertThat(response.getStatus(), is(402));
                }
            }

            @Test
            @DisplayName("Then 204 is returned if email is sent successfully")
            void resendConfirmationEmail_success_shouldReturn204() {
                when(gatewayAccountService.getGatewayAccount(anAccountId)).thenReturn(Optional.of(mockGatewayAccountEntity));
                when(chargeService.findCharge(aChargeId, anAccountId)).thenReturn(Optional.of(mockCharge));
                when(userNotificationService.sendPaymentConfirmedEmailSynchronously(mockCharge, mockGatewayAccountEntity, true))
                        .thenReturn(Optional.of("Email sent"));

                try (Response response = resources
                        .target(String.format("/v1/api/accounts/%d/charges/%s/resend-confirmation-email", anAccountId, aChargeId))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertThat(response.getStatus(), is(204));
                }
            }
        }

        @Nested
        @DisplayName("When service id and account type are provided")
        class ByServiceIdAndAccountType {
            private final String aServiceId = "external-service-id";
            private final GatewayAccountType aGatewayAccountType = GatewayAccountType.TEST;
            
            @Test
            @DisplayName("Then 404 is returned if account is not found")
            void resendConfirmationEmail_accountNotFound_shouldReturn404() {
                when(gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(aServiceId, aGatewayAccountType)).thenReturn(Optional.empty());

                try (Response response = resources
                        .target(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", aServiceId, aGatewayAccountType, aChargeId))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertThat(response.getStatus(), is(404));
                }
            }

            @Test
            @DisplayName("Then 404 is returned if charge is not found")
            void resendConfirmationEmail_chargeNotFound_shouldReturn404() {
                when(gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(aServiceId, aGatewayAccountType)).thenReturn(Optional.of(mockGatewayAccountEntity));
                when(chargeService.findCharge(any(), (Long) any())).thenReturn(Optional.empty());

                try (Response response = resources
                        .target(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", aServiceId, aGatewayAccountType, aChargeId))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertThat(response.getStatus(), is(404));
                }
            }

            @Test
            @DisplayName("Then 402 is returned if email is not sent")
            void resendConfirmationEmail_emailNotSent_shouldReturn402() {
                when(gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(aServiceId, aGatewayAccountType)).thenReturn(Optional.of(mockGatewayAccountEntity));
                when(chargeService.findCharge(eq(aChargeId), any())).thenReturn(Optional.of(mockCharge));
                when(userNotificationService.sendPaymentConfirmedEmailSynchronously(mockCharge, mockGatewayAccountEntity, true))
                        .thenReturn(Optional.empty());

                try (Response response = resources
                        .target(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", aServiceId, aGatewayAccountType, aChargeId))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertThat(response.getStatus(), is(402));
                }
            }

            @Test
            @DisplayName("Then 204 is returned if email is sent successfully")
            void resendConfirmationEmail_success_shouldReturn204() {
                when(gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(aServiceId, aGatewayAccountType)).thenReturn(Optional.of(mockGatewayAccountEntity));
                when(chargeService.findCharge(eq(aChargeId), any())).thenReturn(Optional.of(mockCharge));
                when(userNotificationService.sendPaymentConfirmedEmailSynchronously(mockCharge, mockGatewayAccountEntity, true))
                        .thenReturn(Optional.of("Email sent"));

                try (Response response = resources
                        .target(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", aServiceId, aGatewayAccountType, aChargeId))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertThat(response.getStatus(), is(204));
                }
            }
        }
    }
}
