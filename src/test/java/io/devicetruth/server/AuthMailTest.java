package io.devicetruth.server;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.assertj.core.api.Assertions.*;

class AuthMailTest {
    @Test void productionConfigurationRequiresTlsAndFiniteTimeouts() {
        var environment = new org.springframework.mock.env.MockEnvironment()
                .withProperty("devicetruth.mail.host", "smtp.example.com")
                .withProperty("devicetruth.mail.from", "noreply@example.com")
                .withProperty("devicetruth.mail.public-url", "https://app.example.com/");
        assertThat(new ProductAuthConfiguration().resetMailSender(environment).available()).isTrue();
        environment.setProperty("devicetruth.mail.timeout-ms", "0");
        assertThatThrownBy(() -> new ProductAuthConfiguration().resetMailSender(environment)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SmtpResetMailSender(new JavaMailSenderImpl(), "from", "http://app.example.com/"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void limiterBoundsSourceAccountCapacityAndExpires() {
        var now = new AtomicLong();
        var limiter = new AuthRateLimiter(2, 4, Duration.ofSeconds(1), now::get);
        limiter.check("login", "a", "User@example.com");
        limiter.check("login", "b", " user@example.com ");
        assertThatThrownBy(() -> limiter.check("login", "c", "user@example.com")).hasMessageContaining("AUTH_RATE_LIMITED");
        now.set(Duration.ofSeconds(2).toNanos());
        limiter.check("login", "c", "user@example.com");
        var tiny = new AuthRateLimiter(10, 2, Duration.ofMinutes(1), now::get);
        tiny.check("register", "a", "a@example.com");
        assertThatThrownBy(() -> tiny.check("register", "b", "b@example.com")).hasMessageContaining("AUTH_RATE_LIMITED");
    }
    @Test void smtpProtocolAcceptsMessageAndFailurePropagates() throws Exception {
        runSmtp(false);
        runSmtp(true);
    }
    private void runSmtp(boolean reject) throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()); var executor = Executors.newSingleThreadExecutor()) {
            var received = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    var out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                    out.println("220 test SMTP");
                    StringBuilder data = new StringBuilder();
                    boolean body = false;
                    for (String line; (line = in.readLine()) != null;) {
                        if (body) {
                            if (line.equals(".")) { body = false; out.println("250 accepted"); }
                            else data.append(line).append('\n');
                        } else if (line.startsWith("EHLO") || line.startsWith("HELO")) out.println("250 localhost");
                        else if (line.startsWith("RCPT")) out.println(reject ? "550 rejected" : "250 ok");
                        else if (line.equals("DATA")) { out.println("354 send message"); body = true; }
                        else if (line.equals("QUIT")) { out.println("221 bye"); break; }
                        else out.println("250 ok");
                    }
                    return data.toString();
                }
            });
            var smtp = sender(server.getLocalPort());
            var adapter = new SmtpResetMailSender(smtp, "noreply@example.com", "https://app.example.com/");
            if (reject) assertThatThrownBy(() -> adapter.send("repair@example.com", "test-token")).isInstanceOf(org.springframework.mail.MailException.class);
            else adapter.send("repair@example.com", "test-token");
            String body = received.get(5, TimeUnit.SECONDS);
            if (!reject) {
                var message = new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()),
                        new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
                assertThat(message.getContent().toString()).contains("https://app.example.com/#reset-token=test-token");
                assertThat(message.getAllRecipients()[0].toString()).isEqualTo("repair@example.com");
            }
        }
    }
    @Test void unresponsiveSmtpReadHasBoundedTimeout() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()); var executor = Executors.newSingleThreadExecutor()) {
            var connected = executor.submit(() -> { try (var socket = server.accept()) { Thread.sleep(800); } return true; });
            var adapter = new SmtpResetMailSender(sender(server.getLocalPort()), "noreply@example.com", "https://app.example.com/");
            long start = System.nanoTime();
            assertThatThrownBy(() -> adapter.send("repair@example.com", "secret-token")).isInstanceOf(org.springframework.mail.MailException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
            connected.get(5, TimeUnit.SECONDS);
        }
    }
    private JavaMailSenderImpl sender(int port) {
        var sender = new JavaMailSenderImpl(); sender.setHost("127.0.0.1"); sender.setPort(port); sender.setDefaultEncoding("UTF-8");
        for (String name : java.util.List.of("connectiontimeout", "timeout", "writetimeout")) sender.getJavaMailProperties().setProperty("mail.smtp." + name, "200");
        return sender;
    }
}
