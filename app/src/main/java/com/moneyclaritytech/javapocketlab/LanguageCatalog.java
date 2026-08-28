package com.moneyclaritytech.pocketforge;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Registry of installable language tracks and their runtime capabilities. */
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
                new Language("java", "Java", "Built-in Android-compatible compiler and runner for learning projects.", "Built-in", true),
                new Language("python", "Python", "Install Python 3 online, then run .py files from the PocketForge editor.", "Installable module", true),
                new Language("cpp", "C / C++", "Install Clang online for native console programs and toolchain work.", "Installable module", true),
                new Language("javascript", "JavaScript / Node.js", "Install Node.js online for scripts and server-style JavaScript.", "Installable module", true),
                new Language("kotlin", "Kotlin", "Install a Kotlin toolchain when the package is available for the device architecture.", "Installable module", true),
                new Language("go", "Go", "Install Go online for compiled command-line programs.", "Installable module", true),
                new Language("rust", "Rust", "Install Rust online for systems programming and native tools.", "Installable module", true),
                new Language("php", "PHP", "Install PHP online for web scripting and command-line practice.", "Installable module", true),
                new Language("ruby", "Ruby", "Install Ruby online for scripting and learning.", "Installable module", true),
                new Language("shell", "Shell", "Bash and Android-compatible shell utilities through the runtime backend.", "Installable module", true)
        ));
    }
}
