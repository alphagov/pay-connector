package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.report.dao.PerformanceReportDao;
import uk.gov.pay.connector.report.model.domain.PerformanceReportEntity;
import uk.gov.pay.connector.report.resource.PerformanceReportResource;

import javax.ws.rs.core.Response;
import java.math.BigDecimal;

import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PerformanceReportResourceTest {

    private final Long totalVolume = 100000000000000L;
    private final BigDecimal totalAmount = new BigDecimal("10000000000000000");
    private final BigDecimal averageAmount = new BigDecimal("100");

    @Mock
    private PerformanceReportDao mockPerformanceReportDao;

    @Mock
    private PerformanceReportEntity noTransactionsPerformanceReportEntity,
                                    someTransactionsPerformanceReportEntity;

    private PerformanceReportResource resource;

    @BeforeEach
    void setUp() {
        noTransactionsPerformanceReportEntity = new PerformanceReportEntity(
            0L,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
        someTransactionsPerformanceReportEntity = new PerformanceReportEntity(
            totalVolume,
            totalAmount,
            averageAmount
        );

        resource = new PerformanceReportResource(mockPerformanceReportDao);
    }

    @Test
    void emptyPerformanceReportSerialisesCorrectly() {
        given(mockPerformanceReportDao.aggregateNumberAndValueOfPayments())
                .willReturn(noTransactionsPerformanceReportEntity);

        Response result = resource.getPerformanceReport();

        assertThat(result.getStatus(), is(OK.getStatusCode()));

        ImmutableMap<String, Object> unserializedResponse = (ImmutableMap<String, Object>) result.getEntity();

        assertThat(unserializedResponse.get("total_volume"), is(0L));
        assertThat(unserializedResponse.get("total_amount"), is(BigDecimal.ZERO));
        assertThat(unserializedResponse.get("average_amount"), is(BigDecimal.ZERO));
    }

    @Test
    void nonEmptyPerformanceReportSerialisesCorrectly() {
        given(mockPerformanceReportDao.aggregateNumberAndValueOfPayments())
                .willReturn(someTransactionsPerformanceReportEntity);

        Response result = resource.getPerformanceReport();

        assertThat(result.getStatus(), is(OK.getStatusCode()));

        ImmutableMap<String, Object> unserializedResponse = (ImmutableMap<String, Object>) result.getEntity();

        assertThat(unserializedResponse.get("total_volume"), is(totalVolume));
        assertThat(unserializedResponse.get("total_amount"), is(totalAmount));
        assertThat(unserializedResponse.get("average_amount"), is(averageAmount));
    }
}
