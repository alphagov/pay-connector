package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.model.TransactionsSummaryResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;

import javax.ws.rs.core.Response;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;

@RunWith(MockitoJUnitRunner.class)
public class TransactionsSummaryResourceTest {

    private static final List<ChargeStatus> CHARGE_SUCCESS_STATUSES =
            ImmutableList.of(CAPTURE_APPROVED, CAPTURE_APPROVED_RETRY, CAPTURE_READY, CAPTURED, CAPTURE_SUBMITTED);

    private static final List<RefundStatus> REFUND_SUCCESS_STATUSES = Collections.singletonList(REFUNDED);

    private static final Long GATEWAY_ACCOUNT_ID = 12345L;

    private static final String MIDNIGHT_THURSDAY = "2017-11-23T00:00:00Z";
    private static final String MIDNIGHT_FRIDAY = "2017-11-24T00:00:00Z";

    private static final long GBP_2_50 = 2_50L;
    private static final long GBP_10_00 = 10_00L;
    private static final long GBP_25_00 = 25_00L;
    private static final long GBP_50_00 = 50_00L;
    private static final long GBP_100_00 = 100_00L;

    @Mock
    private GatewayAccountDao mockGatewayAccountDao;

    @Mock
    private ChargeDao mockChargeDao;

    @Mock
    private RefundDao mockRefundDao;

    @Mock
    private ChargeEntity mock25PoundsChargeEntity, mock50PoundsChargeEntity, mock100PoundsChargeEntity;

    @Mock
    private RefundEntity mock10PoundsRefundEntity, mock2Pounds50RefundEntity;

    private TransactionsSummaryResource resource;

    @Before
    public void setUp() {
        given(mock25PoundsChargeEntity.getAmount()).willReturn(GBP_25_00);
        given(mock50PoundsChargeEntity.getAmount()).willReturn(GBP_50_00);
        given(mock100PoundsChargeEntity.getAmount()).willReturn(GBP_100_00);

        given(mock10PoundsRefundEntity.getAmount()).willReturn(GBP_10_00);
        given(mock2Pounds50RefundEntity.getAmount()).willReturn(GBP_2_50);

        given(mockGatewayAccountDao.findById(any(Long.class))).willReturn(Optional.empty());
        given(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).willReturn(Optional.of(mock(GatewayAccountEntity.class)));


        resource = new TransactionsSummaryResource(mockGatewayAccountDao, mockChargeDao, mockRefundDao);
    }

    @Test
    public void validGatewayAccountIdFromDateAndToDateProducesCorrectSummary() {
        given(mockChargeDao.findByAccountBetweenDatesWithStatusIn(GATEWAY_ACCOUNT_ID,
                ZonedDateTime.parse(MIDNIGHT_THURSDAY), ZonedDateTime.parse(MIDNIGHT_FRIDAY), CHARGE_SUCCESS_STATUSES))
                .willReturn(Arrays.asList(mock25PoundsChargeEntity, mock50PoundsChargeEntity, mock100PoundsChargeEntity));

        given(mockRefundDao.findByAccountBetweenDatesWithStatusIn(GATEWAY_ACCOUNT_ID,
                ZonedDateTime.parse(MIDNIGHT_THURSDAY), ZonedDateTime.parse(MIDNIGHT_FRIDAY), REFUND_SUCCESS_STATUSES))
                .willReturn(Arrays.asList(mock10PoundsRefundEntity, mock2Pounds50RefundEntity));

        Response result = resource.getPaymentsSummary(GATEWAY_ACCOUNT_ID, MIDNIGHT_THURSDAY, MIDNIGHT_FRIDAY);

        assertThat(result.getStatus(), is(OK.getStatusCode()));

        TransactionsSummaryResponse response = (TransactionsSummaryResponse) result.getEntity();

        assertThat(response.getSuccessfulPayments().getCount(), is(3));
        assertThat(response.getSuccessfulPayments().getTotalInPence(),
                is(GBP_25_00
                        + GBP_50_00
                        + GBP_100_00));

        assertThat(response.getRefundedPayments().getCount(), is(2));
        assertThat(response.getRefundedPayments().getTotalInPence(),
                is(GBP_10_00
                        + GBP_2_50));

        assertThat(response.getNetIncome().getTotalInPence(),
                is(GBP_25_00
                        + GBP_50_00
                        + GBP_100_00
                        - GBP_10_00
                        - GBP_2_50));
    }

    @Test
    public void nonExistentGatewayAccountIdReturnsNotFoundResponse() {
        Response response = resource.getPaymentsSummary(54321L, MIDNIGHT_THURSDAY, MIDNIGHT_FRIDAY);

        assertThat(response.getStatus(), is(NOT_FOUND.getStatusCode()));
    }

    @Test
    public void missingFromDateReturnsBadRequestResponse() {
        Response response = resource.getPaymentsSummary(GATEWAY_ACCOUNT_ID, null, MIDNIGHT_FRIDAY);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    public void missingToDateReturnsBadRequestResponse() {
        Response response = resource.getPaymentsSummary(GATEWAY_ACCOUNT_ID, MIDNIGHT_THURSDAY, null);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    public void invalidFromDateReturnsBadRequestResponse() {
        Response response = resource.getPaymentsSummary(GATEWAY_ACCOUNT_ID, "not a from date", MIDNIGHT_FRIDAY);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    public void invalidToDateReturnsBadRequestResponse() {
        Response response = resource.getPaymentsSummary(GATEWAY_ACCOUNT_ID, MIDNIGHT_THURSDAY, "not a to date");

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    public void toDateBeforeFromDateReturnsBadRequestResponse() {
        Response response = resource.getPaymentsSummary(GATEWAY_ACCOUNT_ID, MIDNIGHT_FRIDAY, MIDNIGHT_THURSDAY);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

}
