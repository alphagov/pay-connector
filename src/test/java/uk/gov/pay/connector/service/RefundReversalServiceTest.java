package uk.gov.pay.connector.service;

import com.stripe.exception.StripeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClient;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClientFactory;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundReversalService;
import uk.gov.pay.connector.refund.service.RefundReversalStripeConnectTransferRequestBuilder;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @BeforeEach
    void setUp() {
        refundReversalService = new RefundReversalService(mockLedgerService, mockRefundDao, mockStripeSDKClientFactory, mockBuilder);
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
        com.stripe.model.Refund mockedStripeRefund = Mockito.mock(com.stripe.model.Refund.class);
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
        when(mockBuilder.createRequest(mockedStripeRefund)).thenReturn(transferRequest);

        assertDoesNotThrow(() -> refundReversalService.reverseFailedRefund(gatewayAccountEntity, refund));

        verify(mockStripeSDKClient).createTransfer(transferRequest, true);
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
        com.stripe.model.Refund mockedStripeRefund = Mockito.mock(com.stripe.model.Refund.class);
        when(mockStripeSDKClient.getRefund(stripeRefundId, isLiveGatewayAccount)).thenReturn(mockedStripeRefund);
        when(mockedStripeRefund.getStatus()).thenReturn("succeeded");

        WebApplicationException thrown = assertThrows(WebApplicationException.class, () ->
                refundReversalService.reverseFailedRefund(gatewayAccountEntity, refund));

        Response response = thrown.getResponse();
        ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
        assertThat(errorResponse.getMessages(), hasItems("Refund with Refund ID: " + refund.getExternalId() + " and Stripe ID: " + stripeRefundId + " is not in a failed state"));
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
        StripeException mockStripeException = Mockito.mock(StripeException.class);

        when(mockStripeSDKClient.getRefund(stripeRefundId, isLiveGatewayAccount))
                .thenThrow(mockStripeException);

        var thrown = assertThrows(WebApplicationException.class,
                () -> refundReversalService.reverseFailedRefund(gatewayAccountEntity, refund));

        assertThat(thrown.getMessage(),
                is("Unexpected error trying to get refund with ID:" + refund.getExternalId() + " from Stripe"));

        verify(mockStripeSDKClient).getRefund(stripeRefundId, isLiveGatewayAccount);
    }
}
