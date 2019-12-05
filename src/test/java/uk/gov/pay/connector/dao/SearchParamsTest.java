package uk.gov.pay.connector.dao;

import org.junit.Test;
import uk.gov.pay.connector.charge.dao.SearchParams;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;

public class SearchParamsTest {

    @Test
    public void getInternalStates_shouldSetInternalStatesDirectlyToSearchParams() {

        // So internal methods can call findAll with specific states of a charge
        SearchParams params = new SearchParams()
                .withInternalStates(asList(CAPTURED, USER_CANCELLED));

        assertThat(params.getInternalStates(), hasSize(2));
        assertThat(params.getInternalStates(), containsInAnyOrder(CAPTURED, USER_CANCELLED));
    }
}
