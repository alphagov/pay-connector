package uk.gov.pay.connector.tasks;

import uk.gov.pay.connector.model.domain.AbstractVersionedEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "task_audits")
public class TaskAuditEntity extends AbstractVersionedEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "name")
    private String name;
    @Column(name = "last_entity")
    private Long lastEntity;

    public TaskAuditEntity() {
        // JPA
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getLastEntity() {
        return lastEntity;
    }

    public void setLastEntity(Long lastEntity) {
        this.lastEntity = lastEntity;
    }
}
