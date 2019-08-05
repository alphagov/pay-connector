package uk.gov.pay.connector.pact;

import org.apache.commons.lang3.RandomStringUtils;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.sql.Timestamp;
import java.time.ZonedDateTime;

public class RefundHistoryEntityFixture {

    private Long id = 1L;
    private String externalId = RandomStringUtils.randomAlphanumeric(10);
    private Long amount = 50L;
    private String status = RefundStatus.CREATED.getValue();
    private Long chargeId = 1L;
    private ZonedDateTime createdDate = ZonedDateTime.now().minusSeconds(5L);
    private Long version = 1L;
    private String reference;
    private ZonedDateTime historyStartDate = createdDate.plusSeconds(2L);
    private ZonedDateTime historyEndDate = createdDate.plusSeconds(3L);
    private String userExternalId;
    private String gatewayTransactionId = null;
    private String chargeExternalId = RandomStringUtils.randomAlphanumeric(10);
    private Long gatewayAccountId = 123456L;

    private RefundHistoryEntityFixture() {}

    public static RefundHistoryEntityFixture aValidRefundHistoryEntity() {
        return new RefundHistoryEntityFixture();
    }

    public RefundHistory build() {
        return new RefundHistory(id, externalId, amount, status, chargeId, Timestamp.valueOf(createdDate.toLocalDateTime()),
                version, reference, Timestamp.valueOf(historyStartDate.toLocalDateTime()), Timestamp.valueOf(historyEndDate.toLocalDateTime()),
                userExternalId, gatewayTransactionId, chargeExternalId, gatewayAccountId);
    }

    public RefundHistoryEntityFixture withStatus(String status) {
        this.status = status;
        return this;
    }

    public RefundHistoryEntityFixture withUserExternalId(String userExternalId) {
        this.userExternalId = userExternalId;
        return this;
    }

    public RefundHistoryEntityFixture withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public RefundHistoryEntityFixture withChargeExternalId(String chargeExternalId) {
        this.chargeExternalId = chargeExternalId;
        return this;
    }

    public RefundHistoryEntityFixture withAmount(Long amount) {
        this.amount = amount;
        return this;
    }

    public RefundHistoryEntityFixture withChargeId(Long chargeId) {
        this.chargeId = chargeId;
        return this;
    }
}
