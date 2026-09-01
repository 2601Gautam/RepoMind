package com.repomind.repomind.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

// Sends transactional email via Amazon SES v2.
//
// Only one email is wired up right now — the welcome email on registration —
// but the pattern (fire-and-forget, @Async, swallow failures) is the same
// one you'd reuse for ingestion-complete notifications, password resets, etc.
@Service
@Slf4j
public class EmailService {

    private final SesV2Client sesV2Client;

    @Value("${aws.ses.from-email}")
    private String fromEmail;

    public EmailService(SesV2Client sesV2Client) {
        this.sesV2Client = sesV2Client;
    }

    // @Async: registration must never wait on — or fail because of — SES.
    // This runs on Spring's async thread pool (already enabled via
    // @EnableAsync on RepomindApplication) and the HTTP response for
    // /api/auth/register goes back to the client immediately either way.
    @Async
    public void sendWelcomeEmail(String toEmail, String name) {
        try {
            String subject = "Welcome to RepoMind, " + name + "!";
            String bodyText = "Hi " + name + ",\n\n"
                    + "Your RepoMind account is ready. Paste any GitHub repository URL "
                    + "and start chatting with its codebase in a couple of minutes.\n\n"
                    + "— The RepoMind Team";

            SendEmailRequest request = SendEmailRequest.builder()
                    .fromEmailAddress(fromEmail)
                    .destination(Destination.builder().toAddresses(toEmail).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(Content.builder().data(subject).build())
                                    .body(Body.builder()
                                            .text(Content.builder().data(bodyText).build())
                                            .build())
                                    .build())
                            .build())
                    .build();

            sesV2Client.sendEmail(request);
            log.info("Welcome email sent to {}", toEmail);
        } catch (Exception e) {
            // Registration must succeed even if: SES is unreachable, the
            // account is still in sandbox mode and this address isn't
            // verified yet, or the daily sending quota has been hit.
            log.warn("Could not send welcome email to {}: {}", toEmail, e.getMessage());
        }
    }
}
