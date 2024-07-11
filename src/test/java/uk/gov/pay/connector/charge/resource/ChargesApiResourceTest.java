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
import static org.hamcrest.MatcherAssert.assertThat;
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
    private static final GatewayAccountEntity mockGatewayAccountEntity = mock(GatewayAccountEntity.class);
    private static final Charge mockCharge = mock(Charge.class);

    private static final String A_CHARGE_ID = "charge-id";
    private static final String A_SERVICE_ID = "a-service-id";
    private static final Long AN_ACCOUNT_ID = 1234L;
    private static final GatewayAccountType A_GATEWAY_ACCOUNT_TYPE = GatewayAccountType.TEST;

    public static ResourceExtension chargesApiResource = ResourceExtension.builder()
            .addResource(new ChargesApiResource(chargeService, chargeExpiryService, gatewayAccountService, userNotificationService))
            .setRegisterDefaultExceptionMappers(false)
            .addProvider(ConstraintViolationExceptionMapper.class)
            .addProvider(JsonMappingExceptionMapper.class)
            .addProvider(ValidationExceptionMapper.class)
            .build();

    @Nested
    @DisplayName("Given an account id")
    class ByAccountId {

        @Nested
        @DisplayName("Then resend confirmation email")
        class ResendConfirmationEmail {

            @Test
            @DisplayName("Should return 404 if account is not found")
            void accountNotFound_shouldReturn404() {
                when(gatewayAccountService.getGatewayAccount(AN_ACCOUNT_ID)).thenReturn(Optional.empty());

                try (Response response = chargesApiResource
                        .target(String.format("/v1/api/accounts/%d/charges/%s/resend-confirmation-email", AN_ACCOUNT_ID, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertGenericErrorResponse(response, 404, format("Gateway Account with id [%s] not found.", AN_ACCOUNT_ID));
                }
            }

            @Test
            @DisplayName("Should return 404 if charge is not found")
            void chargeNotFound_shouldReturn404() {
                when(gatewayAccountService.getGatewayAccount(AN_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
                when(chargeService.findCharge(A_CHARGE_ID, AN_ACCOUNT_ID)).thenReturn(Optional.empty());

                try (Response response = chargesApiResource
                        .target(String.format("/v1/api/accounts/%d/charges/%s/resend-confirmation-email", AN_ACCOUNT_ID, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertGenericErrorResponse(response, 404, format("Charge with id [%s] not found.", A_CHARGE_ID));
                }
            }

            @Test
            @DisplayName("Should return 402 if email is not sent")
            void emailNotSent_shouldReturn402() {
                when(gatewayAccountService.getGatewayAccount(AN_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
                when(chargeService.findCharge(A_CHARGE_ID, AN_ACCOUNT_ID)).thenReturn(Optional.of(mockCharge));
                when(userNotificationService.sendPaymentConfirmedEmailSynchronously(mockCharge, mockGatewayAccountEntity, true))
                        .thenReturn(Optional.empty());

                try (Response response = chargesApiResource
                        .target(format("/v1/api/accounts/%d/charges/%s/resend-confirmation-email", AN_ACCOUNT_ID, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertGenericErrorResponse(response, 402, "Failed to send email");
                }
            }

            @Test
            @DisplayName("Should return 204 if email is sent successfully")
            void success_shouldReturn204() {
                when(gatewayAccountService.getGatewayAccount(AN_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
                when(chargeService.findCharge(A_CHARGE_ID, AN_ACCOUNT_ID)).thenReturn(Optional.of(mockCharge));
                when(userNotificationService.sendPaymentConfirmedEmailSynchronously(mockCharge, mockGatewayAccountEntity, true))
                        .thenReturn(Optional.of("Email sent"));

                try (Response response = chargesApiResource
                        .target(String.format("/v1/api/accounts/%d/charges/%s/resend-confirmation-email", AN_ACCOUNT_ID, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertThat(response.getStatus(), is(204));
                }
            }
        }
    }

    @Nested
    @DisplayName("Given a service id and account type")
    class ByServiceIdAndAccountType {

        @Nested
        @DisplayName("Then resend confirmation email")
        class ResendConfirmationEmail {

            @Test
            @DisplayName("Should return 404 if account is not found")
            void accountNotFound_shouldReturn404() {
                when(gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE)).thenReturn(Optional.empty());

                try (Response response = chargesApiResource
                        .target(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertGenericErrorResponse(response, 404, format("Gateway account not found for service ID [%s] and account type [%s]", A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE));
                }
            }

            @Test
            @DisplayName("Should return 404 if charge is not found")
            void chargeNotFound_shouldReturn404() {
                when(gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE)).thenReturn(Optional.of(mockGatewayAccountEntity));
                when(chargeService.findCharge(any(), (Long) any())).thenReturn(Optional.empty());

                try (Response response = chargesApiResource
                        .target(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertGenericErrorResponse(response, 404, format("Charge with id [%s] not found.", A_CHARGE_ID));
                }
            }

            @Test
            @DisplayName("Should return 402 if email is not sent")
            void emailNotSent_shouldReturn402() {
                when(gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE)).thenReturn(Optional.of(mockGatewayAccountEntity));
                when(chargeService.findCharge(eq(A_CHARGE_ID), any())).thenReturn(Optional.of(mockCharge));
                when(userNotificationService.sendPaymentConfirmedEmailSynchronously(mockCharge, mockGatewayAccountEntity, true))
                        .thenReturn(Optional.empty());

                try (Response response = chargesApiResource
                        .target(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertGenericErrorResponse(response, 402, "Failed to send email");
                }
            }

            @Test
            @DisplayName("Should return 204 if email is sent successfully")
            void success_shouldReturn204() {
                when(gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE)).thenReturn(Optional.of(mockGatewayAccountEntity));
                when(chargeService.findCharge(eq(A_CHARGE_ID), any())).thenReturn(Optional.of(mockCharge));
                when(userNotificationService.sendPaymentConfirmedEmailSynchronously(mockCharge, mockGatewayAccountEntity, true))
                        .thenReturn(Optional.of("Email sent"));

                try (Response response = chargesApiResource
                        .target(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertThat(response.getStatus(), is(204));
                }
            }
        }
    }

    private static void assertGenericErrorResponse(Response response, int status, String errorMessage) {
        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(response.getStatus(), is(status));
        assertThat(errorResponse.identifier(), is(ErrorIdentifier.GENERIC));
        var errorIsPresentInMessages = errorResponse
                .messages()
                .stream()
                .anyMatch(message -> message
                        .contains(errorMessage));
        assertThat(errorIsPresentInMessages, is(true));
    }
}
