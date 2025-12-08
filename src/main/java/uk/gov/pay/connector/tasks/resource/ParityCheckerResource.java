package uk.gov.pay.connector.tasks.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.tasks.RecordType;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import uk.gov.pay.connector.tasks.service.ParityCheckerService;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.OK;
import static uk.gov.pay.connector.tasks.RecordType.CHARGE;

@Path("/")
@Tag(name = "Tasks")
public class ParityCheckerResource {

    private final ParityCheckerService parityCheckerService;

    @Inject
    public ParityCheckerResource(ParityCheckerService parityCheckerService) {
        this.parityCheckerService = parityCheckerService;
    }

    @POST
    @Path("/v1/tasks/parity-checker")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Parity check charges or refunds with ledger",
            description = "Task to parity check charges or refunds with ledger for a given start_id and max_id range or by parity_check_status." +
                    " Parity checker compares fields of ledger transaction to charge/refund record in connector. <br>" +
                    "When parity check fails, new events are emitted even when the events have been emitted previously. <br>" +
                    " Note: Task is executed in the background. ",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public Response parityCheck(@Parameter(example = "1", description = "Charge/Refund ID (from database) to start with for parity checking. Defaults to 0")
                                @QueryParam("start_id") Long startId,
                                @Parameter(example = "10", description = "Charge/Refund ID until which the records to be parity checked. If not provided, this is set to maximum ID available.")
                                @QueryParam("max_id") Long maybeMaxId,
                                @Parameter(example = "true", description = "Set to true to skip parity checking the records which were previously parity checked and matches with ledger transaction. Defaults to false")
                                @QueryParam("do_not_reprocess_valid_records") boolean doNotReprocessValidRecords,
                                @Parameter(example = "DATA_MISMATCH", description = "Parity check the records, which were parity checked and marked with parity_check_status. " +
                                        "start_id and max_id are ignored if parity checking by parity check status")
                                @QueryParam("parity_check_status") String maybeParityCheckStatus,
                                @Parameter(example = "7200", description = "Duration (in seconds) until which emitted event sweeper should ignore retrying emitting events")
                                @QueryParam("do_not_retry_emit_until") Long doNotRetryEmitUntilDuration,
                                @Parameter(example = "charge", description = "Type of records (charge/refund) to be parity checked. Defaults to 'charge'")
                                @QueryParam("record_type") Optional<RecordType> maybeRecordType) {
        //We run this task in the background and respond 200 so the request from toolbox does not time out
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RecordType recordType = maybeRecordType.orElse(CHARGE);
        if (recordType == CHARGE) {
            executor.execute(() -> parityCheckerService.checkParity(startId, Optional.of(maybeMaxId), doNotReprocessValidRecords,
                    Optional.ofNullable(maybeParityCheckStatus), doNotRetryEmitUntilDuration));
        } else {
            executor.execute(() -> parityCheckerService.checkParityForRefundsOnly(startId, Optional.of(maybeMaxId).orElse(null), doNotReprocessValidRecords,
                    Optional.ofNullable(maybeParityCheckStatus).orElse(null), doNotRetryEmitUntilDuration));
        }
        return Response.status(OK).build();
    }
}
