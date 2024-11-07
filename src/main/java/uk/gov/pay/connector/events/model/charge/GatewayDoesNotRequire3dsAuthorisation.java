package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.charge.GatewayDoesNotRequire3dsAuthorisationEventDetails;

import java.time.Instant;
import java.util.Objects;

public class GatewayDoesNotRequire3dsAuthorisation extends PaymentEvent {
    public GatewayDoesNotRequire3dsAuthorisation(String serviceId, boolean live,
                                                 Long gatewayAccountId, String resourceExternalId,
                                                 GatewayDoesNotRequire3dsAuthorisationEventDetails eventDetails,
                                                 Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, eventDetails, timestamp);
    }

    public static GatewayDoesNotRequire3dsAuthorisation from(ChargeEntity charge, Instant eventDate) {
        return new GatewayDoesNotRequire3dsAuthorisation(charge.getServiceId(), charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(), charge.getExternalId(),
                new GatewayDoesNotRequire3dsAuthorisationEventDetails(), eventDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GatewayDoesNotRequire3dsAuthorisation that = (GatewayDoesNotRequire3dsAuthorisation) o;
        return  isLive() == that.isLive() &&
                Objects.equals(getServiceId(), that.getServiceId()) &&
                Objects.equals(getGatewayAccountId(), that.getGatewayAccountId()) &&
                Objects.equals(getResourceExternalId(), that.getResourceExternalId()) &&
                Objects.equals(getTimestamp(), that.getTimestamp()) &&
                Objects.equals(getEventDetails(), that.getEventDetails()) &&
                Objects.equals(getEventType(), that.getEventType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getServiceId(), isLive(), getGatewayAccountId(),
                getResourceExternalId(), getEventDetails(), getTimestamp(),
                getEventType());
    }
}
