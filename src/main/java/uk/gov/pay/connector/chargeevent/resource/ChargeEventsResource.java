package uk.gov.pay.connector.chargeevent.resource;

import com.google.inject.Inject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.ChargeEventsResponse;
import uk.gov.pay.connector.chargeevent.model.TransactionEvent;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

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
import static uk.gov.pay.connector.chargeevent.model.TransactionEvent.Type.PAYMENT;
import static uk.gov.pay.connector.chargeevent.model.TransactionEvent.Type.REFUND;
import static uk.gov.pay.connector.chargeevent.model.TransactionEvent.extractState;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;

@Path("/")
@Tag(name = "Charge events")
public class ChargeEventsResource {
    private ChargeDao chargeDao;
    private RefundDao refundDao;

    @Inject
    public ChargeEventsResource(ChargeDao chargeDao, RefundDao refundDao) {
        this.chargeDao = chargeDao;
        this.refundDao = refundDao;
    }

    @GET
    @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/events")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Get transaction history for a charge",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = ChargeEventsResponse.class)))
            }
    )
    public Response getEvents(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathParam("accountId") Long accountId,
            @Parameter(example = "2c6vtn9pth38ppbmnt20d57t49", description = "Charge external ID")
            @PathParam("chargeId") String chargeId) {

        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(this::buildEventsResponse)
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    private Response buildEventsResponse(ChargeEntity chargeEntity) {
        List<TransactionEvent> chargeTransactionEvents = normaliseChargeEvents(chargeEntity.getEvents());
        List<TransactionEvent> refundTransactionEvents = normaliseRefundEvents(
                refundDao.searchHistoryByChargeExternalId(chargeEntity.getExternalId()));

        List<TransactionEvent> allTransactionEvents = Stream
                .concat(chargeTransactionEvents.stream(), refundTransactionEvents.stream())
                .collect(Collectors.toList());

        ChargeEventsResponse responsePayload = ChargeEventsResponse.of(
                chargeEntity.getExternalId(), removeDuplicates(allTransactionEvents));

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
                        event.getChargeExternalId(),
                        event.getGatewayTransactionId(),
                        extractState(event.getStatus().toExternal()),
                        event.getAmount(),
                        event.getHistoryStartDate(),
                        event.getUserExternalId()
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
