package com.ot.backend.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {

    // Default Twilio Sandbox Credentials injected here or via
    // application.properties.
    @Value("${twilio.account.sid:AC_DUMMY}")
    private String accountSid;

    @Value("${twilio.auth.token:AUTH_DUMMY}")
    private String authToken;

    @Value("${twilio.phone.from:whatsapp:+14155238886}")
    private String fromPhone;

    @Value("${twilio.phone.to:whatsapp:+1234567890}")
    private String toPhone;

    public void sendWhatsAppAlert(String alertMessage) {
        boolean configured = accountSid != null && !accountSid.contains("DUMMY")
                && authToken != null && !authToken.contains("DUMMY");
        try {
            if (configured) {
                Twilio.init(accountSid, authToken);
                Message.creator(new PhoneNumber(toPhone), new PhoneNumber(fromPhone), alertMessage).create();
                log.info("[WHATSAPP] Sent via Twilio to {}", toPhone);
            } else {
                log.info("[WHATSAPP] Twilio credentials not configured (application.properties) — "
                        + "message NOT actually sent, logging only: {}", alertMessage);
            }
        } catch (Exception e) {
            log.warn("Failed to dispatch WhatsApp Alert: {}", e.getMessage());
        }
    }
}
