package uk.gov.pay.connector.refund.model;

import black.door.hate.HalRepresentation;
import black.door.hate.HalResource;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;

import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

public class RefundsResponse extends HalResourceResponse {

    private RefundsResponse(HalRepresentation.HalRepresentationBuilder refundHalRepresentation, URI location) {
        super(refundHalRepresentation, location);
    }

    public static RefundsResponse valueOf(ChargeEntity chargeEntity, UriInfo uriInfo) {

        String accountId = chargeEntity.getGatewayAccount().getId().toString();
        String externalChargeId = chargeEntity.getExternalId();

        URI selfLink = uriInfo.getBaseUriBuilder()
                .path("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds")
                .build(accountId, externalChargeId);

        URI paymentLink = uriInfo.getBaseUriBuilder()
                .path("/v1/api/accounts/{accountId}/charges/{chargeId}")
                .build(accountId, externalChargeId);

        List<HalResource> refunds = chargeEntity.getRefunds().stream()
                .map(refundEntity -> RefundResponse.valueOf(refundEntity, uriInfo))
                .collect(Collectors.toList());

        return new RefundsResponse(HalRepresentation.builder()
                .addProperty("payment_id", chargeEntity.getExternalId())
                .addLink("self", selfLink)
                .addLink("payment", paymentLink)
                .addEmbedded("refunds", refunds), selfLink);
    }
}
