package uk.gov.pay.connector.model;

import black.door.hate.HalRepresentation;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.util.DateTimeUtils;

import javax.ws.rs.core.UriInfo;
import java.net.URI;

import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.REFUND_API_PATH;

public class RefundResponse extends HalResourceResponse {

    private RefundResponse(HalRepresentation.HalRepresentationBuilder refundHalRepresentation, URI location) {
        super(refundHalRepresentation, location);
    }

    public static RefundResponse valueOf(RefundEntity refundEntity, UriInfo uriInfo) {

        Long accountId = refundEntity.getChargeEntity().getGatewayAccount().getId();
        String externalChargeId = refundEntity.getChargeEntity().getExternalId();
        String externalRefundId = refundEntity.getExternalId();

        URI selfLink = uriInfo.getBaseUriBuilder()
                .path(REFUND_API_PATH)
                .build(accountId, externalChargeId, externalRefundId);

        URI paymentLink = uriInfo.getBaseUriBuilder()
                .path(CHARGE_API_PATH)
                .build(accountId, externalChargeId);

        return new RefundResponse(HalRepresentation.builder()
                .addProperty("refund_id", refundEntity.getExternalId())
                .addProperty("amount", refundEntity.getAmount())
                .addProperty("status", refundEntity.getStatus().toExternal().getStatus())
                .addProperty("created_date", DateTimeUtils.toUTCDateString(refundEntity.getCreatedDate()))
                .addLink("self", selfLink)
                .addLink("payment", paymentLink), selfLink);
    }
}
