package com.mindplates.nextchapter.adapter.out.persistence.admin;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountJpaRepository extends JpaRepository<AdminAccountJpaEntity, Long> {

    List<AdminAccountJpaEntity> findAllByOrderByEmailAsc();

    Optional<AdminAccountJpaEntity> findByEmail(String email);
}
