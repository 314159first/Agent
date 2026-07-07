package org.xhy.infrastructure.email;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.Properties;

@Service
public class EmailService {
    @Value("${mail.smtp.host}")
    private String host;

    @Value("${mail.smtp.port}")
    private int port;

    @Value("${mail.smtp.username}")
    private String username;

    @Value("${mail.smtp.password}")
    private String password;

    @Value("${mail.smtp.starttls-enable:true}")
    private boolean starttlsEnable;

    @Value("${mail.smtp.starttls-required:true}")
    private boolean starttlsRequired;

    @Value("${mail.smtp.ssl-enable:false}")
    private boolean sslEnable;

    @Value("${mail.smtp.connection-timeout:10000}")
    private int connectionTimeout;

    @Value("${mail.smtp.timeout:10000}")
    private int timeout;

    @Value("${mail.smtp.write-timeout:10000}")
    private int writeTimeout;

    @Value("${mail.verification.subject}")
    private String verificationSubject;

    @Value("${mail.verification.template}")
    private String verificationTemplate;

    public void sendVerificationCode(String to, String code) {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", String.valueOf(connectionTimeout));
        props.put("mail.smtp.timeout", String.valueOf(timeout));
        props.put("mail.smtp.writetimeout", String.valueOf(writeTimeout));

        boolean useSsl = sslEnable || port == 465;
        if (useSsl) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.trust", host);
            props.put("mail.smtp.socketFactory.port", String.valueOf(port));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else if (starttlsEnable) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", String.valueOf(starttlsRequired));
            props.put("mail.smtp.ssl.trust", host);
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(verificationSubject);
            message.setText(String.format(verificationTemplate, code));

            Transport.send(message);
        } catch (MessagingException e) {
            throw new BusinessException("发送邮件失败: " + e.getMessage(), e);
        }
    }
}
