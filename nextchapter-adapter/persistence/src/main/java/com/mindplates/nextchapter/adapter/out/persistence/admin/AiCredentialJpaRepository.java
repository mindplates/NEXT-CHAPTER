package com.mindplates.nextchapter.adapter.out.persistence.admin;

import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiCredentialJpaRepository extends JpaRepository<AiCredentialJpaEntity, Long> {

    List<AiCredentialJpaEntity> findAllByOrderByVendorAsc();

    Optional<AiCredentialJpaEntity> findByVendor(AiVendor vendor);

    void deleteByVendor(AiVendor vendor);
}
