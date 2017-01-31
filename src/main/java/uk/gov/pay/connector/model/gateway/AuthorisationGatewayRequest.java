package uk.gov.pay.connector.model.gateway;

import uk.gov.pay.connector.model.GatewayRequest;
import uk.gov.pay.connector.model.domain.AuthorisationDetails;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import java.util.Optional;

public class AuthorisationGatewayRequest implements GatewayRequest {
    private AuthorisationDetails authorisationDetails;
    private ChargeEntity charge;

    public AuthorisationGatewayRequest(ChargeEntity charge, AuthorisationDetails authorisationDetails) {
        this.charge = charge;
        this.authorisationDetails = authorisationDetails;
    }

    public Optional<String> getTransactionId() {
        return Optional.ofNullable(charge.getGatewayTransactionId());
    }

    public AuthorisationDetails getAuthorisationDetails() {
        return authorisationDetails;
    }

    public String getAmount() {
        return String.valueOf(charge.getAmount());
    }

    public String getDescription() {
        return charge.getDescription();
    }

    public String getChargeExternalId() {
        return String.valueOf(charge.getExternalId());
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {return charge.getGatewayAccount();
    }

    public static AuthorisationGatewayRequest valueOf(ChargeEntity charge, AuthorisationDetails authorisationDetails) {
        return new AuthorisationGatewayRequest(charge, authorisationDetails);
    }
}
