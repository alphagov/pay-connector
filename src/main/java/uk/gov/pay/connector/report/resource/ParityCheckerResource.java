package uk.gov.pay.connector.report.resource;

import uk.gov.pay.connector.report.ParityCheckerService;
import uk.gov.pay.connector.tasks.RecordType;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.status;
import static uk.gov.pay.connector.tasks.RecordType.CHARGE;

@Path("/")
public class ParityCheckerResource {

    private final ParityCheckerService parityCheckerService;

    @Inject
    public ParityCheckerResource(ParityCheckerService parityCheckerService) {
        this.parityCheckerService = parityCheckerService;
    }

    @POST
    @Path("/v1/tasks/parity-checker")
    @Produces(APPLICATION_JSON)
    public Response parityCheck(@QueryParam("start_id") Long startId,
                                @QueryParam("max_id") Long maybeMaxId,
                                @QueryParam("do_not_reprocess_valid_records") boolean doNotReprocessValidRecords,
                                @QueryParam("parity_check_status") String maybeParityCheckStatus,
                                @QueryParam("do_not_retry_emit_until") Long doNotRetryEmitUntilDuration,
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
