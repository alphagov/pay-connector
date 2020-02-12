package uk.gov.pay.connector.expunge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.model.domain.LedgerTransactionFixture;
import uk.gov.pay.connector.paritycheck.LedgerTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class LedgerStub {

    public void returnLedgerTransaction(String externalId, DatabaseFixtures.TestCharge testCharge) throws JsonProcessingException {
        Map<String, Object> ledgerTransactionFields = testChargeToLedgerTransactionJson(testCharge);
        stubResponse(externalId, ledgerTransactionFields);
    }

    public void returnLedgerTransactionWithMismatch(String externalId, DatabaseFixtures.TestCharge testCharge) throws JsonProcessingException {
        Map<String, Object> ledgerTransactionFields = testChargeToLedgerTransactionJson(testCharge);
        ledgerTransactionFields.put("description", "This is a mismatch");
        stubResponse(externalId, ledgerTransactionFields);
    }
    
    private void stubResponse(String externalId, Map<String, Object> ledgerTransactionFields) throws JsonProcessingException {
        ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                .withStatus(200)
                .withBody(new ObjectMapper().writeValueAsString(ledgerTransactionFields));
        stubFor(
                get(urlPathEqualTo(format("/v1/transaction/%s", externalId)))
                        .withQueryParam("override_account_id_restriction", equalTo("true"))
                        .willReturn(
                                responseDefBuilder
                        )
        );
    }
    
    private static Map<String, Object> testChargeToLedgerTransactionJson(DatabaseFixtures.TestCharge testCharge)  {
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
        map.put("live", false);
        return map;
    }

}
