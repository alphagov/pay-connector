package uk.gov.pay.connector.util;

import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Card;

import static uk.gov.pay.connector.util.CardUtils.buildCardDetails;

public class AuthorisationUtils {
    public static final String CHARGE_AMOUNT = "500";

    public static AuthorisationRequest getCardAuthorisationRequest() {
        Address address = Address.anAddress();
        address.setLine1("41");
        address.setLine2("Scala Street");
        address.setCity("London");
        address.setCounty("London");
        address.setPostcode("EC2A 1AE");
        address.setCountry("GB");

        Card card = aValidSmartpayCard();
        card.setAddress(address);

        String amount = CHARGE_AMOUNT;
        String description = "This is the description";
        return new AuthorisationRequest("chargeId", card, amount, description);
    }

    public static Card aValidSmartpayCard() {
        String validSandboxCard = "5555444433331111";
        return buildCardDetails(validSandboxCard, "737", "08/18");
    }
}
