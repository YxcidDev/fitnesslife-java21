package com.fitnesslife.gym.service;

import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import com.fitnesslife.gym.config.RetentionMailProperties;
import com.fitnesslife.gym.exception.RetentionMailException;
import com.fitnesslife.gym.model.RetentionAssignment;
import com.fitnesslife.gym.model.RetentionCampaign;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionEmailService {

    private static final Locale LOCALE = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", LOCALE);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final RetentionMailProperties properties;

    @Value("${spring.mail.username:}")
    private String defaultFrom;

    private record Content(String subject, String template, Map<String, Object> variables) {
    }

    public void send(RetentionAssignment assignment, RetentionCampaign campaign) {
        String to = assignment.getEmail() == null ? "" : assignment.getEmail().trim();
        if (to.isEmpty()) {
            throw new RetentionMailException("La asignación no tiene correo", false);
        }

        Content content = contentFor(assignment, campaign);
        String html = render(content, assignment);

        if (properties.isDryRun()) {
            log.info("[RetentionMail][DRY-RUN] Correo NO enviado -> {} | asunto: {} | {} caracteres de HTML",
                    mask(to), content.subject(), html.length());
            return;
        }
        deliver(to, content.subject(), html);
    }

    private Content contentFor(RetentionAssignment assignment, RetentionCampaign campaign) {
        String gym = properties.getGymName();
        String action = assignment.getAction() == null ? "" : assignment.getAction();

        return switch (action) {
            case "REMINDER" -> new Content(
                    "Te extrañamos en " + gym,
                    "email/retention-reminder",
                    Map.of("ctaUrl", link(properties.getReminderPath())));

            case "PROMOTION" -> {
                double rate = campaign.getParameters() != null ? campaign.getParameters().getDiscountRate() : 0;
                long percent = Math.round(rate * 100);
                LocalDate from = campaign.getLaunchedAt() != null ? campaign.getLaunchedAt().toLocalDate()
                        : LocalDate.now();
                yield new Content(
                        percent + "% de descuento para ti en " + gym,
                        "email/retention-promotion",
                        Map.of("discountPercent", percent,
                                "validUntil", DATE.format(from.plusDays(properties.getPromotionValidDays())),
                                "ctaUrl", link(properties.getPromotionPath())));
            }

            case "CLASS_INVITE" -> new Content(
                    "Te invitamos a una clase grupal en " + gym,
                    "email/retention-class-invite",
                    Map.of("ctaUrl", link(properties.getClassPath())));

            default -> throw new RetentionMailException("No hay mensaje definido para la acción " + action, false);
        };
    }

    private String render(Content content, RetentionAssignment assignment) {
        Map<String, Object> variables = new HashMap<>(content.variables());
        variables.put("firstName", firstName(assignment.getName()));
        variables.put("gymName", properties.getGymName());
        try {
            return templateEngine.process(content.template(), new Context(LOCALE, variables));
        } catch (RuntimeException e) {
            throw new RetentionMailException("No se pudo generar el correo: " + e.getMessage(), e, false);
        }
    }

    private void deliver(String to, String subject, String html) {
        String from = properties.getFrom() != null && !properties.getFrom().isBlank()
                ? properties.getFrom()
                : defaultFrom;
        if (from == null || from.isBlank()) {
            throw new RetentionMailException(
                    "Remitente no configurado (retention.mail.from o spring.mail.username)", false);
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from, properties.getFromName());
            helper.setTo(to);
            helper.setSubject(singleLine(subject));
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RetentionMailException("Mensaje inválido: " + e.getMessage(), e, false);
        } catch (MailAuthenticationException | MailParseException e) {
            throw new RetentionMailException(e.getMessage(), e, false);
        } catch (MailSendException e) {
            throw new RetentionMailException(e.getMessage(), e, !isInvalidAddress(e));
        } catch (MailException e) {
            throw new RetentionMailException(e.getMessage(), e, true);
        }
    }

    private boolean isInvalidAddress(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof AddressException || t instanceof SendFailedException) {
                return true;
            }
        }
        return false;
    }

    private String link(String path) {
        String base = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().replaceAll("/+$", "");
        String suffix = path == null || path.isBlank() ? "" : (path.startsWith("/") ? path : "/" + path);
        return base + suffix;
    }

    private String firstName(String fullName) {
        return fullName == null || fullName.isBlank() ? "" : fullName.trim().split("\\s+")[0];
    }

    private String singleLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String mask(String address) {
        int at = address.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? address.substring(at) : "");
        }
        return address.substring(0, 2) + "***" + address.substring(at);
    }
}
