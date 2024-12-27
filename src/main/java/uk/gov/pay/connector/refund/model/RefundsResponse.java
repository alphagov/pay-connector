package uk.gov.pay.connector.refund.model;

import black.door.hate.HalRepresentation;
import black.door.hate.HalResource;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

public class RefundsResponse extends HalResourceResponse {

    private RefundsResponse(HalRepresentation.HalRepresentationBuilder refundHalRepresentation, URI location) {
        super(refundHalRepresentation, location);
    }

    public static RefundsResponse valueOf(ChargeEntity chargeEntity, List<RefundEntity> refundEntityList, UriInfo uriInfo) {

        Long accountId = chargeEntity.getGatewayAccount().getId();
        String externalChargeId = chargeEntity.getExternalId();

        URI selfLink = uriInfo.getBaseUriBuilder()
                .path("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds")
                .build(accountId.toString(), externalChargeId);

        URI paymentLink = uriInfo.getBaseUriBuilder()
                .path("/v1/api/accounts/{accountId}/charges/{chargeId}")
                .build(accountId.toString(), externalChargeId);

        List<HalResource> refunds = refundEntityList.stream()
                .map(refundEntity -> RefundResponse.valueOf(refundEntity, accountId, uriInfo))
                .collect(Collectors.toList());

        return new RefundsResponse(HalRepresentation.builder()
                .addProperty("payment_id", chargeEntity.getExternalId())
                .addLink("self", selfLink)
                .addLink("payment", paymentLink)
                .addEmbedded("refunds", refunds), selfLink);
    }
}
