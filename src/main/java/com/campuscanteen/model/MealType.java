package com.campuscanteen.model;

/**
 * What kind of meal a session is. Used for one fixed rule: Chinese food may only be sold
 * during LUNCH and SNACKS sessions, never at breakfast (or in any other session).
 */
public enum MealType {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    SNACKS("Snacks"),
    OTHER("Other");

    private final String label;

    MealType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean allowsChinese() {
        return this == LUNCH || this == SNACKS;
    }
}
