package uk.gov.pay.connector.pact;

import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.sql.Timestamp;
import java.time.ZonedDateTime;

import static java.time.ZoneOffset.UTC;
import static uk.gov.pay.connector.util.RandomAlphaNumericString.randomAlphaNumeric;

public class RefundHistoryEntityFixture {

    private Long id = 1L;
    private String externalId = randomAlphaNumeric(10);
    private Long amount = 50L;
    private String status = RefundStatus.CREATED.getValue();
    private ZonedDateTime createdDate = ZonedDateTime.now(UTC).minusSeconds(5L);
    private Long version = 1L;
    private ZonedDateTime historyStartDate = createdDate.plusSeconds(2L);
    private ZonedDateTime historyEndDate = createdDate.plusSeconds(3L);
    private String userExternalId;
    private String userEmail;
    private String gatewayTransactionId = null;
    private String chargeExternalId = randomAlphaNumeric(10);
    private Long gatewayAccountId = 123456L;

    private RefundHistoryEntityFixture() {
    }

    public static RefundHistoryEntityFixture aValidRefundHistoryEntity() {
        return new RefundHistoryEntityFixture();
    }

    public RefundHistory build() {
        return new RefundHistory(id, externalId, amount, status, Timestamp.valueOf(createdDate.toLocalDateTime()),
                version,
                Timestamp.from(historyStartDate.toInstant()),
                Timestamp.from(historyEndDate.toInstant()),
                userExternalId, gatewayTransactionId, chargeExternalId, userEmail);
    }

    public RefundHistoryEntityFixture withExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    public RefundHistoryEntityFixture withStatus(String status) {
        this.status = status;
        return this;
    }

    public RefundHistoryEntityFixture withUserExternalId(String userExternalId) {
        this.userExternalId = userExternalId;
        return this;
    }

    public RefundHistoryEntityFixture withChargeExternalId(String chargeExternalId) {
        this.chargeExternalId = chargeExternalId;
        return this;
    }

    public RefundHistoryEntityFixture withHistoryStartDate(ZonedDateTime historyStartDate) {
        this.historyStartDate = historyStartDate;
        return this;
    }

    public RefundHistoryEntityFixture withAmount(Long amount) {
        this.amount = amount;
        return this;
    }

    public RefundHistoryEntityFixture withUserEmail(String userEmail) {
        this.userEmail = userEmail;
        return this;
    }

    public RefundHistoryEntityFixture withGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
        return this;
    }
}
