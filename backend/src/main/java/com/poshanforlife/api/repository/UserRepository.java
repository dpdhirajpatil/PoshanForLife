package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * The single account that has proven ownership of this number. Only
     * verified phones are unique, so an unverified duplicate sitting on an
     * abandoned signup never shadows the real owner.
     */
    Optional<User> findByPhoneAndPhoneVerifiedTrue(String phone);

    boolean existsByPhoneAndPhoneVerifiedTrue(String phone);

    /**
     * search must be non-null — pass "" for no filter. A null String param
     * inside lower()/like makes the PG driver bind bytea and blow up with
     * "function lower(bytea) does not exist".
     */
    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:search = ''
                   or lower(u.name) like lower(concat('%', :search, '%'))
                   or lower(u.email) like lower(concat('%', :search, '%')))
            """)
    Page<User> search(@Param("role") Role role, @Param("search") String search, Pageable pageable);

    List<User> findByIdInAndRole(List<UUID> ids, Role role);

    /**
     * Patients assigned to a doctor, newest first, optionally filtered by
     * name/email. search must be non-null — pass "" for no filter (see above).
     */
    @Query("""
            select dp.patient from DoctorPatient dp
            where dp.doctor.id = :doctorId
              and (:search = ''
                   or lower(dp.patient.name) like lower(concat('%', :search, '%'))
                   or lower(dp.patient.email) like lower(concat('%', :search, '%')))
            order by dp.patient.createdAt desc
            """)
    Page<User> searchPatientsOfDoctor(@Param("doctorId") UUID doctorId,
                                      @Param("search") String search,
                                      Pageable pageable);

    @Query("select dp.patient.id from DoctorPatient dp where dp.doctor.id = :doctorId")
    List<UUID> findPatientIdsOfDoctor(@Param("doctorId") UUID doctorId);

    long countByRoleAndIsActiveTrue(Role role);

    /** Broadcast recipients — e.g. every active ADMIN, for new-lead-signup notifications. */
    List<User> findByRoleAndIsActiveTrue(Role role);

    /** One row per active doctor, patientCount 0 for doctors with no assignments. */
    @Query("""
            select d.id as doctorId, d.name as doctorName, count(dp.id) as patientCount
            from User d left join DoctorPatient dp on dp.doctor.id = d.id
            where d.role = :role and d.isActive = true
            group by d.id, d.name
            order by d.name asc
            """)
    List<DoctorPatientCount> countPatientsPerDoctor(@Param("role") Role role);

    interface DoctorPatientCount {
        UUID getDoctorId();

        String getDoctorName();

        Long getPatientCount();
    }
}
