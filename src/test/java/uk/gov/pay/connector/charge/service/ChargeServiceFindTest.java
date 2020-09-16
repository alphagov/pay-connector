package uk.gov.pay.connector.charge.service;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.token.model.domain.TokenEntity;
import uk.gov.pay.connector.wallets.WalletType;

import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;

@RunWith(JUnitParamsRunner.class)
public class ChargeServiceFindTest extends ChargeServiceTest {
    
    @Test
    public void shouldNotFindCharge() {
        PaymentOutcome paymentOutcome = new PaymentOutcome("success");

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withProviderId("new")
                .withPaymentOutcome(paymentOutcome)
                .build();

        Optional<ChargeResponse> telephoneChargeResponse = service.findCharge(1234L, telephoneChargeCreateRequest);

        ArgumentCaptor<String> gatewayTransactionIdArgumentCaptor = forClass(String.class);
        verify(mockedChargeDao).findByGatewayTransactionIdAndAccount(anyLong(), gatewayTransactionIdArgumentCaptor.capture());

        String providerId = gatewayTransactionIdArgumentCaptor.getValue();
        assertThat(providerId, is("new"));
        assertThat(telephoneChargeResponse.isPresent(), is(false));
    }

    @Test
    @Parameters({
            "CREATED",
            "ENTERING_CARD_DETAILS",
            "AUTHORISATION_READY"
    })
    public void shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenChargeStatusIs(String status) throws Exception {
        Long chargeId = 101L;

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(ChargeStatus.valueOf(status))
                .withWalletType(WalletType.APPLE_PAY)
                .build();

        String externalId = newCharge.getExternalId();

        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.ofNullable(newCharge));

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeEntity().getId(), is(newCharge.getId()));
        assertThat(tokenEntity.getToken(), is(notNullValue()));

        ChargeResponse.ChargeResponseBuilder chargeResponseWithoutCorporateCardSurcharge = chargeResponseBuilderOf(newCharge);
        chargeResponseWithoutCorporateCardSurcharge.withWalletType(WalletType.APPLE_PAY);
        chargeResponseWithoutCorporateCardSurcharge.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId));
        chargeResponseWithoutCorporateCardSurcharge.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId + "/refunds"));
        chargeResponseWithoutCorporateCardSurcharge.withLink("next_url", GET, new URI("http://frontend.test/secure/" + tokenEntity.getToken()));
        chargeResponseWithoutCorporateCardSurcharge.withLink("next_url_post", POST, new URI("http://frontend.test/secure"), "application/x-www-form-urlencoded", new HashMap<String, Object>() {{
            put("chargeTokenId", tokenEntity.getToken());
        }});

        assertThat(chargeResponseForAccount.isPresent(), is(true));
        final ChargeResponse chargeResponse = chargeResponseForAccount.get();
        assertThat(chargeResponse.getCorporateCardSurcharge(), is(nullValue()));
        assertThat(chargeResponse.getTotalAmount(), is(nullValue()));
        assertThat(chargeResponse, is(chargeResponseWithoutCorporateCardSurcharge.build()));
        assertThat(chargeResponse.getWalletType(), is(WalletType.APPLE_PAY));
    }

    @Test
    public void shouldFindChargeForChargeId_withCorporateSurcharge() {
        Long chargeId = 101L;
        Long totalAmount = 1250L;

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(AUTHORISATION_READY)
                .withAmount(1000L)
                .withCorporateSurcharge(250L)
                .build();

        String externalId = newCharge.getExternalId();

        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.ofNullable(newCharge));

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        assertThat(chargeResponseForAccount.isPresent(), is(true));
        final ChargeResponse chargeResponse = chargeResponseForAccount.get();
        assertThat(chargeResponse.getCorporateCardSurcharge(), is(250L));
        assertThat(chargeResponse.getTotalAmount(), is(totalAmount));
        assertThat(chargeResponse.getRefundSummary().getAmountAvailable(), is(totalAmount));
    }

    @Test
    public void shouldFindChargeForChargeId_withFee() {
        Long chargeId = 101L;
        Long amount = 1000L;
        Long fee = 100L;

        ChargeEntity charge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(AUTHORISATION_READY)
                .withAmount(amount)
                .withFee(fee)
                .build();

        String externalId = charge.getExternalId();

        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.ofNullable(charge));

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        assertThat(chargeResponseForAccount.isPresent(), is(true));
        final ChargeResponse chargeResponse = chargeResponseForAccount.get();

        assertThat(chargeResponse.getNetAmount(), is(amount - fee));
        assertThat(chargeResponse.getAmount(), is(amount));
    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithoutNextUrlWhenChargeCannotBeResumed() throws Exception {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);

        shouldFindChargeForChargeIdAndAccountIdWithoutNextUrlWhenChargeStatusIs(CAPTURED);
    }

    private void shouldFindChargeForChargeIdAndAccountIdWithoutNextUrlWhenChargeStatusIs(ChargeStatus status) throws Exception {
        Long chargeId = 101L;

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(status)
                .build();

        String externalId = newCharge.getExternalId();
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.ofNullable(newCharge));

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedTokenDao, never()).persist(any());

        ChargeResponse.ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(newCharge);
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId + "/refunds"));

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse.build()));
    }

    @Test
    public void shouldNotFindAChargeWhenNoChargeForChargeIdAndAccountId() {
        String externalChargeId = "101abc";

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.empty());

        Optional<ChargeResponse> chargeForAccount = service.findChargeForAccount(externalChargeId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        assertThat(chargeForAccount.isPresent(), is(false));
    }


    @Test
    public void shouldFindChargeWithCaptureUrlAndNoNextUrl_whenChargeInAwaitingCaptureRequest() throws Exception {
        Long chargeId = 101L;

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(AWAITING_CAPTURE_REQUEST)
                .build();

        String externalId = newCharge.getExternalId();

        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.ofNullable(newCharge));

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedTokenDao, never()).persist(any());

        ChargeResponse.ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(newCharge);
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId + "/refunds"));
        expectedChargeResponse.withLink("capture", POST, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId + "/capture"));

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse.build()));
    }


    @Test
    public void shouldFindCharge_fromDbIfExists() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(chargeEntity.getExternalId(), GATEWAY_ACCOUNT_ID)).thenReturn(Optional.ofNullable(chargeEntity));

        Optional<Charge> charge = service.findCharge(chargeEntity.getExternalId(), GATEWAY_ACCOUNT_ID);

        verifyNoInteractions(ledgerService);

        assertThat(charge.isPresent(), is(true));
        final Charge result = charge.get();
        assertThat(result.getExternalId(), is(chargeEntity.getExternalId()));
        assertThat(result.getAmount(), is(chargeEntity.getAmount()));
    }

    @Test
    public void shouldFindCharge_fromLedgerIfNotExists() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setTransactionId(chargeEntity.getExternalId());
        transaction.setAmount(chargeEntity.getAmount());
        transaction.setCreatedDate(ZonedDateTime.now(ZoneId.of("UTC")).toString());
        transaction.setGatewayAccountId(String.valueOf(GATEWAY_ACCOUNT_ID));
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(chargeEntity.getExternalId(), GATEWAY_ACCOUNT_ID)).thenReturn(Optional.empty());

        when(ledgerService.getTransactionForGatewayAccount(chargeEntity.getExternalId(), GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(transaction));

        Optional<Charge> charge = service.findCharge(chargeEntity.getExternalId(), GATEWAY_ACCOUNT_ID);

        assertThat(charge.isPresent(), is(true));
        final Charge result = charge.get();
        assertThat(result.getExternalId(), is(chargeEntity.getExternalId()));
        assertThat(result.getAmount(), is(chargeEntity.getAmount()));
    }

    @Test
    public void findByProviderAndTransactionIdFromDbOrLedger_fromDbIfExists() {
        ChargeEntity chargeEntity = aValidChargeEntity().build();

        when(mockedChargeDao.findByProviderAndTransactionId(
                "sandbox",
                chargeEntity.getExternalId()
        )).thenReturn(Optional.of(chargeEntity));

        Optional<Charge> charge = service.findByProviderAndTransactionIdFromDbOrLedger("sandbox",
                chargeEntity.getExternalId());

        verifyNoInteractions(ledgerService);

        assertThat(charge.isPresent(), is(true));
        final Charge result = charge.get();
        assertThat(result.getAmount(), is(chargeEntity.getAmount()));
        assertThat(result.getExternalId(), is(chargeEntity.getExternalId()));
    }

    @Test
    public void findByProviderAndTransactionIdFromDbOrLedger_fromLedgerIfNotExists() {
        ChargeEntity chargeEntity = aValidChargeEntity().build();

        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setTransactionId(chargeEntity.getExternalId());
        transaction.setAmount(chargeEntity.getAmount());
        transaction.setCreatedDate(ZonedDateTime.now(ZoneId.of("UTC")).toString());
        transaction.setGatewayAccountId(String.valueOf(GATEWAY_ACCOUNT_ID));
        when(mockedChargeDao.findByProviderAndTransactionId(
                "sandbox",
                chargeEntity.getExternalId()
        )).thenReturn(Optional.empty());

        when(ledgerService.getTransactionForProviderAndGatewayTransactionId("sandbox",
                chargeEntity.getExternalId())).thenReturn(Optional.of(transaction));

        Optional<Charge> charge = service.findByProviderAndTransactionIdFromDbOrLedger("sandbox",
                chargeEntity.getExternalId());

        assertThat(charge.isPresent(), is(true));
        final Charge result = charge.get();
        assertThat(result.getAmount(), is(chargeEntity.getAmount()));
        assertThat(result.getExternalId(), is(chargeEntity.getExternalId()));
    }

    @Test
    public void findByProviderAndTransactionIdFromDbOrLedger_shouldReturnEmptyOptionalIfChargeIsNotInDbOrLedger() {
        ChargeEntity chargeEntity = aValidChargeEntity().build();

        when(mockedChargeDao.findByProviderAndTransactionId(
                "sandbox", chargeEntity.getExternalId()
        )).thenReturn(Optional.empty());

        when(ledgerService.getTransactionForProviderAndGatewayTransactionId("sandbox",
                chargeEntity.getExternalId())).thenReturn(Optional.empty());

        Optional<Charge> charge = service.findByProviderAndTransactionIdFromDbOrLedger("sandbox",
                chargeEntity.getExternalId());

        assertThat(charge.isPresent(), is(false));
    }
}
