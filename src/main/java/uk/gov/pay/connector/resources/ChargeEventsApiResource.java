package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.EventJpaDao;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeEventExternal;

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
public class ChargeEventsApiResource {

    private EventJpaDao eventDao;

    private static final Logger logger = LoggerFactory.getLogger(ChargeEventsApiResource.class);

    @Inject
    public ChargeEventsApiResource(EventJpaDao eventDao) {
        this.eventDao = eventDao;
    }

    @GET
    @Path(CHARGE_EVENTS_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getEvents(@PathParam("accountId") Long accountId, @PathParam("chargeId") Long chargeId) {
        List<ChargeEventEntity> events = eventDao.findEventsEntities(accountId, chargeId);
        List<ChargeEventExternal> eventsExternal = transformToExternalStatus(events);
        List<ChargeEventExternal> nonRepeatingExternalChargeEvents = getNonRepeatingChargeEvents(eventsExternal);

        ImmutableMap<String, Object> responsePayload = ImmutableMap.of("charge_id", chargeId, "events", nonRepeatingExternalChargeEvents);
        return ok().entity(responsePayload).build();
    }

    private List<ChargeEventExternal> transformToExternalStatus(List<ChargeEventEntity> events) {
        List<ChargeEventExternal> externalEvents = events
                .stream()
                .map(event ->
                        new ChargeEventExternal(event.getChargeEntity().getId(), mapFromStatus(event.getStatus()), event.getUpdated()))
                .collect(toList());

        return externalEvents;
    }

    private List<ChargeEventExternal> getNonRepeatingChargeEvents(List<ChargeEventExternal> externalEvents) {
        ListIterator<ChargeEventExternal> iterator = externalEvents.listIterator();
        ChargeEventExternal prev = null;
        while (iterator.hasNext()) {
            ChargeEventExternal current = iterator.next();
            if (current.equals(prev)) { // remove any immediately repeating statuses
                iterator.remove();
            }
            prev = current;
        }
        return externalEvents;
    }
}
