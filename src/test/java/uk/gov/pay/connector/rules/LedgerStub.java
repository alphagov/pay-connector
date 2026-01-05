package uk.gov.pay.connector.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.model.RefundTransactionsForPayment;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static uk.gov.pay.connector.model.domain.RefundTransactionsForPaymentFixture.aValidRefundTransactionsForPayment;
import static uk.gov.service.payments.commons.model.CommonDateTimeFormatters.ISO_INSTANT_MILLISECOND_PRECISION;

public class LedgerStub {

    private static ObjectMapper objectMapper = new ObjectMapper();
    private final WireMockServer wireMockServer;

    public LedgerStub(WireMockServer wireMockServer) {
        this.wireMockServer = wireMockServer;
    }
    
    public void acceptPostEvent() {
        wireMockServer.stubFor(
                post(urlPathEqualTo("/v1/event"))
                        .willReturn(aResponse()
                                .withStatus(202)
                        )
        );
    }

    public void returnLedgerTransaction(String externalId, DatabaseFixtures.TestCharge testCharge, DatabaseFixtures.TestFee testFee) throws JsonProcessingException {
        Map<String, Object> ledgerTransactionFields = testChargeToLedgerTransactionJson(testCharge, testFee);
        stubResponse(externalId, ledgerTransactionFields);
    }

    public void returnLedgerTransaction(String externalId, LedgerTransaction ledgerTransaction) throws JsonProcessingException {
        ResponseDefinitionBuilder response = aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                .withStatus(200)
                .withBody(objectMapper.writeValueAsString(ledgerTransaction));
        wireMockServer.stubFor(
                get(urlPathEqualTo(format("/v1/transaction/%s", externalId)))
                        .withQueryParam("override_account_id_restriction", equalTo("true"))
                        .willReturn(response)
        );
    }

    public void returnLedgerTransaction(String externalId, long gatewayAccountId, LedgerTransaction ledgerTransaction) throws JsonProcessingException {
        ResponseDefinitionBuilder response = aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                .withStatus(200)
                .withBody(objectMapper.writeValueAsString(ledgerTransaction));
        wireMockServer.stubFor(
                get(urlPathEqualTo(format("/v1/transaction/%s", externalId)))
                        .withQueryParam("account_id", equalTo(String.valueOf(gatewayAccountId)))
                        .willReturn(response)
        );
    }

    public void returnLedgerTransactionWithMismatch(String externalId, DatabaseFixtures.TestCharge testCharge, DatabaseFixtures.TestFee testFee) throws JsonProcessingException {
        Map<String, Object> ledgerTransactionFields = testChargeToLedgerTransactionJson(testCharge, testFee);
        ledgerTransactionFields.put("description", "This is a mismatch");
        stubResponse(externalId, ledgerTransactionFields);
    }

    public void returnLedgerTransactionWithMismatch(RefundEntity refund, ZonedDateTime refundCreatedEventDate) throws JsonProcessingException {
        Map<String, Object> ledgerTransactionFields = refundEntityToLedgerTransaction(refund, null, refundCreatedEventDate);
        ledgerTransactionFields.put("external_id", "This is a mismatch");
        stubResponse(refund.getExternalId(), ledgerTransactionFields);
    }

    public void returnLedgerTransaction(RefundEntity refund, Long gatewayAccountId,
                                        ZonedDateTime refundCreatedEventDate) throws JsonProcessingException {
        Map<String, Object> ledgerTransactionFields = refundEntityToLedgerTransaction(refund,
                gatewayAccountId, refundCreatedEventDate);
        stubResponse(refund.getExternalId(), ledgerTransactionFields);
    }

    public void returnNotFoundForFindByProviderAndGatewayTransactionId(String paymentProvider,
                                                                       String gatewayTransactionId) throws JsonProcessingException {
        stubResponseForProviderAndGatewayTransactionId(gatewayTransactionId, paymentProvider,
                null, 404);
    }

    public void return500ForFindByProviderAndGatewayTransactionId(String paymentProvider,
                                                                       String gatewayTransactionId) throws JsonProcessingException {
        stubResponseForProviderAndGatewayTransactionId(gatewayTransactionId, paymentProvider,
                null, 500);
    }
    
    public void returnLedgerTransactionForProviderAndGatewayTransactionId(DatabaseFixtures.TestCharge testCharge,
                                                                          String paymentProvider) throws JsonProcessingException {
        Map<String, Object> ledgerTransactionFields = testChargeToLedgerTransactionJson(testCharge, null);
        String body = null;

        objectMapper.writeValueAsString(ledgerTransactionFields);

        stubResponseForProviderAndGatewayTransactionId(testCharge.getTransactionId(),
                paymentProvider,
                ledgerTransactionFields, 200);
    }

    public void returnRefundsForPayment(String chargeExternalId, List<LedgerTransaction> refunds) throws JsonProcessingException {
        RefundTransactionsForPayment refundTransactionsForPayment = aValidRefundTransactionsForPayment()
                .withParentTransactionId(chargeExternalId)
                .withTransactions(refunds)
                .build();

        ResponseDefinitionBuilder response = aResponse()
                .withStatus(200)
                .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                .withBody(objectMapper.writeValueAsString(refundTransactionsForPayment));

        String url = format("/v1/transaction/%s/transaction", chargeExternalId);
        wireMockServer.stubFor(WireMock.get(urlPathEqualTo(url))
                .willReturn(response));
    }

    public void returnErrorForFindRefundsForPayment(String chargeExternalId) {
        ResponseDefinitionBuilder response = aResponse().withStatus(500);
        String url = format("/v1/transaction/%s/transaction", chargeExternalId);
       wireMockServer.stubFor(WireMock.get(urlPathEqualTo(url))
                .willReturn(response));

    }

    private void stubResponseForProviderAndGatewayTransactionId(String gatewayTransactionId, String paymentProvider,
                                                                Map<String, Object> ledgerTransactionFields, int status) throws JsonProcessingException {
        ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                .withStatus(status);

        if (nonNull(ledgerTransactionFields) && ledgerTransactionFields.size() > 0) {
            responseDefBuilder.withBody(objectMapper.writeValueAsString(ledgerTransactionFields));
        }
        wireMockServer.stubFor(
                get(urlPathEqualTo("/v1/transaction/gateway-transaction"))
                        .withQueryParam("payment_provider", equalTo(paymentProvider))
                        .withQueryParam("gateway_transaction_id", equalTo(gatewayTransactionId))
                        .willReturn(
                                responseDefBuilder
                        )
        );
    }

    private void stubResponse(String externalId, Map<String, Object> ledgerTransactionFields) throws JsonProcessingException {
        ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                .withStatus(200)
                .withBody(objectMapper.writeValueAsString(ledgerTransactionFields));
        wireMockServer.stubFor(
                get(urlPathEqualTo(format("/v1/transaction/%s", externalId)))
                        .withQueryParam("override_account_id_restriction", equalTo("true"))
                        .willReturn(
                                responseDefBuilder
                        )
        );
    }

    public void returnTransactionNotFound(String externalId) {
        ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                .withStatus(404);

        wireMockServer.stubFor(
                get(urlPathEqualTo(format("/v1/transaction/%s", externalId)))
                        .withQueryParam("override_account_id_restriction", equalTo("true"))
                        .willReturn(responseDefBuilder)
        );
    }

    private static Map<String, Object> testChargeToLedgerTransactionJson(DatabaseFixtures.TestCharge testCharge, DatabaseFixtures.TestFee fee) {
        var map = new HashMap<String, Object>();
        Optional.ofNullable(testCharge.getExternalChargeId()).ifPresent(value -> map.put("id", value));
        Optional.of(testCharge.getAmount()).ifPresent(value -> map.put("amount", String.valueOf(value)));
        Optional.of(testCharge.getAmount()).ifPresent(value -> map.put("total_amount", String.valueOf(value)));
        Optional.ofNullable(testCharge.getCorporateCardSurcharge()).ifPresent(value -> map.put("corporate_card_surcharge",
                String.valueOf(value)));
        Optional.ofNullable(testCharge.getChargeStatus()).ifPresent(value -> map.put("state",
                value.toExternal().getStatusV2()));
        Optional.ofNullable(testCharge.getDescription()).ifPresent(value -> map.put("description",
                value));
        Optional.ofNullable(testCharge.getReference()).ifPresent(value -> map.put("reference",
                String.valueOf(value)));
        Optional.ofNullable(testCharge.getLanguage()).ifPresent(value -> map.put("language",
                String.valueOf(value)));
        Optional.ofNullable(testCharge.getExternalChargeId()).ifPresent(value -> map.put("transaction_id",
                value));
        Optional.ofNullable(testCharge.getReturnUrl()).ifPresent(value -> map.put("return_url",
                value));
        Optional.ofNullable(testCharge.getEmail()).ifPresent(value -> map.put("email",
                value));
        Optional.ofNullable(testCharge.getCreatedDate()).ifPresent(value -> map.put("created_date",
                String.valueOf(value)));
        Optional.ofNullable(testCharge.getCardDetails()).ifPresent(value -> map.put("card_details",
                String.valueOf(value)));
        Optional.ofNullable(testCharge.getTransactionId()).ifPresent(value -> map.put("gateway_transaction_id",
                value));
        Optional.ofNullable(testCharge.getTestAccount().getAccountId()).ifPresent(account -> map.put("gateway_account_id",
                account));
        Optional.ofNullable(testCharge.getTestAccount().getPaymentProvider()).ifPresent(account -> map.put("payment_provider",
                account));
        if (fee != null) {
            map.put("fee", 0);
            Optional.of(testCharge.getAmount()).ifPresent(amount -> map.put("net_amount", amount));
            Optional.of(testCharge.getAmount()).ifPresent(amount -> map.put("total_amount", amount));
        }

        Optional.ofNullable(testCharge.getAuthorisationMode()).ifPresent(authMode -> map.put("authorisation_mode", authMode.getName()));

        ChargeResponse.RefundSummary refundSummary = new ChargeResponse.RefundSummary();
        refundSummary.setStatus("available");
        Optional.ofNullable(testCharge.getTestAccount().getPaymentProvider()).ifPresent(account -> map.put("refund_summary",
                refundSummary));

        map.put("live", false);
        return map;
    }

    private Map<String, Object> refundEntityToLedgerTransaction(RefundEntity refund, Long gatewayAccountId,
                                                                ZonedDateTime refundCreatedEventDate) {
        var map = new HashMap<String, Object>();

        Optional.ofNullable(refund.getExternalId()).ifPresent(value -> map.put("transaction_id", value));
        Optional.ofNullable(gatewayAccountId).ifPresent(value -> map.put("gateway_account_id", gatewayAccountId));
        Optional.ofNullable(refund.getChargeExternalId()).ifPresent(value -> map.put("parent_transaction_id", value));
        Optional.of(refund.getAmount()).ifPresent(value -> map.put("amount", String.valueOf(value)));
        Optional.ofNullable(refund.getGatewayTransactionId()).ifPresent(value -> map.put("gateway_transaction_id", value));
        Optional.ofNullable(refund.getUserExternalId()).ifPresent(value -> map.put("refunded_by", value));
        Optional.ofNullable(refund.getUserEmail()).ifPresent(value -> map.put("refunded_by_user_email", value));
        Optional.ofNullable(refundCreatedEventDate).ifPresent(value -> map.put("created_date",
                ISO_INSTANT_MILLISECOND_PRECISION.format(value)));

        Optional.ofNullable(refund.getStatus()).ifPresent(value -> map.put("state",
                refund.getStatus().toExternal()));

        return map;
    }
}
