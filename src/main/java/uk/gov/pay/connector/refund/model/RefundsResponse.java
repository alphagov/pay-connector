package uk.gov.pay.connector.refund.model;

import black.door.hate.HalRepresentation;
import black.door.hate.HalResource;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

public class RefundsResponse extends HalResourceResponse {

    private RefundsResponse(HalRepresentation.HalRepresentationBuilder refundHalRepresentation, URI location) {
        super(refundHalRepresentation, location);
    }

    public static RefundsResponse valueOf(Charge charge, List<RefundEntity> refundEntityList,
                                          Long gatewayAccountId, UriInfo uriInfo) {

        String externalChargeId = charge.getExternalId();

        URI selfLink = uriInfo.getBaseUriBuilder()
                .path("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds")
                .build(gatewayAccountId.toString(), externalChargeId);

        URI paymentLink = uriInfo.getBaseUriBuilder()
                .path("/v1/api/accounts/{accountId}/charges/{chargeId}")
                .build(gatewayAccountId.toString(), externalChargeId);

        List<HalResource> refunds = refundEntityList.stream()
                .map(refundEntity -> RefundResponse.valueOf(refundEntity, gatewayAccountId, uriInfo))
                .collect(Collectors.toList());

        return new RefundsResponse(HalRepresentation.builder()
                .addProperty("payment_id", charge.getExternalId())
                .addLink("self", selfLink)
                .addLink("payment", paymentLink)
                .addEmbedded("refunds", refunds), selfLink);
    }
}
