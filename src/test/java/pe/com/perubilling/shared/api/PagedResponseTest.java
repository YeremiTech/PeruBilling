package pe.com.perubilling.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PagedResponseTest {
    @Test
    void exposesStablePaginationContract() {
        var source = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        var response = PagedResponse.from(source);

        assertEquals(List.of("a", "b"), response.content());
        assertEquals(1, response.page());
        assertEquals(2, response.size());
        assertEquals(5, response.totalElements());
        assertEquals(3, response.totalPages());
        assertFalse(response.first());
        assertFalse(response.last());
    }

    @Test
    void marksSinglePageAsFirstAndLast() {
        var response = PagedResponse.from(new PageImpl<>(List.of("only")));
        assertTrue(response.first());
        assertTrue(response.last());
    }
}
