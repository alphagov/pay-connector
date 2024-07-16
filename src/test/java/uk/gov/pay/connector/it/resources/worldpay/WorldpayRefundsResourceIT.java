package uk.gov.pay.connector.it.resources.worldpay;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.matcher.RefundsMatcher.aRefundMatching;

public class WorldpayRefundsResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("worldpay", app.getLocalPort(), app.getDatabaseTestHelper());

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    
    @BeforeEach
    void setUpCharge() {
        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(Long.parseLong(testBaseExtension.getAccountId()))
                .withGatewayAccountCredentials(List.of(testBaseExtension.getCredentialParams()))
                .withCredentials(testBaseExtension.getCredentials());

        defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withAmount(100L)
                .withTransactionId("MyUniqueTransactionId!")
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(CAPTURED)
                .withPaymentProvider(testBaseExtension.getPaymentProvider())
                .withGatewayCredentialId(testBaseExtension.getCredentialParams().getId())
                .insert();
    }
    
    @Nested
    class ByAccountId {        
        @Nested
        class GetRefunds {

            @Test
            void shouldBeAbleToRetrieveAllRefundsForACharge() {

                DatabaseFixtures.TestRefund testRefund1 = DatabaseFixtures
                        .withDatabaseTestHelper(app.getDatabaseTestHelper())
                        .aTestRefund()
                        .withAmount(10L)
                        .withCreatedDate(ZonedDateTime.of(2016, 8, 1, 0, 0, 0, 0, ZoneId.of("UTC")))
                        .withTestCharge(defaultTestCharge)
                        .withType(RefundStatus.REFUND_SUBMITTED)
                        .withChargeExternalId(defaultTestCharge.getExternalChargeId())
                        .insert();

                DatabaseFixtures.TestRefund testRefund2 = DatabaseFixtures
                        .withDatabaseTestHelper(app.getDatabaseTestHelper())
                        .aTestRefund()
                        .withAmount(20L)
                        .withCreatedDate(ZonedDateTime.of(2016, 8, 2, 0, 0, 0, 0, ZoneId.of("UTC")))
                        .withTestCharge(defaultTestCharge)
                        .withType(RefundStatus.REFUND_SUBMITTED)
                        .withChargeExternalId(defaultTestCharge.getExternalChargeId())
                        .insert();

                String paymentUrl = format("https://localhost:%s/v1/api/accounts/%s/charges/%s",
                        app.getLocalPort(), defaultTestAccount.getAccountId(), defaultTestCharge.getExternalChargeId());

                getRefundsFor(defaultTestAccount.getAccountId(),
                        defaultTestCharge.getExternalChargeId())
                        .statusCode(OK.getStatusCode())
                        .body("payment_id", is(defaultTestCharge.getExternalChargeId()))
                        .body("_links.self.href", is(paymentUrl + "/refunds"))
                        .body("_links.payment.href", is(paymentUrl))
                        .body("_embedded.refunds", hasSize(2))
                        .body("_embedded.refunds[0].refund_id", is(testRefund1.getExternalRefundId()))
                        .body("_embedded.refunds[0].amount", is(10))
                        .body("_embedded.refunds[0].status", is("submitted"))
                        .body("_embedded.refunds[0].created_date", is("2016-08-01T00:00:00.000Z"))
                        .body("_embedded.refunds[0]._links.self.href", is(paymentUrl + "/refunds/" + testRefund1.getExternalRefundId()))
                        .body("_embedded.refunds[0]._links.payment.href", is(paymentUrl))
                        .body("_embedded.refunds[1].refund_id", is(testRefund2.getExternalRefundId()))
                        .body("_embedded.refunds[1].amount", is(20))
                        .body("_embedded.refunds[1].status", is("submitted"))
                        .body("_embedded.refunds[1].created_date", is("2016-08-02T00:00:00.000Z"))
                        .body("_embedded.refunds[1]._links.self.href", is(paymentUrl + "/refunds/" + testRefund2.getExternalRefundId()))
                        .body("_embedded.refunds[1]._links.payment.href", is(paymentUrl));
            }
        }
        
        @Nested
        class GetRefund {
            @Test
            void shouldBeAbleRetrieveARefund() {

                DatabaseFixtures.TestRefund testRefund = DatabaseFixtures
                        .withDatabaseTestHelper(app.getDatabaseTestHelper())
                        .aTestRefund()
                        .withTestCharge(defaultTestCharge)
                        .withType(RefundStatus.REFUND_SUBMITTED)
                        .withGatewayTransactionId(randomAlphanumeric(10))
                        .withChargeExternalId(defaultTestCharge.getExternalChargeId())
                        .insert();

                ValidatableResponse validatableResponse = getRefundFor(
                        defaultTestAccount.getAccountId(), defaultTestCharge.getExternalChargeId(), testRefund.getExternalRefundId());
                String refundId = assertRefundResponseWith(testRefund.getAmount(), validatableResponse, OK.getStatusCode());


                List<Map<String, Object>> refundsFoundByChargeExternalId = (app.getDatabaseTestHelper()).getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(1));
                assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), testRefund.getAmount(), "REFUND SUBMITTED")));
            }

            @Test
            void shouldFailRetrieveARefund_whenNonExistentAccountId() {
                DatabaseFixtures.TestRefund testRefund = DatabaseFixtures
                        .withDatabaseTestHelper(app.getDatabaseTestHelper())
                        .aTestRefund()
                        .withTestCharge(defaultTestCharge)
                        .withType(RefundStatus.REFUND_SUBMITTED)
                        .insert();

                Long nonExistentAccountId = 999L;

                ValidatableResponse validatableResponse = getRefundFor(
                        nonExistentAccountId, defaultTestCharge.getExternalChargeId(), testRefund.getExternalRefundId());

                validatableResponse.statusCode(NOT_FOUND.getStatusCode())
                        .body("message", contains(format("Charge with id [%s] not found.", defaultTestCharge.getExternalChargeId())))
                        .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
            }

            @Test
            void shouldFailRetrieveARefund_whenNonExistentChargeId() {
                DatabaseFixtures.TestRefund testRefund = DatabaseFixtures
                        .withDatabaseTestHelper(app.getDatabaseTestHelper())
                        .aTestRefund()
                        .withTestCharge(defaultTestCharge)
                        .withType(RefundStatus.REFUND_SUBMITTED)
                        .insert();

                String nonExistentChargeId = "999";

                ValidatableResponse validatableResponse = getRefundFor(
                        defaultTestAccount.getAccountId(), nonExistentChargeId, testRefund.getExternalRefundId());

                validatableResponse.statusCode(NOT_FOUND.getStatusCode())
                        .body("message", contains(format("Charge with id [%s] not found.", nonExistentChargeId)))
                        .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
            }

            @Test
            void shouldFailRetrieveARefund_whenNonExistentRefundId() {
                DatabaseFixtures
                        .withDatabaseTestHelper(app.getDatabaseTestHelper())
                        .aTestRefund()
                        .withTestCharge(defaultTestCharge)
                        .withType(RefundStatus.REFUND_SUBMITTED)
                        .insert();

                String nonExistentRefundId = "999";

                ValidatableResponse validatableResponse = getRefundFor(
                        defaultTestAccount.getAccountId(), defaultTestCharge.getExternalChargeId(), nonExistentRefundId);

                validatableResponse.statusCode(NOT_FOUND.getStatusCode())
                        .body("message", contains(format("Refund with id [%s] not found.", nonExistentRefundId)))
                        .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
            }
        }
    }

    private ValidatableResponse getRefundsFor(Long accountId, String chargeId) {
        return app.givenSetup()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .get("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                        .replace("{accountId}", accountId.toString())
                        .replace("{chargeId}", chargeId))
                .then();
    }

    private ValidatableResponse getRefundFor(Long accountId, String chargeId, String refundId) {
        return app.givenSetup()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .get("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds/{refundId}"
                        .replace("{accountId}", accountId.toString())
                        .replace("{chargeId}", chargeId)
                        .replace("{refundId}", refundId))
                .then();
    }

    private String assertRefundResponseWith(Long refundAmount, ValidatableResponse validatableResponse, int expectedStatusCode) {
        ValidatableResponse response = validatableResponse
                .statusCode(expectedStatusCode)
                .body("refund_id", is(notNullValue()))
                .body("amount", is(refundAmount.intValue()))
                .body("status", is("submitted"))
                .body("created_date", is(notNullValue()));

        String paymentUrl = format("https://localhost:%s/v1/api/accounts/%s/charges/%s",
                app.getLocalPort(), defaultTestAccount.getAccountId(), defaultTestCharge.getExternalChargeId());

        String refundId = response.extract().path("refund_id");
        response.body("_links.self.href", is(paymentUrl + "/refunds/" + refundId))
                .body("_links.payment.href", is(paymentUrl));

        return refundId;
    }
}
