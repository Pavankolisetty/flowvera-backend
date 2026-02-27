package com.workly.service;

import com.workly.entity.Employee;
import com.workly.entity.Role;
import com.workly.repo.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Create default admin if no employees exist
        if (employeeRepository.count() == 0) {
            Employee admin = new Employee();
            admin.setEmpId("0001");
            admin.setName("Admin User");
            admin.setEmail("admin@flowvera.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setPhone("1234567890");
            admin.setRole(Role.ADMIN);
            
            employeeRepository.save(admin);
            System.out.println("Default admin created - EmpId: 0001, Password: admin123");
        }
    }
}