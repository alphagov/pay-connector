package uk.gov.pay.connector.cardtype.model.domain;

import uk.gov.pay.connector.common.model.domain.UuidAbstractEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "card_types")
public class CardBrandLabelEntity extends UuidAbstractEntity {

    @Column
    private String brand;

    @Column
    private String label;
    
    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
