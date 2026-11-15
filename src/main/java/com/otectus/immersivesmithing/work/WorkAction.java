package com.otectus.immersivesmithing.work;

/**
 * Every intent a client may report for station work. The same actions come from the focused scene and from World view;
 * the server re-validates each against the station, the piece, the tool and the smith. None of them carries an item, a
 * score, a temperature or a result.
 */
public enum WorkAction {
    /** Ask to work at the station in a focused view (a control lease). */
    FOCUS,
    /** Leave the focused view; the work itself stays where it is. */
    RELEASE,
    /** One stroke of the forge's bellows. */
    PUMP,
    /** Move the billet along the tray; target = position, or -1 for the next one. */
    TURN,
    /** Commit the pinned plan: reserve metal, escrow components, cast the billet. */
    COMMIT,
    /** Draw the billet out with tongs. */
    DRAW,
    /** Cancel an undrawn job: release the metal, refund the components. */
    CANCEL_JOB,
    /** Lay the piece in the tongs on the forge's reheat rest. */
    REST_PLACE,
    /** Take the piece from the reheat rest with tongs. */
    REST_TAKE,
    /** Open the forge's pattern book. */
    BOOK,
    /** One hammer blow; target = operation index (or -1 to resolve from u, v). */
    STRIKE,
    /** Turn the piece over on the anvil. */
    FLIP,
    /** Fit a reserved component; target = socket index (or -1 for the next one). */
    FIT,
    /** Collect the finished piece from the anvil. */
    COLLECT,
    /** Take the piece off the anvil with tongs. */
    TAKE_PIECE,
    /** Put the piece in the tongs on the anvil. */
    PLACE_PIECE,
    /** Lower the piece in the tongs into the trough. */
    LOWER,
    /** Lift the piece out of the trough. */
    LIFT,
    /** Hold the edge to the turning wheel (sent again while held). */
    GRIND_HOLD,
    /** Lift the edge away from the wheel. */
    GRIND_STOP,
    /** Choose the refinement aspect; target 0 durability, 1 efficacy. */
    GRIND_ASPECT,
    /** Take the piece off the grindstone. */
    GRIND_TAKE;

    private static final WorkAction[] VALUES = values();

    public static WorkAction byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }

    /** Flags carried with an action. */
    public static final int HEAVY = 1;
}
