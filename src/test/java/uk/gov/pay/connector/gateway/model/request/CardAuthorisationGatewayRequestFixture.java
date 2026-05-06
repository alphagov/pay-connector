package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.service.payments.commons.model.AgreementPaymentType;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.SupportedLanguage;

import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

public class CardAuthorisationGatewayRequestFixture {
    private String gatewayTransactionId = "gateway_transaction_id";
    private String email = "email@example.com";
    private SupportedLanguage language = SupportedLanguage.ENGLISH;
    private boolean moto = false;
    private String amount = "1000";
    private String description = "a test description";
    private ServicePaymentReference reference = ServicePaymentReference.of("test_reference");
    private String govUkPayPaymentId = "gov_uk_payment_id";
    private GatewayCredentials credentials = () -> false;
    private GatewayAccountEntity gatewayAccount = aGatewayAccountEntity().build();
    private AuthorisationMode authorisationMode = AuthorisationMode.WEB;
    private boolean savePaymentInstrumentToAgreement = false;
    private AgreementPaymentType agreementPaymentType = AgreementPaymentType.UNSCHEDULED;
    private AgreementEntity agreement = anAgreementEntity().build();
    private AuthCardDetails authCardDetails = anAuthCardDetails().build();

    public static CardAuthorisationGatewayRequestFixture aCardAuthorisationGatewayRequest() {
        return new CardAuthorisationGatewayRequestFixture();
    }

    public CardAuthorisationGatewayRequestFixture withAuthCardDetails(AuthCardDetails authCardDetails) {
        this.authCardDetails = authCardDetails;
        return this;
    }

    public CardAuthorisationGatewayRequestFixture withGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
        return this;
    }

    public CardAuthorisationGatewayRequestFixture withEmail(String email) {
        this.email = email;
        return this;
    }

    public CardAuthorisationGatewayRequestFixture withLanguage(SupportedLanguage language) {
        this.language = language;
        return this;
    }

    public CardAuthorisationGatewayRequestFixture withMoto(boolean moto) {
        this.moto = moto;
        return this;
    }

    public CardAuthorisationGatewayRequestFixture withAmount(String amount) {
        this.amount = amount;
        return this;
    }

    public CardAuthorisationGatewayRequestFixture withDescription(String description) {
        this.description = description;
        return this;
    }

    public CardAuthorisationGatewayRequestFixture withReference(ServicePaymentReference reference) {
        this.reference = reference;
        return this;
    }

    public CardAuthorisationGatewayRequestFixture withGovUkPayPaymentId(String govUkPayPaymentId) {
        this.govUkPayPaymentId = govUkPayPaymentId;
        return this;
    }

    public CardAuthorisationGatewayRequestFixture withCredentials(GatewayCredentials credentials) {
        this.credentials = credentials;
        return this;
    }

    public CardAuthorisationGatewayRequestFixture withGatewayAccount(GatewayAccountEntity gatewayAccount) {
        this.gatewayAccount = gatewayAccount;
        return this;
    }

    public CardAuthorisationGatewayRequestFixture withAuthorisationMode(AuthorisationMode authorisationMode) {
        this.authorisationMode = authorisationMode;
        return this;
    }

    public CardAuthorisationGatewayRequestFixture withSavePaymentInstrumentToAgreement(boolean savePaymentInstrumentToAgreement) {
        this.savePaymentInstrumentToAgreement = savePaymentInstrumentToAgreement;
        return this;
    }

    public CardAuthorisationGatewayRequestFixture withAgreementPaymentType(AgreementPaymentType agreementPaymentType) {
        this.agreementPaymentType = agreementPaymentType;
        return this;
    }

    public CardAuthorisationGatewayRequestFixture withAgreement(AgreementEntity agreement) {
        this.agreement = agreement;
        return this;
    }
    
    public CardAuthorisationGatewayRequest build() {
        return new CardAuthorisationGatewayRequest(
                gatewayTransactionId,
                email,
                language,
                moto,
                amount,
                description,
                reference,
                govUkPayPaymentId,
                credentials,
                gatewayAccount,
                authorisationMode,
                savePaymentInstrumentToAgreement,
                agreementPaymentType,
                agreement,
                authCardDetails);
    }
}
