package at.rtr.rmbt.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "test_cert_address")
public class TestCertAddress {

    @Id
    @Column(name = "loop_uuid")
    private UUID loopUuid;

    @Column(name = "address")
    private String address;

    @Column(name = "am_code")
    private Integer amCode;

    @Column(name = "x_wgs")
    private Double xWgs;

    @Column(name = "y_wgs")
    private Double yWgs;

    @Builder
    public TestCertAddress(UUID loopUuid, String address, Integer amCode, Double xWgs, Double yWgs) {
        this.loopUuid = loopUuid;
        this.address = address;
        this.amCode = amCode;
        this.xWgs = xWgs;
        this.yWgs = yWgs;
    }
}
