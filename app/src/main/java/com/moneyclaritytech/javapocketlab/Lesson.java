package com.moneyclaritytech.javapocketlab;

public final class Lesson {
    public final String title;
    public final String concept;
    public final String code;
    public final String expected;
    public final String challenge;

    public Lesson(String title, String concept, String code, String expected, String challenge) {
        this.title = title;
        this.concept = concept;
        this.code = code;
        this.expected = expected;
        this.challenge = challenge;
    }
}
