package com.poshanforlife.api.service;

import com.poshanforlife.api.entity.Order;
import com.poshanforlife.api.entity.PaymentType;
import com.poshanforlife.api.entity.Transaction;
import com.poshanforlife.api.entity.TransactionType;
import com.poshanforlife.api.repository.TransactionRepository;
import com.poshanforlife.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Builds ledger entries for orders — the one place transactionId/invoiceNumber
 * generation and Transaction assembly happen, shared by the service-assignment
 * flow (prompt 06), the order mark-as-paid rule (prompt 07) and manual/
 * webhook entry (prompt 08).
 */
@Component
@RequiredArgsConstructor
public class TransactionFactory {

    private final TransactionRepository transactionRepository;
    private final TransactionNumbers transactionNumbers;
    private final UserRepository userRepository;

    /**
     * Convenience for the auto-activation case: full order amount, OFFLINE,
     * source "admin" (payments are recorded in-clinic until the payment
     * gateway lands). Must run inside the caller's transaction.
     */
    public Transaction activation(Order order, UUID createdById) {
        return record(order, TransactionType.ACTIVATION, PaymentType.OFFLINE,
                order.getAmountInr(), BigDecimal.ZERO, order.getAmountInr(), BigDecimal.ZERO,
                "admin", null, null, createdById);
    }

    /**
     * General ledger entry — backs manual admin entry and the future
     * payment-gateway webhook variant (source="mobile_app" + paymentGatewayRef).
     * Must run inside the caller's transaction (invoice numbering locks a
     * counter row).
     */
    public Transaction record(Order order, TransactionType type, PaymentType paymentType,
                              BigDecimal priceInr, BigDecimal discountInr, BigDecimal amountInr,
                              BigDecimal creditCharged, String source, String paymentGatewayRef,
                              String notes, UUID createdById) {
        Transaction tx = new Transaction();
        tx.setTransactionId(transactionNumbers.newTransactionId());
        tx.setInvoiceNumber(transactionNumbers.nextInvoiceNumber());
        tx.setTransactionType(type);
        tx.setPaymentType(paymentType);
        tx.setPriceInr(priceInr);
        tx.setDiscountInr(discountInr);
        tx.setAmountInr(amountInr);
        tx.setCreditCharged(creditCharged);
        tx.setSource(source);
        tx.setPaymentGatewayRef(paymentGatewayRef);
        tx.setNotes(notes);
        tx.setOrder(order);
        tx.setPatient(order.getPatient());
        tx.setCreatedBy(userRepository.getReferenceById(createdById));
        return transactionRepository.save(tx);
    }
}
