package com.workly.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommunicationRecipientOptionsDto {
    private boolean canSendAnnouncements;
    private String myDepartment;
    private List<EmployeeOptionDto> employees;
    private List<String> departments;
}
