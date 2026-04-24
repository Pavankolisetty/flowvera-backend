package com.workly.service;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DepartmentDirectory {

    private static final Map<String, String> DEPARTMENT_CODES = Map.of(
        "Electronics", "ED",
        "Bio Med", "BD",
        "Design", "DD",
        "Software", "SD"
    );

    private static final Map<String, Set<String>> DEPARTMENT_ROLES = Map.of(
        "Electronics", Set.of(
            "Embedded Firmware Engineer",
            "Circuit Designer",
            "Circuit Testing Engineer"
        ),
        "Bio Med", Set.of(
            "Research And Development Engineer",
            "Research Engineer"
        ),
        "Design", Set.of("CAD Designer"),
        "Software", Set.of(
            "Full Stack Developer",
            "Cloud Engineer",
            "Front End Developer",
            "Backend Developer",
            "Data Scientist"
        )
    );

    public boolean isValidDepartment(String department) {
        return DEPARTMENT_CODES.containsKey(normalizeDepartment(department));
    }

    public boolean isValidRole(String department, String designation) {
        String normalizedDepartment = normalizeDepartment(department);
        String normalizedDesignation = normalizeDesignation(designation);
        return DEPARTMENT_ROLES.getOrDefault(normalizedDepartment, Set.of()).contains(normalizedDesignation);
    }

    public String getDepartmentCode(String department) {
        String code = DEPARTMENT_CODES.get(normalizeDepartment(department));
        if (code == null) {
            throw new IllegalArgumentException("Unsupported department selected.");
        }
        return code;
    }

    public String normalizeDepartment(String department) {
        String value = department == null ? "" : department.trim().replaceAll("\\s+", " ");
        return switch (value.toLowerCase()) {
            case "electronics" -> "Electronics";
            case "bio med", "biomed", "bio-med", "biomedical" -> "Bio Med";
            case "design" -> "Design";
            case "software" -> "Software";
            default -> value;
        };
    }

    public String normalizeDesignation(String designation) {
        String value = designation == null ? "" : designation.trim().replaceAll("\\s+", " ");
        return switch (value.toLowerCase()) {
            case "embedded firmware engineer" -> "Embedded Firmware Engineer";
            case "circuit designer" -> "Circuit Designer";
            case "circuit testing engineer" -> "Circuit Testing Engineer";
            case "research and development engineer" -> "Research And Development Engineer";
            case "research engineer" -> "Research Engineer";
            case "cad designer" -> "CAD Designer";
            case "full stack developer" -> "Full Stack Developer";
            case "cloud engineer" -> "Cloud Engineer";
            case "frontend developer", "front end developer" -> "Front End Developer";
            case "backend developer", "back end developer" -> "Backend Developer";
            case "data scientist" -> "Data Scientist";
            default -> value;
        };
    }
}
