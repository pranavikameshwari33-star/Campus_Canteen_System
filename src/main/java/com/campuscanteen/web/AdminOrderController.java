package com.campuscanteen.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.campuscanteen.model.DailyMenuItem;
import com.campuscanteen.model.MenuItem;
import com.campuscanteen.model.Order;
import com.campuscanteen.model.OrderStatus;
import com.campuscanteen.model.Payment;
import com.campuscanteen.model.PaymentStatus;
import com.campuscanteen.security.CanteenUserDetails;
import com.campuscanteen.service.BusinessException;
import com.campuscanteen.service.CanteenService;
import com.campuscanteen.service.CurrentMenuService;
import com.campuscanteen.service.CurrentMenuService.CurrentMenu;
import com.campuscanteen.service.DailyMenuService;
import com.campuscanteen.service.MenuItemService;
import com.campuscanteen.service.OrderService;
import com.campuscanteen.service.PaymentService;
import com.campuscanteen.service.QueueService;
import com.campuscanteen.service.ReportService;

/**
 * Administrator pages for day-to-day service: dashboard, payments (verify QR, record cash),
 * the FIFO queue and order details. Only CANTEEN_ADMIN users can reach /admin/** (see SecurityConfig).
 */
@Controller
@RequestMapping("/admin")
public class AdminOrderController {

    private final CanteenService canteenService;
    private final CurrentMenuService currentMenuService;
    private final DailyMenuService dailyMenuService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final QueueService queueService;
    private final ReportService reportService;
    private final MenuItemService menuItemService;
    private final ViewFormat fmt;

    /** Items with this many portions or fewer are listed as "running low" on the dashboard. */
    private static final int LOW_STOCK = 5;

    public AdminOrderController(CanteenService canteenService, CurrentMenuService currentMenuService,
                                DailyMenuService dailyMenuService, OrderService orderService,
                                PaymentService paymentService, QueueService queueService,
                                ReportService reportService, MenuItemService menuItemService, ViewFormat fmt) {
        this.menuItemService = menuItemService;
        this.fmt = fmt;
        this.canteenService = canteenService;
        this.currentMenuService = currentMenuService;
        this.dailyMenuService = dailyMenuService;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.queueService = queueService;
        this.reportService = reportService;
    }

    @GetMapping
    public String dashboard(Model model) {
        String canteenId = canteenService.getDefaultCanteen().getId();
        List<Order> queue = queueService.activeQueue(canteenId);
        CurrentMenu current = currentMenuService.current();
        model.addAttribute("current", current);
        // "Needs attention": draft food items, and sold-out or low items on the menu customers see now.
        List<MenuItem> activeItems = menuItemService.listActive();
        model.addAttribute("noPriceCount", activeItems.stream().filter(i -> i.getDefaultPrice() == null).count());
        model.addAttribute("unconfirmedCount", activeItems.stream().filter(MenuItem::isNameNeedsConfirmation).count());
        List<DailyMenuItem> menuItems = current.menu() == null ? List.of() : current.menu().getItems();
        model.addAttribute("soldOut", menuItems.stream().filter(i -> !i.isOrderable()).map(DailyMenuItem::getName).toList());
        model.addAttribute("runningLow", menuItems.stream()
                .filter(i -> i.isOrderable() && i.getAvailableQuantity() <= LOW_STOCK)
                .map(i -> i.getName() + " (" + i.getAvailableQuantity() + " left)").toList());
        model.addAttribute("qrToCheckCount", queue.stream()
                .filter(o -> PaymentStatus.QR_UNCHECKED.contains(o.getPaymentStatus())).count());
        model.addAttribute("cashDueCount", queue.stream()
                .filter(o -> o.getPaymentStatus() == PaymentStatus.CASH_DUE).count());
        model.addAttribute("queuedCount", queue.stream().filter(o -> o.getOrderStatus() == OrderStatus.QUEUED).count());
        model.addAttribute("preparingCount", queue.stream().filter(o -> o.getOrderStatus() == OrderStatus.PREPARING).count());
        model.addAttribute("readyCount", queue.stream().filter(o -> o.getOrderStatus() == OrderStatus.READY).count());
        model.addAttribute("nextOrders", queue.stream().limit(5).toList());
        model.addAttribute("report", reportService.dailyReport(canteenId, dailyMenuService.today()));
        return "admin/dashboard";
    }

    // ---------- Payments (orders are already in the queue) ----------

    @GetMapping("/payments")
    public String payments(Model model) {
        String canteenId = canteenService.getDefaultCanteen().getId();
        List<PendingPayment> qrToCheck = orderService.listInQueueWithPaymentStatus(canteenId, PaymentStatus.QR_UNCHECKED)
                .stream()
                .map(o -> new PendingPayment(o, paymentService.findForOrder(o.getId()).orElse(null)))
                .toList();
        model.addAttribute("qrToCheck", qrToCheck);
        model.addAttribute("cashDue", orderService.listInQueueWithPaymentStatus(canteenId, List.of(PaymentStatus.CASH_DUE)));
        model.addAttribute("rejected", orderService.listRejectedPayments(canteenId, dailyMenuService.today()));
        return "admin/payments";
    }

    @PostMapping("/orders/{id}/verify")
    public String verify(@PathVariable String id, @AuthenticationPrincipal CanteenUserDetails admin,
                         @RequestParam(defaultValue = "payments") String back, RedirectAttributes redirect) {
        try {
            paymentService.verifyQr(id, admin.getCollegeId());
            redirect.addFlashAttribute("success", "QR payment for token " + orderService.get(id).getTokenDisplay()
                    + " verified.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return redirectBack(back, id);
    }

    @PostMapping("/orders/{id}/reject")
    public String reject(@PathVariable String id, @RequestParam(required = false) String reason,
                         @AuthenticationPrincipal CanteenUserDetails admin,
                         @RequestParam(defaultValue = "payments") String back, RedirectAttributes redirect) {
        try {
            paymentService.rejectQr(id, admin.getCollegeId(), reason);
            redirect.addFlashAttribute("success", "Payment rejected. The order has left the queue and the customer can see the reason.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return redirectBack(back, id);
    }

    /** Records that the customer paid cash at the counter. */
    @PostMapping("/orders/{id}/cash")
    public String cashReceived(@PathVariable String id, @AuthenticationPrincipal CanteenUserDetails admin,
                               @RequestParam(defaultValue = "queue") String back, RedirectAttributes redirect) {
        try {
            paymentService.recordCash(id, admin.getCollegeId());
            Order order = orderService.get(id);
            redirect.addFlashAttribute("success", "Cash of " + fmt.money(order.getTotalAmount())
                    + " recorded for token " + order.getTokenDisplay() + ".");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return redirectBack(back, id);
    }

    /** One click at the counter for a READY cash order: record the cash, then mark it taken. */
    @PostMapping("/orders/{id}/cash-and-taken")
    public String cashAndTaken(@PathVariable String id, @AuthenticationPrincipal CanteenUserDetails admin,
                               @RequestParam(defaultValue = "queue") String back, RedirectAttributes redirect) {
        try {
            Order order = orderService.get(id);
            if (order.getOrderStatus() != OrderStatus.READY) {
                throw new BusinessException("Token " + order.getTokenDisplay() + " is not ready yet.");
            }
            paymentService.recordCash(id, admin.getCollegeId());
            queueService.updateStatus(id, OrderStatus.COMPLETED, admin.getCollegeId());
            redirect.addFlashAttribute("success", "Cash recorded and token " + order.getTokenDisplay()
                    + " collected. The order has left the queue.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return redirectBack(back, id);
    }

    // ---------- Queue ----------

    @GetMapping("/queue")
    public String queue(Model model) {
        model.addAttribute("queue", queueService.activeQueue(canteenService.getDefaultCanteen().getId()));
        return "admin/queue";
    }

    @PostMapping("/orders/{id}/status")
    public String updateStatus(@PathVariable String id, @RequestParam OrderStatus status,
                               @AuthenticationPrincipal CanteenUserDetails admin,
                               @RequestParam(defaultValue = "queue") String back, RedirectAttributes redirect) {
        try {
            queueService.updateStatus(id, status, admin.getCollegeId());
            Order order = orderService.get(id);
            String message = status == OrderStatus.COMPLETED
                    ? "Token " + order.getTokenDisplay() + " collected. The order has left the queue."
                    : "Token " + order.getTokenDisplay() + " is now: " + status.getLabel() + ".";
            redirect.addFlashAttribute("success", message);
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return redirectBack(back, id);
    }

    @PostMapping("/orders/{id}/cancel")
    public String cancel(@PathVariable String id, @RequestParam(required = false) String reason,
                         @AuthenticationPrincipal CanteenUserDetails admin, RedirectAttributes redirect) {
        try {
            queueService.cancel(id, admin.getCollegeId(), reason);
            redirect.addFlashAttribute("success", "Order cancelled and removed from the queue.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    @GetMapping("/orders/{id}")
    public String order(@PathVariable String id, Model model) {
        Order order = orderService.get(id);
        Payment payment = paymentService.findForOrder(id).orElse(null);
        model.addAttribute("order", order);
        model.addAttribute("payment", payment);
        return "admin/order";
    }

    private static String redirectBack(String back, String orderId) {
        return switch (back) {
            case "order" -> "redirect:/admin/orders/" + orderId;
            case "queue" -> "redirect:/admin/queue";
            default -> "redirect:/admin/payments";
        };
    }

    public record PendingPayment(Order order, Payment payment) {
    }
}
