package com.campuscanteen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.campuscanteen.model.Canteen;
import com.campuscanteen.repository.CanteenRepository;

/**
 * Looks after the canteen record. This version serves one canteen (the first active one).
 */
@Service
public class CanteenService {

    private static final Logger log = LoggerFactory.getLogger(CanteenService.class);

    private final CanteenRepository canteenRepository;

    public CanteenService(CanteenRepository canteenRepository) {
        this.canteenRepository = canteenRepository;
    }

    /** Creates a default canteen the first time the application runs. The admin can rename it in Settings. */
    public void ensureDefaultCanteen() {
        if (canteenRepository.findFirstByActiveTrueOrderByIdAsc().isEmpty()) {
            Canteen canteen = new Canteen();
            canteen.setName("College Canteen");
            canteen.setLocation("Main campus");
            canteen.setOpeningTime("07:30");
            canteen.setClosingTime("18:00");
            canteenRepository.save(canteen);
            log.info("Created the default canteen record.");
        }
    }

    public Canteen getDefaultCanteen() {
        return canteenRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("No active canteen found"));
    }

    public Canteen save(Canteen canteen) {
        return canteenRepository.save(canteen);
    }
}
