package uk.gov.pay.connector.gatewayaccount.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;

@Entity
@Table(name = "gateway_accounts_adyen_setup")
@SequenceGenerator(name = "gateway_accounts_adyen_setup_id_seq",
        sequenceName = "gateway_accounts_adyen_setup_id_seq", allocationSize = 1)
public class AdyenAccountSetupTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gateway_accounts_adyen_setup_id_seq")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "gateway_account_id", updatable = false)
    private GatewayAccountEntity gatewayAccount;

    @ManyToOne
    @JoinColumn(name = "gateway_account_credential_id", updatable = false)
    private GatewayAccountCredentialsEntity gatewayAccountCredential;

    @Column(name = "task")
    @Enumerated(EnumType.STRING)
    private AdyenAccountSetupTask task;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private AdyenAccountSetupStatus status;

    public AdyenAccountSetupTaskEntity(GatewayAccountEntity gatewayAccount, AdyenAccountSetupTask task, 
                                       GatewayAccountCredentialsEntity gatewayAccountCredentials, AdyenAccountSetupStatus status) {
        this.gatewayAccount = gatewayAccount;
        this.task = task;
        this.status = status;
        this.gatewayAccountCredential = gatewayAccountCredentials;
    }

    public AdyenAccountSetupTaskEntity() {
        // We ❤️ JPA
    }

    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccount;
    }

    public AdyenAccountSetupTask getTask() {
        return task;
    }

    public AdyenAccountSetupStatus getStatus() {
        return status;
    }

    public GatewayAccountCredentialsEntity getGatewayAccountCredential() {
        return gatewayAccountCredential;
    }

    public void setGatewayAccount(GatewayAccountEntity gatewayAccount) {
        this.gatewayAccount = gatewayAccount;
    }

    public void setTask(AdyenAccountSetupTask task) {
        this.task = task;
    }
    
    public void setStatus(AdyenAccountSetupStatus status) {
        this.status = status;
    }
}
