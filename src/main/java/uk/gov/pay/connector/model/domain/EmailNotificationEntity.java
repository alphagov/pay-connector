package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.*;

@Entity
@Table(name = "email_notifications")
@SequenceGenerator(name = "email_notifications_id_seq", sequenceName = "email_notifications_id_seq", allocationSize = 1)
public class EmailNotificationEntity extends AbstractEntity {

    @Column(name = "template_body")
    @JsonProperty("template_body")
    private String templateBody;

    private boolean enabled;

    @OneToOne
    @JsonIgnore
    @JoinColumn(name = "account_id", nullable = false)
    @JsonManagedReference
    private GatewayAccountEntity accountEntity;

    public EmailNotificationEntity () {
    }

    public EmailNotificationEntity(GatewayAccountEntity accountEntity) {
        this.accountEntity = accountEntity;
        this.enabled = true;
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


    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
