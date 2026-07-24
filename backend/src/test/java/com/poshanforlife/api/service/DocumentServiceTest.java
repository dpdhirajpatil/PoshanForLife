package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CreateDocumentItemRequest;
import com.poshanforlife.api.dto.CreateDocumentRequest;
import com.poshanforlife.api.dto.DocumentDetailDto;
import com.poshanforlife.api.dto.FromOrderRequest;
import com.poshanforlife.api.dto.UpdateDocumentStatusRequest;
import com.poshanforlife.api.entity.Document;
import com.poshanforlife.api.entity.DocumentStatus;
import com.poshanforlife.api.entity.DocumentType;
import com.poshanforlife.api.entity.Lead;
import com.poshanforlife.api.entity.LeadSource;
import com.poshanforlife.api.entity.LeadStage;
import com.poshanforlife.api.entity.Order;
import com.poshanforlife.api.entity.OrderStatus;
import com.poshanforlife.api.entity.PatientProgramme;
import com.poshanforlife.api.entity.PatientProgrammeStatus;
import com.poshanforlife.api.entity.PaymentStatus;
import com.poshanforlife.api.entity.Programme;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.repository.ChallengeRepository;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.DocumentRepository;
import com.poshanforlife.api.repository.LeadRepository;
import com.poshanforlife.api.repository.OrderRepository;
import com.poshanforlife.api.repository.ProgrammeRepository;
import com.poshanforlife.api.repository.SessionRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DoctorPatientRepository doctorPatientRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProgrammeRepository programmeRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private ChallengeRepository challengeRepository;
    @Mock
    private TransactionNumbers transactionNumbers;
    @Mock
    private DocumentPdfRenderer pdfRenderer;
    @Mock
    private DocumentStorageService documentStorageService;

    private DocumentService documentService;

    private User admin;
    private User doctor;
    private User otherDoctor;
    private User patient;
    private AuthenticatedUser adminCaller;
    private AuthenticatedUser doctorCaller;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, leadRepository, userRepository,
                doctorPatientRepository, orderRepository, programmeRepository, sessionRepository,
                challengeRepository, transactionNumbers, pdfRenderer, documentStorageService);

        admin = newUser("Admin", Role.ADMIN);
        doctor = newUser("Dr Priya", Role.DOCTOR);
        otherDoctor = newUser("Dr Arjun", Role.DOCTOR);
        patient = newUser("Pat", Role.PATIENT);
        adminCaller = new AuthenticatedUser(admin.getId().toString(), "admin@poshan.test", Role.ADMIN);
        doctorCaller = new AuthenticatedUser(doctor.getId().toString(), "doctor@poshan.test", Role.DOCTOR);

        lenient().when(userRepository.getReferenceById(admin.getId())).thenReturn(admin);
        lenient().when(userRepository.getReferenceById(doctor.getId())).thenReturn(doctor);
        lenient().when(documentRepository.save(any(Document.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
    }

    @Test
    void create_estimateForLead_generatesEstimateNumberAndDefaultValidForDays() {
        Lead lead = newLead(doctor);
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        when(transactionNumbers.nextEstimateNumber()).thenReturn("EST-202607-0001");
        CreateDocumentRequest request = new CreateDocumentRequest(DocumentType.ESTIMATE, lead.getId(), null,
                List.of(new CreateDocumentItemRequest("Weight loss programme", null, null, 1,
                        new BigDecimal("1000.00"))),
                null, null, null);

        DocumentDetailDto dto = documentService.create(request, adminCaller);

        assertThat(dto.documentNumber()).isEqualTo("EST-202607-0001");
        assertThat(dto.validForDays()).isEqualTo(7);
        verify(transactionNumbers, never()).nextInvoiceNumber();
    }

    @Test
    void create_invoiceForPatient_reusesTheSharedInvoiceNumberSeries() {
        when(userRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(transactionNumbers.nextInvoiceNumber()).thenReturn("INV-202607-0042");
        CreateDocumentRequest request = new CreateDocumentRequest(DocumentType.INVOICE, null, patient.getId(),
                List.of(new CreateDocumentItemRequest("Consultation", null, "9993", 2,
                        new BigDecimal("500.00"))),
                null, null, null);

        DocumentDetailDto dto = documentService.create(request, adminCaller);

        assertThat(dto.documentNumber()).isEqualTo("INV-202607-0042");
        assertThat(dto.validForDays()).isNull();
        verify(transactionNumbers, never()).nextEstimateNumber();
    }

    @Test
    void create_totalsAreGstAwareAndReflectDiscount() {
        when(userRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(transactionNumbers.nextInvoiceNumber()).thenReturn("INV-202607-0001");
        CreateDocumentRequest request = new CreateDocumentRequest(DocumentType.INVOICE, null, patient.getId(),
                List.of(new CreateDocumentItemRequest("Item A", null, null, 2, new BigDecimal("500.00"))),
                null, new BigDecimal("100.00"), null);

        DocumentDetailDto dto = documentService.create(request, adminCaller);

        // items total = 1000, discount 100 -> subtotal 900; CGST/SGST 2.5% each = 22.50; total 945.00
        assertThat(dto.subtotal()).isEqualByComparingTo("900.00");
        assertThat(dto.cgstAmount()).isEqualByComparingTo("22.50");
        assertThat(dto.sgstAmount()).isEqualByComparingTo("22.50");
        assertThat(dto.total()).isEqualByComparingTo("945.00");
    }

    @Test
    void create_bothLeadAndPatientSet_throwsValidationError() {
        CreateDocumentRequest request = new CreateDocumentRequest(DocumentType.ESTIMATE,
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(new CreateDocumentItemRequest("X", null, null, 1, BigDecimal.ONE)), null, null, null);

        assertThatThrownBy(() -> documentService.create(request, adminCaller))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void create_neitherLeadNorPatientSet_throwsValidationError() {
        CreateDocumentRequest request = new CreateDocumentRequest(DocumentType.ESTIMATE, null, null,
                List.of(new CreateDocumentItemRequest("X", null, null, 1, BigDecimal.ONE)), null, null, null);

        assertThatThrownBy(() -> documentService.create(request, adminCaller))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void create_doctorCannotCreateForUnassignedLead() {
        Lead lead = newLead(otherDoctor);
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        CreateDocumentRequest request = new CreateDocumentRequest(DocumentType.ESTIMATE, lead.getId(), null,
                List.of(new CreateDocumentItemRequest("X", null, null, 1, BigDecimal.ONE)), null, null, null);

        assertThatThrownBy(() -> documentService.create(request, doctorCaller))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void create_doctorCannotCreateForUnassignedPatient() {
        when(userRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(doctorPatientRepository.existsByDoctorIdAndPatientId(doctor.getId(), patient.getId()))
                .thenReturn(false);
        CreateDocumentRequest request = new CreateDocumentRequest(DocumentType.INVOICE, null, patient.getId(),
                List.of(new CreateDocumentItemRequest("X", null, null, 1, BigDecimal.ONE)), null, null, null);

        assertThatThrownBy(() -> documentService.create(request, doctorCaller))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void fromOrder_unpaidOrder_throwsValidationError() {
        Order order = newOrder(patient, PaymentStatus.PENDING, null);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> documentService.fromOrder(new FromOrderRequest(order.getId()), adminCaller))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void fromOrder_paidOrder_derivesLineItemFromCatalogueItemAndReusesInvoiceSeries() {
        Programme programme = new Programme();
        setId(programme, UUID.randomUUID());
        programme.setName("12-week transformation");
        PatientProgramme pp = new PatientProgramme();
        pp.setServiceType(CatalogueItemType.PROGRAMME);
        pp.setProgrammeId(programme.getId());
        Order order = newOrder(patient, PaymentStatus.PAID, pp);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));
        when(userRepository.getReferenceById(patient.getId())).thenReturn(patient);
        when(transactionNumbers.nextInvoiceNumber()).thenReturn("INV-202607-0007");

        DocumentDetailDto dto = documentService.fromOrder(new FromOrderRequest(order.getId()), adminCaller);

        assertThat(dto.documentNumber()).isEqualTo("INV-202607-0007");
        assertThat(dto.items()).hasSize(1);
        assertThat(dto.items().get(0).itemName()).isEqualTo("12-week transformation");
        assertThat(dto.items().get(0).rateInr()).isEqualByComparingTo(order.getAmountInr());
    }

    @Test
    void updateStatus_setsStatusWithNoOtherSideEffects() {
        Document document = existingDocument(patient, null);
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        documentService.updateStatus(document.getId(), new UpdateDocumentStatusRequest(DocumentStatus.SENT),
                adminCaller);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.SENT);
    }

    @Test
    void getPdfUrl_rendersOnceThenReusesCachedObjectPath() {
        Document document = existingDocument(patient, null);
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(pdfRenderer.render(any(), any(), any())).thenReturn(new byte[] {1, 2, 3});
        when(documentStorageService.uploadPdf(any(), eq(document.getId()))).thenReturn("obj/path.pdf");
        when(documentStorageService.createSignedUrl("obj/path.pdf")).thenReturn("https://signed/url");

        var first = documentService.getPdfUrl(document.getId(), adminCaller);
        assertThat(first.pdfUrl()).isEqualTo("https://signed/url");
        assertThat(document.getPdfObjectPath()).isEqualTo("obj/path.pdf");

        // second call must not re-render / re-upload
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        documentService.getPdfUrl(document.getId(), adminCaller);
        verify(pdfRenderer, org.mockito.Mockito.times(1)).render(any(), any(), any());
        verify(documentStorageService, org.mockito.Mockito.times(1)).uploadPdf(any(), any());
    }

    // ---- fixtures ------------------------------------------------------

    private Lead newLead(User assignedPractitioner) {
        Lead lead = withId(new Lead());
        lead.setName("Some Lead");
        lead.setSource(LeadSource.WEBSITE);
        lead.setStage(LeadStage.NEW);
        lead.setAssignedPractitioner(assignedPractitioner);
        lead.setCreatedBy(admin);
        return lead;
    }

    private Order newOrder(User patientUser, PaymentStatus paymentStatus, PatientProgramme pp) {
        Order order = withId(new Order());
        order.setPatient(patientUser);
        order.setPatientProgramme(pp);
        order.setAmountInr(new BigDecimal("2500.00"));
        order.setStatus(OrderStatus.ACTIVE);
        order.setPaymentStatus(paymentStatus);
        order.setCreatedBy(admin);
        if (pp != null) {
            pp.setPatient(patientUser);
            pp.setStartDate(LocalDate.now());
            pp.setPriceInr(order.getAmountInr());
            pp.setStatus(PatientProgrammeStatus.ACTIVE);
        }
        return order;
    }

    private Document existingDocument(User patientUser, Lead lead) {
        Document document = withId(new Document());
        document.setDocumentType(DocumentType.INVOICE);
        document.setDocumentNumber("INV-202607-0099");
        document.setPatient(patientUser);
        document.setLead(lead);
        document.setItems(List.of(new com.poshanforlife.api.entity.DocumentLineItem(
                "Item", null, null, 1, new BigDecimal("100.00"))));
        document.setDiscountInr(BigDecimal.ZERO);
        document.setCreatedBy(admin);
        return document;
    }

    private User newUser(String name, Role role) {
        User user = withId(new User());
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(" ", ".") + "@poshan.test");
        user.setRole(role);
        return user;
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field field = findIdField(entity.getClass());
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T withId(T entity) {
        setId(entity, UUID.randomUUID());
        return entity;
    }

    private static Field findIdField(Class<?> type) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField("id");
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("id");
    }
}
