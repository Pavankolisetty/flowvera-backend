package com.workly.service;

import com.workly.dto.CommunicationAttachmentDto;
import com.workly.dto.CommunicationMessageDto;
import com.workly.dto.CommunicationRecipientOptionsDto;
import com.workly.dto.CommunicationSummaryDto;
import com.workly.dto.EmployeeOptionDto;
import com.workly.entity.CommunicationAttachment;
import com.workly.entity.CommunicationAudience;
import com.workly.entity.CommunicationMessage;
import com.workly.entity.CommunicationReceipt;
import com.workly.entity.Employee;
import com.workly.entity.Role;
import com.workly.repo.CommunicationAttachmentRepository;
import com.workly.repo.CommunicationMessageRepository;
import com.workly.repo.CommunicationReceiptRepository;
import com.workly.repo.EmployeeRepository;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CommunicationServiceImpl implements CommunicationService {

    private static final int MESSAGE_LIMIT = 50;

    private final EmployeeRepository employeeRepo;
    private final CommunicationMessageRepository messageRepo;
    private final CommunicationAttachmentRepository attachmentRepo;
    private final CommunicationReceiptRepository receiptRepo;
    private final FileService fileService;

    @Override
    public CommunicationRecipientOptionsDto getRecipientOptions(String empId) {
        Employee current = getEmployee(empId);
        List<EmployeeOptionDto> employees = employeeRepo.findAll().stream()
            .filter(employee -> Boolean.TRUE.equals(employee.getIsApproved()) || employee.getRole() == Role.ADMIN)
            .filter(employee -> !employee.getEmpId().equals(empId))
            .sorted(Comparator.comparing(employee -> displayName(employee).toLowerCase(Locale.ROOT)))
            .map(employee -> new EmployeeOptionDto(
                employee.getEmpId(),
                displayName(employee),
                safe(employee.getDepartment()),
                employee.getRole() == null ? "" : employee.getRole().name()))
            .toList();

        List<String> departments = employeeRepo.findAll().stream()
            .map(Employee::getDepartment)
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
            .stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();

        return new CommunicationRecipientOptionsDto(
            canSendAnnouncements(current),
            safe(current.getDepartment()),
            employees,
            departments);
    }

    @Override
    @Transactional(readOnly = true)
    public CommunicationSummaryDto getSummary(String empId) {
        return new CommunicationSummaryDto(
            receiptRepo.existsByRecipientEmpIdAndUnreadTrue(empId),
            getMessages(empId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommunicationMessageDto> getMessages(String empId) {
        List<Long> messageIds = receiptRepo.findMessageIdsForRecipient(empId);
        List<CommunicationMessage> messages = messageIds.isEmpty()
            ? messageRepo.findSentMessages(empId, PageRequest.of(0, MESSAGE_LIMIT))
            : messageRepo.findVisibleMessages(empId, messageIds, PageRequest.of(0, MESSAGE_LIMIT));

        return messages.stream()
            .sorted(Comparator.comparing(CommunicationMessage::getCreatedAt))
            .map(message -> toDto(message, empId))
            .toList();
    }

    @Override
    @Transactional
    public CommunicationMessageDto sendMessage(
            String empId,
            String audienceValue,
            String targetEmpId,
            String targetDepartment,
            String body,
            MultipartFile file) throws IOException {
        Employee sender = getEmployee(empId);
        CommunicationAudience audience = parseAudience(audienceValue);
        String cleanBody = body == null ? "" : body.trim();
        boolean hasFile = file != null && !file.isEmpty();

        if (cleanBody.isBlank() && !hasFile) {
            throw new IllegalArgumentException("Please write a message or attach a document.");
        }

        if (audience == CommunicationAudience.ANNOUNCEMENT && !canSendAnnouncements(sender)) {
            throw new IllegalArgumentException("Only admin or department lead can send announcements.");
        }

        CommunicationMessage message = new CommunicationMessage();
        message.setSender(sender);
        message.setAudience(audience);
        message.setBody(cleanBody);

        List<Employee> recipients = resolveRecipients(sender, audience, targetEmpId, targetDepartment);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("No eligible recipients found for this message.");
        }

        if (audience == CommunicationAudience.EMPLOYEE) {
            message.setTargetEmpId(recipients.get(0).getEmpId());
        } else if (audience == CommunicationAudience.DEPARTMENT) {
            message.setTargetDepartment(normalizeTargetDepartment(targetDepartment, sender));
        } else if (audience == CommunicationAudience.ANNOUNCEMENT) {
            message.setTargetDepartment(sender.getRole() == Role.ADMIN ? "All employees" : safe(sender.getDepartment()));
        }

        CommunicationMessage saved = messageRepo.save(message);

        if (hasFile) {
            CommunicationAttachment attachment = new CommunicationAttachment();
            attachment.setMessage(saved);
            attachment.setFilePath(fileService.uploadFile(file, "communication-docs"));
            attachment.setOriginalFileName(file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename());
            attachment.setContentType(file.getContentType());
            attachment.setSizeBytes(file.getSize());
            saved.getAttachments().add(attachment);
        }

        for (Employee recipient : recipients) {
            CommunicationReceipt receipt = new CommunicationReceipt();
            receipt.setMessage(saved);
            receipt.setRecipient(recipient);
            receipt.setUnread(true);
            receiptRepo.save(receipt);
        }

        return toDto(messageRepo.save(saved), empId);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadAttachment(String empId, Long attachmentId) throws IOException {
        CommunicationAttachment attachment = attachmentRepo.findById(attachmentId)
            .orElseThrow(() -> new IllegalArgumentException("Attachment not found."));
        if (!canAccessMessage(empId, attachment.getMessage())) {
            throw new IllegalArgumentException("You are not allowed to access this attachment.");
        }
        return fileService.downloadFile(attachment.getFilePath());
    }

    @Override
    @Transactional(readOnly = true)
    public String getAttachmentFileName(Long attachmentId) {
        return attachmentRepo.findById(attachmentId)
            .map(CommunicationAttachment::getOriginalFileName)
            .orElse("attachment");
    }

    @Override
    @Transactional(readOnly = true)
    public String getAttachmentContentType(Long attachmentId) {
        return attachmentRepo.findById(attachmentId)
            .map(CommunicationAttachment::getContentType)
            .filter(value -> !value.isBlank())
            .orElse("application/octet-stream");
    }

    @Override
    @Transactional
    public void markSeen(String empId) {
        receiptRepo.markAllSeen(empId);
    }

    private List<Employee> resolveRecipients(Employee sender, CommunicationAudience audience, String targetEmpId, String targetDepartment) {
        List<Employee> approved = employeeRepo.findAll().stream()
            .filter(employee -> Boolean.TRUE.equals(employee.getIsApproved()) || employee.getRole() == Role.ADMIN)
            .filter(employee -> !employee.getEmpId().equals(sender.getEmpId()))
            .toList();

        return switch (audience) {
            case EMPLOYEE -> {
                if (targetEmpId == null || targetEmpId.isBlank()) {
                    throw new IllegalArgumentException("Please select an employee.");
                }
                yield approved.stream()
                    .filter(employee -> employee.getEmpId().equals(targetEmpId))
                    .toList();
            }
            case DEPARTMENT -> {
                String department = normalizeTargetDepartment(targetDepartment, sender);
                yield approved.stream()
                    .filter(employee -> sameDepartment(employee.getDepartment(), department))
                    .toList();
            }
            case ALL -> approved;
            case ANNOUNCEMENT -> {
                if (sender.getRole() == Role.ADMIN) {
                    yield approved;
                }
                yield approved.stream()
                    .filter(employee -> sameDepartment(employee.getDepartment(), sender.getDepartment()))
                    .toList();
            }
        };
    }

    private boolean canAccessMessage(String empId, CommunicationMessage message) {
        return message.getSender().getEmpId().equals(empId)
            || receiptRepo.findMessageIdsForRecipient(empId).contains(message.getId());
    }

    private CommunicationAudience parseAudience(String value) {
        try {
            return CommunicationAudience.valueOf((value == null ? "" : value.trim()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Please select a valid message audience.");
        }
    }

    private String normalizeTargetDepartment(String targetDepartment, Employee sender) {
        String department = targetDepartment == null || targetDepartment.isBlank()
            ? sender.getDepartment()
            : targetDepartment.trim();
        if (department == null || department.isBlank()) {
            throw new IllegalArgumentException("Please select a department.");
        }
        return department;
    }

    private Employee getEmployee(String empId) {
        return employeeRepo.findByEmpId(empId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found."));
    }

    private boolean canSendAnnouncements(Employee employee) {
        return employee.getRole() == Role.ADMIN || Boolean.TRUE.equals(employee.getDepartmentLead());
    }

    private CommunicationMessageDto toDto(CommunicationMessage message, String currentEmpId) {
        CommunicationMessageDto dto = new CommunicationMessageDto();
        dto.setId(message.getId());
        dto.setSenderEmpId(message.getSender().getEmpId());
        dto.setSenderName(displayName(message.getSender()));
        dto.setSenderDepartment(safe(message.getSender().getDepartment()));
        dto.setAudience(message.getAudience().name());
        dto.setTargetEmpId(message.getTargetEmpId());
        dto.setTargetDepartment(message.getTargetDepartment());
        dto.setBody(message.getBody());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setSentByMe(message.getSender().getEmpId().equals(currentEmpId));

        if (message.getTargetEmpId() != null) {
            employeeRepo.findByEmpId(message.getTargetEmpId())
                .ifPresent(employee -> dto.setTargetName(displayName(employee)));
        }

        dto.setAttachments(message.getAttachments().stream().map(this::toAttachmentDto).toList());
        return dto;
    }

    private CommunicationAttachmentDto toAttachmentDto(CommunicationAttachment attachment) {
        CommunicationAttachmentDto dto = new CommunicationAttachmentDto();
        dto.setId(attachment.getId());
        dto.setFileName(attachment.getOriginalFileName());
        dto.setContentType(attachment.getContentType());
        dto.setSizeBytes(attachment.getSizeBytes());
        return dto;
    }

    private String displayName(Employee employee) {
        if (employee.getName() != null && !employee.getName().isBlank()) {
            return employee.getName().trim();
        }
        return employee.getEmpId();
    }

    private boolean sameDepartment(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
