package at.rtr.rmbt.model;

import at.rtr.rmbt.enums.IntegrityAction;
import at.rtr.rmbt.enums.IntegrityStatus;
import org.junit.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestIntegrityTest {

    @Test
    public void entity_expectTableAndColumnNamesMatchMigration() throws Exception {
        // Given: column names as defined in CTU_integrity_check_migration.sql
        assertEquals("test_integrity", TestIntegrity.class.getAnnotation(Table.class).name());

        assertColumn("testUid", "test_uid");
        assertColumn("clientUuid", "client_uuid");
        assertColumn("platform", "platform");
        assertColumn("provider", "provider");
        assertColumn("status", "status");
        assertColumn("action", "action");
        assertColumn("failedChecks", "failed_checks");
        assertColumn("clientError", "client_error");
        assertColumn("clientErrorDetail", "client_error_detail");
        assertColumn("softwareVersionCode", "software_version_code");
        assertColumn("tokenDigest", "token_digest");
        assertColumn("decodeLatencyMs", "decode_latency_ms");
    }

    @Test
    public void entity_whenBuilt_expectFieldsAccessible() {
        // Given / When
        TestIntegrity entity = TestIntegrity.builder()
                .provider("PLAY_INTEGRITY")
                .status(IntegrityStatus.PASS)
                .action(IntegrityAction.ALLOWED)
                .failedChecks(List.of())
                .build();

        // Then
        assertEquals(IntegrityStatus.PASS, entity.getStatus());
        assertEquals(IntegrityAction.ALLOWED, entity.getAction());
        assertNotNull(entity.getFailedChecks());
    }

    private void assertColumn(String fieldName, String expectedColumn) throws NoSuchFieldException {
        Field field = TestIntegrity.class.getDeclaredField(fieldName);
        assertEquals(expectedColumn, field.getAnnotation(Column.class).name());
    }
}
