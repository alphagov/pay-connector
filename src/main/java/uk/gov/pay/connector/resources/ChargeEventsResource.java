package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.model.TransactionEvent;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.RefundHistory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;
import static uk.gov.pay.connector.model.TransactionEvent.Type.PAYMENT;
import static uk.gov.pay.connector.model.TransactionEvent.Type.REFUND;
import static uk.gov.pay.connector.model.TransactionEvent.extractState;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_EVENTS_API_PATH;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;

@Path("/")
public class ChargeEventsResource {
    private ChargeDao chargeDao;
    private RefundDao refundDao;

    @Inject
    public ChargeEventsResource(ChargeDao chargeDao, RefundDao refundDao) {
        this.chargeDao = chargeDao;
        this.refundDao = refundDao;
    }

    @GET
    @Path(CHARGE_EVENTS_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getEvents(@PathParam("accountId") Long accountId, @PathParam("chargeId") String chargeId) {

        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> buildEventsResponse(chargeEntity))
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    private Response buildEventsResponse(ChargeEntity chargeEntity) {
        List<TransactionEvent> chargeTransactionEvents = normaliseChargeEvents(chargeEntity.getEvents());
        List<TransactionEvent> refundTransactionEvents = normaliseRefundEvents(
                refundDao.searchHistoryByChargeId(chargeEntity.getId()));

        List<TransactionEvent> allTransactionEvents = Stream
                .concat(chargeTransactionEvents.stream(), refundTransactionEvents.stream())
                .collect(Collectors.toList());

        ImmutableMap<String, Object> responsePayload = ImmutableMap.of(
                "charge_id", chargeEntity.getExternalId(),
                "events", removeDuplicates(allTransactionEvents));
        return ok().entity(responsePayload).build();
    }

    private List<TransactionEvent> normaliseChargeEvents(List<ChargeEventEntity> events) {
        return events.stream()
                .map(event -> new TransactionEvent(
                        PAYMENT,
                        event.getChargeEntity().getExternalId(),
                        extractState(event.getStatus().toExternal()),
                        event.getChargeEntity().getAmount(),
                        event.getUpdated()
                ))
                        .collect(toList());
    }

    private List<TransactionEvent> normaliseRefundEvents(List<RefundHistory> events) {
        return events.stream()
                .map(event -> new TransactionEvent(
                        REFUND,
                        event.getChargeEntity().getExternalId(),
                        event.getReference(),
                        extractState(event.getStatus().toExternal()),
                        event.getAmount(),
                        event.getHistoryStartDate()
                ))
                .collect(toList());
    }

    private List<TransactionEvent> removeDuplicates(List<TransactionEvent> events) {
        return events.stream()
                .sorted()
                .distinct()
                .collect(toList());
    }
}
