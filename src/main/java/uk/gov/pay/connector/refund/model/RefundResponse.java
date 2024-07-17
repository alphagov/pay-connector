package uk.gov.pay.connector.refund.model;

import black.door.hate.HalRepresentation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import javax.ws.rs.core.UriInfo;
import java.net.URI;

import static uk.gov.service.payments.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION;

public class RefundResponse extends HalResourceResponse {

    private RefundResponse(HalRepresentation.HalRepresentationBuilder refundHalRepresentation, URI location) {
        super(refundHalRepresentation, location);
    }

    public static RefundResponse valueOf(RefundEntity refundEntity, Long gatewayAccountId, UriInfo uriInfo) {

        String externalChargeId = refundEntity.getChargeExternalId();
        String externalRefundId = refundEntity.getExternalId();

        URI selfLink = uriInfo.getBaseUriBuilder()
                .path("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds/{refundId}")
                .build(gatewayAccountId, externalChargeId, externalRefundId);

        URI paymentLink = uriInfo.getBaseUriBuilder()
                .path("/v1/api/accounts/{accountId}/charges/{chargeId}")
                .build(gatewayAccountId, externalChargeId);

        return new RefundResponse(HalRepresentation.builder()
                .addProperty("refund_id", refundEntity.getExternalId())
                .addProperty("amount", refundEntity.getAmount())
                .addProperty("status", refundEntity.getStatus().toExternal().getStatus())
                .addProperty("created_date", ISO_INSTANT_MILLISECOND_PRECISION.format(refundEntity.getCreatedDate()))
                .addProperty("user_external_id", refundEntity.getUserExternalId())
                .addLink("self", selfLink)
                .addLink("payment", paymentLink), selfLink);
    }
    
    public static RefundResponse valueOf(RefundEntity refundEntity, String serviceId, GatewayAccountType accountType, UriInfo uriInfo) {

        String externalChargeId = refundEntity.getChargeExternalId();
        String externalRefundId = refundEntity.getExternalId();

        URI selfLink = uriInfo.getBaseUriBuilder()
                .path("/v1/api/service/{serviceId}/account/{accountType}/charges/{chargeId}/refunds/{refundId}")
                .build(serviceId, accountType, externalChargeId, externalRefundId);

        URI paymentLink = uriInfo.getBaseUriBuilder()
                .path("/v1/api/service/{serviceId}/account/{accountType}/charges/{chargeId}")
                .build(serviceId, accountType, externalChargeId, externalRefundId);

        return new RefundResponse(HalRepresentation.builder()
                .addProperty("refund_id", refundEntity.getExternalId())
                .addProperty("amount", refundEntity.getAmount())
                .addProperty("status", refundEntity.getStatus().toExternal().getStatus())
                .addProperty("created_date", ISO_INSTANT_MILLISECOND_PRECISION.format(refundEntity.getCreatedDate()))
                .addProperty("user_external_id", refundEntity.getUserExternalId())
                .addLink("self", selfLink)
                .addLink("payment", paymentLink), selfLink);
    }
}
