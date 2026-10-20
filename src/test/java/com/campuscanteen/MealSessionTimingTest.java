package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.campuscanteen.model.MealSession;
import com.campuscanteen.service.MealSessionService;

/** Requirement 6: the open meal session follows the configured timings. No database needed. */
class MealSessionTimingTest {

    private static MealSession session(String name, String start, String end, boolean active) {
        MealSession s = new MealSession();
        s.setId(name);
        s.setName(name);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setActive(active);
        return s;
    }

    private final List<MealSession> sessions = List.of(
            session("Breakfast", "07:30", "10:00", true),
            session("Lunch", "11:00", "14:30", true),
            session("Snacks", "15:30", "17:30", true),
            session("Dinner", "19:00", "21:00", false));

    private String openAt(String time) {
        return MealSessionService.findOpenSession(sessions, LocalTime.parse(time)).map(MealSession::getName).orElse("none");
    }

    @Test
    void picksTheSessionMatchingTheTime() {
        assertEquals("Breakfast", openAt("08:15"));
        assertEquals("Lunch", openAt("12:00"));
        assertEquals("Snacks", openAt("16:00"));
    }

    @Test
    void startIsInclusiveEndIsExclusive() {
        assertEquals("Lunch", openAt("11:00"));
        assertEquals("none", openAt("14:30"));
    }

    @Test
    void noSessionBetweenSessionsOrWhenInactive() {
        assertEquals("none", openAt("10:30"));
        assertEquals("none", openAt("19:30"));
    }

    @Test
    void changingTimingsChangesTheOpenSession() {
        MealSession lunch = sessions.get(1);
        lunch.setStartTime("10:15");
        assertEquals("Lunch", openAt("10:30"));
        assertTrue(lunch.isOpenAt(LocalTime.of(10, 15)));
    }
}
