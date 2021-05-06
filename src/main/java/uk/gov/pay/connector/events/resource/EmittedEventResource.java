package uk.gov.pay.connector.events.resource;

import uk.gov.pay.connector.events.EmittedEventsBackfillService;
import uk.gov.pay.connector.events.HistoricalEventEmitterService;
import uk.gov.pay.connector.tasks.RecordType;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.status;
import static uk.gov.pay.connector.tasks.RecordType.CHARGE;

@Path("/")
public class EmittedEventResource {

    private final EmittedEventsBackfillService emittedEventsBackfillService;
    private final HistoricalEventEmitterService historicalEventEmitterService;

    @Inject
    public EmittedEventResource(EmittedEventsBackfillService emittedEventsBackfillService,
                                HistoricalEventEmitterService historicalEventEmitterService) {
        this.emittedEventsBackfillService = emittedEventsBackfillService;
        this.historicalEventEmitterService = historicalEventEmitterService;
    }

    @POST
    @Path("/v1/tasks/emitted-events-sweep")
    @Produces(APPLICATION_JSON)
    public Response expireCharges() {
        emittedEventsBackfillService.backfillNotEmittedEvents();
        return status(OK).build();
    }

    @POST
    @Path("/v1/tasks/historical-event-emitter")
    @Produces(APPLICATION_JSON)
    public Response emitHistoricEvents(@QueryParam("start_id") Long startId,
                                       @QueryParam("max_id") Long maybeMaxId,
                                       @QueryParam("record_type") Optional<RecordType> maybeRecordType,
                                       @QueryParam("do_not_retry_emit_until_duration") Long doNotRetryEmitUntilDuration) {
        //We run this task in the background and response 200 so the request from toolbox does not time out
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RecordType recordType = maybeRecordType.orElse(CHARGE);
        if (recordType == CHARGE) {
            executor.execute(() -> historicalEventEmitterService.emitHistoricEventsById(startId,
                    OptionalLong.of(maybeMaxId),
                    doNotRetryEmitUntilDuration));
        } else {
            executor.execute(() -> historicalEventEmitterService.emitRefundEventsOnlyById(startId,
                    OptionalLong.of(maybeMaxId),
                    doNotRetryEmitUntilDuration));
        }
        return status(OK).build();
    }

    @POST
    @Path("/v1/tasks/historical-event-emitter-by-date")
    @Produces(APPLICATION_JSON)
    public Response emitHistoricEventsByDate(@QueryParam("start_date") String startDate,
                                             @QueryParam("end_date") String endDate,
                                             @QueryParam("do_not_retry_emit_until_duration") Long doNotRetryEmitUntilDuration) {
        //We run this task in the background and response 200 so the request from toolbox does not time out
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> historicalEventEmitterService.emitHistoricEventsByDate(ZonedDateTime.parse(startDate),
                ZonedDateTime.parse(endDate),
                doNotRetryEmitUntilDuration));
        return status(OK).build();
    }

}
