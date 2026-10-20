package com.campuscanteen.model;

/** The three kinds of accounts. Students and faculty have exactly the same ordering rights and queue priority. */
public enum Role {
    STUDENT("Student"),
    FACULTY("Faculty"),
    CANTEEN_ADMIN("Canteen administrator");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isCustomer() {
        return this == STUDENT || this == FACULTY;
    }
}
