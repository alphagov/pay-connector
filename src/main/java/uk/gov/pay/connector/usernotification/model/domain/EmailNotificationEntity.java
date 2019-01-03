package uk.gov.pay.connector.usernotification.model.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;


@Entity
@Table(name = "email_notifications")
@SequenceGenerator(name = "email_notifications_id_seq",
        sequenceName = "email_notifications_id_seq", allocationSize = 1)
public class EmailNotificationEntity extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "email_notifications_id_seq")
    @JsonIgnore
    private Long id;

    @Column(name = "template_body")
    @JsonProperty("template_body")
    private String templateBody;

    private boolean enabled;

    @Column(name = "type", insertable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    private EmailNotificationType type;

    @OneToOne
    @JoinColumn(name = "account_id", nullable = false)
    @JsonBackReference
    private GatewayAccountEntity accountEntity;

    public EmailNotificationEntity() {
        // for JPA
    }

    public EmailNotificationEntity(GatewayAccountEntity gatewayAccount, String templateBody, boolean enabled) {
        this.accountEntity = gatewayAccount;
        this.templateBody = templateBody;
        this.enabled = enabled;
    }

    public EmailNotificationEntity(GatewayAccountEntity gatewayAccount) {
        this(gatewayAccount, null, true);
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
