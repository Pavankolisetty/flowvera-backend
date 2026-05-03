package com.workly.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.workly.entity.Employee;
import com.workly.entity.Role;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, String> {
    Optional<Employee> findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
    List<Employee> findAllByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<Employee> findByEmpId(String empId);
    Optional<Employee> findByPhone(String phone);
    List<Employee> findByIsApprovedFalseAndRole(Role role);
    List<Employee> findByRole(Role role);
    List<Employee> findByReportingManagerEmpIdAndIsApprovedTrue(String reportingManagerEmpId);

    @Query("SELECT MAX(e.empId) FROM Employee e")
    String findMaxEmpId();
}
