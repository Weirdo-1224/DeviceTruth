package io.devicetruth.server;

import java.net.URI;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** 同步 SMTP 使用有界 socket 等待；不将传输异常或凭证带入 API/日志。 */
public final class SmtpResetMailSender implements PasswordRecoveryService.ResetMailSender {
    private final JavaMailSenderImpl sender;
    private final String from;
    private final String publicUrl;
    public SmtpResetMailSender(JavaMailSenderImpl sender, String from, String publicUrl) {
        this.sender = sender; this.from = from; this.publicUrl = publicUrl;
        if (!publicUrl.isBlank()) {
            URI uri = URI.create(publicUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null)
                throw new IllegalArgumentException("Mail public URL must be an HTTPS URL without credentials/query/fragment");
        }
    }
    public boolean available() { return sender.getHost() != null && !sender.getHost().isBlank() && !from.isBlank() && !publicUrl.isBlank(); }
    public void send(String email, String token) {
        sendLink(email, token, "DeviceTruth 密码重置", "reset-token");
    }
    public void sendInvitation(String email, String token) {
        sendLink(email, token, "DeviceTruth 团队邀请", "invitation-token");
    }
    private void sendLink(String email, String token, String subject, String fragment) {
        if (!available()) throw new IllegalStateException("MAIL_UNAVAILABLE");
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email); message.setSubject(subject);
        message.setText("请在有效期内打开以下链接完成操作。若非本人操作，请忽略。\n" + publicUrl + "#" + fragment + "=" + token);
        sender.send(message);
    }
}
