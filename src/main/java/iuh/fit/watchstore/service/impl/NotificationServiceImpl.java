package iuh.fit.watchstore.service.impl;

import iuh.fit.watchstore.entity.Order;
import iuh.fit.watchstore.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    @Override
    public void sendRegistrationEmail(String toEmail, String fullName) {
        log.info("==================================================");
        log.info("Mock Email: Registration Successful");
        log.info("To: {}", toEmail);
        log.info("Subject: Welcome to Watch Store!");
        log.info("Body: Hello {}, thank you for registering an account with us. Happy shopping!", fullName);
        log.info("==================================================");
    }

    @Override
    public void sendOrderConfirmationEmail(Order order) {
        log.info("==================================================");
        log.info("Mock Email: Order Confirmation");
        log.info("To: {}", order.getUser().getEmail());
        log.info("Subject: Order Confirmation - Order #{}", order.getId());
        log.info("Body: Hello {}, your order has been received successfully.", order.getReceiverName());
        log.info("Delivery Address: {}", order.getAddress());
        log.info("Total Amount: ${}", order.getTotalAmount());
        log.info("==================================================");
    }
}
