package org.korhan.schemathesis;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Absichtlich eingebaute Fehler. Jeder Bug wird über LAB_BUGS eingeschaltet,
 * damit man sieht, welcher Schemathesis-Check ihn findet.
 */
@Component
public class Bugs {

    public enum Bug {
        /** Übung 1: Server-Fehler (500) bei bestimmten Eingaben. */
        AUTHOR_CRASH,
        /** Übung 2: Schema verspricht Validierung, Code prüft nicht. */
        MISSING_VALIDATION,
        /** Übung 3: Antwort passt nicht mehr zum Schema. */
        SCHEMA_DRIFT,
        /** Übung 4: Statuscode, der nicht im Schema steht. */
        UNDOCUMENTED_STATUS,
        /** Übung 5 (stateful): DELETE meldet Erfolg, löscht aber nicht. */
        DELETE_NOOP,
        /** Übung 6 (stateful): POST meldet Erfolg, speichert aber nicht immer. */
        LOST_WRITE;

        static Bug parse(String name) {
            return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        }
    }

    private static final Logger log = LoggerFactory.getLogger(Bugs.class);

    private final Set<Bug> active = EnumSet.noneOf(Bug.class);

    public Bugs(@Value("${lab.bugs:}") Set<String> names) {
        names.stream().filter(n -> !n.isBlank()).map(Bug::parse).forEach(active::add);
        log.info("Aktive Bugs: {}", active.isEmpty() ? "keine" : active);
    }

    public boolean on(Bug bug) {
        return active.contains(bug);
    }
}
