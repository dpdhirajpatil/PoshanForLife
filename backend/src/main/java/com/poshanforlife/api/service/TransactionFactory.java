package com.poshanforlife.api.service;

import com.poshanforlife.api.entity.Order;
import com.poshanforlife.api.entity.PaymentType;
import com.poshanforlife.api.entity.Transaction;
import com.poshanforlife.api.entity.TransactionType;
import com.poshanforlife.api.repository.TransactionRepository;
import com.poshanforlife.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Builds ledger entries for orders — the one place activation transactions
 * are assembled, shared by the service-assignment flow (prompt 06) and the
 * order mark-as-paid rule (prompt 07). paymentType defaults to OFFLINE
 * (payments are recorded in-clinic until the payment gateway lands);
 * source "admin" marks portal-originated entries.
 */
@Component
@RequiredArgsConstructor
public class TransactionFactory {

    private final TransactionRepository transactionRepository;
    private final TransactionNumbers transactionNumbers;
    private final UserRepository userRepository;

    /** Must run inside the caller's transaction (invoice numbering locks a counter row). */
    public Transaction activation(Order order, UUID createdById) {
        Transaction tx = new Transaction();
        tx.setTransactionId(transactionNumbers.newTransactionId());
        tx.setInvoiceNumber(transactionNumbers.nextInvoiceNumber());
        tx.setTransactionType(TransactionType.ACTIVATION);
        tx.setPaymentType(PaymentType.OFFLINE);
        tx.setPriceInr(order.getAmountInr());
        tx.setAmountInr(order.getAmountInr());
        tx.setOrder(order);
        tx.setPatient(order.getPatient());
        tx.setCreatedBy(userRepository.getReferenceById(createdById));
        return transactionRepository.save(tx);
    }
}
