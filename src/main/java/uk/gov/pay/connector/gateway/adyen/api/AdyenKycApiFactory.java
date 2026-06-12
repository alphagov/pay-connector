package uk.gov.pay.connector.gateway.adyen.api;

import com.adyen.Client;
import com.adyen.service.legalentitymanagement.BusinessLinesApi;
import com.adyen.service.legalentitymanagement.LegalEntitiesApi;
import com.adyen.service.legalentitymanagement.PciQuestionnairesApi;
import com.adyen.service.legalentitymanagement.TermsOfServiceApi;
import com.adyen.service.legalentitymanagement.TransferInstrumentsApi;
import jakarta.inject.Inject;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;

import java.util.Objects;

import static com.adyen.enums.Environment.TEST;

public class AdyenKycApiFactory {

    private final PciQuestionnairesApi pciQuestionnairesApi;
    private final TermsOfServiceApi termsOfServiceApi;
    private final LegalEntitiesApi legalEntitiesApi;
    private final BusinessLinesApi businessLinesApi;
    private final TransferInstrumentsApi transferInstrumentsApi;
    
    @Inject
    public AdyenKycApiFactory(AdyenGatewayConfig adyenGatewayConfig) {
        Client legalEntityManagementApiClient = new com.adyen.Client(adyenGatewayConfig.getApiKeys().legalEntityManagement().test(), TEST);
        
        pciQuestionnairesApi = createPciQuestionnairesApi(legalEntityManagementApiClient, adyenGatewayConfig);
        termsOfServiceApi = createTermsOfServiceApi(legalEntityManagementApiClient, adyenGatewayConfig);
        legalEntitiesApi = createLegalEntitiesApi(legalEntityManagementApiClient, adyenGatewayConfig);
        businessLinesApi = createBusinessLinesApi(legalEntityManagementApiClient, adyenGatewayConfig);
        transferInstrumentsApi = createTransferInstrumentsApi(legalEntityManagementApiClient, adyenGatewayConfig);
    }

    private PciQuestionnairesApi createPciQuestionnairesApi(Client client, AdyenGatewayConfig adyenGatewayConfig) {
        String legalEntityManagementBaseUrl = adyenGatewayConfig.getBaseUrls().legalEntityManagement().test();

        if (Objects.isNull(legalEntityManagementBaseUrl)) {
            return new PciQuestionnairesApi(client);
        }
        return new PciQuestionnairesApi(client, legalEntityManagementBaseUrl);
    }

    private TermsOfServiceApi createTermsOfServiceApi(Client client, AdyenGatewayConfig adyenGatewayConfig) {
        String legalEntityManagementBaseUrl = adyenGatewayConfig.getBaseUrls().legalEntityManagement().test();

        if (Objects.isNull(legalEntityManagementBaseUrl)) {
            return new TermsOfServiceApi(client);
        }
        return new TermsOfServiceApi(client, legalEntityManagementBaseUrl);
    }

    private LegalEntitiesApi createLegalEntitiesApi(Client client, AdyenGatewayConfig adyenGatewayConfig) {
        String legalEntityManagementBaseUrl = adyenGatewayConfig.getBaseUrls().legalEntityManagement().test();

        if (Objects.isNull(legalEntityManagementBaseUrl)) {
            return new LegalEntitiesApi(client);
        }
        return new LegalEntitiesApi(client, legalEntityManagementBaseUrl);
    }

    private BusinessLinesApi createBusinessLinesApi(Client client, AdyenGatewayConfig adyenGatewayConfig) {
        String legalEntityManagementBaseUrl = adyenGatewayConfig.getBaseUrls().legalEntityManagement().test();

        if (Objects.isNull(legalEntityManagementBaseUrl)) {
            return new BusinessLinesApi(client);
        }
        return new BusinessLinesApi(client, legalEntityManagementBaseUrl);
    }

    private TransferInstrumentsApi createTransferInstrumentsApi(Client client, AdyenGatewayConfig adyenGatewayConfig) {
        String legalEntityManagementBaseUrl = adyenGatewayConfig.getBaseUrls().legalEntityManagement().test();

        if (Objects.isNull(legalEntityManagementBaseUrl)) {
            return new TransferInstrumentsApi(client);
        }
        return new TransferInstrumentsApi(client, legalEntityManagementBaseUrl);
    }

    public PciQuestionnairesApi getPciQuestionnairesApi() {
        return pciQuestionnairesApi;
    }

    public TermsOfServiceApi getTermsOfServiceApi() {
        return termsOfServiceApi;
    }

    public LegalEntitiesApi getLegalEntitiesApi() {
        return legalEntitiesApi;
    }

    public BusinessLinesApi getBusinessLinesApi() {
        return businessLinesApi;
    }

    public TransferInstrumentsApi getTransferInstrumentsApi() {
        return transferInstrumentsApi;
    }
}
