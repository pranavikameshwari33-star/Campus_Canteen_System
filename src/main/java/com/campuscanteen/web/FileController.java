package com.campuscanteen.web;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.campuscanteen.model.Canteen;
import com.campuscanteen.model.Order;
import com.campuscanteen.model.Payment;
import com.campuscanteen.security.CanteenUserDetails;
import com.campuscanteen.service.CanteenService;
import com.campuscanteen.service.FileStorageService;
import com.campuscanteen.service.NotFoundException;
import com.campuscanteen.service.OrderService;
import com.campuscanteen.service.PaymentService;

/**
 * Sends stored images to the browser after checking permissions.
 * - Payment screenshots: only the customer who placed the order, or an administrator.
 * - Payment QR code: any logged-in user.
 */
@Controller
public class FileController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final CanteenService canteenService;
    private final FileStorageService fileStorage;

    public FileController(OrderService orderService, PaymentService paymentService,
                          CanteenService canteenService, FileStorageService fileStorage) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.canteenService = canteenService;
        this.fileStorage = fileStorage;
    }

    @GetMapping("/files/payment-proof/{orderId}")
    public ResponseEntity<Resource> paymentProof(@PathVariable String orderId,
                                                 @AuthenticationPrincipal CanteenUserDetails user) {
        Order order = user.isAdmin() ? orderService.get(orderId) : orderService.getForCustomer(orderId, user.getId());
        Payment payment = paymentService.getForOrder(order.getId());
        if (!payment.isProofUploaded()) {
            throw new NotFoundException("No screenshot uploaded");
        }
        return image(FileStorageService.PAYMENT_PROOFS, payment.getProofFileName(), true);
    }

    @GetMapping("/files/payment-qr")
    public ResponseEntity<Resource> paymentQr() {
        Canteen canteen = canteenService.getDefaultCanteen();
        if (!canteen.isPaymentQrConfigured()) {
            throw new NotFoundException("QR not configured");
        }
        return image(FileStorageService.QR, canteen.getPaymentQrFile(), false);
    }

    /** Food photos uploaded by an administrator. Not private: every logged-in user sees them on the menu. */
    @GetMapping("/files/menu-photo/{fileName}")
    public ResponseEntity<Resource> menuPhoto(@PathVariable String fileName) {
        return image(FileStorageService.MENU_PHOTOS, fileName, false);
    }

    private ResponseEntity<Resource> image(String folder, String fileName, boolean privateFile) {
        Resource resource = fileStorage.load(folder, fileName);
        return ResponseEntity.ok()
                .contentType(FileStorageService.mediaTypeOf(fileName))
                .cacheControl(privateFile ? CacheControl.noStore() : CacheControl.noCache())
                .body(resource);
    }
}
