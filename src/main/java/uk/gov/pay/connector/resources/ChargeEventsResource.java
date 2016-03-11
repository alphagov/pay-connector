package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import uk.gov.pay.connector.dao.EventDao;
import uk.gov.pay.connector.model.ChargeEvent;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.ListIterator;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.mapFromStatus;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_EVENTS_API_PATH;

@Path("/")
public class ChargeEventsResource {

    private EventDao eventDao;

    @Inject
    public ChargeEventsResource(EventDao eventDao) {
        this.eventDao = eventDao;
    }

    @GET
    @Path(CHARGE_EVENTS_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getEvents(@PathParam("accountId") Long accountId, @PathParam("chargeId") Long chargeId) {
        List<ChargeEventEntity> events = eventDao.findEvents(accountId, chargeId);
        List<ChargeEvent> eventsExternal = transformToExternalStatus(events);
        List<ChargeEvent> nonRepeatingExternalChargeEvents = getNonRepeatingChargeEvents(eventsExternal);

        ImmutableMap<String, Object> responsePayload = ImmutableMap.of("charge_id", chargeId, "events", nonRepeatingExternalChargeEvents);
        return ok().entity(responsePayload).build();
    }

    private List<ChargeEvent> transformToExternalStatus(List<ChargeEventEntity> events) {
        List<ChargeEvent> externalEvents = events
                .stream()
                .map(event ->
                        new ChargeEvent(event.getChargeEntity().getId(), mapFromStatus(event.getStatus()), event.getUpdated()))
                .collect(toList());

        return externalEvents;
    }

    private List<ChargeEvent> getNonRepeatingChargeEvents(List<ChargeEvent> externalEvents) {
        ListIterator<ChargeEvent> iterator = externalEvents.listIterator();
        ChargeEvent prev = null;
        while (iterator.hasNext()) {
            ChargeEvent current = iterator.next();
            if (current.equals(prev)) { // remove any immediately repeating statuses
                iterator.remove();
            }
            prev = current;
        }
        return externalEvents;
    }
}
