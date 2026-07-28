package com.example.mes.material.repository;

import com.example.mes.material.domain.BomItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BomItemRepository extends JpaRepository<BomItem, Long> {

    List<BomItem> findByProductCode(String productCode);
}
