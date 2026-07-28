package com.example.mes.material.repository;

import com.example.mes.material.domain.MaterialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialTransactionRepository extends JpaRepository<MaterialTransaction, Long> {

    List<MaterialTransaction> findByWorkOrderIdOrderByIdAsc(Long workOrderId);
}
