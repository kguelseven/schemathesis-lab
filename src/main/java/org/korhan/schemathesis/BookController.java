package org.korhan.schemathesis;

import static org.korhan.schemathesis.Bugs.Bug.AUTHOR_CRASH;
import static org.korhan.schemathesis.Bugs.Bug.DELETE_NOOP;
import static org.korhan.schemathesis.Bugs.Bug.LOST_WRITE;
import static org.korhan.schemathesis.Bugs.Bug.MISSING_VALIDATION;
import static org.korhan.schemathesis.Bugs.Bug.SCHEMA_DRIFT;
import static org.korhan.schemathesis.Bugs.Bug.UNDOCUMENTED_STATUS;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.links.Link;
import io.swagger.v3.oas.annotations.links.LinkParameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(path = "/books", produces = MediaType.APPLICATION_JSON_VALUE)
public class BookController {

    private final BookStore store;
    private final Bugs bugs;
    private final Validator validator;

    public BookController(BookStore store, Bugs bugs, Validator validator) {
        this.store = store;
        this.bugs = bugs;
        this.validator = validator;
    }

    @GetMapping
    @Operation(operationId = "listBooks", summary = "Bücher auflisten")
    @ApiResponse(responseCode = "200", description = "Liste der Bücher")
    public List<Book> list(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return store.all().stream()
                .sorted(Comparator.comparingLong(Book::id))
                .limit(limit)
                .map(this::present)
                .toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createBook", summary = "Buch anlegen")
    @ApiResponse(responseCode = "201", description = "Angelegt", links = {
            @Link(name = "GetBook", operationId = "getBook",
                    parameters = @LinkParameter(name = "id", expression = "$response.body#/id")),
            @Link(name = "UpdateBook", operationId = "updateBook",
                    parameters = @LinkParameter(name = "id", expression = "$response.body#/id")),
            @Link(name = "DeleteBook", operationId = "deleteBook",
                    parameters = @LinkParameter(name = "id", expression = "$response.body#/id"))})
    public ResponseEntity<Book> create(@RequestBody BookRequest request) {
        validate(request);
        if (bugs.on(UNDOCUMENTED_STATUS) && store.existsIsbn(request.isbn())) {
            // Bug: 409 steht nirgends im Schema
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ISBN existiert bereits");
        }
        Book book = toBook(store.nextId(), request);
        boolean nonAscii = !request.title().chars().allMatch(c -> c < 128);
        if (!(bugs.on(LOST_WRITE) && nonAscii)) {
            store.save(book);
        }
        return ResponseEntity.created(URI.create("/books/" + book.id())).body(present(book));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getBook", summary = "Buch lesen")
    @ApiResponse(responseCode = "200", description = "Gefunden")
    @ApiResponse(responseCode = "404", description = "Nicht gefunden", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
    public Book get(@PathVariable long id) {
        return present(findOr404(id));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateBook", summary = "Buch ersetzen")
    @ApiResponse(responseCode = "200", description = "Aktualisiert")
    @ApiResponse(responseCode = "404", description = "Nicht gefunden", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
    public Book update(@PathVariable long id, @RequestBody BookRequest request) {
        findOr404(id);
        validate(request);
        Book book = toBook(id, request);
        store.save(book);
        return present(book);
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteBook", summary = "Buch löschen")
    @ApiResponse(responseCode = "204", description = "Gelöscht")
    @ApiResponse(responseCode = "404", description = "Nicht gefunden", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
    public ResponseEntity<Void> delete(@PathVariable long id) {
        findOr404(id);
        if (!bugs.on(DELETE_NOOP)) {
            store.delete(id);
        }
        return ResponseEntity.noContent().build();
    }

    private Book findOr404(long id) {
        return store.find(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Buch " + id + " nicht gefunden"));
    }

    private void validate(BookRequest request) {
        String errors = validator.validate(request).stream()
                // Bug: Schema sagt year 1450..2100, geprüft wird es nicht
                .filter(v -> !(bugs.on(MISSING_VALIDATION) && isYearRange(v)))
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errors);
        }
    }

    private static boolean isYearRange(ConstraintViolation<?> v) {
        Class<?> type = v.getConstraintDescriptor().getAnnotation().annotationType();
        return v.getPropertyPath().toString().equals("year") && (type == Min.class || type == Max.class);
    }

    private Book toBook(long id, BookRequest r) {
        return new Book(id, r.title(), r.author(), authorShort(r.author()), r.isbn(), r.year(), r.price());
    }

    private String authorShort(String author) {
        String[] parts = author.trim().split("\\s+");
        if (bugs.on(AUTHOR_CRASH)) {
            // Bug: geht davon aus, dass jeder Autor Vor- und Nachname hat
            return parts[0].charAt(0) + ". " + parts[1];
        }
        if (parts.length < 2 || parts[0].isEmpty()) {
            return author;
        }
        return parts[0].charAt(0) + ". " + parts[parts.length - 1];
    }

    private Book present(Book b) {
        if (bugs.on(SCHEMA_DRIFT)) {
            // Bug: "das Frontend wollte Bindestriche", das Schema weiss davon nichts
            String i = b.isbn();
            String pretty = i.substring(0, 3) + "-" + i.charAt(3) + "-" + i.substring(4, 6) + "-"
                    + i.substring(6, 12) + "-" + i.charAt(12);
            return new Book(b.id(), b.title(), b.author(), b.authorShort(), pretty, b.year(), b.price());
        }
        return b;
    }
}
