package uk.gov.pay.connector.dao;

import org.junit.Test;
import uk.gov.pay.connector.model.api.ExternalChargeState;

import java.time.ZonedDateTime;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class ChargeSearchParamsTest {

    public static final String FROM_DATE = "2012-06-30T12:30:40Z[GMT]";
    public static final String TO_DATE = "2012-07-30T12:30:40Z[GMT]";
    public static final String EXPECTED_QUERY_STRING =
            "reference=ref" +
            "&email=alice" +
            "&from_date=" + FROM_DATE +
            "&to_date=" + TO_DATE +
            "&page=%s" +
            "&display_size=%s" +
            "&state=created";

    @Test
    public void shouldBuildQueryParamsForChargeSearch() throws Exception {
        ChargeSearchParams params = new ChargeSearchParams()
                .withDisplaySize(5L)
                .withExternalChargeState(EXTERNAL_CREATED.getStatus())
                .withGatewayAccountId(111L)
                .withPage(2L)
                .withReferenceLike("ref")
                .withEmailLike("alice")
                .withFromDate(ZonedDateTime.parse(FROM_DATE))
                .withToDate(ZonedDateTime.parse(TO_DATE));

        assertEquals("query params string mismatch", format(EXPECTED_QUERY_STRING, 2, 5), params.buildQueryParams());

    }

    @Test
    public void shouldPopulateAllInternalStateFromExternalState() {
        ChargeSearchParams params = new ChargeSearchParams()
                .withDisplaySize(5L)
                .withExternalChargeState(ExternalChargeState.EXTERNAL_FAILED_CANCELLED.getStatus());

        assertEquals(7, params.getChargeStatuses().size());
        assertThat(params.getChargeStatuses(), containsInAnyOrder(
                USER_CANCEL_READY,USER_CANCEL_ERROR, USER_CANCELLED, EXPIRE_CANCEL_READY, EXPIRED, EXPIRE_CANCEL_FAILED, AUTHORISATION_REJECTED));

    }
}
