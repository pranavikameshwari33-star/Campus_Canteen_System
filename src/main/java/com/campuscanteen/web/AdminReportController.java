package com.campuscanteen.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.campuscanteen.model.OrderStatus;
import com.campuscanteen.service.CanteenService;
import com.campuscanteen.service.DailyMenuService;
import com.campuscanteen.service.OrderService;
import com.campuscanteen.service.ReportService;

/** Administrator pages for finished orders and the simple daily report. */
@Controller
@RequestMapping("/admin")
public class AdminReportController {

    private final CanteenService canteenService;
    private final DailyMenuService dailyMenuService;
    private final OrderService orderService;
    private final ReportService reportService;

    public AdminReportController(CanteenService canteenService, DailyMenuService dailyMenuService,
                                 OrderService orderService, ReportService reportService) {
        this.canteenService = canteenService;
        this.dailyMenuService = dailyMenuService;
        this.orderService = orderService;
        this.reportService = reportService;
    }

    @GetMapping("/history")
    public String history(@RequestParam(required = false) String date,
                          @RequestParam(required = false) OrderStatus status, Model model) {
        String selectedDate = validDateOrToday(date);
        List<OrderStatus> statuses = status != null && ReportService.FINISHED.contains(status)
                ? List.of(status) : ReportService.FINISHED;
        model.addAttribute("date", selectedDate);
        model.addAttribute("status", status);
        model.addAttribute("statusOptions", ReportService.FINISHED);
        model.addAttribute("orders", orderService.listForDateAndStatuses(
                canteenService.getDefaultCanteen().getId(), selectedDate, statuses));
        return "admin/history";
    }

    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) String date, Model model) {
        String selectedDate = validDateOrToday(date);
        model.addAttribute("date", selectedDate);
        model.addAttribute("report", reportService.dailyReport(canteenService.getDefaultCanteen().getId(), selectedDate));
        return "admin/reports";
    }

    private String validDateOrToday(String date) {
        return date != null && date.matches("^\\d{4}-\\d{2}-\\d{2}$") ? date : dailyMenuService.today();
    }
}
