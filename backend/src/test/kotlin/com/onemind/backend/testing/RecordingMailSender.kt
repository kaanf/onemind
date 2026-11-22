package com.onemind.backend.testing

import com.onemind.backend.common.email.EmailMessage
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.util.concurrent.CopyOnWriteArrayList

/** Replaces the JavaMailSender under the real EmailSender, so the whole send path runs. */
class RecordingMailSender : JavaMailSenderImpl() {
    val sent = CopyOnWriteArrayList<EmailMessage>()

    override fun send(vararg simpleMessages: SimpleMailMessage) {
        simpleMessages.forEach { sent += EmailMessage(it.to.orEmpty().single(), it.subject.orEmpty(), it.text.orEmpty()) }
    }

    /** The 6-digit code in the last email sent to [email]. */
    fun lastCodeFor(email: String): String =
        sent
            .last { it.to == email }
            .text
            .let { Regex("\\b([0-9]{6})\\b").find(it)?.groupValues?.get(1) ?: error("no code in email") }
}
