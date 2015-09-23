package uk.gov.pay.connector.unit.service;

import fj.data.Either;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.CardService;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.PaymentProviders;
import uk.gov.pay.connector.util.CardUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CardServiceTest {

    private final String gatewayAccountId = "theAccountId";
    private final String providerName = "theProvider";
    private final PaymentProvider theProvider = mock(PaymentProvider.class);

    private GatewayAccountDao accountDao = mock(GatewayAccountDao.class);
    private ChargeDao chargeDao = mock(ChargeDao.class);
    private PaymentProviders providers = mock(PaymentProviders.class);
    private final CardService cardService = new CardService(accountDao, chargeDao, providers);

    @Test
    public void shouldAuthoriseACharge() throws Exception {

        String chargeId = "theChargeId";
        String gatewayTxId = "theTxId";

        mockSuccessfulAuthorisation(chargeId, gatewayTxId);
        Card cardDetails = CardUtils.aValidCard();
        Either<GatewayError, GatewayResponse> response = cardService.doAuthorise(chargeId, cardDetails);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));
        verify(chargeDao, times(1)).updateStatus(chargeId, ChargeStatus.AUTHORISATION_SUCCESS);
        verify(chargeDao, times(1)).updateGatewayTransactionId(eq(chargeId), any(String.class));
    }

    @Test
    public void shouldCaptureACharge() throws Exception {

        String chargeId = "theChargeId";
        String gatewayTxId = "theTxId";
        mockSuccessfulCapture(chargeId, gatewayTxId);

        Either<GatewayError, GatewayResponse> response = cardService.doCapture(chargeId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));
        verify(chargeDao, times(1)).updateStatus(chargeId, ChargeStatus.CAPTURED);

        ArgumentCaptor<CaptureRequest> request = ArgumentCaptor.forClass(CaptureRequest.class);
        verify(theProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getTransactionId(), is(gatewayTxId));

    }

    private void mockSuccessfulCapture(String chargeId, String gatewayTransactionId) {
        Map<String, Object> charge = theCharge(ChargeStatus.AUTHORISATION_SUCCESS);
        charge.put("gateway_transaction_id", gatewayTransactionId);

        when(chargeDao.findById(chargeId)).thenReturn(Optional.of(charge));
        when(accountDao.findById(gatewayAccountId)).thenReturn(Optional.of(theAccount()));
        when(providers.resolve(providerName)).thenReturn(theProvider);
        CaptureResponse response = new CaptureResponse(true, null);
        when(theProvider.capture(any())).thenReturn(response);
    }

    private void mockSuccessfulAuthorisation(String chargeId, String transactionId) {
        Map<String, Object> charge = theCharge(ChargeStatus.CREATED);

        when(chargeDao.findById(chargeId)).thenReturn(Optional.of(charge));
        when(accountDao.findById(gatewayAccountId)).thenReturn(Optional.of(theAccount()));
        when(providers.resolve(providerName)).thenReturn(theProvider);
        AuthorisationResponse resp = new AuthorisationResponse(true, null, ChargeStatus.AUTHORISATION_SUCCESS, transactionId);
        when(theProvider.authorise(any())).thenReturn(resp);
    }

    private Map<String, Object> theAccount() {
        return new HashMap<String, Object>() {{
            put("payment_provider", providerName);
        }};
    }

    private Map<String, Object> theCharge(ChargeStatus status) {
        return new HashMap<String, Object>() {{
            put("status", status.getValue());
            put("amount", "500");
            put("gateway_account_id", gatewayAccountId);
        }};
    }


    private Matcher<GatewayResponse> aSuccessfulResponse() {
        return new TypeSafeMatcher<GatewayResponse>() {
            private GatewayResponse gatewayResponse;

            @Override
            protected boolean matchesSafely(GatewayResponse gatewayResponse) {
                this.gatewayResponse = gatewayResponse;
                return gatewayResponse.isSuccessful() && gatewayResponse.getError() == null;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Success, but response was not successful: " + gatewayResponse.getError().getMessage());
            }
        };
    }
}