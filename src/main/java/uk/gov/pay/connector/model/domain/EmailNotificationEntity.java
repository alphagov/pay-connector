package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import javax.persistence.*;

@Entity
@Table(name = "email_notifications")
@SequenceGenerator(name = "email_notifications_id_seq", sequenceName = "email_notifications_id_seq", allocationSize = 1)
public class EmailNotificationEntity extends AbstractEntity {

    @Column(name = "template_body")
    private String templateBody;

    @OneToOne
    @JoinColumn(name = "account_id", nullable = false)
    @JsonManagedReference
    private GatewayAccountEntity accountEntity;

    public EmailNotificationEntity () {

    }
    public EmailNotificationEntity(GatewayAccountEntity accountEntity, String templateBody) {
        this.accountEntity = accountEntity;
        this.templateBody = templateBody;
    }

    public String getTemplateBody() {
        return templateBody;
    }

    public void setTemplateBody(String templateBody) {
        this.templateBody = templateBody;
    }

    public GatewayAccountEntity getAccountEntity() {
        return accountEntity;
    }
}
