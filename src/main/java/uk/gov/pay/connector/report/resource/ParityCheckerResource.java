package uk.gov.pay.connector.report.resource;

import uk.gov.pay.connector.report.ParityCheckerService;

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
import static javax.ws.rs.core.Response.Status.OK;

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
                                @QueryParam("parity_check_status") String mayBeParityCheckStatus,
                                @QueryParam("do_not_retry_emit_until") Long doNotRetryEmitUntilDuration){
        //We run this task in the background and respond 200 so the request from toolbox does not time out
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> parityCheckerService.checkParity(startId, Optional.of(maybeMaxId), doNotReprocessValidRecords,
                Optional.ofNullable(mayBeParityCheckStatus), doNotRetryEmitUntilDuration));
        return Response.status(OK).build();
    }
}
