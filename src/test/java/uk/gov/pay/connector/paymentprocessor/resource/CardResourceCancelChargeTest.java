package uk.gov.pay.connector.paymentprocessor.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.pay.connector.charge.dao.ChargeDao;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeCancelService;
import uk.gov.pay.connector.charge.service.ChargeEligibleForCaptureService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.DelayedCaptureService;
import uk.gov.pay.connector.charge.service.motoapi.MotoApiCardNumberValidationService;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService;
import uk.gov.pay.connector.paymentprocessor.service.CardAuthoriseService;
import uk.gov.pay.connector.paymentprocessor.service.QueryService;
import uk.gov.pay.connector.rules.ResourceTestRuleWithCustomExceptionMappersBuilder;
import uk.gov.pay.connector.token.TokenService;
import uk.gov.pay.connector.wallets.WalletService;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Optional;

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;

@ExtendWith(DropwizardExtensionsSupport.class)
public class CardResourceCancelChargeTest {

    private static final ChargeDao mockChargeDao = mock(ChargeDao.class);
    private static final PaymentProviders mockPaymentProviders = mock(PaymentProviders.class);
    private static final ChargeService mockChargeService = mock(ChargeService.class);
    private static final QueryService mockQueryService = mock(QueryService.class);
    private static final CardAuthoriseService mockCardAuthoriseService = mock(CardAuthoriseService.class);
    private static final Card3dsResponseAuthService mockCard3dsResponseAuthService = mock(Card3dsResponseAuthService.class);
    private static final ChargeEligibleForCaptureService mockChargeEligibleForCaptureService = mock(ChargeEligibleForCaptureService.class);
    private static final DelayedCaptureService mockDelayedCaptureService = mock(DelayedCaptureService.class);
    private static final ChargeCancelService mockChargeCancelService = new ChargeCancelService(
            mockChargeDao,
            mockPaymentProviders,
            mockChargeService,
            mockQueryService
    );
    private static final WalletService mockWalletService = mock(WalletService.class);
    private static final TokenService mockTokenService = mock(TokenService.class);
    private static final MotoApiCardNumberValidationService mockMotoApiCardNumberValidationService = mock(MotoApiCardNumberValidationService.class);
    private static final GatewayAccountService mockGatewayAccountService = mock(GatewayAccountService.class);

    private static final String A_CHARGE_ID = "a-charge-id";
    private static final String A_SERVICE_ID = "a-service-id";
    private static final Long A_GATEWAY_ACCOUNT_ID = 1234L;
    private static final GatewayAccountType A_GATEWAY_ACCOUNT_TYPE = GatewayAccountType.TEST;

    private final ResourceExtension cardResource = ResourceTestRuleWithCustomExceptionMappersBuilder.getBuilder()
            .addResource(new CardResource(
                    mockCardAuthoriseService,
                    mockCard3dsResponseAuthService,
                    mockChargeEligibleForCaptureService,
                    mockDelayedCaptureService,
                    mockChargeCancelService,
                    mockWalletService,
                    mockTokenService,
                    mockMotoApiCardNumberValidationService,
                    mockGatewayAccountService
            ))
            .build();
    
    @AfterEach
    void teardown() {
        reset(mockChargeService);
    }

    @Nested
    @DisplayName("Given a gateway account id")
    class ByAccountId {

        @Nested
        @DisplayName("Then cancel charge")
        class CancelCharge {

            @Test
            @DisplayName("Should return 404 if charge is not found")
            void chargeNotFound_shouldReturn404() {
                when(mockChargeDao.findByExternalIdAndGatewayAccount(any(), any())).thenReturn(Optional.empty());
                try (Response response = cardResource
                        .target(String.format("/v1/api/accounts/%d/charges/%s/cancel", A_GATEWAY_ACCOUNT_ID, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertErrorResponse(response, 404, format("Charge with id [%s] not found.", A_CHARGE_ID));
                }
                verify(mockChargeService, times(0)).transitionChargeState(anyString(), any(ChargeStatus.class));
            }

            @Test
            @DisplayName("Should return 400 if charge is not in correct state")
            void badChargeState_shouldReturn400() {
                when(mockChargeDao.findByExternalIdAndGatewayAccount(A_CHARGE_ID, A_GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(
                        aValidChargeEntity()
                                .withExternalId("bad-charge-state")
                                .withStatus(ChargeStatus.CAPTURE_QUEUED)
                                .build()
                ));
                try (Response response = cardResource
                        .target(String.format("/v1/api/accounts/%d/charges/%s/cancel", A_GATEWAY_ACCOUNT_ID, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertErrorResponse(response, 400, format("Charge not in correct state to be processed, %s", "bad-charge-state"));
                }
                verify(mockChargeService, times(0)).transitionChargeState(anyString(), any(ChargeStatus.class));
            }

            @Test
            @DisplayName("Should return 204 if charge is cancelled successfully")
            void success_shouldReturn204() {
                when(mockChargeDao.findByExternalIdAndGatewayAccount(A_CHARGE_ID, A_GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(
                        aValidChargeEntity()
                                .withExternalId(A_CHARGE_ID)
                                .withStatus(ChargeStatus.CREATED)
                                .build()
                ));
                try (Response response = cardResource
                        .target(String.format("/v1/api/accounts/%d/charges/%s/cancel", A_GATEWAY_ACCOUNT_ID, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertThat(response.getStatus(), is(204));
                }
                verify(mockChargeService, times(1)).transitionChargeState(A_CHARGE_ID, ChargeStatus.SYSTEM_CANCELLED);
            }

            @Test
            @DisplayName("Should return 202 if cancel operation is already in progress")
            void success_shouldReturn202() {
                when(mockChargeDao.findByExternalIdAndGatewayAccount(A_CHARGE_ID, A_GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(
                        aValidChargeEntity()
                                .withExternalId(A_CHARGE_ID)
                                .withStatus(ChargeStatus.SYSTEM_CANCEL_SUBMITTED)
                                .build()
                ));
                try (Response response = cardResource
                        .target(String.format("/v1/api/accounts/%d/charges/%s/cancel", A_GATEWAY_ACCOUNT_ID, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertThat(response.getStatus(), is(202));
                }
                verify(mockChargeService, times(0)).transitionChargeState(anyString(), any(ChargeStatus.class));
            }
        }

    }

    @Nested
    @DisplayName("Given a service id and account type")
    class ByServiceIdAndAccountType {

        @Nested
        @DisplayName("Then cancel charge")
        class CancelCharge {

            @Test
            @DisplayName("Should return 404 if account is not found")
            void accountNotFound_shouldReturn404() {
                when(mockGatewayAccountService.getGatewayAccountByServiceIdAndAccountType(any(), any())).thenReturn(Optional.empty());
                try (Response response = cardResource
                        .target(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertErrorResponse(response, 404, format("Charge with id [%s] not found.", A_CHARGE_ID));
                }
                verify(mockChargeService, times(0)).transitionChargeState(anyString(), any(ChargeStatus.class));
            }

            @Test
            @DisplayName("Should return 404 if charge is not found")
            void chargeNotFound_shouldReturn404() {
                when(mockGatewayAccountService.getGatewayAccountByServiceIdAndAccountType(A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE)).thenReturn(Optional.of(
                        aGatewayAccountEntity()
                                .withId(A_GATEWAY_ACCOUNT_ID)
                                .build()
                ));
                when(mockChargeDao.findByExternalIdAndGatewayAccount(A_CHARGE_ID, A_GATEWAY_ACCOUNT_ID)).thenReturn(Optional.empty());
                try (Response response = cardResource
                        .target(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertErrorResponse(response, 404, format("Charge with id [%s] not found.", A_CHARGE_ID));
                }
                verify(mockChargeService, times(0)).transitionChargeState(anyString(), any(ChargeStatus.class));
            }

            @Test
            @DisplayName("Should return 400 if charge is not in correct state")
            void badChargeState_shouldReturn400() {
                when(mockGatewayAccountService.getGatewayAccountByServiceIdAndAccountType(A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE)).thenReturn(Optional.of(
                        aGatewayAccountEntity()
                                .withId(A_GATEWAY_ACCOUNT_ID)
                                .build()
                ));
                when(mockChargeDao.findByExternalIdAndGatewayAccount(A_CHARGE_ID, A_GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(
                        aValidChargeEntity()
                                .withExternalId(A_CHARGE_ID)
                                .withStatus(ChargeStatus.CAPTURE_QUEUED)
                                .build()
                ));
                try (Response response = cardResource
                        .target(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertErrorResponse(response, 400, format("Charge not in correct state to be processed, %s", A_CHARGE_ID));
                }
                verify(mockChargeService, times(0)).transitionChargeState(anyString(), any(ChargeStatus.class));
            }

            @Test
            @DisplayName("Should return 204 if charge is cancelled successfully")
            void success_shouldReturn204() {
                when(mockGatewayAccountService.getGatewayAccountByServiceIdAndAccountType(A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE)).thenReturn(Optional.of(
                        aGatewayAccountEntity()
                                .withId(A_GATEWAY_ACCOUNT_ID)
                                .build()
                ));
                when(mockChargeDao.findByExternalIdAndGatewayAccount(A_CHARGE_ID, A_GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(
                        aValidChargeEntity()
                                .withExternalId(A_CHARGE_ID)
                                .withStatus(ChargeStatus.CREATED)
                                .build()
                ));
                try (Response response = cardResource
                        .target(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {
                    
                    assertThat(response.getStatus(), is(204));
                }
                verify(mockChargeService, times(1)).transitionChargeState(A_CHARGE_ID, ChargeStatus.SYSTEM_CANCELLED);
            }

            @Test
            @DisplayName("Should return 202 if cancel operation is already in progress")
            void success_shouldReturn202() {
                when(mockGatewayAccountService.getGatewayAccountByServiceIdAndAccountType(A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE)).thenReturn(Optional.of(
                        aGatewayAccountEntity()
                                .withId(A_GATEWAY_ACCOUNT_ID)
                                .build()
                ));
                when(mockChargeDao.findByExternalIdAndGatewayAccount(A_CHARGE_ID, A_GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(
                        aValidChargeEntity()
                                .withStatus(ChargeStatus.SYSTEM_CANCEL_SUBMITTED)
                                .build()
                ));
                try (Response response = cardResource
                        .target(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", A_SERVICE_ID, A_GATEWAY_ACCOUNT_TYPE, A_CHARGE_ID))
                        .request()
                        .post(Entity.json(Collections.emptyMap()))) {

                    assertThat(response.getStatus(), is(202));
                }
                verify(mockChargeService, times(0)).transitionChargeState(anyString(), any(ChargeStatus.class));
            }
        }
    }

    private void assertErrorResponse(Response response, int status, String errorMessage) {
        assertThat(response.getStatus(), is(status));
        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(errorResponse.messages(), hasItem(errorMessage));
    }
}
