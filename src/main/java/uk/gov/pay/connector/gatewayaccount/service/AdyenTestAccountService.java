package uk.gov.pay.connector.gatewayaccount.service;

import com.adyen.Client;
import com.adyen.model.balanceplatform.AccountHolder;
import com.adyen.model.balanceplatform.AccountHolderInfo;
import com.adyen.model.balanceplatform.BalanceAccount;
import com.adyen.model.balanceplatform.BalanceAccountInfo;
import com.adyen.model.legalentitymanagement.AcceptTermsOfServiceRequest;
import com.adyen.model.legalentitymanagement.Address;
import com.adyen.model.legalentitymanagement.BankAccountInfo;
import com.adyen.model.legalentitymanagement.BankAccountInfoAccountIdentification;
import com.adyen.model.legalentitymanagement.BirthData;
import com.adyen.model.legalentitymanagement.BusinessLine;
import com.adyen.model.legalentitymanagement.BusinessLineInfo;
import com.adyen.model.legalentitymanagement.GeneratePciDescriptionRequest;
import com.adyen.model.legalentitymanagement.GetTermsOfServiceDocumentRequest;
import com.adyen.model.legalentitymanagement.GetTermsOfServiceDocumentResponse;
import com.adyen.model.legalentitymanagement.Individual;
import com.adyen.model.legalentitymanagement.LegalEntity;
import com.adyen.model.legalentitymanagement.LegalEntityAssociation;
import com.adyen.model.legalentitymanagement.LegalEntityInfo;
import com.adyen.model.legalentitymanagement.LegalEntityInfoRequiredType;
import com.adyen.model.legalentitymanagement.Name;
import com.adyen.model.legalentitymanagement.Organization;
import com.adyen.model.legalentitymanagement.PciSigningRequest;
import com.adyen.model.legalentitymanagement.PhoneNumber;
import com.adyen.model.legalentitymanagement.TaxInformation;
import com.adyen.model.legalentitymanagement.TransferInstrumentInfo;
import com.adyen.model.legalentitymanagement.UKLocalAccountIdentification;
import com.adyen.model.legalentitymanagement.WebData;
import com.adyen.model.management.PaymentMethodSetupInfo;
import com.adyen.model.management.Store;
import com.adyen.model.management.StoreCreationWithMerchantCodeRequest;
import com.adyen.model.management.StoreLocation;
import com.adyen.service.balanceplatform.AccountHoldersApi;
import com.adyen.service.balanceplatform.BalanceAccountsApi;
import com.adyen.service.exception.ApiException;
import com.adyen.service.legalentitymanagement.BusinessLinesApi;
import com.adyen.service.legalentitymanagement.LegalEntitiesApi;
import com.adyen.service.legalentitymanagement.PciQuestionnairesApi;
import com.adyen.service.legalentitymanagement.TermsOfServiceApi;
import com.adyen.service.legalentitymanagement.TransferInstrumentsApi;
import com.adyen.service.management.AccountStoreLevelApi;
import com.adyen.service.management.PaymentMethodsMerchantLevelApi;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.adyen.enums.Environment.TEST;
import static com.adyen.model.legalentitymanagement.UKLocalAccountIdentification.TypeEnum.UKLOCAL;
import static org.apache.http.HttpStatus.SC_BAD_GATEWAY;

public class AdyenTestAccountService {

    private final Client legalEntityManagementApiClient;
    private final Client companyApiClient;
    private final Client balancePlatformApiClient;
    private final String balancePlatformTestUrl;
    private final String legalEntityManagementTestUrl;
    private final String managementTestUrl;
    private final AdyenGatewayConfig adyenGatewayConfig;

    @Inject
    public AdyenTestAccountService(AdyenGatewayConfig adyenGatewayConfig) {
        legalEntityManagementApiClient = new Client(adyenGatewayConfig.getApiKeys().legalEntityManagement().test(), TEST);
        companyApiClient = new Client(adyenGatewayConfig.getApiKeys().companyAccount().test(), TEST);
        balancePlatformApiClient = new Client(adyenGatewayConfig.getApiKeys().balancePlatform().test(), TEST);
        balancePlatformTestUrl = adyenGatewayConfig.getBaseUrls().balancePlatform().test();
        legalEntityManagementTestUrl = adyenGatewayConfig.getBaseUrls().legalEntityManagement().test();
        managementTestUrl = adyenGatewayConfig.getBaseUrls().management().test();
        this.adyenGatewayConfig = adyenGatewayConfig;
    }

    public AdyenCredentials createTestAccount(String serviceName) {
        String legalEntityId = createOrganisation(serviceName);
        String businessLineId = createBusinessLine(legalEntityId);

        createAndLinkAssociateLegalEntity(legalEntityId);
        createBankAccount(legalEntityId);

        String merchantAccountIdTest = adyenGatewayConfig.getMerchantAccountIds().test();
        String storeId = createStore(merchantAccountIdTest, legalEntityId, businessLineId);
        addPaymentMethodsToStore(merchantAccountIdTest, storeId, businessLineId);

        String balancePlatformIdTest = adyenGatewayConfig.getBalancePlatformIds().test();
        String accountHolderId = createAccountHolder(legalEntityId, balancePlatformIdTest, serviceName);
        String balanceAccountId = createBalanceAccount(accountHolderId, serviceName);

        acceptTermsOfService(legalEntityId);
        signPciQuestionnaire(legalEntityId);

        return new AdyenCredentials(legalEntityId, storeId, accountHolderId, balanceAccountId);
    }

    private void signPciQuestionnaire(String legalEntityId) {
        try {
            PciQuestionnairesApi pciQuestionnairesApi = new PciQuestionnairesApi(legalEntityManagementApiClient, legalEntityManagementTestUrl);

            GeneratePciDescriptionRequest generatePciDescriptionRequest = new GeneratePciDescriptionRequest()
                    .language("en");

            var pciTemplateReferences =
                    pciQuestionnairesApi.generatePciQuestionnaire(legalEntityId, generatePciDescriptionRequest)
                            .getPciTemplateReferences();

            PciSigningRequest pciSigningRequest = new PciSigningRequest()
                    .pciTemplateReferences(pciTemplateReferences);

            pciQuestionnairesApi.signPciQuestionnaire(legalEntityId, pciSigningRequest);

        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error generating or submitting signed PCI questionnaire", e);
        }
    }

    private void acceptTermsOfService(String legalEntityId) {
        try {
            TermsOfServiceApi termsOfServiceApi = new TermsOfServiceApi(legalEntityManagementApiClient, legalEntityManagementTestUrl);

            GetTermsOfServiceDocumentRequest getTermsOfServiceDocumentRequest = new GetTermsOfServiceDocumentRequest()
                    .type(GetTermsOfServiceDocumentRequest.TypeEnum.ADYENFORPLATFORMSADVANCED)
                    .language("en");

            GetTermsOfServiceDocumentResponse getTermsOfServiceDocumentResponse = termsOfServiceApi
                    .getTermsOfServiceDocument(legalEntityId, getTermsOfServiceDocumentRequest);
            AcceptTermsOfServiceRequest acceptTermsOfServiceRequest = new AcceptTermsOfServiceRequest()
                    .acceptedBy(legalEntityId);

            termsOfServiceApi.acceptTermsOfService(legalEntityId, getTermsOfServiceDocumentResponse.
                    getTermsOfServiceDocumentId(), acceptTermsOfServiceRequest);

        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error getting or accepting terms of service", e);
        }
    }

    private String createBalanceAccount(String accountHolderId, String serviceName) {
        try {
            BalanceAccountInfo balanceAccountInfo = new BalanceAccountInfo()
                    .accountHolderId(accountHolderId)
                    .description(String.format("Balance account for '%s' service", serviceName))
                    .defaultCurrencyCode("GBP")
                    .timeZone("Europe/London");

            BalanceAccountsApi balanceAccountsApi = new BalanceAccountsApi(balancePlatformApiClient, balancePlatformTestUrl);
            BalanceAccount balanceAccount = balanceAccountsApi.createBalanceAccount(balanceAccountInfo);

            return balanceAccount.getId();
        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error creating BalanceAccount", e);
        }
    }

    private String createAccountHolder(String legalEntityId, String balancePlatformId, String serviceName) {
        try {
            AccountHolderInfo accountHolderInfo = new AccountHolderInfo()
                    .reference(serviceName)
                    .legalEntityId(legalEntityId)
                    .balancePlatform(balancePlatformId)
                    .description(String.format("Liable account holder used for %s", serviceName));

            AccountHoldersApi accountHoldersApi = new AccountHoldersApi(balancePlatformApiClient, balancePlatformTestUrl);
            AccountHolder accountHolder = accountHoldersApi.createAccountHolder(accountHolderInfo, null);
            return accountHolder.getId();
        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error creating account holder", e);
        }
    }

    private String createOrganisation(String serviceName) {
        try {
            TaxInformation taxInformation = new TaxInformation()
                    .country("GB")
                    .number("12345678901")
                    .type("ABN");
            Organization organization = new Organization()
                    .legalName(serviceName)
                    .doingBusinessAs(serviceName)
                    .registrationNumber("34179503")
                    .vatNumber("12345678")
                    .taxInformation(List.of(taxInformation))
                    .registeredAddress(buildAddress())
                    .type(Organization.TypeEnum.GOVERNMENTALORGANIZATION)
                    .email("organization@example.com");

            LegalEntityInfoRequiredType legalEntityInfoRequiredType = new LegalEntityInfoRequiredType()
                    .organization(organization)
                    .type(LegalEntityInfoRequiredType.TypeEnum.ORGANIZATION);

            LegalEntitiesApi legalEntitiesApi = new LegalEntitiesApi(legalEntityManagementApiClient, legalEntityManagementTestUrl);
            LegalEntity response = legalEntitiesApi.createLegalEntity(legalEntityInfoRequiredType, null);

            return response.getId();
        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error creating Adyen test account", e, SC_BAD_GATEWAY);
        }
    }

    private static Address buildAddress() {
        return new Address()
                .country("GB")
                .city("London")
                .street("10 Park Row")
                .postalCode("AB1 2BC");
    }

    private String createBusinessLine(String legalEntityId) {
        try {
            WebData webData = new WebData()
                    .webAddress("https://gov.uk");

            BusinessLineInfo businessLineInfo = new BusinessLineInfo()
                    .salesChannels(Arrays.asList("eCommerce", "ecomMoto"))
                    .legalEntityId(legalEntityId)
                    .service(BusinessLineInfo.ServiceEnum.PAYMENTPROCESSING)
                    .webData(Collections.singletonList(webData))
                    .industryCode("9399");

            BusinessLinesApi businessLinesApi = new BusinessLinesApi(legalEntityManagementApiClient, legalEntityManagementTestUrl);
            BusinessLine businessLine = businessLinesApi.createBusinessLine(businessLineInfo, null);
            return businessLine.getId();
        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error creating business line for Adyen test account", e);
        }
    }

    private void createAndLinkAssociateLegalEntity(String legalEntityId) {
        try {
            PhoneNumber phoneNumber = new PhoneNumber()
                    .number("+31858888138")
                    .type("mobile");

            Name name = new Name()
                    .firstName("Shelly")
                    .lastName("Eller");

            BirthData birthData = new BirthData()
                    .dateOfBirth("1990-06-21");

            Individual individual = new Individual()
                    .phone(phoneNumber)
                    .residentialAddress(buildAddress())
                    .name(name)
                    .birthData(birthData)
                    .email("s.eller@example.com");

            LegalEntityInfoRequiredType legalEntityInfoRequiredType = new LegalEntityInfoRequiredType()
                    .individual(individual)
                    .type(LegalEntityInfoRequiredType.TypeEnum.INDIVIDUAL);

            LegalEntitiesApi legalEntitiesApi = new LegalEntitiesApi(legalEntityManagementApiClient, legalEntityManagementTestUrl);
            LegalEntity legalEntityIndividual = legalEntitiesApi.createLegalEntity(legalEntityInfoRequiredType, null);

            LegalEntityAssociation legalEntityAssociationSRO = new LegalEntityAssociation()
                    .legalEntityId(legalEntityIndividual.getId())
                    .jobTitle("CEO")
                    .type(LegalEntityAssociation.TypeEnum.UBOTHROUGHCONTROL);
            LegalEntityAssociation legalEntityAssociationDirector = new LegalEntityAssociation()
                    .legalEntityId(legalEntityIndividual.getId())
                    .jobTitle("Director")
                    .type(LegalEntityAssociation.TypeEnum.DIRECTOR);

            LegalEntityInfo legalEntityInfo = new LegalEntityInfo()
                    .entityAssociations(Arrays.asList(legalEntityAssociationSRO, legalEntityAssociationDirector));

            legalEntitiesApi.updateLegalEntity(legalEntityId, legalEntityInfo, null);
        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error associating individuals to legal entity", e);
        }
    }

    private String createStore(String merchantAccountId, String serviceName, String businessLineId) {
        try {
            AccountStoreLevelApi accountStoreLevelApi = new AccountStoreLevelApi(companyApiClient, managementTestUrl);

            StoreCreationWithMerchantCodeRequest request = new StoreCreationWithMerchantCodeRequest()
                    .merchantId(merchantAccountId)
                    .description(serviceName)
                    .shopperStatement("TEST DESCRIPTOR")
                    .phoneNumber("+13123456789")
                    .address(new StoreLocation().country("GB")
                            .city("London")
                            .line1("10 Park Row")
                            .postalCode("AB1 2BC"))
                    .businessLineIds(List.of(businessLineId));

            Store store = accountStoreLevelApi.createStore(request);
            return store.getId();
        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error creating store", e);
        }
    }

    private void addPaymentMethodsToStore(String merchantAccountIdTest, String storeId, String businessLineId) {
        PaymentMethodsMerchantLevelApi paymentMethodsMerchantLevelApi = 
                new PaymentMethodsMerchantLevelApi(companyApiClient, managementTestUrl);
        var paymentTypes = List.of(
                PaymentMethodSetupInfo.TypeEnum.VISA,
                PaymentMethodSetupInfo.TypeEnum.MC,
                PaymentMethodSetupInfo.TypeEnum.AMEX,
                PaymentMethodSetupInfo.TypeEnum.JCB,
                PaymentMethodSetupInfo.TypeEnum.MAESTRO,
                PaymentMethodSetupInfo.TypeEnum.DISCOVER,
                PaymentMethodSetupInfo.TypeEnum.DINERS,
                PaymentMethodSetupInfo.TypeEnum.CUP, // China Union Pay
                PaymentMethodSetupInfo.TypeEnum.PAYBYBANK,
                PaymentMethodSetupInfo.TypeEnum.APPLEPAY,
                PaymentMethodSetupInfo.TypeEnum.GOOGLEPAY);
        
        for (PaymentMethodSetupInfo.TypeEnum paymentType : paymentTypes) {
            var paymentMethodSetupInfo = generatePaymentMethodSetupInfo(storeId, businessLineId, paymentType);
            try {
                paymentMethodsMerchantLevelApi.requestPaymentMethod(merchantAccountIdTest, paymentMethodSetupInfo);
            } catch (ApiException | IOException e) {
                throw new WebApplicationException("Error adding payment method " + paymentType, e);
            }
        }
    }

    private static PaymentMethodSetupInfo generatePaymentMethodSetupInfo(String storeId, 
                                                                         String businessLineId,
                                                                         PaymentMethodSetupInfo.TypeEnum paymentMethodSetupType) {
        return new PaymentMethodSetupInfo()
                .countries(List.of("GB"))
                .type(paymentMethodSetupType)
                .businessLineId(businessLineId)
                .storeIds(Collections.singletonList(storeId))
                .currencies(List.of("GBP"));
    }

    private void createBankAccount(String legalEntityId) {
        try {
            UKLocalAccountIdentification ukLocalAccountIdentification = new UKLocalAccountIdentification()
                    .accountNumber("12345678")
                    .sortCode("123456")
                    .type(UKLOCAL);
            BankAccountInfo bankAccountInfo = new BankAccountInfo()
                    .accountIdentification(new BankAccountInfoAccountIdentification(ukLocalAccountIdentification));

            TransferInstrumentInfo transferInstrumentInfo = new TransferInstrumentInfo()
                    .bankAccount(bankAccountInfo)
                    .legalEntityId(legalEntityId)
                    .type(TransferInstrumentInfo.TypeEnum.BANKACCOUNT);

            TransferInstrumentsApi transferInstrumentsApi = 
                    new TransferInstrumentsApi(legalEntityManagementApiClient, legalEntityManagementTestUrl);
            transferInstrumentsApi.createTransferInstrument(transferInstrumentInfo, null);
        } catch (IOException | ApiException e) {
            throw new WebApplicationException("Error creating bank account for Adyen test account", e);
        }
    }
}
