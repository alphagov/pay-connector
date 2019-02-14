package uk.gov.pay.connector.gatewayaccount.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "gateway_accounts_stripe_setup")
@SequenceGenerator(name = "gateway_accounts_stripe_setup_id_seq",
        sequenceName = "gateway_accounts_stripe_setup_id_seq", allocationSize = 1)
public class StripeAccountSetupTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gateway_accounts_stripe_setup_id_seq")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "gateway_account_id", updatable = false)
    private GatewayAccountEntity gatewayAccount;

    @Column(name = "task")
    @Enumerated(EnumType.STRING)
    private StripeAccountSetupTask task;

    public StripeAccountSetupTaskEntity(GatewayAccountEntity gatewayAccount, StripeAccountSetupTask task) {
        this.gatewayAccount = gatewayAccount;
        this.task = task;
    }

    public StripeAccountSetupTaskEntity() {
        // We ❤️ JPA
    }

    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccount;
    }

    public StripeAccountSetupTask getTask() {
        return task;
    }

    public void setGatewayAccount(GatewayAccountEntity gatewayAccount) {
        this.gatewayAccount = gatewayAccount;
    }

    public void setTask(StripeAccountSetupTask task) {
        this.task = task;
    }
}
