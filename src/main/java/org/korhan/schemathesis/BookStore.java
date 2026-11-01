package org.korhan.schemathesis;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/** In-Memory-Speicher. Reicht für das Lab, nach Neustart ist alles weg. */
@Component
public class BookStore {

    private final Map<Long, Book> books = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public long nextId() {
        return nextId.getAndIncrement();
    }

    public void save(Book book) {
        books.put(book.id(), book);
    }

    public Optional<Book> find(long id) {
        return Optional.ofNullable(books.get(id));
    }

    public Collection<Book> all() {
        return books.values();
    }

    public boolean existsIsbn(String isbn) {
        return books.values().stream().anyMatch(b -> b.isbn().equals(isbn));
    }

    public boolean delete(long id) {
        return books.remove(id) != null;
    }
}
