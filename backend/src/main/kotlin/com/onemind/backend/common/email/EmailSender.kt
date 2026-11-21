package com.onemind.backend.common.email

import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/** A plain-text email. [to] is personal data: never log it. */
data class EmailMessage(
    val to: String,
    val subject: String,
    val text: String,
)

/**
 * Port for sending emails (ADR 0009). The provider is chosen with hosting (ADR 0004/0008,
 * `docs/OPEN_QUESTIONS.md`); every candidate speaks SMTP, so changing it is configuration only.
 */
interface EmailSender {
    fun send(message: EmailMessage)
}

/** SMTP through Spring's [JavaMailSender] (`spring.mail.*`); Mailpit locally. */
@Component
class SmtpEmailSender(
    private val mailSender: JavaMailSender,
    @param:Value("\${onemind.mail.from}") private val sender: String,
) : EmailSender {
    override fun send(message: EmailMessage) {
        mailSender.send(
            // Not `from`: inside apply it would be the message's own (empty) sender.
            SimpleMailMessage().apply {
                setFrom(sender)
                setTo(message.to)
                subject = message.subject
                text = message.text
            },
        )
    }
}
