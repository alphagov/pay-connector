package uk.gov.pay.connector.events.eventdetails.refund;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.service.payments.commons.api.json.IsoInstantMicrosecondSerializer;

import java.time.Instant;

public class PaymentStatusCorrectedToSuccessByAdminEventDetails extends EventDetails {
    private final long fee;
    private final long netAmount;
    @JsonSerialize(using = IsoInstantMicrosecondSerializer.class)
    private final Instant capturedDate;
    private final ExternalRefundStatus refundStatus;
    private final String updatedReason;
    private final String adminGithubId;
    private final long gatewayAccountId;
    @JsonSerialize(using = IsoInstantMicrosecondSerializer.class)
    private final Instant captureSubmittedDate;
    private final long refundAmountRefunded;
    private final long refundAmountAvailable;
    private final String reference;
    private final String zendeskId;

    public PaymentStatusCorrectedToSuccessByAdminEventDetails(
            long fee,
            long netAmount,
            Instant capturedDate,
            ExternalRefundStatus refundStatus,
            String updatedReason,
            String adminGithubId,
            long gatewayAccountId,
            Instant captureSubmittedDate,
            long refundAmountRefunded,
            long refundAmountAvailable,
            String reference,
            String zendeskId) {
        this.fee = fee;  
        this.netAmount = netAmount;
        this.capturedDate = capturedDate;
        this.refundStatus = refundStatus;
        this.updatedReason = updatedReason;
        this.adminGithubId = adminGithubId;
        this.gatewayAccountId = gatewayAccountId;
        this.captureSubmittedDate = captureSubmittedDate;
        this.refundAmountRefunded = refundAmountRefunded;
        this.refundAmountAvailable = refundAmountAvailable;
        this.reference = reference;
        this.zendeskId = zendeskId;
    }

    public long getFee() {
        return fee;
    }

    public long getNetAmount() {
        return netAmount;
    }

    public Instant getCapturedDate() {
        return capturedDate;
    }

    public ExternalRefundStatus getRefundStatus() {
        return refundStatus;
    }

    public String getUpdatedReason() {
        return updatedReason;
    }

    public String getAdminGithubId() {
        return adminGithubId;
    }

    public long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public Instant getCaptureSubmittedDate() {
        return captureSubmittedDate;
    }

    public long getRefundAmountRefunded() {
        return refundAmountRefunded;
    }

    public long getRefundAmountAvailable() {
        return refundAmountAvailable;
    }


    public String getReference() {
        return reference;
    }

    public String getZendeskId() {
        return zendeskId;
    }
}
