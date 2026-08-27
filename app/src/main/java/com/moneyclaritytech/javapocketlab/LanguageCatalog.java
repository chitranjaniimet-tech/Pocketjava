package com.moneyclaritytech.pocketforge;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Central registry for language tracks and future downloadable runtimes. */
public final class LanguageCatalog {
    private LanguageCatalog() {}

    public static final class Language {
        public final String id;
        public final String name;
        public final String description;
        public final String statusLabel;
        public final boolean available;

        Language(String id, String name, String description, String statusLabel, boolean available) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.statusLabel = statusLabel;
            this.available = available;
        }
    }

    public static List<Language> all() {
        return Collections.unmodifiableList(Arrays.asList(
                new Language("java", "Java", "Compile and run learning projects locally with the built-in Android-compatible compiler.", "Available in this build", true),
                new Language("python", "Python", "Planned downloadable interpreter module with lessons, scripts and package support.", "Module planned", false),
                new Language("cpp", "C / C++", "Planned native toolchain module for portable console programs.", "Module planned", false),
                new Language("javascript", "JavaScript / Node.js", "Planned scripting module for browser-style JavaScript and server-side Node workflows.", "Module planned", false),
                new Language("kotlin", "Kotlin", "Planned Kotlin learning and Android-oriented scripting track.", "Module planned", false),
                new Language("go", "Go", "Planned compiled-language module for command-line tools.", "Module planned", false),
                new Language("rust", "Rust", "Planned systems-language module with carefully bounded native execution.", "Module planned", false),
                new Language("php", "PHP", "Planned web-scripting module for learning and small local projects.", "Module planned", false),
                new Language("shell", "Shell", "Use the current Android-sandbox terminal for shell commands and automation.", "Terminal foundation available", true)
        ));
    }
}
