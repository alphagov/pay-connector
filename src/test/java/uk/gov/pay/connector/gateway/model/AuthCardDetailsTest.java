package uk.gov.pay.connector.gateway.model;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.card.model.AddressEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.client.cardid.model.CardInformationFixture;
import uk.gov.pay.connector.client.cardid.model.CardidCardType;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.card.model.AuthoriseRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

class AuthCardDetailsTest {

    private final AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Joe");
    private final CardInformation cardInformation = CardInformationFixture.aCardInformation().build();

    @Test
    void shouldReturnAuthCardDetailsWithCardAndAddressDetailFromAuthoriseRequest() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withCardDetails(anAuthCardDetails().getCardDetailsEntity())
                .build();

        AuthCardDetails authCardDetails = AuthCardDetails.of(authoriseRequest, chargeEntity, cardInformation);

        assertAuthCardDetails(authCardDetails, cardInformation);
        assertThat(authCardDetails.getAddress().isPresent(), is(true));

        Address address = authCardDetails.getAddress().get();
        AddressEntity addressEntity = chargeEntity.getChargeCardDetails().getBillingAddress().get();

        assertThat(address.getLine1(), is(addressEntity.getLine1()));
        assertThat(address.getLine2(), is(addressEntity.getLine2()));
        assertThat(address.getCity(), is(addressEntity.getCity()));
        assertThat(address.getCounty(), is(addressEntity.getCounty()));
        assertThat(address.getPostcode(), is(addressEntity.getPostcode()));
        assertThat(address.getCountry(), is(addressEntity.getCountry()));
    }

    @Test
    void shouldReturnAuthCardDetailsWithoutAddressFromAuthoriseRequest() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .build();

        AuthCardDetails authCardDetails = AuthCardDetails.of(authoriseRequest, chargeEntity, cardInformation);

        assertAuthCardDetails(authCardDetails, cardInformation);
        assertThat(authCardDetails.getAddress().isPresent(), is(false));
    }

    private void assertAuthCardDetails(AuthCardDetails authCardDetails, CardInformation cardInformation) {
        assertThat(authCardDetails.getCardNo(), is("4242424242424242"));
        assertThat(authCardDetails.getCvc(), is("123"));
        assertThat(authCardDetails.getCardHolder(), is("Joe"));
        assertThat(authCardDetails.getEndDate().toString(), is("11/99"));

        assertThat(authCardDetails.getCardBrand(), is(cardInformation.getBrand()));
        assertThat(authCardDetails.isCorporateCard(), is(cardInformation.isCorporate()));
        assertThat(authCardDetails.getPayersCardType(), is(CardidCardType.toPayersCardType(cardInformation.getType())));
        assertThat(authCardDetails.getPayersCardPrepaidStatus(), is(cardInformation.getPrepaidStatus()));
    }

}
