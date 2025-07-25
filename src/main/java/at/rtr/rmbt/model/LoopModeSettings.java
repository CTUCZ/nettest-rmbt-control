package at.rtr.rmbt.model;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Table(name = "test_loopmode")
public class LoopModeSettings implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "test_loopmode_seq")
    @SequenceGenerator(name = "test_loopmode_seq", sequenceName = "test_loopmode_uid_seq", allocationSize = 1)
    @Column(name = "uid")
    private Long uid;

    @Column(name = "test_uuid")
    protected UUID testUuid;

    @Column(name = "client_uuid")
    protected UUID clientUuid;

    @Column(name = "max_delay")
    protected Integer maxDelay;

    @Column(name = "max_movement")
    protected Integer maxMovement;

    @Column(name = "max_tests")
    protected Integer maxTests;

    @Column(name = "test_counter")
    protected Integer testCounter;

    @Column(name = "loop_uuid")
    private UUID loopUuid;

    @Column(name = "cert_mode")
    private Boolean certMode;
}
