package com.campuscanteen.service;

import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.campuscanteen.model.Canteen;
import com.campuscanteen.model.MealSession;
import com.campuscanteen.model.MealType;
import com.campuscanteen.model.MenuItem;
import com.campuscanteen.repository.MealSessionRepository;
import com.campuscanteen.web.form.MealSessionForm;

/**
 * Meal sessions (Breakfast, Lunch, Snacks) and their timings, as configured by the administrator.
 * The "open" session is worked out from these timings, the canteen's opening hours and the
 * current time in the canteen's time zone (Asia/Kolkata by default); nothing is hardcoded.
 * Active sessions of one canteen may not overlap, so at most one session is open at any moment.
 */
@Service
public class MealSessionService {

    private final MealSessionRepository repository;
    private final Clock clock;

    public MealSessionService(MealSessionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<MealSession> listForCanteen(String canteenId) {
        return repository.findByCanteenIdOrderByStartTimeAsc(canteenId);
    }

    public MealSession get(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Meal session not found"));
    }

    /** The session that is open right now, if any (also requires the canteen itself to be open). */
    public Optional<MealSession> findOpenSession(Canteen canteen) {
        return findOpenSession(canteen, listForCanteen(canteen.getId()), LocalTime.now(clock));
    }

    /** Pure helper (easy to test): which of these sessions is open at the given time. */
    public static Optional<MealSession> findOpenSession(Canteen canteen, List<MealSession> sessions, LocalTime time) {
        if (canteen != null && !canteen.isOpenAt(time)) {
            return Optional.empty();
        }
        return sessions.stream().filter(s -> s.isOpenAt(time)).findFirst();
    }

    public static Optional<MealSession> findOpenSession(List<MealSession> sessions, LocalTime time) {
        return findOpenSession(null, sessions, time);
    }

    public MealSession save(String canteenId, MealSessionForm form) {
        LocalTime start = LocalTime.parse(form.getStartTime());
        LocalTime end = LocalTime.parse(form.getEndTime());
        if (!start.isBefore(end)) {
            throw new BusinessException("End time must be after start time (sessions cannot cross midnight).");
        }
        MealType type = form.getType() == null ? MealType.OTHER : form.getType();
        List<String> categories = new ArrayList<>();
        for (String category : form.getAllowedCategories() == null ? List.<String>of() : form.getAllowedCategories()) {
            if (!MenuItem.CATEGORIES.contains(category)) {
                throw new BusinessException("Unknown food category: " + category + ".");
            }
            if (!categories.contains(category)) {
                categories.add(category);
            }
        }
        if (categories.isEmpty()) {
            throw new BusinessException("Tick at least one food category for this session.");
        }
        if (categories.contains(MenuItem.CHINESE) && !type.allowsChinese()) {
            throw new BusinessException("Chinese items can only be sold in Lunch or Snacks sessions, not in a "
                    + type.getLabel() + " session.");
        }
        MealSession session = form.getId() == null || form.getId().isBlank()
                ? new MealSession()
                : get(form.getId());
        session.setCanteenId(canteenId);
        session.setName(form.getName().trim());
        session.setType(type);
        session.setStartTime(form.getStartTime());
        session.setEndTime(form.getEndTime());
        session.setAllowedCategories(categories);
        session.setActive(form.isActive());
        checkNoOverlap(session);
        return repository.save(session);
    }

    public MealSessionForm toForm(MealSession session) {
        MealSessionForm form = new MealSessionForm();
        form.setId(session.getId());
        form.setName(session.getName());
        form.setType(session.getType());
        form.setStartTime(session.getStartTime());
        form.setEndTime(session.getEndTime());
        form.setAllowedCategories(new ArrayList<>(session.getEffectiveCategories()));
        form.setActive(session.isActive());
        return form;
    }

    public void setActive(String id, boolean active) {
        MealSession session = get(id);
        session.setActive(active);
        checkNoOverlap(session);
        repository.save(session);
    }

    /**
     * Creates the standard sessions for a canteen that has none:
     * Breakfast from opening time until 12:00, Lunch 12:00-15:00 (with Chinese),
     * Snacks from 15:00 until closing time (with Chinese). Returns how many were created.
     */
    public int createDefaultSessions(Canteen canteen) {
        if (!listForCanteen(canteen.getId()).isEmpty()) {
            return 0;
        }
        String opening = canteen.getOpeningTime() != null && canteen.getOpeningTime().compareTo("12:00") < 0
                ? canteen.getOpeningTime() : "07:30";
        String closing = canteen.getClosingTime() != null && canteen.getClosingTime().compareTo("15:00") > 0
                ? canteen.getClosingTime() : "18:00";
        repository.save(newSession(canteen.getId(), "Breakfast", MealType.BREAKFAST, opening, "12:00",
                List.of(MenuItem.BREAKFAST)));
        repository.save(newSession(canteen.getId(), "Lunch", MealType.LUNCH, "12:00", "15:00",
                List.of(MenuItem.LUNCH, MenuItem.CHINESE)));
        repository.save(newSession(canteen.getId(), "Snacks", MealType.SNACKS, "15:00", closing,
                List.of(MenuItem.SNACKS, MenuItem.CHINESE)));
        return 3;
    }

    private static MealSession newSession(String canteenId, String name, MealType type, String start, String end,
                                          List<String> categories) {
        MealSession session = new MealSession();
        session.setCanteenId(canteenId);
        session.setName(name);
        session.setType(type);
        session.setStartTime(start);
        session.setEndTime(end);
        session.setAllowedCategories(new ArrayList<>(categories));
        return session;
    }

    private void checkNoOverlap(MealSession session) {
        if (!session.isActive()) {
            return;
        }
        for (MealSession other : listForCanteen(session.getCanteenId())) {
            boolean same = other.getId().equals(session.getId());
            boolean overlaps = session.start().isBefore(other.end()) && other.start().isBefore(session.end());
            if (!same && other.isActive() && overlaps) {
                throw new BusinessException("These timings overlap with " + other.getName()
                        + " (" + other.getTimeRange() + "). Active sessions must not overlap.");
            }
        }
    }
}
