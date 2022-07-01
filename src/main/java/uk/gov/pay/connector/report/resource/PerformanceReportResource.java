package uk.gov.pay.connector.report.resource;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.report.dao.PerformanceReportDao;
import uk.gov.pay.connector.report.model.domain.PerformanceReportEntity;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.stream.Collectors;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;
import static uk.gov.pay.connector.common.validator.ApiValidators.parseZonedDateTime;

@Path("/")
@Tag(name = "Performance reports")
public class PerformanceReportResource {
    private PerformanceReportDao performanceReportDao;

    @Inject
    public PerformanceReportResource(PerformanceReportDao performanceReportDao) {
        this.performanceReportDao = performanceReportDao;
    }

    @GET
    @Path("/v1/api/reports/performance-report")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Retrieve performance summary",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "" +
                                    "  \"total_volume\": 12345," +
                                    "  \"total_amount\": 12345," +
                                    "  \"average_amount\": 1" +
                                    "}"))),
            }
    )
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
    @Operation(
            summary = "Retrieves performance summary scoped for a day",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "" +
                                    "  \"total_volume\": 12345," +
                                    "  \"total_amount\": 12345," +
                                    "  \"average_amount\": 1" +
                                    "}"))),
            }
    )
    public Response getDailyPerformanceReport(@Parameter(required = true, example = "2022-06-21T00:00:00Z")
                                              @QueryParam("date") String rawDate) {
        return parseZonedDateTime(rawDate)
                .map(date -> {
                    PerformanceReportEntity performanceReport = performanceReportDao.aggregateNumberAndValueOfPaymentsForAGivenDay(date);

                    ImmutableMap<String, Object> responsePayload = ImmutableMap.of(
                            "total_volume", performanceReport.getTotalVolume(),
                            "total_amount", performanceReport.getTotalAmount(),
                            "average_amount", performanceReport.getAverageAmount());

                    return ok().entity(responsePayload).build();
                })
                .orElseGet(() -> ResponseUtil.badRequestResponse("Could not parse date"));
    }

    @GET
    @Path("/v1/api/reports/gateway-account-performance-report")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Retrieves performance summary segmented by gateway account",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "  \"1\": {" +
                                    "    \"total_volume\": 100," +
                                    "    \"total_amount\": 1000," +
                                    "    \"average_amount\": 10," +
                                    "    \"min_amount\": 1," +
                                    "    \"max_amount\": 9" +
                                    "  }" +
                                    "}"))),
            }
    )
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
