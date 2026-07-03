package at.rtr.rmbt.repository;

import at.rtr.rmbt.model.TestIntegrity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface TestIntegrityRepository extends JpaRepository<TestIntegrity, Long> {

    /**
     * Links an integrity record to the test created by the facade. Runs in its own short
     * transaction after the facade transaction committed.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE test_integrity SET test_uid = (SELECT uid FROM test WHERE uuid = CAST(:testUuid AS uuid)), " +
            "modified_date = now() WHERE uid = :recordUid", nativeQuery = true)
    void attachTest(@Param("recordUid") Long recordUid, @Param("testUuid") UUID testUuid);
}
