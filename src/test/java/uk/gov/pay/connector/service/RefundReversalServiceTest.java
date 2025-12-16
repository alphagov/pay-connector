package uk.gov.pay.connector.service;

import com.stripe.exception.StripeException;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.client.ledger.exception.GetRefundsForPaymentException;
import uk.gov.pay.connector.client.ledger.exception.LedgerException;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.eventdetails.refund.PaymentStatusCorrectedToSuccessByAdminEventDetails;
import uk.gov.pay.connector.events.eventdetails.refund.RefundFailureFundsSentToConnectAccountEventDetails;
import uk.gov.pay.connector.events.eventdetails.refund.RefundStatusCorrectedToErrorByAdminEventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.refund.PaymentStatusCorrectedToSuccessByAdmin;
import uk.gov.pay.connector.events.model.refund.RefundFailureFundsSentToConnectAccount;
import uk.gov.pay.connector.events.model.refund.RefundStatusCorrectedToErrorByAdmin;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClient;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClientFactory;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.GithubAndZendeskCredential;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundReversalService;
import uk.gov.pay.connector.refund.service.RefundReversalStripeConnectTransferRequestBuilder;
import uk.gov.pay.connector.util.RandomIdGenerator;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

@ExtendWith(MockitoExtension.class)
public class RefundReversalServiceTest {
    private RefundReversalService refundReversalService;

    private final String refundExternalId = "refund123";

    @Mock
    private RefundDao mockRefundDao;
    @Mock
    private LedgerService mockLedgerService;
    @Mock
    private StripeSdkClient mockStripeSDKClient;
    @Mock
    private StripeSdkClientFactory mockStripeSDKClientFactory;
    @Mock
    private RefundReversalStripeConnectTransferRequestBuilder mockBuilder;

    ChargeEntityFixture chargeEntityFixture = ChargeEntityFixture.aValidChargeEntity()
            .withGatewayAccountEntity(aGatewayAccountEntity().withType(LIVE).build());
    Charge charge = Charge.from(chargeEntityFixture.build());
    GithubAndZendeskCredential githubAndZendeskCredential = new GithubAndZendeskCredential("1223333343", "John Doe (JohnDoeGds)");

    private final String githubUserId = githubAndZendeskCredential.githubUserId();
    private final String zendeskId = githubAndZendeskCredential.zendeskTicketId();

    @BeforeEach
    void setUp() {
        refundReversalService = new RefundReversalService(mockLedgerService, mockRefundDao,
                mockStripeSDKClientFactory, mockBuilder);
    }

    @Test
    void shouldFindRefundInLedger() {

        LedgerTransaction ledgerTransaction = aValidLedgerTransaction()
                .withExternalId(refundExternalId)
                .withStatus(ExternalRefundStatus.EXTERNAL_SUBMITTED.getStatus())
                .build();
        when(mockLedgerService.getTransaction(refundExternalId)).thenReturn(Optional.of(ledgerTransaction));

        Optional<Refund> result = refundReversalService.findMaybeHistoricRefundByRefundId(refundExternalId);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().getExternalId(), is(refundExternalId));
    }


    @Test
    void shouldFindRefundInConnector() {
        RefundEntity refundEntityInConnector = aValidRefundEntity().withExternalId(refundExternalId).build();

        when(mockRefundDao.findByExternalId(refundExternalId)).thenReturn(Optional.of(refundEntityInConnector));

        Optional<Refund> result = refundReversalService.findMaybeHistoricRefundByRefundId(refundExternalId);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().getExternalId(), is(refundExternalId));

        verifyNoInteractions(mockLedgerService);
    }

    @Test
    void shouldNotFindRefund() {
        when(mockRefundDao.findByExternalId(refundExternalId)).thenReturn(Optional.empty());
        when(mockLedgerService.getTransaction(refundExternalId)).thenReturn(Optional.empty());

        Optional<Refund> result = refundReversalService.findMaybeHistoricRefundByRefundId(refundExternalId);

        assertThat(result.isPresent(), is(false));
    }

    @Test
    void shouldCreateTransferWhenRefundIsInFailedState() throws StripeException {

        when(mockStripeSDKClientFactory.getInstance()).thenReturn(mockStripeSDKClient);

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withType(LIVE).build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withAmount(100L)
                .withGatewayTransactionId("a-transaction-id")
                .build();
        Refund refund = Refund.from(refundEntity);

        String stripeRefundId = refund.getGatewayTransactionId();
        boolean isLiveGatewayAccount = gatewayAccountEntity.isLive();
        com.stripe.model.Refund mockedStripeRefund = mock(com.stripe.model.Refund.class);
        when(mockStripeSDKClient.getRefund(stripeRefundId, isLiveGatewayAccount)).thenReturn(mockedStripeRefund);
        when(mockedStripeRefund.getStatus()).thenReturn("failed");

        Map<String, Object> transferRequest = Map.of(
                "destination", "acct_jdsa7789d",
                "amount", 100L,
                "metadata", Map.of(
                        "stripeChargeId", "ch_sdkhdg887s",
                        "correctionPaymentId", "random123"
                ),
                "currency", "GBP",
                "transferGroup", "abc",
                "expand", new String[]{"balance_transaction", "destination_payment"}
        );

        when(mockBuilder.createRequest(anyString(), eq(mockedStripeRefund))).thenReturn(transferRequest);
        assertDoesNotThrow(() -> refundReversalService.reverseFailedRefund(gatewayAccountEntity, refund, charge, githubUserId, zendeskId));

        verify(mockStripeSDKClient).createTransfer(transferRequest, true, refund.getExternalId());
    }

    @Test
    void shouldThrowExceptionWhenRefundIsNotInFailedState() throws StripeException {

        when(mockStripeSDKClientFactory.getInstance()).thenReturn(mockStripeSDKClient);

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withType(LIVE).build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withAmount(100L)
                .withGatewayTransactionId("a-transaction-id")
                .build();
        Refund refund = Refund.from(refundEntity);

        String stripeRefundId = refund.getGatewayTransactionId();
        boolean isLiveGatewayAccount = gatewayAccountEntity.isLive();
        com.stripe.model.Refund mockedStripeRefund = mock(com.stripe.model.Refund.class);
        when(mockStripeSDKClient.getRefund(stripeRefundId, isLiveGatewayAccount)).thenReturn(mockedStripeRefund);
        when(mockedStripeRefund.getStatus()).thenReturn("succeeded");

        WebApplicationException thrown = assertThrows(WebApplicationException.class, () ->
                refundReversalService.reverseFailedRefund(gatewayAccountEntity, refund, charge, githubUserId, zendeskId));

        Response response = thrown.getResponse();
        ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
        assertThat(errorResponse.messages(), hasItems("Refund with Refund ID: " + refund.getExternalId() + " and Stripe ID: " + stripeRefundId + " is not in a failed state"));
    }

    @Test
    void shouldThrowWebApplicationExceptionForUnexpectedError() throws StripeException {

        when(mockStripeSDKClientFactory.getInstance()).thenReturn(mockStripeSDKClient);

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withType(LIVE).build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withAmount(100L)
                .withGatewayTransactionId("a-transaction-id")
                .build();
        Refund refund = Refund.from(refundEntity);

        String stripeRefundId = refund.getGatewayTransactionId();
        boolean isLiveGatewayAccount = gatewayAccountEntity.isLive();
        StripeException mockStripeException = mock(StripeException.class);

        when(mockStripeSDKClient.getRefund(stripeRefundId, isLiveGatewayAccount))
                .thenThrow(mockStripeException);

        var thrown = assertThrows(WebApplicationException.class,
                () -> refundReversalService.reverseFailedRefund(gatewayAccountEntity, refund, charge, githubUserId, zendeskId));

        assertThat(thrown.getMessage(),
                is("There was an error trying to get refund from Stripe with refund id: " + refund.getExternalId()));

        verify(mockStripeSDKClient).getRefund(stripeRefundId, isLiveGatewayAccount);
    }

    @Test
    void shouldCreateCorrectEventsAfterRefundReversalToPostToLedger() throws StripeException {
        when(mockStripeSDKClientFactory.getInstance()).thenReturn(mockStripeSDKClient);
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withType(LIVE).build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withAmount(100L)
                .withGatewayTransactionId("a-transaction-id")
                .build();
        Refund refund = Refund.from(refundEntity);

        com.stripe.model.Refund mockedStripeRefund = mock(com.stripe.model.Refund.class);
        when(mockStripeSDKClient.getRefund(refund.getGatewayTransactionId(), gatewayAccountEntity.isLive()))
                .thenReturn(mockedStripeRefund);
        when(mockedStripeRefund.getStatus()).thenReturn("failed");


        refundReversalService.reverseFailedRefund(gatewayAccountEntity, refund, charge, githubUserId, zendeskId);

        ArgumentCaptor<List<Event>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockLedgerService).postEvent(eventsCaptor.capture());
        List<Event> postedEvents = eventsCaptor.getValue();

        assertThat(postedEvents, hasSize(3));
        assertInstanceOf(RefundFailureFundsSentToConnectAccount.class, postedEvents.getFirst());
        assertInstanceOf(PaymentStatusCorrectedToSuccessByAdmin.class, postedEvents.get(1));
        assertInstanceOf(RefundStatusCorrectedToErrorByAdmin.class, postedEvents.get(2));

        assertThat(((RefundFailureFundsSentToConnectAccount) postedEvents.getFirst()).getGatewayAccountId(), is(gatewayAccountEntity.getId()));
        assertThat(((PaymentStatusCorrectedToSuccessByAdmin) postedEvents.get(1)).getGatewayAccountId(), is(gatewayAccountEntity.getId()));
        assertThat(((RefundStatusCorrectedToErrorByAdmin) postedEvents.get(2)).getGatewayAccountId(), is(gatewayAccountEntity.getId()));


        assertThat(((RefundFailureFundsSentToConnectAccount) postedEvents.getFirst()).getServiceId(), is(charge.getServiceId()));
        assertThat(((PaymentStatusCorrectedToSuccessByAdmin) postedEvents.get(1)).getServiceId(), is(charge.getServiceId()));
        assertThat(((RefundStatusCorrectedToErrorByAdmin) postedEvents.get(2)).getServiceId(), is(charge.getServiceId()));

        RefundFailureFundsSentToConnectAccount refundFailureEvent = (RefundFailureFundsSentToConnectAccount) postedEvents.getFirst();
        PaymentStatusCorrectedToSuccessByAdmin paymentStatusEvent = (PaymentStatusCorrectedToSuccessByAdmin) postedEvents.get(1);
        RefundStatusCorrectedToErrorByAdmin refundStatusEvent = (RefundStatusCorrectedToErrorByAdmin) postedEvents.get(2);

        RefundFailureFundsSentToConnectAccountEventDetails refundFailureDetails =
                (RefundFailureFundsSentToConnectAccountEventDetails) refundFailureEvent.getEventDetails();
        assertThat(refundFailureDetails.getAmount(), is(refund.getAmount()));
        assertThat(refundFailureDetails.getUpdatedReason(), containsString("Zendesk ticket " + zendeskId));

        PaymentStatusCorrectedToSuccessByAdminEventDetails paymentStatusDetails =
                (PaymentStatusCorrectedToSuccessByAdminEventDetails) paymentStatusEvent.getEventDetails();
        assertThat(paymentStatusDetails.getRefundStatus(), is(ExternalChargeRefundAvailability.EXTERNAL_UNAVAILABLE.getStatus()));
        assertThat(paymentStatusDetails.getAdminGithubId(), is(githubUserId));

        RefundStatusCorrectedToErrorByAdminEventDetails refundStatusDetails =
                (RefundStatusCorrectedToErrorByAdminEventDetails) refundStatusEvent.getEventDetails();
        assertThat(refundStatusDetails.getAdminGithubId(), is(githubUserId));
        assertThat(refundStatusDetails.getUpdatedReason(), containsString("Zendesk ticket " + zendeskId));
    }

    @Test
    void shouldCatchThrownLedgerException() throws StripeException {
        when(mockStripeSDKClientFactory.getInstance()).thenReturn(mockStripeSDKClient);
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withType(LIVE).build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withAmount(100L)
                .withGatewayTransactionId("a-transaction-id")
                .build();
        Refund refund = Refund.from(refundEntity);

        com.stripe.model.Refund mockedStripeRefund = mock(com.stripe.model.Refund.class);
        when(mockStripeSDKClient.getRefund(refund.getGatewayTransactionId(), gatewayAccountEntity.isLive()))
                .thenReturn(mockedStripeRefund);
        when(mockedStripeRefund.getStatus()).thenReturn("failed");

        when(mockLedgerService.postEvent(anyList())).thenThrow(LedgerException.class);

        assertThrows(LedgerException.class, () -> mockLedgerService.postEvent(anyList()));
        Response response = refundReversalService.reverseFailedRefund(gatewayAccountEntity, refund, charge, githubUserId, zendeskId);
        ErrorResponse actualErrorResponse = (ErrorResponse) response.getEntity();
        assertEquals("Stripe transfer successful but error updating payment and refunds",
                actualErrorResponse.messages().getFirst());

    }

}
