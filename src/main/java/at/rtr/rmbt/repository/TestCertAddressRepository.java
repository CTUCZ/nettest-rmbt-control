package at.rtr.rmbt.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import at.rtr.rmbt.model.TestCertAddress;

public interface TestCertAddressRepository extends JpaRepository<TestCertAddress, Long> {
}
