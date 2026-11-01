# Schemathesis Lab

Eine kleine Bücher-API (Spring Boot 4, Java 25), gegen die man Schemathesis laufen lässt.
Sechs Bugs sind eingebaut und lassen sich einzeln einschalten.

## Die Idee in drei Sätzen

Bei klassischen Tests denkst du dir Eingaben aus und prüfst die Ausgabe. Bei **Property-based Testing**
beschreibst du nur, welche Eingaben erlaubt sind und was *immer* gelten muss, das Tool erzeugt dann
hunderte Eingaben selbst. Schemathesis nimmt dafür dein OpenAPI-Schema als Beschreibung und prüft
Regeln wie "nie ein 500er" oder "die Antwort passt zum Schema".

Beispiel: Das Schema sagt `author` ist ein String mit 1–100 Zeichen. Du testest vielleicht
`"Franz Kafka"`. Schemathesis probiert auch `"0"`, `"ä 𝕏"`, 100 Leerzeichen oder `null` und schaut,
ob der Server dabei abstürzt.

## Setup

```sh
./mvnw spring-boot:run                      # API auf http://localhost:8080
```

- Schema: http://localhost:8080/v3/api-docs (generiert von springdoc aus dem Code)
- Swagger UI: http://localhost:8080/swagger-ui.html

In einem zweiten Terminal:

```sh
uvx schemathesis run http://localhost:8080/v3/api-docs --checks all --output-sanitize false
```

`--checks all` schaltet alle Prüfungen ein. `--output-sanitize false` ist nötig, weil Schemathesis
sonst Felder mit "auth" im Namen für Secrets hält und `author` als `[Filtered]` ausgibt.

Bugs schaltest du per Umgebungsvariable ein (mehrere mit Komma):

```sh
LAB_BUGS=author-crash ./mvnw spring-boot:run
```

## Was Schemathesis macht

Ein Lauf hat vier Phasen:

| Phase      | Was passiert |
|------------|--------------|
| examples   | Schickt die `example`-Werte aus dem Schema |
| coverage   | Systematische Grenzwerte: Minimum, Maximum, knapp daneben, falsche Typen |
| fuzzing    | Zufällige Daten über Hypothesis, auch absichtlich ungültige |
| stateful   | Ketten von Requests, z.B. POST → GET → DELETE → GET |

Jeder Fehler bekommt einen Code (ST101, ST201, ...). `uvx schemathesis explain ST101` erklärt ihn.
Zu jedem Fund gibt es einen `curl`-Befehl zum Nachstellen und eine Test-Case-ID für `st replay`.

Hypothesis merkt sich gefundene Fehler in `.hypothesis/` und probiert sie beim nächsten Lauf zuerst.
Wenn du "frisch" testen willst, lösch den Ordner.

### Beispiel-Report

In der Konsole stehen nur Fehler. Alle Requests findest du in `examples/report/`, erzeugt mit der
sauberen Version (ohne Bugs):

```sh
uvx schemathesis run http://localhost:8080/v3/api-docs --checks all --output-sanitize false --seed 42 \
  --report har,html,vcr,junit \
  --report-har-path examples/report/requests.har \
  --report-vcr-path examples/report/requests.vcr.yaml \
  --report-junit-path examples/report/junit.xml \
  --report-html-path examples/report/html
```

| Datei | Inhalt |
|-------|--------|
| `html/index.html` | Übersicht im Browser |
| `requests.har` | Jeder Request mit Antwort, importierbar in die Browser-DevTools (Network-Tab) |
| `requests.vcr.yaml` | Dasselbe als YAML, gut im Editor lesbar |
| `junit.xml` | Ein Testfall pro Operation, so wie es ein CI-Server sieht |

`examples/report-bugs/` ist derselbe Lauf mit allen sechs Bugs
(`LAB_BUGS=author-crash,missing-validation,schema-drift,undocumented-status,delete-noop,lost-write`).
Dort siehst du, wie Fehler im HTML- und JUnit-Report aussehen. `console.txt` ist die Konsolenausgabe
dazu. Achtung: Das verrät die Lösungen der Übungen.

Ohne die `--report-*-path`-Optionen landet alles mit Zeitstempel in `schemathesis-report/`
(steht im `.gitignore`).

---

## Übung 0: Die saubere Version

Starte ohne Bugs und lass Schemathesis laufen. Ergebnis: alles grün.

Das war beim ersten Entwurf nicht so. Ganz normaler Spring-Code mit springdoc hatte 10 Funde.
Die Fixes liegen in eigenen Klassen, damit du sie nachvollziehen kannst:

| Fund | Ursache | Fix |
|------|---------|-----|
| ST108 bei jedem 400/404 | springdoc dokumentiert `ProblemDetail.instance` als `format: uri`, Spring liefert aber `/books/1` | `Problem.java` mit `uri-reference` |
| ST103 400 bei GET `/books/9223372036854775808` | Zahl passt nicht in `long`, 400 war nicht dokumentiert | `OpenApiConfig` hängt 400 an jede Operation |
| ST103 415 bei POST ohne Content-Type | 415 nicht dokumentiert | `OpenApiConfig` hängt 415 an Operationen mit Body |
| ST105 bei `GET /books?=1` | Tomcat wirft bei Parametern ohne Namen, Spring Boot rendert das als `application/json` statt Problem | `ApiErrors` |
| ST102 500 bei `Content-Type: multipart/form-data` | fehlende boundary, Spring wirft `MultipartException` | `ApiErrors` |
| ST201 `{"title": true}` wird angenommen | Jackson macht daraus still den String `"true"` | `JacksonConfig` |

**Aufgabe:** Lösch `JacksonConfig.java` (oder kommentier die Bean aus), starte neu, lass Schemathesis
laufen. Findest du den Fund wieder? Danach wiederherstellen.

---

## Übung 1: Absturz

```sh
LAB_BUGS=author-crash ./mvnw spring-boot:run
```

Lass Schemathesis laufen. Welcher Check schlägt an? Welche Eingabe löst es aus?
Den Bug findest du in `BookController.authorShort()`.

<details><summary>Lösung</summary>

**ST101 Server error.** `authorShort` macht aus "Franz Kafka" ein "F. Kafka" und nimmt an, dass es immer
zwei Wörter gibt. Ein Autor ohne Leerzeichen wie `"000…0"` führt zu `ArrayIndexOutOfBoundsException` → 500.
Dazu kommt ST103, weil 500 nicht im Schema steht.

Das ist der Klassiker: Niemand schreibt einen Unit-Test mit einem einwortigen Autor, Hypothesis probiert
so etwas als Erstes, weil es Eingaben gezielt verkleinert ("shrinking").
</details>

## Übung 2: Fehlende Validierung

```sh
LAB_BUGS=missing-validation ./mvnw spring-boot:run
```

Das Schema sagt `year` liegt zwischen 1450 und 2100. Was meldet Schemathesis? In welcher Phase?

<details><summary>Lösung</summary>

**ST201 Schema-violating request accepted**, gefunden in der **coverage**-Phase. Die probiert gezielt
`year: 1449` und `2101`, also knapp neben den Grenzen. Der Server antwortet 201 statt 400.

Schemathesis schickt nicht nur gültige Daten. Es prüft auch, dass ungültige abgelehnt werden.
Der Check heisst `negative_data_rejection`.
</details>

## Übung 3: Schema-Drift

```sh
LAB_BUGS=schema-drift ./mvnw spring-boot:run
```

Hier ist die Eingabe egal. Was ist an der Antwort falsch?

<details><summary>Lösung</summary>

**ST108 Response violates schema.** Die API gibt die ISBN mit Bindestrichen zurück
(`978-3-16-148410-0`), das Schema verspricht `^[0-9]{13}$`. Schon die **examples**-Phase findet das.

Typisches Szenario: Jemand ändert die Ausgabe fürs Frontend, das Schema bleibt alt. Ein generierter
Client in einer anderen Sprache würde dann beim Parsen scheitern.
</details>

## Übung 4: Undokumentierter Statuscode

```sh
LAB_BUGS=undocumented-status ./mvnw spring-boot:run
```

<details><summary>Lösung</summary>

**ST103 Undocumented status code.** Bei doppelter ISBN antwortet die API mit 409 Conflict. Das ist
fachlich vernünftig, steht aber nicht im Schema. Gefunden wird es, weil Schemathesis die
Beispiel-ISBN `9783161484100` mehrfach schickt.

Zwei mögliche Fixes: 409 dokumentieren (`@ApiResponse(responseCode = "409", ...)`) oder die Regel
weglassen. Probier den ersten aus.
</details>

## Übung 5: Stateful – Löschen, das nicht löscht

```sh
LAB_BUGS=delete-noop ./mvnw spring-boot:run
```

Schau, welche Phasen grün sind und welche nicht.

<details><summary>Lösung</summary>

**ST301 Deleted resource still available.** Nur die **stateful**-Phase findet es. DELETE antwortet 204,
das Buch bleibt aber da. Ein einzelner Request sieht korrekt aus, erst die Kette DELETE → GET zeigt
den Fehler.

Woher weiss Schemathesis, dass die `id` aus der POST-Antwort in `GET /books/{id}` gehört?
Aus den **OpenAPI-Links** in `BookController.create()` (`@Link`). Im Schema unter
`paths./books.post.responses.201.links`. Ohne Links versucht Schemathesis die Zusammenhänge zu raten
(z.B. über den `Location`-Header), explizite Links sind zuverlässiger.
</details>

## Übung 6: Stateful – verlorene Schreibvorgänge

```sh
LAB_BUGS=lost-write ./mvnw spring-boot:run
```

<details><summary>Lösung</summary>

**ST302 Created resource not found.** POST antwortet 201 mit einer `id`, ein GET darauf gibt 404.
Passiert nur bei Titeln mit Nicht-ASCII-Zeichen. Hypothesis erzeugt gerne Unicode, deshalb fällt
das schnell auf. Mit handgeschriebenen Testdaten wie "Der Process" nie.
</details>

---

## Weiter ausprobieren

- Alle Bugs auf einmal: `LAB_BUGS=author-crash,missing-validation,schema-drift,undocumented-status,delete-noop,lost-write`
- Nur eine Phase: `--phases stateful`
- Mehr Versuche pro Operation: `-n 500`
- Reproduzierbarer Lauf: `--seed <zahl>` (die Seed steht am Ende jeder Ausgabe)
- Einzelne Operation: `--include-operation-id createBook`
- HTML-Report: `--report html` (landet in `schemathesis-report/`)
- Einen Fund nachspielen: `uvx schemathesis replay <Test-Case-ID>`
- Bugs selbst fixen und prüfen, dass der Lauf wieder grün wird.

## Aufbau

```
src/main/java/org/korhan/schemathesis/
  BookController.java   REST-Endpunkte, enthält die Bugs (Kommentar "// Bug:")
  Bugs.java             liest LAB_BUGS
  BookRequest.java      Eingabe, Bean-Validation-Annotationen → Constraints im Schema
  Book.java             Antwort
  BookStore.java        In-Memory-Speicher
  OpenApiConfig.java    gemeinsame Fehlerantworten im Schema
  ApiErrors.java        Fehler, die Spring nicht als ProblemDetail rendert
  JacksonConfig.java    strenges JSON-Parsing
  Problem.java          korrektes Schema für ProblemDetail
```
