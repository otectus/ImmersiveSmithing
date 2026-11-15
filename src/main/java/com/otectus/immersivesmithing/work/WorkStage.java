package com.otectus.immersivesmithing.work;

import java.util.Locale;

/**
 * Where a workpiece is in its making. BILLET: drawn from the forge, not yet struck. SHAPING: being hammered. SHAPED: every
 * required operation done, ready to quench (still correctable on the anvil). QUENCHING: a quench has begun. COOLED: quenched,
 * waiting for its components. FITTING: at least one component attached.
 */
public enum WorkStage {
    BILLET, SHAPING, SHAPED, QUENCHING, COOLED, FITTING;

    public boolean shapeable() {
        return this == BILLET || this == SHAPING || this == SHAPED;
    }

    public boolean quenched() {
        return this == COOLED || this == FITTING;
    }

    /** Whether the old FORGED/SHAPED view calls this shaped. */
    public boolean legacyShaped() {
        return ordinal() >= SHAPED.ordinal();
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WorkStage byName(String name) {
        for (WorkStage s : values()) if (s.name().equalsIgnoreCase(name)) return s;
        return null;
    }
}
