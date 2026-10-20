package com.campuscanteen.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

/**
 * Small formatting helpers used inside templates, e.g. ${@fmt.money(order.totalAmount)}.
 * Times are shown in the canteen's time zone.
 */
@Component("fmt")
public class ViewFormat {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final Clock clock;

    public ViewFormat(Clock clock) {
        this.clock = clock;
    }

    public String money(BigDecimal amount) {
        return amount == null ? "" : "₹" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public String time(Instant instant) {
        return instant == null ? "" : TIME.format(instant.atZone(clock.getZone()));
    }

    public String dateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant.atZone(clock.getZone()));
    }

    /** CSS class for a status label, so colour is used only to show status. */
    public String statusClass(Object status) {
        return status == null ? "status" : "status status-" + status.toString().toLowerCase().replace('_', '-');
    }
}
