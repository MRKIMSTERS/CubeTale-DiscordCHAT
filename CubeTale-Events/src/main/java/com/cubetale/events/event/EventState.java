package com.cubetale.events.event;

public enum EventState {
    /** Event has been created but not yet accepting players. */
    WAITING,
    /** Accepting players before the event begins. */
    JOINING,
    /** Event is actively running. */
    RUNNING,
    /** Event has ended and rewards are being distributed. */
    ENDED
}
