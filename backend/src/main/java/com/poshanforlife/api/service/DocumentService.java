package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CreateDocumentItemRequest;
import com.poshanforlife.api.dto.CreateDocumentRequest;
import com.poshanforlife.api.dto.DocumentDetailDto;
import com.poshanforlife.api.dto.DocumentItemDto;
import com.poshanforlife.api.dto.DocumentListItemDto;
import com.poshanforlife.api.dto.FromOrderRequest;
import com.poshanforlife.api.dto.LeadRefDto;
import com.poshanforlife.api.dto.PatientRefDto;
import com.poshanforlife.api.dto.PdfUrlDto;
import com.poshanforlife.api.dto.UpdateDocumentStatusRequest;
import com.poshanforlife.api.dto.UserRefDto;
import com.poshanforlife.api.entity.CatalogueItem;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.Document;
import com.poshanforlife.api.entity.DocumentLineItem;
import com.poshanforlife.api.entity.DocumentStatus;
import com.poshanforlife.api.entity.DocumentType;
import com.poshanforlife.api.entity.Lead;
import com.poshanforlife.api.entity.Order;
import com.poshanforlife.api.entity.PatientProgramme;
import com.poshanforlife.api.entity.PaymentStatus;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.ChallengeRepository;
import com.poshanforlife.api.repository.DocumentRepository;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.LeadRepository;
import com.poshanforlife.api.repository.OrderRepository;
import com.poshanforlife.api.repository.ProgrammeRepository;
import com.poshanforlife.api.repository.SessionRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.util.DocumentTotals;
import com.poshanforlife.api.util.WireEnums;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Draft estimates and GST-aware invoices (extends prompt 08's Transaction/
 * invoice-number machinery). ADMIN+DOCTOR; DOCTOR is scoped to documents
 * whose lead is assigned to them or whose patient is one of theirs — same
 * pattern as LeadService/PatientService/TransactionService.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final int DEFAULT_ESTIMATE_VALID_DAYS = 7;

    private final DocumentRepository documentRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final DoctorPatientRepository doctorPatientRepository;
    private final OrderRepository orderRepository;
    private final ProgrammeRepository programmeRepository;
    private final SessionRepository sessionRepository;
    private final ChallengeRepository challengeRepository;
    private final TransactionNumbers transactionNumbers;
    private final DocumentPdfRenderer pdfRenderer;
    private final DocumentStorageService documentStorageService;

    @Transactional(readOnly = true)
    public Page<DocumentListItemDto> list(UUID leadId, UUID patientId, String type, String status,
                                          int page, int limit, AuthenticatedUser caller) {
        DocumentType typeFilter = WireEnums.parse(type, DocumentType::fromWire,
                "type must be one of estimate, invoice");
        DocumentStatus statusFilter = WireEnums.parse(status, DocumentStatus::fromWire,
                "status must be one of draft, sent, paid");
        UUID doctorId = caller.role() == Role.DOCTOR ? UUID.fromString(caller.id()) : null;
        Page<Document> result = documentRepository.search(leadId, patientId, typeFilter, statusFilter, doctorId,
                PageRequest.of(Math.max(page - 1, 0), limit, Sort.by("createdAt").descending()));
        return result.map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public DocumentDetailDto get(UUID id, AuthenticatedUser caller) {
        return toDetail(findAccessible(id, caller));
    }

    @Transactional
    public DocumentDetailDto create(CreateDocumentRequest request, AuthenticatedUser caller) {
        if ((request.leadId() == null) == (request.patientId() == null)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Exactly one of leadId or patientId must be set");
        }

        Lead lead = null;
        User patient = null;
        if (request.leadId() != null) {
            lead = leadRepository.findById(request.leadId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lead", request.leadId()));
            requireLeadAccess(lead, caller);
        } else {
            patient = findPatient(request.patientId());
            requirePatientAccess(patient.getId(), caller);
        }

        boolean isEstimate = request.documentType() == DocumentType.ESTIMATE;
        Document document = new Document();
        document.setDocumentType(request.documentType());
        document.setDocumentNumber(isEstimate
                ? transactionNumbers.nextEstimateNumber()
                : transactionNumbers.nextInvoiceNumber());
        document.setLead(lead);
        document.setPatient(patient);
        document.setItems(request.items().stream().map(DocumentService::toLineItem).toList());
        document.setDiscountInr(request.discountInr() != null ? request.discountInr() : BigDecimal.ZERO);
        document.setNotes(request.notes());
        document.setValidForDays(isEstimate
                ? (request.validForDays() != null ? request.validForDays() : DEFAULT_ESTIMATE_VALID_DAYS)
                : null);
        document.setCreatedBy(userRepository.getReferenceById(UUID.fromString(caller.id())));

        return toDetail(documentRepository.save(document));
    }

    /**
     * Pre-fills an invoice from an existing paid Order: patient, a single
     * line item derived from the order's assigned catalogue item (or a
     * generic "Service" line if the order has no linked programme), and the
     * order's own amountInr as the pre-GST rate — GST is then added on top
     * for the formal invoice, since orders/transactions never modelled GST.
     */
    @Transactional
    public DocumentDetailDto fromOrder(FromOrderRequest request, AuthenticatedUser caller) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.orderId()));
        requirePatientAccess(order.getPatient().getId(), caller);
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Order must be paid before generating an invoice from it");
        }

        DocumentLineItem item = lineItemFromOrder(order);

        Document document = new Document();
        document.setDocumentType(DocumentType.INVOICE);
        document.setDocumentNumber(transactionNumbers.nextInvoiceNumber());
        document.setPatient(userRepository.getReferenceById(order.getPatient().getId()));
        document.setItems(List.of(item));
        document.setDiscountInr(BigDecimal.ZERO);
        document.setCreatedBy(userRepository.getReferenceById(UUID.fromString(caller.id())));

        return toDetail(documentRepository.save(document));
    }

    /** No side effects beyond the status change — see class javadoc / the feature spec. */
    @Transactional
    public DocumentDetailDto updateStatus(UUID id, UpdateDocumentStatusRequest request, AuthenticatedUser caller) {
        Document document = findAccessible(id, caller);
        document.setStatus(request.status());
        return toDetail(documentRepository.save(document));
    }

    /** Renders (once, cached thereafter) and returns a signed download URL. */
    @Transactional
    public PdfUrlDto getPdfUrl(UUID id, AuthenticatedUser caller) {
        Document document = findAccessible(id, caller);
        if (document.getPdfObjectPath() == null) {
            byte[] pdf = pdfRenderer.render(document, subjectName(document), subjectContactLine(document));
            String objectPath = documentStorageService.uploadPdf(pdf, document.getId());
            document.setPdfObjectPath(objectPath);
            documentRepository.save(document);
        }
        return new PdfUrlDto(documentStorageService.createSignedUrl(document.getPdfObjectPath()));
    }

    // ---- helpers -----------------------------------------------------------

    private Document findAccessible(UUID id, AuthenticatedUser caller) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        if (document.getLead() != null) {
            requireLeadAccess(document.getLead(), caller);
        } else {
            requirePatientAccess(document.getPatient().getId(), caller);
        }
        return document;
    }

    private void requireLeadAccess(Lead lead, AuthenticatedUser caller) {
        if (caller.role() == Role.DOCTOR
                && (lead.getAssignedPractitioner() == null
                    || !lead.getAssignedPractitioner().getId().equals(UUID.fromString(caller.id())))) {
            throw new AccessDeniedException("This lead is not assigned to you");
        }
    }

    private void requirePatientAccess(UUID patientId, AuthenticatedUser caller) {
        if (caller.role() == Role.DOCTOR && !doctorPatientRepository.existsByDoctorIdAndPatientId(
                UUID.fromString(caller.id()), patientId)) {
            throw new AccessDeniedException("This patient is not assigned to you");
        }
    }

    private User findPatient(UUID id) {
        return userRepository.findById(id)
                .filter(u -> u.getRole() == Role.PATIENT)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    private DocumentLineItem lineItemFromOrder(Order order) {
        PatientProgramme pp = order.getPatientProgramme();
        String itemName = "Service";
        if (pp != null) {
            itemName = findCatalogueItem(pp.getServiceType(), pp.itemId())
                    .map(CatalogueItem::getName).orElse("Service");
        }
        return new DocumentLineItem(itemName, null, null, 1, order.getAmountInr());
    }

    private Optional<CatalogueItem> findCatalogueItem(CatalogueItemType type, UUID id) {
        return switch (type) {
            case PROGRAMME -> programmeRepository.findById(id).map(CatalogueItem.class::cast);
            case SESSION -> sessionRepository.findById(id).map(CatalogueItem.class::cast);
            case CHALLENGE -> challengeRepository.findById(id).map(CatalogueItem.class::cast);
        };
    }

    private String subjectName(Document document) {
        return document.getLead() != null ? document.getLead().getName() : document.getPatient().getName();
    }

    private String subjectContactLine(Document document) {
        String email = document.getLead() != null ? document.getLead().getEmail() : document.getPatient().getEmail();
        String phone = document.getLead() != null ? document.getLead().getPhone() : document.getPatient().getPhone();
        if (email != null && phone != null) {
            return email + " · " + phone;
        }
        return email != null ? email : phone;
    }

    private DocumentListItemDto toListItem(Document d) {
        DocumentTotals totals = DocumentTotals.compute(d.getItems(), d.getDiscountInr());
        return new DocumentListItemDto(
                d.getId().toString(), d.getDocumentType(), d.getDocumentNumber(), d.getStatus(),
                leadRef(d.getLead()), patientRef(d.getPatient()), totals.total(), d.getCreatedAt());
    }

    private DocumentDetailDto toDetail(Document d) {
        DocumentTotals totals = DocumentTotals.compute(d.getItems(), d.getDiscountInr());
        List<DocumentItemDto> items = d.getItems().stream()
                .map(i -> new DocumentItemDto(i.itemName(), i.description(), i.hsnSac(), i.quantity(),
                        i.rateInr(), i.lineTotal()))
                .toList();
        return new DocumentDetailDto(
                d.getId().toString(), d.getDocumentType(), d.getDocumentNumber(), d.getStatus(),
                leadRef(d.getLead()), patientRef(d.getPatient()), items, d.getDiscountInr(),
                totals.subtotal(), totals.cgstAmount(), totals.sgstAmount(), totals.total(),
                d.getNotes(), d.getValidForDays(), userRef(d.getCreatedBy()), d.getCreatedAt(), d.getUpdatedAt());
    }

    private static DocumentLineItem toLineItem(CreateDocumentItemRequest item) {
        return new DocumentLineItem(item.itemName(), item.description(), item.hsnSac(),
                item.quantity(), item.rateInr());
    }

    private static LeadRefDto leadRef(Lead lead) {
        return lead == null ? null : new LeadRefDto(lead.getId().toString(), lead.getName(),
                lead.getEmail(), lead.getPhone());
    }

    private static PatientRefDto patientRef(User patient) {
        return patient == null ? null : new PatientRefDto(patient.getId().toString(), patient.getName(),
                patient.getEmail(), patient.getPhone());
    }

    private static UserRefDto userRef(User user) {
        return user == null ? null : new UserRefDto(user.getId().toString(), user.getName());
    }
}
