package com.ayurveda.platform.master.service;

import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderItem;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Service for sending transactional emails (order confirmation, payment confirmation).
 * Email sending is fire-and-forget: exceptions are caught and logged but never propagated,
 * so email failure never breaks order creation.
 * Set MAIL_ENABLED=true environment variable to enable actual sending.
 */
@Service
@Slf4j
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.company-name}")
    private String companyName;

    @Value("${app.mail.store-url}")
    private String storeUrl;

    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends an HTML order confirmation email to the customer.
     * Silently skips if mail is disabled or customer has no email.
     *
     * @param order    The placed order
     * @param customer The customer who placed the order
     */
    public void sendOrderConfirmation(Order order, Customer customer) {
        if (!mailEnabled) {
            log.debug("Mail disabled, skipping order confirmation email for order {}", order.getOrderNumber());
            return;
        }
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            log.debug("No customer email, skipping order confirmation for order {}", order.getOrderNumber());
            return;
        }

        try {
            String subject = "Order Confirmed: " + order.getOrderNumber() + " | " + companyName;
            String html = buildOrderConfirmationHtml(order, customer);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(customer.getEmail());
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(mimeMessage);
            log.info("Order confirmation email sent to {} for order {}", customer.getEmail(), order.getOrderNumber());

        } catch (Exception e) {
            log.warn("Failed to send order confirmation email for order {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    /**
     * Sends a simple payment confirmation email to the customer.
     * Silently skips if mail is disabled or customer has no email.
     *
     * @param order     The order that was paid
     * @param customer  The customer
     * @param paymentId The Razorpay payment ID for reference
     */
    public void sendPaymentConfirmation(Order order, Customer customer, String paymentId) {
        if (!mailEnabled) {
            log.debug("Mail disabled, skipping payment confirmation for order {}", order.getOrderNumber());
            return;
        }
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            log.debug("No customer email, skipping payment confirmation for order {}", order.getOrderNumber());
            return;
        }

        try {
            String subject = "Payment Received: " + order.getOrderNumber() + " | " + companyName;
            String html = buildPaymentConfirmationHtml(order, paymentId);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(customer.getEmail());
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(mimeMessage);
            log.info("Payment confirmation email sent to {} for order {}", customer.getEmail(), order.getOrderNumber());

        } catch (Exception e) {
            log.warn("Failed to send payment confirmation email for order {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    // ---- HTML builders ----

    private String buildOrderConfirmationHtml(Order order, Customer customer) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><body style=\"font-family: Arial, sans-serif; margin:0; padding:0; background:#f5f5f5;\">")
          .append("<div style=\"max-width:600px; margin:20px auto; background:#fff; border-radius:8px; overflow:hidden;\">")

          // Header
          .append("<div style=\"background:#2C5F2E; padding:24px; text-align:center;\">")
          .append("<h1 style=\"color:#fff; margin:0; font-size:24px;\">").append(companyName).append("</h1>")
          .append("<p style=\"color:#c8e6c9; margin:6px 0 0;\">Order Confirmed ✓</p>")
          .append("</div>")

          // Body
          .append("<div style=\"padding:24px;\">")
          .append("<p style=\"font-size:16px;\">Dear <strong>").append(escapeHtml(customer.getName())).append("</strong>,</p>")
          .append("<p>Thank you for your order! We have received it and will process it shortly.</p>")

          // Order info
          .append("<table style=\"width:100%; border-collapse:collapse; margin:16px 0;\">")
          .append("<tr><td style=\"padding:8px; background:#f9f9f9; font-weight:bold;\">Order Number</td>")
          .append("<td style=\"padding:8px;\">").append(escapeHtml(order.getOrderNumber())).append("</td></tr>")
          .append("<tr><td style=\"padding:8px; background:#f9f9f9; font-weight:bold;\">Order Date</td>")
          .append("<td style=\"padding:8px;\">").append(order.getOrderDate()).append("</td></tr>")
          .append("<tr><td style=\"padding:8px; background:#f9f9f9; font-weight:bold;\">Payment Mode</td>")
          .append("<td style=\"padding:8px;\">").append(order.getPaymentMode() != null ? order.getPaymentMode().name() : "N/A").append("</td></tr>")
          .append("</table>");

        // Items table
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            sb.append("<h3 style=\"color:#2C5F2E; border-bottom:2px solid #2C5F2E; padding-bottom:8px;\">Order Items</h3>")
              .append("<table style=\"width:100%; border-collapse:collapse;\">")
              .append("<thead><tr style=\"background:#2C5F2E; color:#fff;\">")
              .append("<th style=\"padding:8px; text-align:left;\">Product</th>")
              .append("<th style=\"padding:8px; text-align:center;\">Qty</th>")
              .append("<th style=\"padding:8px; text-align:right;\">Unit Price</th>")
              .append("<th style=\"padding:8px; text-align:right;\">Total</th>")
              .append("</tr></thead><tbody>");

            for (OrderItem item : order.getItems()) {
                sb.append("<tr style=\"border-bottom:1px solid #eee;\">")
                  .append("<td style=\"padding:8px;\">").append(escapeHtml(item.getProductNameSnapshot())).append("</td>")
                  .append("<td style=\"padding:8px; text-align:center;\">").append(item.getQuantity()).append("</td>")
                  .append("<td style=\"padding:8px; text-align:right;\">₹").append(formatAmount(item.getUnitPrice())).append("</td>")
                  .append("<td style=\"padding:8px; text-align:right;\">₹").append(formatAmount(item.getLineTotal())).append("</td>")
                  .append("</tr>");
            }
            sb.append("</tbody></table>");
        }

        // Totals
        sb.append("<table style=\"width:100%; border-collapse:collapse; margin-top:16px;\">")
          .append("<tr><td style=\"padding:6px; text-align:right; color:#555;\">Subtotal:</td>")
          .append("<td style=\"padding:6px; text-align:right; width:120px;\">₹").append(formatAmount(order.getSubtotal())).append("</td></tr>");

        if (order.getCouponDiscount() != null && order.getCouponDiscount().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("<tr><td style=\"padding:6px; text-align:right; color:#388e3c;\">Coupon Discount:</td>")
              .append("<td style=\"padding:6px; text-align:right; color:#388e3c;\">- ₹").append(formatAmount(order.getCouponDiscount())).append("</td></tr>");
        }

        if (order.getShippingCharge() != null && order.getShippingCharge().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("<tr><td style=\"padding:6px; text-align:right; color:#555;\">Shipping:</td>")
              .append("<td style=\"padding:6px; text-align:right;\">₹").append(formatAmount(order.getShippingCharge())).append("</td></tr>");
        }

        sb.append("<tr style=\"font-weight:bold; font-size:16px; border-top:2px solid #2C5F2E;\">")
          .append("<td style=\"padding:8px; text-align:right;\">Grand Total:</td>")
          .append("<td style=\"padding:8px; text-align:right;\">₹").append(formatAmount(order.getTotalAmount())).append("</td></tr>")
          .append("</table>");

        // Delivery address from notes
        if (order.getNotes() != null && !order.getNotes().isBlank()) {
            sb.append("<div style=\"margin-top:16px; padding:12px; background:#f5f5f5; border-radius:4px;\">")
              .append("<strong>Delivery Details:</strong><br>")
              .append("<span style=\"color:#555; white-space:pre-line;\">").append(escapeHtml(order.getNotes())).append("</span>")
              .append("</div>");
        }

        // Footer
        sb.append("<div style=\"margin-top:24px; padding:16px; background:#f5f5f5; border-radius:4px; text-align:center;\">")
          .append("<p style=\"color:#555; margin:0;\">Track your order at <a href=\"").append(storeUrl).append("/orders\" style=\"color:#2C5F2E;\">")
          .append(storeUrl).append("/orders</a></p>")
          .append("</div>")
          .append("</div></div></body></html>");

        return sb.toString();
    }

    private String buildPaymentConfirmationHtml(Order order, String paymentId) {
        return String.format("""
            <!DOCTYPE html><html><body style="font-family: Arial, sans-serif; background:#f5f5f5;">
            <div style="max-width:600px; margin:20px auto; background:#fff; border-radius:8px; overflow:hidden;">
              <div style="background:#2C5F2E; padding:24px; text-align:center;">
                <h1 style="color:#fff; margin:0;">%s</h1>
                <p style="color:#c8e6c9; margin:6px 0 0;">Payment Received ✓</p>
              </div>
              <div style="padding:24px;">
                <p>Payment has been successfully received for order <strong>%s</strong>.</p>
                <p><strong>Payment Reference:</strong> %s</p>
                <p><strong>Amount Paid:</strong> ₹%s</p>
                <div style="margin-top:16px; text-align:center;">
                  <a href="%s/orders" style="color:#2C5F2E;">Track your order</a>
                </div>
              </div>
            </div>
            </body></html>
            """,
                companyName,
                escapeHtml(order.getOrderNumber()),
                escapeHtml(paymentId),
                formatAmount(order.getTotalAmount()),
                storeUrl
        );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return String.format("%.2f", amount);
    }
}
