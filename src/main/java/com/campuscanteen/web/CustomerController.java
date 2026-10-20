package com.campuscanteen.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.campuscanteen.model.DailyMenuItem;
import com.campuscanteen.model.MenuItem;
import com.campuscanteen.model.Order;
import com.campuscanteen.model.PaymentMethod;
import com.campuscanteen.security.CanteenUserDetails;
import com.campuscanteen.service.BusinessException;
import com.campuscanteen.service.CanteenService;
import com.campuscanteen.service.Cart;
import com.campuscanteen.service.CartView;
import com.campuscanteen.service.CurrentMenuService;
import com.campuscanteen.service.CurrentMenuService.CurrentMenu;
import com.campuscanteen.service.MenuItemService;
import com.campuscanteen.service.OrderService;
import com.campuscanteen.service.PaymentService;

import jakarta.servlet.http.HttpSession;

/**
 * Pages for students and faculty (both use exactly the same pages and rules):
 * home, menu, cart, checkout (Cash or QR Code), QR payment, and their own orders.
 */
@Controller
public class CustomerController {

    private final CurrentMenuService currentMenuService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final CanteenService canteenService;
    private final ViewFormat fmt;

    public CustomerController(CurrentMenuService currentMenuService, OrderService orderService,
                              PaymentService paymentService, CanteenService canteenService, ViewFormat fmt) {
        this.currentMenuService = currentMenuService;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.canteenService = canteenService;
        this.fmt = fmt;
    }

    @GetMapping("/home")
    public String home(@AuthenticationPrincipal CanteenUserDetails user, HttpSession session, Model model) {
        List<Order> orders = orderService.listForCustomer(user.getId());
        model.addAttribute("activeOrders", orders.stream().filter(o -> !o.getOrderStatus().isFinished()).toList());
        model.addAttribute("recentFinished", orders.stream().filter(o -> o.getOrderStatus().isFinished()).limit(3).toList());
        model.addAttribute("current", currentMenuService.current());
        model.addAttribute("cartCount", cart(session).getItemCount());
        return "customer/home";
    }

    // ---------- Menu and cart ----------

    @GetMapping("/menu")
    public String menu(HttpSession session, Model model) {
        CurrentMenu current = currentMenuService.current();
        model.addAttribute("current", current);
        Cart cart = cart(session);
        if (current.isOrderingOpen()) {
            explainChange(cart.attachTo(current.menu()), model);
            // Group by section in the order of the printed menu (Breakfast, Lunch, Snacks, Chinese - Rice, ...), names A-Z.
            Map<String, List<DailyMenuItem>> byCategory = new LinkedHashMap<>();
            current.menu().getItems().stream()
                    .sorted(Comparator.comparingInt((DailyMenuItem i) -> MenuItemService.categoryPosition(i.getCategory()))
                            .thenComparing(i -> i.getSubcategory() == null ? "" : i.getSubcategory())
                            .thenComparing(DailyMenuItem::getName, String.CASE_INSENSITIVE_ORDER))
                    .forEach(i -> byCategory.computeIfAbsent(MenuItem.categoryLabel(i.getCategory(), i.getSubcategory()),
                            k -> new ArrayList<>()).add(i));
            model.addAttribute("itemsByCategory", byCategory);
        } else if (!cart.isEmpty()) {
            cart.clear();
            model.addAttribute("notice", "Ordering is closed now, so your cart was emptied.");
        }
        model.addAttribute("cart", cart);
        model.addAttribute("cartCount", cart.getItemCount());
        return "customer/menu";
    }

    @PostMapping("/cart/add")
    public String addToCart(@RequestParam String menuItemId, @RequestParam(defaultValue = "1") int quantity,
                            HttpSession session, RedirectAttributes redirect) {
        CurrentMenu current = currentMenuService.current();
        if (!current.isOrderingOpen()) {
            redirect.addFlashAttribute("error", "Ordering is closed right now.");
            return "redirect:/menu";
        }
        DailyMenuItem item = current.menu().findItem(menuItemId).orElse(null);
        if (item == null || !item.isOrderable()) {
            redirect.addFlashAttribute("error", "That item is not available.");
            return "redirect:/menu";
        }
        Cart cart = cart(session);
        Cart.Change change = cart.attachTo(current.menu());
        if (change != null) {
            redirect.addFlashAttribute("notice", change.message());
        }
        int newQuantity = cart.getQuantities().getOrDefault(menuItemId, 0) + Math.max(quantity, 1);
        int limit = Math.min(CartView.MAX_PER_ITEM, item.getAvailableQuantity());
        if (newQuantity > limit) {
            newQuantity = limit;
            redirect.addFlashAttribute("error", "You can add at most " + limit + " of " + item.getName() + ".");
        } else {
            redirect.addFlashAttribute("success", item.getName() + " added to your cart.");
        }
        cart.setQuantity(item, newQuantity);
        return "redirect:/menu";
    }

    @GetMapping("/cart")
    public String viewCart(HttpSession session, Model model) {
        CurrentMenu current = currentMenuService.current();
        Cart cart = cart(session);
        model.addAttribute("current", current);
        if (current.isOrderingOpen()) {
            explainChange(cart.attachTo(current.menu()), model);
            model.addAttribute("cartView", CartView.calculate(current.menu(), cart));
        } else {
            if (!cart.isEmpty()) {
                model.addAttribute("notice", "Ordering is closed now, so your cart was emptied.");
            }
            cart.clear();
        }
        return "customer/cart";
    }

    @PostMapping("/cart/update")
    public String updateCart(@RequestParam String menuItemId, @RequestParam int quantity, HttpSession session) {
        cart(session).setQuantity(menuItemId, Math.min(Math.max(quantity, 0), CartView.MAX_PER_ITEM));
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam String menuItemId, HttpSession session) {
        cart(session).setQuantity(menuItemId, 0);
        return "redirect:/cart";
    }

    // ---------- Checkout ----------

    @GetMapping("/checkout")
    public String checkout(HttpSession session, Model model, RedirectAttributes redirect) {
        CurrentMenu current = currentMenuService.current();
        Cart cart = cart(session);
        if (!current.isOrderingOpen()) {
            redirect.addFlashAttribute("error", "Ordering is closed right now.");
            return "redirect:/cart";
        }
        Cart.Change change = cart.attachTo(current.menu());
        if (change != null) {
            // The session changed: let the customer review the cart before paying.
            redirect.addFlashAttribute("notice", change.message());
            return "redirect:/cart";
        }
        CartView view = CartView.calculate(current.menu(), cart);
        if (!view.isReadyForCheckout()) {
            if (view.getProblems().isEmpty()) {
                redirect.addFlashAttribute("error", "Your cart is empty.");
            }
            return "redirect:/cart";
        }
        model.addAttribute("current", current);
        model.addAttribute("cartView", view);
        // A fresh random key for this checkout page. Submitting it twice (double click, refresh,
        // back button) returns the same order instead of creating a second one.
        model.addAttribute("checkoutKey", UUID.randomUUID().toString());
        return "customer/checkout";
    }

    /**
     * The customer chose Cash or QR Code. The order is created, gets its token and joins the
     * FIFO queue right now, for both methods. QR customers then go to the QR payment page.
     */
    @PostMapping("/checkout")
    public String placeOrder(@AuthenticationPrincipal CanteenUserDetails user,
                             @RequestParam(value = "paymentMethod", required = false) String paymentMethod,
                             @RequestParam(value = "checkoutKey", required = false) String checkoutKey,
                             HttpSession session, RedirectAttributes redirect) {
        Cart cart = cart(session);
        try {
            Order order = orderService.placeOrder(user.getId(), cart, parseMethod(paymentMethod), checkoutKey);
            if (order.getPaymentMethod() == PaymentMethod.QR) {
                redirect.addFlashAttribute("success", "Order placed and added to the queue. Your token is "
                        + order.getTokenDisplay() + ". Now pay " + fmt.money(order.getTotalAmount()) + " using the QR code below.");
                return "redirect:/orders/" + order.getId() + "/pay";
            }
            redirect.addFlashAttribute("success", "Order placed and added to the queue. Your token is "
                    + order.getTokenDisplay() + ". Please pay " + fmt.money(order.getTotalAmount()) + " in cash when you collect your food.");
            return "redirect:/orders/" + order.getId();
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return cart.isEmpty() ? "redirect:/menu" : "redirect:/checkout";
        }
    }

    private static PaymentMethod parseMethod(String value) {
        if (value == null) {
            return null;
        }
        try {
            return PaymentMethod.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------- QR payment (the order is already in the queue) ----------

    @GetMapping("/orders/{id}/pay")
    public String payByQr(@PathVariable String id, @AuthenticationPrincipal CanteenUserDetails user, Model model) {
        Order order = orderService.getForCustomer(id, user.getId());
        if (order.getPaymentMethod() != PaymentMethod.QR) {
            return "redirect:/orders/" + id;
        }
        model.addAttribute("order", order);
        model.addAttribute("payment", paymentService.findForOrder(order.getId()).orElse(null));
        model.addAttribute("canteen", canteenService.getDefaultCanteen());
        return "customer/pay";
    }

    @PostMapping("/orders/{id}/payment-proof")
    public String uploadProof(@PathVariable String id, @AuthenticationPrincipal CanteenUserDetails user,
                              @RequestParam(value = "paymentProof", required = false) MultipartFile paymentProof,
                              @RequestParam(value = "transactionRef", required = false) String transactionRef,
                              RedirectAttributes redirect) {
        try {
            orderService.submitPaymentProof(id, user.getId(), paymentProof, transactionRef);
            redirect.addFlashAttribute("success", "Payment screenshot sent. Canteen staff will check it. "
                    + "Your order stays in the queue with the same token.");
            return "redirect:/orders/" + id;
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/orders/" + id + "/pay";
        }
    }

    // ---------- Orders ----------

    @GetMapping("/orders")
    public String orders(@AuthenticationPrincipal CanteenUserDetails user, Model model) {
        model.addAttribute("orders", orderService.listForCustomer(user.getId()));
        return "customer/orders";
    }

    @GetMapping("/orders/{id}")
    public String order(@PathVariable String id, @AuthenticationPrincipal CanteenUserDetails user, Model model) {
        // getForCustomer only returns the order if it belongs to the logged-in user.
        Order order = orderService.getForCustomer(id, user.getId());
        model.addAttribute("order", order);
        model.addAttribute("payment", paymentService.findForOrder(order.getId()).orElse(null));
        model.addAttribute("ordersAhead", orderService.countOrdersAhead(order));
        return "customer/order";
    }

    private static void explainChange(Cart.Change change, Model model) {
        if (change != null) {
            model.addAttribute("notice", change.message());
        }
    }

    private static Cart cart(HttpSession session) {
        Cart cart = (Cart) session.getAttribute(Cart.SESSION_KEY);
        if (cart == null) {
            cart = new Cart();
            session.setAttribute(Cart.SESSION_KEY, cart);
        }
        return cart;
    }
}
