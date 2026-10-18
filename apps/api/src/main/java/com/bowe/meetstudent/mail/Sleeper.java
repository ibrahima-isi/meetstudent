package com.bowe.meetstudent.mail;

import java.time.Duration;

/** Waits between delivery attempts. Injectable so tests never sleep. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;

    Sleeper THREAD = duration -> Thread.sleep(duration.toMillis());
}
