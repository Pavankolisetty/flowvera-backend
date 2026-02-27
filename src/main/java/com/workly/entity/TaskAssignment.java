package com.workly.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_assignments")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class TaskAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne
    @JoinColumn(name = "emp_id")
    private Employee employee;

    private String assignedBy; // empId of who assigned

    private LocalDateTime assignedAt = LocalDateTime.now(); // When task was assigned

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.PENDING;

    private Integer progress = 0;

    private String assignmentDocPath; // Path to assigned document with custom naming (taskName + empId)
    
    private String submissionDocPath; // Path to employee's submitted document
    
    private Boolean requiresSubmission = false; // Whether this task requires document submission for completion
}
