package uk.gov.pay.connector.report.resource;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import uk.gov.pay.connector.report.dao.PerformanceReportDao;
import uk.gov.pay.connector.report.model.domain.PerformanceReportEntity;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.ok;
import static uk.gov.pay.connector.common.validator.ApiValidators.parseZonedDateTime;

@Path("/")
public class PerformanceReportResource {
    private PerformanceReportDao performanceReportDao;

    @Inject
    public PerformanceReportResource(PerformanceReportDao performanceReportDao) {
        this.performanceReportDao = performanceReportDao;
    }

    @GET
    @Path("/v1/api/reports/performance-report")
    @Produces(APPLICATION_JSON)
    public Response getPerformanceReport() {
        PerformanceReportEntity performanceReport = performanceReportDao.aggregateNumberAndValueOfPayments();

        ImmutableMap<String, Object> responsePayload = ImmutableMap.of(
                "total_volume", performanceReport.getTotalVolume(),
                "total_amount", performanceReport.getTotalAmount(),
                "average_amount", performanceReport.getAverageAmount());
        return ok().entity(responsePayload).build();
    }

    @GET
    @Path("/v1/api/reports/daily-performance-report")
    @Produces(APPLICATION_JSON)
    public Response getDailyPerformanceReport(@QueryParam("date") String rawDate) {
        return parseZonedDateTime(rawDate)
          .map(date -> {
            PerformanceReportEntity performanceReport = performanceReportDao.aggregateNumberAndValueOfPaymentsForAGivenDay(date);

            ImmutableMap<String, Object> responsePayload = ImmutableMap.of(
                    "total_volume", performanceReport.getTotalVolume(),
                    "total_amount", performanceReport.getTotalAmount(),
                    "average_amount", performanceReport.getAverageAmount());

            return ok().entity(responsePayload).build();
          })
          .orElseGet(() -> {
            return Response
              .status(Response.Status.BAD_REQUEST)
              .entity("Could not parse date: " + rawDate)
              .type(TEXT_PLAIN)
              .build();
          });
    }

    @GET
    @Path("/v1/api/reports/gateway-account-performance-report")
    @Produces(APPLICATION_JSON)
    public Response getGatewayAccountPerformanceReport() {
         Map<String, Map<String, Object>> response = performanceReportDao.aggregateNumberAndValueOfPaymentsByGatewayAccount()
                 .collect(Collectors.toMap(
                         performance -> performance.getGatewayAccountId().toString(),
                         performance -> ImmutableMap.of(
                                 "total_volume", performance.getTotalVolume(),
                                 "total_amount", performance.getTotalAmount(),
                                 "average_amount", performance.getAverageAmount(),
                                 "min_amount", performance.getMinAmount(),
                                 "max_amount", performance.getMaxAmount()
                 )));

         return ok().entity(response).build();
    }
}
