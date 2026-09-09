package com.ghaniram.zoya

import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Real SMTP email sender using credentials from AnuSettingsStore.
 * Supports Gmail App Password (port 465 SSL or 587 STARTTLS).
 */
object EmailSender {

    data class Result(val success: Boolean, val message: String)

    fun send(
        store: AnuSettingsStore,
        to: String,
        subject: String,
        body: String
    ): Result {
        val from = store.emailAddress.trim()
        val password = store.emailAppPassword.trim().replace(" ", "")
        if (from.isBlank() || password.isBlank()) {
            return Result(false, "Email is not configured. Add Gmail + App Password in Settings \u2192 Email.")
        }
        if (to.isBlank()) {
            return Result(false, "Recipient address is missing.")
        }

        val host = store.emailSmtpHost.trim().ifBlank { "smtp.gmail.com" }
        val port = store.emailSmtpPort.trim().ifBlank { "465" }
        val senderName = store.emailSenderName.trim().ifBlank { store.userName.ifBlank { "Anu" } }
        val signature = store.emailSignature.trim()
        val fullBody = if (signature.isNotBlank()) "$body\n\n$signature" else body

        return try {
            val props = Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", port)
                put("mail.smtp.auth", "true")
                if (port == "465") {
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    put("mail.smtp.socketFactory.port", "465")
                    put("mail.smtp.socketFactory.fallback", "false")
                } else {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                }
                put("mail.smtp.connectiontimeout", "15000")
                put("mail.smtp.timeout", "20000")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(from, password)
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(from, senderName))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to.trim()))
                this.subject = subject.ifBlank { "Message from Anu" }
                setText(fullBody, "utf-8")
            }

            Transport.send(message)
            Result(true, "Email sent to $to")
        } catch (e: Exception) {
            Result(false, "Failed to send email: ${e.message ?: "unknown error"}")
        }
    }
}
