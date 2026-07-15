package uk.gov.pay.connector.gatewayaccount.service;

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
import com.adyen.model.legalentitymanagement.FinancialReport;
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
import com.adyen.model.legalentitymanagement.Support;
import com.adyen.model.legalentitymanagement.TransferInstrumentInfo;
import com.adyen.model.legalentitymanagement.UKLocalAccountIdentification;
import com.adyen.model.legalentitymanagement.WebData;
import com.adyen.model.management.PaymentMethodSetupInfo;
import com.adyen.model.management.Store;
import com.adyen.model.management.StoreCreationWithMerchantCodeRequest;
import com.adyen.model.management.StoreLocation;
import com.adyen.service.exception.ApiException;
import com.adyen.service.legalentitymanagement.LegalEntitiesApi;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.adyen.api.AdyenBalancePlatformApiFactory;
import uk.gov.pay.connector.gateway.adyen.api.AdyenCompanyAccountApiFactory;
import uk.gov.pay.connector.gateway.adyen.api.AdyenKycApiFactory;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.adyen.model.legalentitymanagement.UKLocalAccountIdentification.TypeEnum.UKLOCAL;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.hc.core5.http.HttpStatus.SC_BAD_GATEWAY;

public class AdyenTestAccountService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenTestAccountService.class);

    private final AdyenGatewayConfig adyenGatewayConfig;
    private final AdyenBalancePlatformApiFactory adyenBalancePlatformApiFactory;
    private final AdyenCompanyAccountApiFactory adyenCompanyAccountApiFactory;
    private final AdyenKycApiFactory adyenKycApiFactory;

    @Inject
    public AdyenTestAccountService(AdyenGatewayConfig adyenGatewayConfig,
                                   AdyenBalancePlatformApiFactory adyenBalancePlatformApiFactory,
                                   AdyenCompanyAccountApiFactory adyenCompanyAccountApiFactory, 
                                   AdyenKycApiFactory adyenKycApiFactory) {
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.adyenBalancePlatformApiFactory = adyenBalancePlatformApiFactory;
        this.adyenCompanyAccountApiFactory = adyenCompanyAccountApiFactory;
        this.adyenKycApiFactory = adyenKycApiFactory;
    }

    public AdyenCredentials createTestAccount(String serviceName) {
        var legalEntitiesApi = adyenKycApiFactory.getLegalEntitiesApi();
        
        String legalEntityId = createOrganisation(serviceName, legalEntitiesApi);
        String businessLineId = createBusinessLine(legalEntityId);

        String sroLegalEntityId = createIndividual(legalEntitiesApi);
        associateLegalEntity(legalEntityId, sroLegalEntityId, legalEntitiesApi);

        createBankAccount(legalEntityId);

        String merchantAccountIdTest = adyenGatewayConfig.getMerchantAccountIds().test();
        String storeId = createStore(merchantAccountIdTest, legalEntityId, businessLineId);
        addPaymentMethodsToStore(merchantAccountIdTest, storeId, businessLineId);
        
        LOGGER.info("All payment methods added to store",
                kv("store_id", storeId),
                kv("business_line_id", businessLineId)
        );

        String balancePlatformIdTest = adyenGatewayConfig.getBalancePlatformIds().test();
        String accountHolderId = createAccountHolder(legalEntityId, balancePlatformIdTest, serviceName);
        String balanceAccountId = createBalanceAccount(accountHolderId, serviceName);

        acceptTermsOfService(legalEntityId, sroLegalEntityId);
        signPciQuestionnaire(legalEntityId, sroLegalEntityId);

        LOGGER.info("Adyen credentials created and linked",
                kv("legal_entity_id", legalEntityId),
                kv("store_id", storeId),
                kv("account_holder_id", accountHolderId),
                kv("balance_account_id", balanceAccountId)
        );

        return new AdyenCredentials(legalEntityId, storeId, accountHolderId, balanceAccountId);
    }

    private void signPciQuestionnaire(String legalEntityId, String sroLegalEntityId) {
        var pciQuestionnairesApi = adyenKycApiFactory.getPciQuestionnairesApi();
        
        try {
            GeneratePciDescriptionRequest generatePciDescriptionRequest = new GeneratePciDescriptionRequest()
                    .language("en");

            var pciTemplateReferences =
                    pciQuestionnairesApi.generatePciQuestionnaire(legalEntityId, generatePciDescriptionRequest)
                            .getPciTemplateReferences();

            if (pciTemplateReferences != null && !pciTemplateReferences.isEmpty()) {
                PciSigningRequest pciSigningRequest = new PciSigningRequest()
                        .pciTemplateReferences(pciTemplateReferences);

                pciQuestionnairesApi.signPciQuestionnaire(sroLegalEntityId, pciSigningRequest);

                LOGGER.info("PCI questionnaire signed by legal entity", 
                        kv("sro_legal_entity_id", sroLegalEntityId)
                );
            }

        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error generating or submitting signed PCI questionnaire", e);
        }
    }

    private void acceptTermsOfService(String legalEntityId, String sroLegalEntityId) {
        var termsOfServiceApi = adyenKycApiFactory.getTermsOfServiceApi();
        
        try {
            GetTermsOfServiceDocumentRequest getTermsOfServiceDocumentRequest = new GetTermsOfServiceDocumentRequest()
                    .type(GetTermsOfServiceDocumentRequest.TypeEnum.ADYENFORPLATFORMSADVANCED)
                    .language("en");

            GetTermsOfServiceDocumentResponse getTermsOfServiceDocumentResponse = termsOfServiceApi
                    .getTermsOfServiceDocument(legalEntityId, getTermsOfServiceDocumentRequest);
            AcceptTermsOfServiceRequest acceptTermsOfServiceRequest = new AcceptTermsOfServiceRequest()
                    .acceptedBy(sroLegalEntityId);

            String termsOfServiceDocumentId = getTermsOfServiceDocumentResponse.getTermsOfServiceDocumentId();
            termsOfServiceApi.acceptTermsOfService(legalEntityId, termsOfServiceDocumentId, acceptTermsOfServiceRequest);

            LOGGER.info("Terms of service accepted by legal entity",
                    kv("terms_of_service_document_id", termsOfServiceDocumentId),
                    kv("sro_legal_entity_id", sroLegalEntityId)
            );

        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error getting or accepting terms of service", e);
        }
    }

    private String createBalanceAccount(String accountHolderId, String serviceName) {
        var balanceAccountsApi = adyenBalancePlatformApiFactory.getBalanceAccountsApi();
        
        try {
            BalanceAccountInfo balanceAccountInfo = new BalanceAccountInfo()
                    .accountHolderId(accountHolderId)
                    .description(String.format("Balance account for '%s' service", serviceName))
                    .defaultCurrencyCode("GBP")
                    .timeZone("Europe/London");
            
            BalanceAccount balanceAccount = balanceAccountsApi.createBalanceAccount(balanceAccountInfo);
            String balanceAccountId = balanceAccount.getId();
            
            LOGGER.info("Balance account created", 
                    kv( "balance_account_id", balanceAccountId)
            );
            
            return balanceAccountId;
        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error creating BalanceAccount", e);
        }
    }

    private String createAccountHolder(String legalEntityId, String balancePlatformId, String serviceName) {
        var accountHoldersApi = adyenBalancePlatformApiFactory.getAccountHoldersApi();
        
        try {
            AccountHolderInfo accountHolderInfo = new AccountHolderInfo()
                    .reference(serviceName)
                    .legalEntityId(legalEntityId)
                    .balancePlatform(balancePlatformId)
                    .description(String.format("Liable account holder used for %s", serviceName));
            
            AccountHolder accountHolder = accountHoldersApi.createAccountHolder(accountHolderInfo, null);
            String accountHolderId = accountHolder.getId();
            
            LOGGER.info("Account holder created",
                    kv( "account_holder_id", accountHolderId)
            );
            
            return accountHolderId;
        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error creating account holder", e);
        }
    }

    private String createOrganisation(String serviceName, LegalEntitiesApi legalEntitiesApi) {
        try {
            Support support = new Support()
                    .email("test@example.com")
                    .phone(new PhoneNumber().number("+447500000000"));
            Organization organization = new Organization()
                    .legalName(serviceName)
                    .doingBusinessAs(serviceName)
                    .registrationNumber("34179503")
                    .vatNumber("GB123456789")
                    .registeredAddress(buildAddress())
                    .type(Organization.TypeEnum.GOVERNMENTALORGANIZATION)
                    .support(support)
                    .countryOfGoverningLaw("GB")
                    .dateOfIncorporation("2026-05-30")
                    .financialReports(List.of(new FinancialReport().employeeCount("1")))
                    .email("organization@example.com");

            LegalEntityInfoRequiredType legalEntityInfoRequiredType = new LegalEntityInfoRequiredType()
                    .organization(organization)
                    .type(LegalEntityInfoRequiredType.TypeEnum.ORGANIZATION);
            
            LegalEntity legalEntity = legalEntitiesApi.createLegalEntity(legalEntityInfoRequiredType, null);
            String legalEntityId = legalEntity.getId();
            
            LOGGER.info("Legal entity created",
                    kv("type", legalEntity.getType()),
                    kv("legal_entity_id", legalEntityId)
            );

            return legalEntityId;
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
        var businessLinesApi = adyenKycApiFactory.getBusinessLinesApi();
        
        try {
            WebData webData = new WebData()
                    .webAddress("https://gov.uk");

            BusinessLineInfo businessLineInfo = new BusinessLineInfo()
                    .salesChannels(Arrays.asList("eCommerce", "ecomMoto"))
                    .legalEntityId(legalEntityId)
                    .service(BusinessLineInfo.ServiceEnum.PAYMENTPROCESSING)
                    .webData(Collections.singletonList(webData))
                    .industryCode("921");
            
            BusinessLine businessLine = businessLinesApi.createBusinessLine(businessLineInfo, null);
            String businessLineId = businessLine.getId();
            
            LOGGER.info("Business line created",
                    kv("business_line_id", businessLineId)
            );
            
            return businessLineId;
        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error creating business line for Adyen test account", e);
        }
    }

    private String createIndividual(LegalEntitiesApi legalEntitiesApi) {
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
            
            LegalEntity legalEntityIndividual = legalEntitiesApi.createLegalEntity(legalEntityInfoRequiredType, null);
            String legalEntityIndividualId = legalEntityIndividual.getId();
            
            LOGGER.info("Legal entity created",
                    kv("type", legalEntityIndividual.getType()),
                    kv("legal_entity_id", legalEntityIndividualId)
            );
            
            return legalEntityIndividualId;
        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error creating individual legal entity", e);
        }
    }

    private void associateLegalEntity(String legalEntityId, String individualLegalEntityId, LegalEntitiesApi legalEntitiesApi) {
        try {
            LegalEntityAssociation legalEntityAssociationSRO = new LegalEntityAssociation()
                    .legalEntityId(individualLegalEntityId)
                    .jobTitle("CEO")
                    .type(LegalEntityAssociation.TypeEnum.UBOTHROUGHCONTROL);

            LegalEntityAssociation legalEntityAssociationSignatory = new LegalEntityAssociation()
                    .legalEntityId(individualLegalEntityId)
                    .jobTitle("CEO")
                    .type(LegalEntityAssociation.TypeEnum.SIGNATORY);

            LegalEntityAssociation legalEntityAssociationDirector = new LegalEntityAssociation()
                    .legalEntityId(individualLegalEntityId)
                    .jobTitle("Director")
                    .type(LegalEntityAssociation.TypeEnum.DIRECTOR);

            LegalEntityInfo legalEntityInfo = new LegalEntityInfo()
                    .entityAssociations(Arrays.asList(legalEntityAssociationSRO, legalEntityAssociationDirector, legalEntityAssociationSignatory));

            LOGGER.info("Associated individuals to legal entity",
                    kv("legal_entity_id),", legalEntityId),
                    kv("sro", individualLegalEntityId),
                    kv("director", individualLegalEntityId),
                    kv("signatory", individualLegalEntityId)
            );

            legalEntitiesApi.updateLegalEntity(legalEntityId, legalEntityInfo, null);
        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error associating individuals to legal entity", e);
        }
    }

    private String createStore(String merchantAccountId, String serviceName, String businessLineId) {
        var accountStoreLevelApi = adyenCompanyAccountApiFactory.getAccountStoreLevelApi();
        
        try {

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
            String storeId = store.getId();

            LOGGER.info("Store created",
                    kv( "store_id", storeId)
            );
            
            return storeId;
        } catch (ApiException | IOException e) {
            throw new WebApplicationException("Error creating store", e);
        }
    }

    private void addPaymentMethodsToStore(String merchantAccountIdTest, String storeId, String businessLineId) {
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
        
        var paymentMethodsMerchantLevelApi = adyenCompanyAccountApiFactory.getPaymentMethodsMerchantLevelApi();
        
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
        var transferInstrumentsApi = adyenKycApiFactory.getTransferInstrumentsApi();
        
        try {
            UKLocalAccountIdentification ukLocalAccountIdentification = new UKLocalAccountIdentification()
                    .accountNumber("12345678")
                    .sortCode("090102")
                    .type(UKLOCAL);
            BankAccountInfo bankAccountInfo = new BankAccountInfo()
                    .accountIdentification(new BankAccountInfoAccountIdentification(ukLocalAccountIdentification));

            TransferInstrumentInfo transferInstrumentInfo = new TransferInstrumentInfo()
                    .bankAccount(bankAccountInfo)
                    .legalEntityId(legalEntityId)
                    .type(TransferInstrumentInfo.TypeEnum.BANKACCOUNT);

            LOGGER.info("Transfer instrument created for legal entity",
                    kv( "legal_entity_id", legalEntityId)
            );
            
           transferInstrumentsApi.createTransferInstrument(transferInstrumentInfo, null);
        } catch (IOException | ApiException e) {
            throw new WebApplicationException("Error creating bank account for Adyen test account", e);
        }
    }
}
