package com.workly.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "communication_receipts",
    indexes = {
        @Index(name = "idx_comm_receipt_employee_unread", columnList = "recipient_emp_id,unread"),
        @Index(name = "idx_comm_receipt_message", columnList = "message_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class CommunicationReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private CommunicationMessage message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_emp_id", nullable = false)
    private Employee recipient;

    @Column(nullable = false)
    private boolean unread = true;

    private LocalDateTime seenAt;
}
