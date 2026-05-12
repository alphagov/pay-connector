package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.WireMockServer;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.lang.String.format;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_ACCEPT_TERMS_OF_SERVICE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_BUSINESS_LINE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_CREATE_INDIVIDUAL_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_CREATE_LEGAL_ENTITY_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_PCI_QUESTIONNAIRE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_SIGN_PCI_QUESTIONNAIRE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_TERMS_OF_SERVICE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_TRANSFER_INSTRUMENT_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_UPDATE_LEGAL_ENTITY_RESPONSE;

public class AdyenKycMockClient extends AdyenMockClient {
    
    public AdyenKycMockClient(WireMockServer wireMockServer) {
        super(wireMockServer);
    }
    
    public void mockCreateLegalEntity() {
        String responseBody = TestTemplateResourceLoader.load(ADYEN_CREATE_LEGAL_ENTITY_RESPONSE);
        var path = "/legalEntities";
        setupPostResponse(responseBody, path, SC_OK);
    }

    public void mockUpdateLegalEntity(String legalEntityId) {
        String responseBody = TestTemplateResourceLoader.load(ADYEN_UPDATE_LEGAL_ENTITY_RESPONSE);
        var path = "/legalEntities/" + legalEntityId ;
        setupPatchResponse(responseBody, path);
    }

    private void setupPatchResponse(String responseBody, String path) {
        wireMockServer.stubFor(patch(urlPathEqualTo(path))
                .withHeader(CONTENT_TYPE, matching(APPLICATION_JSON))
                .willReturn(aResponse()
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .withStatus(org.apache.http.HttpStatus.SC_OK)
                        .withBody(responseBody)));
    }

    public void mockCreateBusinessLine() {
        String responseBody = TestTemplateResourceLoader.load(ADYEN_BUSINESS_LINE_RESPONSE);
        var path = "/businessLines";
        setupPostResponse(responseBody, path, SC_OK);
    }

    public void mockCreateTransferInstrument() {
        String responseBody = TestTemplateResourceLoader.load(ADYEN_TRANSFER_INSTRUMENT_RESPONSE);
        var path = "/transferInstruments";
        setupPostResponse(responseBody, path, SC_OK);
    }

    public void mockGetTermsOfServiceDocument(String legalEntityId) {
        String responseBody = TestTemplateResourceLoader.load(ADYEN_TERMS_OF_SERVICE_RESPONSE);
        var path = format("/legalEntities/%s/termsOfService", legalEntityId);
        setupPostResponse(responseBody, path, SC_OK);
    }

    public void mockAcceptTermsOfService(String legalEntityId, String termsOfServiceDocumentId) {
        String responseBody = TestTemplateResourceLoader.load(ADYEN_ACCEPT_TERMS_OF_SERVICE_RESPONSE);
        var path = format("/legalEntities/%s/termsOfService/%s", legalEntityId, termsOfServiceDocumentId);
        setupPatchResponse(responseBody, path);
    }

    public void mockGeneratePciQuestionnaire(String legalEntityId) {
        String responseBody = TestTemplateResourceLoader.load(ADYEN_PCI_QUESTIONNAIRE_RESPONSE);
        var path = format("/legalEntities/%s/pciQuestionnaires/generatePciTemplates", legalEntityId);
        setupPostResponse(responseBody, path, SC_OK);
    }

    public void mockSignPciTemplates(String legalEntityId) {
        String responseBody = TestTemplateResourceLoader.load(ADYEN_SIGN_PCI_QUESTIONNAIRE_RESPONSE);
        var path = format("/legalEntities/%s/pciQuestionnaires/signPciTemplates", legalEntityId);
        setupPostResponse(responseBody, path, SC_OK);
    }

    public void mockCreateIndividual() {
        String responseBody = TestTemplateResourceLoader.load(ADYEN_CREATE_INDIVIDUAL_RESPONSE);
        var path = "/legalEntities";
        setupPostResponse(responseBody, path, SC_OK);
    }
}
