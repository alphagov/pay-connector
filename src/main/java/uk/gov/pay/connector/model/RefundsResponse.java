package uk.gov.pay.connector.model;

import black.door.hate.HalRepresentation;
import black.door.hate.HalResource;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.REFUNDS_API_PATH;

public class RefundsResponse extends HalResourceResponse {

    private RefundsResponse(HalRepresentation.HalRepresentationBuilder refundHalRepresentation, URI location) {
        super(refundHalRepresentation, location);
    }

    public static RefundsResponse valueOf(ChargeEntity chargeEntity, UriInfo uriInfo) {

        String accountId = chargeEntity.getGatewayAccount().getId().toString();
        String externalChargeId = chargeEntity.getExternalId();

        URI selfLink = uriInfo.getBaseUriBuilder()
                .path(REFUNDS_API_PATH)
                .build(accountId, externalChargeId);

        URI paymentLink = uriInfo.getBaseUriBuilder()
                .path(CHARGE_API_PATH)
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
