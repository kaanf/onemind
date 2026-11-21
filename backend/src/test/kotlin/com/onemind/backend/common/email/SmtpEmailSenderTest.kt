package com.onemind.backend.common.email

import org.junit.jupiter.api.Test
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSenderImpl
import kotlin.test.assertEquals

class SmtpEmailSenderTest {
    private class RecordingMailSender : JavaMailSenderImpl() {
        val sent = mutableListOf<SimpleMailMessage>()

        override fun send(vararg simpleMessages: SimpleMailMessage) {
            sent += simpleMessages
        }
    }

    @Test
    fun `sends from the configured sender`() {
        val mail = RecordingMailSender()

        SmtpEmailSender(mail, "OneMind <no-reply@onemind.app>").send(EmailMessage("owner@example.com", "Subject", "Text"))

        val message = mail.sent.single()
        assertEquals("OneMind <no-reply@onemind.app>", message.from)
        assertEquals(listOf("owner@example.com"), message.to?.toList())
        assertEquals("Subject", message.subject)
        assertEquals("Text", message.text)
    }
}
