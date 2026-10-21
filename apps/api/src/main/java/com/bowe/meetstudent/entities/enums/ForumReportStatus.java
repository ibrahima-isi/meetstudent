package com.bowe.meetstudent.entities.enums;

/** Where a forum report stands in the administrator's queue. A closed report (HANDLED or DISMISSED) never reopens. */
public enum ForumReportStatus {
    OPEN,
    HANDLED,
    DISMISSED
}
