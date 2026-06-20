package com.workly.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommunicationSummaryDto {
    private boolean hasNewMessages;
    private List<CommunicationMessageDto> messages;
}
