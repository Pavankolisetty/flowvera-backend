package com.workly.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Service
public class MailDeliveryServiceImpl implements MailDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(MailDeliveryServiceImpl.class);

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final SesV2Client sesClient;
    private final String provider;
    private final String fromName;
    private final String fromAddress;
    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUsername;
    private final String sendGridApiKey;
    private final String sendGridApiUrl;

    public MailDeliveryServiceImpl(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            ObjectMapper objectMapper,
            @Value("${app.mail.provider:smtp}") String provider,
            @Value("${app.mail.from-name:Workly}") String fromName,
            @Value("${app.mail.from-address:${spring.mail.username:}}") String fromAddress,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${spring.mail.port:587}") int smtpPort,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${sendgrid.api-key:}") String sendGridApiKey,
            @Value("${sendgrid.api-url:https://api.sendgrid.com/v3/mail/send}") String sendGridApiUrl,
            @Value("${aws.region:${AWS_REGION:ap-south-2}}") String awsRegion,
            @Value("${app.mail.http-timeout-seconds:15}") long timeoutSeconds) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.provider = provider == null ? "smtp" : provider.trim().toLowerCase();
        this.fromName = fromName;
        this.fromAddress = fromAddress;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUsername = smtpUsername;
        this.sendGridApiKey = sendGridApiKey;
        this.sendGridApiUrl = sendGridApiUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        this.sesClient = "ses".equals(this.provider)
                ? SesV2Client.builder().region(Region.of(awsRegion)).build()
                : null;
        log.info(
                "Mail delivery configured: provider={}, fromAddress={}, smtpHost={}, smtpPort={}, smtpUsernameConfigured={}, sendGridConfigured={}, sesRegion={}",
                this.provider,
                blankToPlaceholder(this.fromAddress),
                blankToPlaceholder(this.smtpHost),
                this.smtpPort,
                this.smtpUsername != null && !this.smtpUsername.isBlank(),
                this.sendGridApiKey != null && !this.sendGridApiKey.isBlank(),
                awsRegion);
    }

    @Override
    public void sendHtmlEmail(String to, String subject, String html) {
        switch (provider) {
            case "ses" -> sendViaSes(to, subject, html);
            case "sendgrid" -> sendViaSendGrid(to, subject, html);
            case "smtp" -> sendViaSmtp(to, subject, html);
            default -> throw new IllegalStateException("Unsupported mail provider: " + provider);
        }
    }

    private void sendViaSes(String to, String subject, String html) {
        if (sesClient == null) {
            throw new IllegalStateException("SES mail client is not available. Check AWS credentials and region configuration.");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException("Mail sender is not configured. Set MAIL_FROM_ADDRESS.");
        }

        try {
            String source = fromName == null || fromName.isBlank()
                    ? fromAddress
                    : fromName + " <" + fromAddress + ">";

            sesClient.sendEmail(SendEmailRequest.builder()
                    .fromEmailAddress(source)
                    .destination(Destination.builder().toAddresses(to).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(Content.builder().data(subject).charset(StandardCharsets.UTF_8.name()).build())
                                    .body(Body.builder()
                                            .html(Content.builder().data(html).charset(StandardCharsets.UTF_8.name()).build())
                                            .build())
                                    .build())
                            .build())
                    .build());
            log.info("SES mail sent successfully to {} with subject {}", to, subject);
        } catch (Exception ex) {
            log.error("SES mail send failed for {}", to, ex);
            throw new IllegalStateException("Failed to send email. Please try again.", ex);
        }
    }

    private void sendViaSmtp(String to, String subject, String html) {
        if (mailSender == null) {
            throw new IllegalStateException("SMTP mail sender is not available. Check spring-boot-starter-mail and SMTP configuration.");
        }
        if (smtpHost == null || smtpHost.isBlank()) {
            throw new IllegalStateException("Mail is not configured. Set MAIL_HOST, MAIL_USERNAME, and MAIL_PASSWORD.");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException("Mail sender is not configured. Set MAIL_USERNAME.");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            try {
                helper.setFrom(fromAddress, fromName);
            } catch (java.io.UnsupportedEncodingException ex) {
                helper.setFrom(fromAddress);
            }
            helper.setText(html, true);
            mailSender.send(message);
            log.info("SMTP mail sent successfully to {} with subject {} via host {}", to, subject, smtpHost);
        } catch (MessagingException | MailException ex) {
            log.error("SMTP mail send failed for {}", to, ex);
            throw new IllegalStateException("Failed to send email. Please try again.", ex);
        }
    }

    private String blankToPlaceholder(String value) {
        return value == null || value.isBlank() ? "<not set>" : value;
    }

    private void sendViaSendGrid(String to, String subject, String html) {
        if (sendGridApiKey == null || sendGridApiKey.isBlank()) {
            throw new IllegalStateException("SendGrid is not configured. Set SENDGRID_API_KEY.");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException("Mail sender is not configured. Set MAIL_FROM_ADDRESS.");
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "personalizations", List.of(Map.of("to", List.of(Map.of("email", to)))),
                    "from", Map.of("email", fromAddress, "name", fromName),
                    "subject", subject,
                    "content", List.of(Map.of("type", "text/html", "value", html))
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sendGridApiUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + sendGridApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("SendGrid mail send failed for {} with status {} and body {}", to, response.statusCode(), response.body());
                throw new IllegalStateException("Failed to send email through SendGrid. Status: " + response.statusCode());
            }
            log.info("SendGrid mail sent successfully to {} with subject {}", to, subject);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to build SendGrid request payload.", ex);
        } catch (IOException ex) {
            log.error("SendGrid mail send failed for {}", to, ex);
            throw new IllegalStateException("Failed to send email through SendGrid.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("SendGrid mail send interrupted for {}", to, ex);
            throw new IllegalStateException("Failed to send email through SendGrid.", ex);
        }
    }
}
