package uk.gov.pay.connector.model.domain;

import javax.persistence.*;

@Entity
@Table(name = "email_notifications")
@SequenceGenerator(name = "email_notifications_id_seq", sequenceName = "email_notifications_id_seq", allocationSize = 1)
public class EmailNotificationEntity extends AbstractEntity {

    @Column(name = "template")
    private String template;

    @OneToOne
    @JoinColumn(name = "account_id", nullable = false)
    GatewayAccountEntity accountEntity;

    public EmailNotificationEntity () {

    }
    public EmailNotificationEntity(GatewayAccountEntity accountEntity, String template) {
        this.accountEntity = accountEntity;
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public GatewayAccountEntity getAccountEntity() {
        return accountEntity;
    }
}
