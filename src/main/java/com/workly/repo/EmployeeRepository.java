package com.workly.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.workly.entity.Employee;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, String> {
    Optional<Employee> findByEmail(String email);
    Optional<Employee> findByEmpId(String empId);
    Optional<Employee> findByPhone(String phone);
    
    @Query("SELECT MAX(e.empId) FROM Employee e")
    String findMaxEmpId();
}
