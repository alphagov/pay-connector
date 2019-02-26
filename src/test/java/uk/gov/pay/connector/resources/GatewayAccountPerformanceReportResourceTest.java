package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.report.dao.PerformanceReportDao;
import uk.gov.pay.connector.report.model.domain.GatewayAccountPerformanceReportEntity;
import uk.gov.pay.connector.report.resource.PerformanceReportResource;

import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.stream.Stream;

import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class GatewayAccountPerformanceReportResourceTest {

    @Mock
    private PerformanceReportDao mockPerformanceReportDao;

    private Stream<GatewayAccountPerformanceReportEntity> someTransactionsPerformanceReportEntity;

    private final Long totalVolume = 10000L;
    private final BigDecimal totalAmount = new BigDecimal("10000000");
    private final BigDecimal averageAmount = new BigDecimal("1000");
    private final Long minAmount = 1000L;
    private final Long maxAmount = 1000L;

    private PerformanceReportResource resource;

    @Before
    public void setUp() {
        someTransactionsPerformanceReportEntity = Stream.of(new GatewayAccountPerformanceReportEntity(
            totalVolume,
            totalAmount,
            averageAmount,
            minAmount,
            maxAmount,
            3L
        ));

        resource = new PerformanceReportResource(mockPerformanceReportDao);
    }

    @Test
    public void noTransactionsPerformanceReportEntitySerializesCorrectly() {
        Stream<GatewayAccountPerformanceReportEntity> noTransactionsPerformanceReportEntity
                = Stream.empty();
        given(mockPerformanceReportDao.aggregateNumberAndValueOfPaymentsByGatewayAccount())
                .willReturn(noTransactionsPerformanceReportEntity);

        Response result = resource.getGatewayAccountPerformanceReport();

        assertThat(result.getStatus(), is(OK.getStatusCode()));

        HashMap<String, ImmutableMap<String, Object>> unserializedResponse = (HashMap<String, ImmutableMap<String, Object>>) result.getEntity();

        assertThat(unserializedResponse.size(), is(0));
    }

    @Test
    public void someTransactionsPerformanceReportEntitySerializesCorrectly() {
        given(mockPerformanceReportDao.aggregateNumberAndValueOfPaymentsByGatewayAccount())
                .willReturn(someTransactionsPerformanceReportEntity);

        Response result = resource.getGatewayAccountPerformanceReport();

        assertThat(result.getStatus(), is(OK.getStatusCode()));

        HashMap<String, ImmutableMap<String, Object>> unserializedResponse = (HashMap<String, ImmutableMap<String, Object>>) result.getEntity();

        assertThat(unserializedResponse.size(), is(1));

        ImmutableMap<String, Object> gatewayAccountPerformance = unserializedResponse.get("3");

        assertThat(gatewayAccountPerformance.get("total_volume"),   is(totalVolume));
        assertThat(gatewayAccountPerformance.get("total_amount"),   is(totalAmount));
        assertThat(gatewayAccountPerformance.get("average_amount"), is(averageAmount));
        assertThat(gatewayAccountPerformance.get("min_amount"),     is(minAmount));
        assertThat(gatewayAccountPerformance.get("max_amount"),     is(maxAmount));
    }
}
