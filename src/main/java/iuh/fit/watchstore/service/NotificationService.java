package iuh.fit.watchstore.service;

import iuh.fit.watchstore.entity.Order;

public interface NotificationService {
    void sendRegistrationEmail(String toEmail, String fullName);
    void sendOrderConfirmationEmail(Order order);
}
