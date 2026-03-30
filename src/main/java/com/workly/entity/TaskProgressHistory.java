package com.workly.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "task_progress_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskProgressHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "task_assignment_id")
    private TaskAssignment taskAssignment;

    private Integer progress;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    private LocalDateTime recordedAt = LocalDateTime.now();

    private String source;
}
