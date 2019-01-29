package uk.gov.pay.connector.report.resource;

import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.report.model.TransactionsSummaryResponse;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.LongSummaryStatistics;

import static fj.data.Either.reduce;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.common.validator.ApiValidators.validateFromDateIsBeforeToDate;
import static uk.gov.pay.connector.common.validator.ApiValidators.validateGatewayAccountReference;

@Path("/")
public class TransactionsSummaryResource {

    private static final String ACCOUNT_ID = "accountId";
    private static final String FROM_DATE = "from_date";
    private static final String TO_DATE = "to_date";

    private static final List<ChargeStatus> CHARGE_SUCCESS_STATUSES = Arrays.stream(ChargeStatus.values())
            .filter(status -> status.toExternal() == ExternalChargeState.EXTERNAL_SUCCESS)
            .collect(toList());

    private static final List<RefundStatus> REFUND_SUCCESS_STATUSES = Arrays.stream(RefundStatus.values())
            .filter(status -> status.toExternal() == ExternalRefundStatus.EXTERNAL_SUCCESS)
            .collect(toList());

    private final GatewayAccountDao gatewayAccountDao;
    private final ChargeDao chargeDao;
    private final RefundDao refundDao;

    @Inject
    public TransactionsSummaryResource(GatewayAccountDao gatewayAccountDao, ChargeDao chargeDao, RefundDao refundDao) {
        this.gatewayAccountDao = gatewayAccountDao;
        this.chargeDao = chargeDao;
        this.refundDao = refundDao;
    }

    @GET
    @Path("/v1/api/accounts/{accountId}/transactions-summary")
    @Produces(APPLICATION_JSON)
    public Response getPaymentsSummary(@PathParam(ACCOUNT_ID) Long gatewayAccountId,
                                       @QueryParam(FROM_DATE) String fromDate,
                                       @QueryParam(TO_DATE) String toDate) {
        return reduce(validateGatewayAccountReference(gatewayAccountDao, gatewayAccountId)
                .bimap(ResponseUtil::notFoundResponse,
                        success -> reduce(validateFromDateIsBeforeToDate(FROM_DATE, fromDate, TO_DATE, toDate)
                                .bimap(ResponseUtil::badRequestResponse,
                                        fromDateAndToDate -> summarisePaymentsAndRefunds(gatewayAccountId, fromDateAndToDate)))));
    }

    private Response summarisePaymentsAndRefunds(Long gatewayAccountId, Pair<ZonedDateTime, ZonedDateTime> fromDateAndToDate) {
        LongSummaryStatistics successfulPaymentStats = chargeDao.findByAccountBetweenDatesWithStatusIn(
                    gatewayAccountId,
                    fromDateAndToDate.getLeft(),
                    fromDateAndToDate.getRight(),
                    CHARGE_SUCCESS_STATUSES)
                .stream()
                .mapToLong(ChargeEntity::getAmount)
                .summaryStatistics();

        LongSummaryStatistics successfulRefundStats = refundDao.findByAccountBetweenDatesWithStatusIn(
                    gatewayAccountId,
                    fromDateAndToDate.getLeft(),
                    fromDateAndToDate.getRight(),
                    REFUND_SUCCESS_STATUSES)
                .stream()
                .mapToLong(RefundEntity::getAmount)
                .summaryStatistics();

        TransactionsSummaryResponse response = new TransactionsSummaryResponse(
                (int) successfulPaymentStats.getCount(), successfulPaymentStats.getSum(),
                (int) successfulRefundStats.getCount(), successfulRefundStats.getSum(),
                successfulPaymentStats.getSum() - successfulRefundStats.getSum());

        return Response.ok(response).build();
    }

}
