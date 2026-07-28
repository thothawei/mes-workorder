package com.example.mes.material.repository;

import com.example.mes.material.domain.Material;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    Optional<Material> findByMaterialCode(String materialCode);
}
