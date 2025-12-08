package uk.gov.pay.connector.cardtype.model.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import uk.gov.pay.connector.common.model.domain.UuidAbstractEntity;

@Entity
@Table(name = "card_types")
public class CardBrandLabelEntity extends UuidAbstractEntity {

    @Column
    @Schema(example = "visa")
    private String brand;

    @Column
    @Schema(example = "Visa")
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
