package com.campuscanteen.service;

import java.time.Clock;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.campuscanteen.model.Canteen;
import com.campuscanteen.model.DailyMenu;
import com.campuscanteen.model.MealSession;

/**
 * Answers "what can customers order right now?":
 * the open meal session (from the configured timings) and its published menu for today.
 */
@Service
public class CurrentMenuService {

    private final CanteenService canteenService;
    private final MealSessionService mealSessionService;
    private final DailyMenuService dailyMenuService;
    private final Clock clock;

    public CurrentMenuService(CanteenService canteenService, MealSessionService mealSessionService,
                              DailyMenuService dailyMenuService, Clock clock) {
        this.clock = clock;
        this.canteenService = canteenService;
        this.mealSessionService = mealSessionService;
        this.dailyMenuService = dailyMenuService;
    }

    public CurrentMenu current() {
        Canteen canteen = canteenService.getDefaultCanteen();
        List<MealSession> sessions = mealSessionService.listForCanteen(canteen.getId()).stream()
                .filter(MealSession::isActive).toList();
        MealSession open = mealSessionService.findOpenSession(canteen).orElse(null);
        DailyMenu menu = open == null ? null : dailyMenuService.findPublishedMenu(canteen.getId(), open).orElse(null);
        boolean canteenOpen = canteen.isOpenAt(LocalTime.now(clock));
        return new CurrentMenu(canteen, sessions, open, menu, canteenOpen);
    }

    /**
     * @param sessions    all active sessions (to show today's timings)
     * @param openSession the session open now, or null
     * @param menu        the published menu for the open session, or null
     * @param canteenOpen whether the time is inside the canteen's opening hours
     */
    public record CurrentMenu(Canteen canteen, List<MealSession> sessions, MealSession openSession, DailyMenu menu,
                              boolean canteenOpen) {
        /** When ordering for the open session stops: the session end or the canteen closing time, whichever is first. */
        public String getOrdersCloseAt() {
            if (openSession == null) {
                return null;
            }
            String closing = canteen.getClosingTime();
            return closing != null && closing.compareTo(openSession.getEndTime()) < 0 ? closing : openSession.getEndTime();
        }

        public boolean isOrderingOpen() {
            return openSession != null && menu != null;
        }
    }
}
