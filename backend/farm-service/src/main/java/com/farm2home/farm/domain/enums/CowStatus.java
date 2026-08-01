package com.farm2home.farm.domain.enums;

public enum CowStatus {
    ACTIVE,
    SICK,
    SOLD,
    DECEASED;

    /** SOLD and DECEASED are permanent, real-world-final outcomes - once recorded, a cow's
     *  status can never be changed again (not even back to ACTIVE/SICK, and not from one
     *  terminal status to the other). */
    public boolean isTerminal() {
        return this == SOLD || this == DECEASED;
    }

    public boolean canTransitionTo(CowStatus next) {
        return switch (this) {
            case ACTIVE, SICK   -> true;
            case SOLD, DECEASED -> false;
        };
    }
}
