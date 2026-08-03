package at.rtr.rmbt.constant;

public interface IntegrityConstants {

    String PROVIDER_PLAY_INTEGRITY = "PLAY_INTEGRITY";
    String PROVIDER_NONE = "NONE";

    /** The only error flag deployed clients can parse — never send any other value. */
    String ERROR_FLAG_TEST_REJECTED = "TEST_REJECTED";

    String MESSAGE_KEY_INTEGRITY_CHECK = "ERROR_INTEGRITY_CHECK";

    // failed_checks values (persisted to jsonb, drive Phase 1 statistics)
    String CHECK_UUID_MISSING = "UUID_MISSING";
    String CHECK_TIMESTAMP_MISSING = "TIMESTAMP_MISSING";
    String CHECK_TOKEN_TOO_LARGE = "TOKEN_TOO_LARGE";
    String CHECK_CLIENT_UNKNOWN = "CLIENT_UNKNOWN";
    String CHECK_REQUEST_HASH_MISMATCH = "REQUEST_HASH_MISMATCH";
    String CHECK_STALE_TOKEN = "STALE_TOKEN";
    String CHECK_PACKAGE_NAME_MISMATCH = "PACKAGE_NAME_MISMATCH";
    String CHECK_APP_NOT_RECOGNIZED = "APP_NOT_RECOGNIZED";
    String CHECK_CERT_DIGEST_MISMATCH = "CERT_DIGEST_MISMATCH";
    String CHECK_DEVICE_INTEGRITY_FAILED = "DEVICE_INTEGRITY_FAILED";
    String CHECK_DECODE_FAILED = "DECODE_FAILED";
    String CHECK_QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    String CHECK_GOOGLE_UNAVAILABLE = "GOOGLE_UNAVAILABLE";
    String CHECK_REPLAY = "REPLAY";
    /** Prefix of the failed_checks entry carrying the digest of a replayed token (support lookup). */
    String CHECK_REPLAY_DIGEST_PREFIX = "REPLAY_DIGEST:";

    // Stable log markers for log-based alerting - Zabbix matches "INTEGRITY_ALERT reason=<REASON>"
    // in the application log, one line per affected request. Do NOT rephrase or remove.
    String ALERT_QUOTA_EXCEEDED = "INTEGRITY_ALERT reason=QUOTA_EXCEEDED";
    String ALERT_GOOGLE_UNAVAILABLE = "INTEGRITY_ALERT reason=GOOGLE_UNAVAILABLE";
    String ALERT_CHECK_FAILED = "INTEGRITY_ALERT reason=CHECK_FAILED";
}
