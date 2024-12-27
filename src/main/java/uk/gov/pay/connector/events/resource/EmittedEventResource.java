package uk.gov.pay.connector.events.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.events.EmittedEventsBackfillService;
import uk.gov.pay.connector.events.HistoricalEventEmitterService;
import uk.gov.pay.connector.tasks.RecordType;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.status;
import static uk.gov.pay.connector.tasks.RecordType.CHARGE;

@Path("/")
@Tag(name = "Tasks")
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
    @Operation(
            summary = "Sweep emitted events",
            description = "During the state transition event connector puts an event in an in-memory queue (and database) " +
                    "which is then picked up by the background process to emit the event to SQS. If the process is interrupted there" +
                    " is a database record which indicates that the event has been put in an in-memory queue, but not yet emitted to the SQS. <br>" +
                    "This task retrieves all the records that haven't been fully processed, for each event it invokes the backfill process and marks the event as processed.<br>" +
                    "The default age of the non-emitted event is at least 30 minutes. This value can be controlled with NOT_EMITTED_EVENT_MAX_AGE_IN_SECONDS environment variable.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK")
            }
    )
    @Produces(APPLICATION_JSON)
    public Response expireCharges() {
        emittedEventsBackfillService.backfillNotEmittedEvents();
        return status(OK).build();
    }

    @POST
    @Path("/v1/tasks/historical-event-emitter")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Emit events for charges or refunds",
            description = "Task to emit payment or refunds events for a given start_id and max_id range.<br>" +
                    "Historical event emitter task doesn't emit event, if event was emitted previously. To re-emit events, relevant emitted events records need to be cleared<br>." +
                    "<br>" +
                    "Note: This task runs in the background.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK")
            }
    )
    public Response emitHistoricEvents(@Parameter(example = "1", description = "Charge/Refund ID (from database) to start with to emit events. Defaults to 0")
                                       @QueryParam("start_id") Long startId,
                                       @Parameter(example = "100", description = "Charge/Refund ID until which events to be emitted. If not provided, this is set to maximum ID available.")
                                       @QueryParam("max_id") Long maybeMaxId,
                                       @Parameter(example = "charge", description = "Type of records (charge/refund) for which events to be emitted. Defaults to 'charge'")
                                       @QueryParam("record_type") Optional<RecordType> maybeRecordType,
                                       @Parameter(example = "7200", description = "Duration (in seconds) until which emitted event sweeper should ignore retrying emitting events")
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
    @Operation(
            summary = "Emit events for charges or refunds by date",
            description = "Task to emit payment and refunds events for a given start_date and end_date range.<br>" +
                    "Historical event emitter by date task doesn't emit event, if event was emitted previously. To re-emit events, relevant emitted events records need to be cleared<br>" +
                    "<br>" +
                    "Note: This task runs in the background.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK")
            }
    )
    public Response emitHistoricEventsByDate(@Parameter(example = "2016-01-25T13:23:55Z", required = true, description = "Start date of charge events or refund history events for which events to be emitted")
                                             @QueryParam("start_date") String startDate,
                                             @Parameter(example = "2016-01-25T13:23:55Z", required = true, description = "Date until which the events to be emitted")
                                             @QueryParam("end_date") String endDate,
                                             @Parameter(example = "1200", description = "Duration (in seconds) until which emitted event sweeper should ignore retrying emitting events")
                                             @QueryParam("do_not_retry_emit_until_duration") Long doNotRetryEmitUntilDuration) {
        //We run this task in the background and response 200 so the request from toolbox does not time out
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> historicalEventEmitterService.emitHistoricEventsByDate(ZonedDateTime.parse(startDate),
                ZonedDateTime.parse(endDate),
                doNotRetryEmitUntilDuration));
        return status(OK).build();
    }

}
