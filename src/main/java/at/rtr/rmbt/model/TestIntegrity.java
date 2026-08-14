package at.rtr.rmbt.model;

import at.rtr.rmbt.enums.IntegrityAction;
import at.rtr.rmbt.enums.IntegrityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;

/**
 * Play Integrity evaluation record for a single /testRequest. One row is written whenever the
 * request carried integrity fields, or whenever the policy rejected the request (so support can
 * look up why a client was refused). test_uid is NULL for rejected requests and for allowed
 * requests where the facade created no test (validation error); the authoritative field is action.
 * First entity extending BaseEntity in this codebase - intentional; @PrePersist fills the
 * NOT NULL created_date/modified_date columns.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "test_integrity")
public class TestIntegrity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "uid")
    private Long uid;

    @Column(name = "test_uid")
    private Long testUid;

    @Column(name = "client_uuid")
    private UUID clientUuid;

    @Column(name = "platform")
    private String platform;

    @Column(name = "provider")
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private IntegrityStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    private IntegrityAction action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failed_checks", columnDefinition = "jsonb")
    private List<String> failedChecks;

    @Column(name = "client_error")
    private String clientError;

    @Column(name = "client_error_detail")
    private String clientErrorDetail;

    @Column(name = "software_version_code")
    private Integer softwareVersionCode;

    @Column(name = "token_digest")
    private String tokenDigest;

    @Column(name = "decode_latency_ms")
    private Integer decodeLatencyMs;
}
