package uk.gov.pay.connector.charge.service;

import org.apache.commons.lang.StringUtils;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.exception.MotoPaymentNotAllowedForGatewayAccountException;
import uk.gov.pay.connector.charge.exception.ZeroAmountNotAllowedForGatewayAccountException;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.PrefilledCardHolderDetails;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.Supplemental;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.common.model.domain.PrefilledAddress;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.token.model.domain.TokenEntity;

import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.time.ZonedDateTime.now;
import static java.util.Collections.emptyMap;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.commons.model.Source.CARD_API;
import static uk.gov.pay.commons.model.Source.CARD_EXTERNAL_TELEPHONE;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;

public class ChargeServiceCreateTest extends ChargeServiceTest {
    
    @Test
    public void shouldReturnAResponseForExistingCharge() {
        PaymentOutcome paymentOutcome = new PaymentOutcome("success");

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();

        ExternalMetadata externalMetadata = new ExternalMetadata(
                Map.of(
                        "created_date", "2018-02-21T16:04:25Z",
                        "authorised_date", "2018-02-21T16:05:33Z",
                        "processor_id", "1PROC",
                        "auth_code", "666",
                        "telephone_number", "+447700900796",
                        "status", "success"
                )
        );

        CardDetailsEntity cardDetails = new CardDetailsEntity(
                LastDigitsCardNumber.of("1234"),
                FirstDigitsCardNumber.of("123456"),
                "Jane Doe",
                "01/19",
                "visa",
                CardType.valueOf("DEBIT")
        );

        ChargeEntity returnedChargeEntity = aValidChargeEntity()
                .withAmount(100L)
                .withDescription("Some description")
                .withReference(ServicePaymentReference.of("Some reference"))
                .withGatewayAccountEntity(gatewayAccount)
                .withEmail("jane.doe@example.com")
                .withExternalMetadata(externalMetadata)
                .withSource(CARD_API)
                .withStatus(AUTHORISATION_SUCCESS)
                .withGatewayTransactionId("1PROV")
                .withCardDetails(cardDetails)
                .build();

        when(mockedChargeDao.findByGatewayTransactionIdAndAccount(gatewayAccount.getId(), "1PROV"))
                .thenReturn(Optional.of(returnedChargeEntity));

        service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID);

        Optional<ChargeResponse> telephoneChargeResponse = service.findCharge(gatewayAccount.getId(), telephoneChargeCreateRequest);

        ArgumentCaptor<String> gatewayTransactionIdArgumentCaptor = forClass(String.class);
        verify(mockedChargeDao).findByGatewayTransactionIdAndAccount(anyLong(), gatewayTransactionIdArgumentCaptor.capture());

        String providerId = gatewayTransactionIdArgumentCaptor.getValue();
        assertThat(providerId, is("1PROV"));
        assertThat(telephoneChargeResponse.isPresent(), is(true));
        assertThat(telephoneChargeResponse.get().getAmount(), is(100L));
        assertThat(telephoneChargeResponse.get().getReference().toString(), is("Some reference"));
        assertThat(telephoneChargeResponse.get().getDescription(), is("Some description"));
        assertThat(telephoneChargeResponse.get().getCreatedDate().toString(), is("2018-02-21T16:04:25Z"));
        assertThat(telephoneChargeResponse.get().getAuthorisedDate().toString(), is("2018-02-21T16:05:33Z"));
        assertThat(telephoneChargeResponse.get().getAuthCode(), is("666"));
        assertThat(telephoneChargeResponse.get().getPaymentOutcome().getStatus(), is("success"));
        assertThat(telephoneChargeResponse.get().getCardDetails().getCardBrand(), is("visa"));
        assertThat(telephoneChargeResponse.get().getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(telephoneChargeResponse.get().getEmail(), is("jane.doe@example.com"));
        assertThat(telephoneChargeResponse.get().getCardDetails().getExpiryDate(), is("01/19"));
        assertThat(telephoneChargeResponse.get().getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(telephoneChargeResponse.get().getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(telephoneChargeResponse.get().getTelephoneNumber(), is("+447700900796"));
        assertThat(telephoneChargeResponse.get().getDataLinks(), is(EMPTY_LINKS));
        assertThat(telephoneChargeResponse.get().getDelayedCapture(), is(false));
        assertThat(telephoneChargeResponse.get().getChargeId().length(), is(26));
        assertThat(telephoneChargeResponse.get().getState().getStatus(), is("success"));
        assertThat(telephoneChargeResponse.get().getState().isFinished(), is(true));
    }

    @Test
    public void shouldCreateAChargeWithDefaults() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        populateChargeEntity();

        service.create(requestBuilder.build(), GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));
        assertThat(createdChargeEntity.getStatus(), is("CREATED"));
        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Pay reference")));
        assertThat(createdChargeEntity.getDescription(), is("This is a description"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is(nullValue()));
        assertThat(createdChargeEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, now(ZoneId.of("UTC")))));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
        assertThat(createdChargeEntity.isDelayedCapture(), is(false));
        assertThat(createdChargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(createdChargeEntity.getWalletType(), is(nullValue()));
        assertThat(createdChargeEntity.isMoto(), is(false));

        verify(mockedChargeEventDao).persistChargeEventOf(eq(createdChargeEntity), isNull());
    }

    @Test
    public void shouldCreateAChargeWithDelayedCaptureTrue() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        final ChargeCreateRequest request = requestBuilder.withDelayedCapture(true).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
    }

    @Test
    public void shouldCreateAChargeWithDelayedCaptureFalse() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        final ChargeCreateRequest request = requestBuilder.withDelayedCapture(false).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
    }

    @Test
    public void shouldCreateAChargeWithExternalMetadata() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        Map<String, Object> metadata = Map.of(
                "key1", "string",
                "key2", true,
                "key3", 123,
                "key4", 1.23);
        final ChargeCreateRequest request = requestBuilder.withExternalMetadata(new ExternalMetadata(metadata)).build();

        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        assertThat(chargeEntityArgumentCaptor.getValue().getExternalMetadata().get().getMetadata(), equalTo(metadata));
    }

    @Test
    public void shouldCreateAChargeWithNonDefaultLanguage() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        final ChargeCreateRequest request = requestBuilder.withLanguage(SupportedLanguage.WELSH).build();

        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.WELSH));
    }

    @Test
    public void shouldCreateChargeWithZeroAmountIfGatewayAccountAllowsIt() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        gatewayAccount.setAllowZeroAmount(true);

        final ChargeCreateRequest request = requestBuilder.withAmount(0).build();

        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getAmount(), is(0L));
    }

    @Test(expected = ZeroAmountNotAllowedForGatewayAccountException.class)
    public void shouldThrowExceptionWhenCreateChargeWithZeroAmountIfGatewayAccountDoesNotAllowIt() {
        final ChargeCreateRequest request = requestBuilder.withAmount(0).build();
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    public void shouldCreateMotoChargeIfGatewayAccountAllowsIt() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        gatewayAccount.setAllowMoto(true);
        ChargeCreateRequest request = requestBuilder.withMoto(true).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.isMoto(), is(true));
    }

    @Test(expected = MotoPaymentNotAllowedForGatewayAccountException.class)
    public void shouldThrowExceptionWhenCreateMotoChargeIfGatewayAccountDoesNotAllowIt() {
        ChargeCreateRequest request = requestBuilder.withMoto(true).build();

        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao, never()).persist(any(ChargeEntity.class));
    }


    @Test
    public void shouldCreateAChargeWithAllPrefilledCardHolderDetails() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        var cardHolderDetails = new PrefilledCardHolderDetails();
        cardHolderDetails.setCardHolderName("Joe Bogs");
        var address = new PrefilledAddress("Line1", "Line2", "AB1 CD2", "London", null, "GB");
        cardHolderDetails.setAddress(address);
        final ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getCardDetails(), is(notNullValue()));
        assertThat(createdChargeEntity.getCardDetails().getBillingAddress().isPresent(), is(true));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Joe Bogs"));
        AddressEntity addressEntity = createdChargeEntity.getCardDetails().getBillingAddress().get();
        assertThat(addressEntity.getCity(), is("London"));
        assertThat(addressEntity.getCountry(), is("GB"));
        assertThat(addressEntity.getLine1(), is("Line1"));
        assertThat(addressEntity.getLine2(), is("Line2"));
        assertThat(addressEntity.getPostcode(), is("AB1 CD2"));
        assertThat(addressEntity.getCounty(), is(nullValue()));
    }

    @Test
    public void shouldCreateAChargeWithPrefilledCardHolderDetailsAndSomeAddressMissing() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        var cardHolderDetails = new PrefilledCardHolderDetails();
        cardHolderDetails.setCardHolderName("Joe Bogs");
        var address = new PrefilledAddress("Line1", null, "AB1 CD2", "London", null, null);
        cardHolderDetails.setAddress(address);


        ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        assertThat(createdChargeEntity.getCardDetails(), is(notNullValue()));
        assertThat(createdChargeEntity.getCardDetails().getBillingAddress().isPresent(), is(true));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Joe Bogs"));
        AddressEntity addressEntity = createdChargeEntity.getCardDetails().getBillingAddress().get();
        assertThat(addressEntity.getLine1(), is("Line1"));
        assertThat(addressEntity.getLine2(), is(nullValue()));
        assertThat(addressEntity.getPostcode(), is("AB1 CD2"));
        assertThat(addressEntity.getCity(), is("London"));
        assertThat(addressEntity.getCounty(), is(nullValue()));
        assertThat(addressEntity.getCountry(), is(nullValue()));
    }

    @Test
    public void shouldCreateAChargeWithNoCountryWhenPrefilledAddressCountryIsMoreThanTwoCharacters() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        var cardHolderDetails = new PrefilledCardHolderDetails();
        cardHolderDetails.setCardHolderName("Joe Bogs");
        var address = new PrefilledAddress("Line1", "Line2", "AB1 CD2", "London", "county", "123");
        cardHolderDetails.setAddress(address);

        ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        assertThat(createdChargeEntity.getCardDetails(), is(notNullValue()));
        assertThat(createdChargeEntity.getCardDetails().getBillingAddress().isPresent(), is(true));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Joe Bogs"));
        AddressEntity addressEntity = createdChargeEntity.getCardDetails().getBillingAddress().get();
        assertThat(addressEntity.getLine1(), is("Line1"));
        assertThat(addressEntity.getLine2(), is("Line2"));
        assertThat(addressEntity.getPostcode(), is("AB1 CD2"));
        assertThat(addressEntity.getCity(), is("London"));
        assertThat(addressEntity.getCounty(), is("county"));
        assertThat(addressEntity.getCountry(), is(nullValue()));
    }

    @Test
    public void shouldCreateAChargeWithPrefilledCardHolderDetailsCardholderNameOnly() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        PrefilledCardHolderDetails cardHolderDetails = new PrefilledCardHolderDetails();
        cardHolderDetails.setCardHolderName("Joe Bogs");

        ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        assertThat(createdChargeEntity.getCardDetails(), is(notNullValue()));
        assertThat(createdChargeEntity.getCardDetails().getBillingAddress().isPresent(), is(false));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Joe Bogs"));
    }

    @Test
    public void shouldCreateAChargeWhenPrefilledCardHolderDetailsCardholderNameAndSomeAddressNotPresent() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        var cardHolderDetails = new PrefilledCardHolderDetails();
        var address = new PrefilledAddress("Line1", null, "AB1 CD2", "London", null, null);
        cardHolderDetails.setAddress(address);

        ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        assertThat(createdChargeEntity.getCardDetails(), is(notNullValue()));
        assertThat(createdChargeEntity.getCardDetails().getBillingAddress().isPresent(), is(true));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is(nullValue()));
        AddressEntity addressEntity = createdChargeEntity.getCardDetails().getBillingAddress().get();
        assertThat(addressEntity.getLine1(), is("Line1"));
        assertThat(addressEntity.getLine2(), is(nullValue()));
        assertThat(addressEntity.getPostcode(), is("AB1 CD2"));
        assertThat(addressEntity.getCity(), is("London"));
        assertThat(addressEntity.getCounty(), is(nullValue()));
        assertThat(addressEntity.getCountry(), is(nullValue()));
    }

    @Test
    public void shouldCreateAChargeWhenPrefilledCardHolderDetailsAreNotPresent() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        ChargeCreateRequest request = requestBuilder.build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getCardDetails(), is(nullValue()));
    }

    @Test
    public void shouldCreateAChargeWithSource() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        final ChargeCreateRequest request = requestBuilder.
                withSource(CARD_API).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        assertThat(chargeEntityArgumentCaptor.getValue().getSource(), equalTo(CARD_API));
    }


    @Test
    public void shouldCreateATelephoneChargeForSuccess() {
        PaymentOutcome paymentOutcome = new PaymentOutcome("success");

        Map<String, Object> metadata = Map.of(
                "created_date", "2018-02-21T16:04:25Z",
                "authorised_date", "2018-02-21T16:05:33Z",
                "processor_id", "1PROC",
                "auth_code", "666",
                "telephone_number", "+447700900796",
                "status", "success");

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();

        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        populateChargeEntity();

        service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));

        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Some reference")));
        assertThat(createdChargeEntity.getDescription(), is("Some description"));
        assertThat(createdChargeEntity.getStatus(), is("CAPTURE SUBMITTED"));
        assertThat(createdChargeEntity.getEmail(), is("jane.doe@example.com"));
        assertThat(createdChargeEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, now(ZoneId.of("UTC")))));
        assertThat(createdChargeEntity.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(createdChargeEntity.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(createdChargeEntity.getCardDetails().getExpiryDate(), is("01/19"));
        assertThat(createdChargeEntity.getCardDetails().getCardBrand(), is("visa"));
        assertThat(createdChargeEntity.getCardDetails().getCardType(), is(nullValue()));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is("1PROV"));
        assertThat(createdChargeEntity.getExternalMetadata().get().getMetadata(), equalTo(metadata));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
    }

    @Test
    public void shouldCreateATelephoneChargeForFailureCodeOfP0010() {
        Supplemental supplemental = new Supplemental("ECKOH01234", "textual message describing error code");
        PaymentOutcome paymentOutcome = new PaymentOutcome("failed", "P0010", supplemental);

        Map<String, Object> metadata = Map.of(
                "created_date", "2018-02-21T16:04:25Z",
                "authorised_date", "2018-02-21T16:05:33Z",
                "processor_id", "1PROC",
                "auth_code", "666",
                "telephone_number", "+447700900796",
                "status", "failed",
                "code", "P0010",
                "error_code", "ECKOH01234",
                "error_message", "textual message describing error code"
        );

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .withCardExpiry(null)
                .build();

        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        populateChargeEntity();

        service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));

        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Some reference")));
        assertThat(createdChargeEntity.getDescription(), is("Some description"));
        assertThat(createdChargeEntity.getStatus(), is("AUTHORISATION REJECTED"));
        assertThat(createdChargeEntity.getEmail(), is("jane.doe@example.com"));
        assertThat(createdChargeEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, now(ZoneId.of("UTC")))));
        assertThat(createdChargeEntity.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(createdChargeEntity.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(createdChargeEntity.getCardDetails().getExpiryDate(), is(nullValue()));
        assertThat(createdChargeEntity.getCardDetails().getCardBrand(), is("visa"));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is("1PROV"));
        assertThat(createdChargeEntity.getExternalMetadata().get().getMetadata(), equalTo(metadata));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
    }

    @Test
    public void shouldCreateATelephoneChargeForFailureCodeOfP0050() {
        Supplemental supplemental = new Supplemental("ECKOH01234", "textual message describing error code");
        PaymentOutcome paymentOutcome = new PaymentOutcome("failed", "P0050", supplemental);

        Map<String, Object> metadata = Map.of(
                "created_date", "2018-02-21T16:04:25Z",
                "authorised_date", "2018-02-21T16:05:33Z",
                "processor_id", "1PROC",
                "auth_code", "666",
                "telephone_number", "+447700900796",
                "status", "failed",
                "code", "P0050",
                "error_code", "ECKOH01234",
                "error_message", "textual message describing error code"
        );

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .withCardExpiry(null)
                .build();

        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        populateChargeEntity();

        service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));

        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Some reference")));
        assertThat(createdChargeEntity.getDescription(), is("Some description"));
        assertThat(createdChargeEntity.getStatus(), is("AUTHORISATION ERROR"));
        assertThat(createdChargeEntity.getEmail(), is("jane.doe@example.com"));
        assertThat(createdChargeEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, now(ZoneId.of("UTC")))));
        assertThat(createdChargeEntity.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(createdChargeEntity.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(createdChargeEntity.getCardDetails().getExpiryDate(), is(nullValue()));
        assertThat(createdChargeEntity.getCardDetails().getCardBrand(), is("visa"));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is("1PROV"));
        assertThat(createdChargeEntity.getExternalMetadata().get().getMetadata(), equalTo(metadata));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
    }

    @Test
    public void shouldCreateATelephoneChargeAndTruncateMetaDataOver50Characters() {
        String stringGreaterThan50 = StringUtils.repeat("*", 51);
        String stringOf50 = StringUtils.repeat("*", 50);

        Supplemental supplemental = new Supplemental(stringGreaterThan50, stringGreaterThan50);
        PaymentOutcome paymentOutcome = new PaymentOutcome("failed", "P0050", supplemental);

        Map<String, Object> metadata = Map.of(
                "created_date", "2018-02-21T16:04:25Z",
                "authorised_date", "2018-02-21T16:05:33Z",
                "processor_id", stringOf50,
                "auth_code", stringOf50,
                "telephone_number", stringOf50,
                "status", "failed",
                "code", "P0050",
                "error_code", stringOf50,
                "error_message", stringOf50
        );

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .withProcessorId(stringGreaterThan50)
                .withAuthCode(stringGreaterThan50)
                .withTelephoneNumber(stringGreaterThan50)
                .build();

        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        populateChargeEntity();

        service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));

        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Some reference")));
        assertThat(createdChargeEntity.getDescription(), is("Some description"));
        assertThat(createdChargeEntity.getStatus(), is("AUTHORISATION ERROR"));
        assertThat(createdChargeEntity.getEmail(), is("jane.doe@example.com"));
        assertThat(createdChargeEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, ZonedDateTime.now(ZoneId.of("UTC")))));
        assertThat(createdChargeEntity.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(createdChargeEntity.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(createdChargeEntity.getCardDetails().getExpiryDate(), is("01/19"));
        assertThat(createdChargeEntity.getCardDetails().getCardBrand(), is("visa"));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is("1PROV"));
        assertThat(createdChargeEntity.getExternalMetadata().get().getMetadata(), equalTo(metadata));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
    }

    @Test
    public void shouldCreateATelephoneChargeAndNotTruncateMetaDataOf50Characters() {
        String stringOf50 = StringUtils.repeat("*", 50);

        Supplemental supplemental = new Supplemental(stringOf50, stringOf50);
        PaymentOutcome paymentOutcome = new PaymentOutcome("failed", "P0050", supplemental);

        Map<String, Object> metadata = Map.of(
                "created_date", "2018-02-21T16:04:25Z",
                "authorised_date", "2018-02-21T16:05:33Z",
                "processor_id", stringOf50,
                "auth_code", stringOf50,
                "telephone_number", stringOf50,
                "status", "failed",
                "code", "P0050",
                "error_code", stringOf50,
                "error_message", stringOf50
        );

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .withProcessorId(stringOf50)
                .withAuthCode(stringOf50)
                .withTelephoneNumber(stringOf50)
                .build();

        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        populateChargeEntity();

        service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));

        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Some reference")));
        assertThat(createdChargeEntity.getDescription(), is("Some description"));
        assertThat(createdChargeEntity.getStatus(), is("AUTHORISATION ERROR"));
        assertThat(createdChargeEntity.getEmail(), is("jane.doe@example.com"));
        assertThat(createdChargeEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, ZonedDateTime.now(ZoneId.of("UTC")))));
        assertThat(createdChargeEntity.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(createdChargeEntity.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(createdChargeEntity.getCardDetails().getExpiryDate(), is("01/19"));
        assertThat(createdChargeEntity.getCardDetails().getCardBrand(), is("visa"));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is("1PROV"));
        assertThat(createdChargeEntity.getExternalMetadata().get().getMetadata(), equalTo(metadata));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
    }

    @Test
    public void shouldCreateAnExternalTelephoneChargeWithSource() {
        PaymentOutcome paymentOutcome = new PaymentOutcome("success");
        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();

        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        assertThat(chargeEntityArgumentCaptor.getValue().getSource(), equalTo(CARD_EXTERNAL_TELEPHONE));
    }


    @Test
    public void shouldCreateATelephoneChargeResponseForSuccess() {

        PaymentOutcome paymentOutcome = new PaymentOutcome("success");

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();

        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        ChargeResponse chargeResponse = service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID).get();

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        assertThat(chargeResponse.getAmount(), is(100L));
        assertThat(chargeResponse.getReference().toString(), is("Some reference"));
        assertThat(chargeResponse.getDescription(), is("Some description"));
        assertThat(chargeResponse.getCreatedDate().toString(), is("2018-02-21T16:04:25Z"));
        assertThat(chargeResponse.getAuthorisedDate().toString(), is("2018-02-21T16:05:33Z"));
        assertThat(chargeResponse.getAuthCode(), is("666"));
        assertThat(chargeResponse.getPaymentOutcome().getStatus(), is("success"));
        assertThat(chargeResponse.getCardDetails().getCardBrand(), is("visa"));
        assertThat(chargeResponse.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(chargeResponse.getEmail(), is("jane.doe@example.com"));
        assertThat(chargeResponse.getCardDetails().getExpiryDate(), is("01/19"));
        assertThat(chargeResponse.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(chargeResponse.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(chargeResponse.getTelephoneNumber(), is("+447700900796"));
        assertThat(chargeResponse.getDataLinks(), is(EMPTY_LINKS));
        assertThat(chargeResponse.getDelayedCapture(), is(false));
        assertThat(chargeResponse.getChargeId().length(), is(26));
        assertThat(chargeResponse.getState().getStatus(), is("success"));
        assertThat(chargeResponse.getState().isFinished(), is(true));
    }

    @Test
    public void shouldCreateATelephoneChargeResponseForFailure() {

        Supplemental supplemental = new Supplemental("ECKOH01234", "textual message describing error code");
        PaymentOutcome paymentOutcome = new PaymentOutcome("failed", "P0010", supplemental);

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .withCardExpiry(null)
                .build();

        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        ChargeResponse chargeResponse = service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID).get();

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        assertThat(chargeResponse.getAmount(), is(100L));
        assertThat(chargeResponse.getReference().toString(), is("Some reference"));
        assertThat(chargeResponse.getDescription(), is("Some description"));
        assertThat(chargeResponse.getCreatedDate().toString(), is("2018-02-21T16:04:25Z"));
        assertThat(chargeResponse.getAuthorisedDate().toString(), is("2018-02-21T16:05:33Z"));
        assertThat(chargeResponse.getAuthCode(), is("666"));
        assertThat(chargeResponse.getPaymentOutcome().getStatus(), is("failed"));
        assertThat(chargeResponse.getPaymentOutcome().getCode().get(), is("P0010"));
        assertThat(chargeResponse.getPaymentOutcome().getSupplemental().get().getErrorCode().get(), is("ECKOH01234"));
        assertThat(chargeResponse.getPaymentOutcome().getSupplemental().get().getErrorMessage().get(), is("textual message describing error code"));
        assertThat(chargeResponse.getCardDetails().getCardBrand(), is("visa"));
        assertThat(chargeResponse.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(chargeResponse.getEmail(), is("jane.doe@example.com"));
        assertThat(chargeResponse.getCardDetails().getExpiryDate(), is(nullValue()));
        assertThat(chargeResponse.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(chargeResponse.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(chargeResponse.getTelephoneNumber(), is("+447700900796"));
        assertThat(chargeResponse.getDataLinks(), is(EMPTY_LINKS));
        assertThat(chargeResponse.getDelayedCapture(), is(false));
        assertThat(chargeResponse.getChargeId().length(), is(26));
        assertThat(chargeResponse.getState().getStatus(), is("failed"));
        assertThat(chargeResponse.getState().isFinished(), is(true));
        assertThat(chargeResponse.getState().getMessage(), is("Payment method rejected"));
        assertThat(chargeResponse.getState().getCode(), is("P0010"));
    }

    @Test
    public void shouldCreateAResponse() throws Exception {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        populateChargeEntity();

        ChargeResponse response = service.create(requestBuilder.build(), GATEWAY_ACCOUNT_ID, mockedUriInfo).get();

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();

        // Then - expected response is returned
        ChargeResponse.ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(createdChargeEntity);

        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + EXTERNAL_CHARGE_ID[0]));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + EXTERNAL_CHARGE_ID[0] + "/refunds"));
        expectedChargeResponse.withLink("next_url", GET, new URI("http://frontend.test/secure/" + tokenEntity.getToken()));
        expectedChargeResponse.withLink("next_url_post", POST, new URI("http://frontend.test/secure"), "application/x-www-form-urlencoded", new HashMap<String, Object>() {{
            put("chargeTokenId", tokenEntity.getToken());
        }});

        assertThat(response, is(expectedChargeResponse.build()));
    }

    @Test
    public void shouldCreateAToken() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        populateChargeEntity();

        service.create(requestBuilder.build(), GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeEntity().getId(), is(CHARGE_ENTITY_ID));
        assertThat(tokenEntity.getToken(), is(notNullValue()));
        assertThat(tokenEntity.isUsed(), is(false));
    }

    private void populateChargeEntity() {
        doAnswer(invocation -> {
            ChargeEntity chargeEntityBeingPersisted = (ChargeEntity) invocation.getArguments()[0];
            chargeEntityBeingPersisted.setId(CHARGE_ENTITY_ID);
            EXTERNAL_CHARGE_ID[0] = chargeEntityBeingPersisted.getExternalId();
            return null;
        }).when(mockedChargeDao).persist(any(ChargeEntity.class));
    }
}
