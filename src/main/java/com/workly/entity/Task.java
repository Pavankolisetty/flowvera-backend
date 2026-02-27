package com.workly.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TaskType taskType;

    private String createdBy; // empId of who created

    private String documentPath; // Path to uploaded document if task type is DOC_TEXT

    private LocalDateTime createdAt = LocalDateTime.now();
}
