package uk.gov.pay.connector.refund.resource;


import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundReversalService;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

@ExtendWith(DropwizardExtensionsSupport.class)
class RefundReversalResourceTest {
    private static final ChargeService mockChargeService = mock(ChargeService.class);
    private static final GatewayAccountDao mockGatewayAccountDao = mock(GatewayAccountDao.class);
    private static final RefundReversalService mockRefundReversalService = mock(RefundReversalService.class);

    Long gatewayAccountId = 1234L;
    String externalChargeId = "a-charge-id";
    String refundExternalId = "a-refund-id";

    public static final ResourceExtension resources = ResourceExtension.builder()
            .addResource(new RefundReversalResource(mockChargeService, mockGatewayAccountDao, mockRefundReversalService))
            .build();


    @Test
    void shouldReturn200WhenRefundExistsAndProviderIsStripe() {

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withId(gatewayAccountId).build();
        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(gatewayAccountEntity));

        RefundEntity refundEntity = aValidRefundEntity()
                .withExternalId(refundExternalId)
                .withChargeExternalId(externalChargeId)
                .build();
        Refund refund = Refund.from(refundEntity);
        when(mockRefundReversalService.findMaybeHistoricRefundByRefundId(refundExternalId)).thenReturn(Optional.of(refund));

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withExternalId(externalChargeId)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
        Charge charge = Charge.from(chargeEntity);
        when(mockChargeService.findCharge(externalChargeId)).thenReturn(Optional.of(charge));

        Response response = resources
                .target("/v1/api/accounts/1234/charges/a-charge-id/refunds/a-refund-id/reverse-failed")
                .request()
                .post(Entity.json(Map.of("zendesk_ticket_id", "1223333343", "github_user_id", "87")));

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    }

    @Test
    void shouldReturn400ErrorWhenPaymentProviderIsNotStripe() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withId(gatewayAccountId).build();
        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(gatewayAccountEntity));

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.WORLDPAY.getName())
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withExternalId("a-charge-id")
                .build();
        Charge charge = Charge.from(chargeEntity);
        when(mockChargeService.findCharge("a-charge-id")).thenReturn(Optional.of(charge));

        Response response = resources
                .target("/v1/api/accounts/123/charges/a-charge-id/refunds/a-refund-id/reverse-failed")
                .request()
                .post(Entity.json(""));

        String responseBody = response.readEntity(String.class);

        assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
        assertThat(responseBody, is("Operation only available for Stripe"));
    }

    @Test
    void shouldReturn404WhenRefundDoesNotExist() {
        var wrongRefundExternalId = "a-wrong-refund";
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withId(gatewayAccountId).build();
        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(gatewayAccountEntity));
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withExternalId(externalChargeId)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
        Charge charge = Charge.from(chargeEntity);
        when(mockChargeService.findCharge(externalChargeId)).thenReturn(Optional.of(charge));

        when(mockRefundReversalService.findMaybeHistoricRefundByRefundId(wrongRefundExternalId)).thenReturn(Optional.empty());

        Response response = resources
                .target("/v1/api/accounts/1234/charges/a-charge-id/refunds/a-refund-id/reverse-failed")
                .request()
                .post(Entity.json(Map.of("zendesk_ticket_id", "1223333343", "github_user_id", "Idris")));

        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(errorResponse.getMessages(), hasItem("Refund with id [a-refund-id] not found."));
    }

    @Test
    void shouldReturn404WhenRefundDoesNotBelongToCharge() {
        var wrongChargeId = "a-wrong-charge-id";
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withId(gatewayAccountId).build();
        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(gatewayAccountEntity));
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withExternalId(externalChargeId)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
        Charge charge = Charge.from(chargeEntity);
        when(mockChargeService.findCharge(externalChargeId)).thenReturn(Optional.of(charge));

        RefundEntity refundEntity = aValidRefundEntity()
                .withChargeExternalId(wrongChargeId)
                .build();
        Refund refund = Refund.from(refundEntity);
        when(mockRefundReversalService.findMaybeHistoricRefundByRefundId(refundExternalId)).thenReturn(Optional.of(refund));


        Response response = resources
                .target("/v1/api/accounts/1234/charges/a-charge-id/refunds/a-refund-id/reverse-failed")
                .request()
                .post(Entity.json(Map.of("zendesk_ticket_id", "1223333343", "github_user_id", "Idris")));

        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(errorResponse.getMessages(), hasItem("Refund with id [a-refund-id] not found for Charge wih id [a-charge-id] and gateway account with id [1234]."));
        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
    }

    @Test
    void shouldReturn404WhenWrongGatewayAccountIsProvided() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .build();
        Charge charge = Charge.from(chargeEntity);
        when(mockChargeService.findCharge(externalChargeId)).thenReturn(Optional.of(charge));

        Response response = resources
                .target("/v1/api/accounts/0/charges/a-charge-id/refunds/a-refund-id/reverse-failed")
                .request()
                .post(Entity.json(Map.of("zendesk_ticket_id", "1223333343", "github_user_id", "87")));

        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(errorResponse.getMessages(), hasItem("Gateway Account with id [0] not found."));

    }

    @Test
    void shouldReturn404WhenChargeDoesNotBelongToGatewayAccount() {
        var wrongGatewayAccountId = 998L;

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withId(gatewayAccountId)
                .build();

        GatewayAccountEntity wrongGatewayAccountEntity = aGatewayAccountEntity()
                .withId(wrongGatewayAccountId)
                .build();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withExternalId(externalChargeId)
                .withGatewayAccountEntity(wrongGatewayAccountEntity)
                .withTransactionId(externalChargeId)
                .build();
        Charge charge = Charge.from(chargeEntity);

        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findCharge(externalChargeId)).thenReturn(Optional.of(charge));

        Response response = resources
                .target("/v1/api/accounts/1234/charges/a-charge-id/refunds/a-refund-id/reverse-failed")
                .request()
                .post(Entity.json(Map.of("zendesk_ticket_id", "1223333343", "github_user_id", "87")));

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(errorResponse.getMessages(), hasItem("Refund with id [a-refund-id] not found for Charge wih id [a-charge-id] and gateway account with id [1234]."));
    }

    @Test
    void shouldReturn404WhenChargeIsNotPresent() {

        var externalChargeId = "a-charge-id";

        when(mockChargeService.findCharge(externalChargeId)).thenReturn(Optional.empty());

        Response response = resources
                .target("/v1/api/accounts/129/charges/a-charge-id/refunds/a-refund-id/reverse-failed")
                .request()
                .post(Entity.json(Map.of("zendesk_ticket_id", "1223333343", "github_user_id", "87")));

        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
        assertThat(errorResponse.getMessages(), hasItem("Charge with id [a-charge-id] not found."));
    }
}
