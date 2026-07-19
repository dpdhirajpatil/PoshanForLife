package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.Transaction;
import com.poshanforlife.api.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<Transaction> findByOrderIdInOrderByCreatedAtDesc(Collection<UUID> orderIds);

    boolean existsByOrderIdAndTransactionTypeNot(UUID orderId, TransactionType transactionType);
}
