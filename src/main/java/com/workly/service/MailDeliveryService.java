package com.workly.service;

public interface MailDeliveryService {
    void sendHtmlEmail(String to, String subject, String html);
}
