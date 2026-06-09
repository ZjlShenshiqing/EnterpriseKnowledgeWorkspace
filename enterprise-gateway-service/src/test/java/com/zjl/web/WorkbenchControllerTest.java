package com.zjl.web;

import com.zjl.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkbenchControllerTest {

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void countDataItemsUsesPageTotalWhenDataIsPageMap() {
        Map<String, Object> response = Map.of(
                "code", "200",
                "data", Map.of(
                        "total", 7,
                        "records", List.of(Map.of("id", 1))
                )
        );

        assertEquals(7L, WorkbenchController.countDataItems(response));
    }

    @Test
    void countDataItemsUsesListSizeWhenDataIsList() {
        Map<String, Object> response = Map.of(
                "code", "200",
                "data", List.of(
                        Map.of("id", 1),
                        Map.of("id", 2),
                        Map.of("id", 3)
                )
        );

        assertEquals(3L, WorkbenchController.countDataItems(response));
    }

    @Test
    void overviewSendsDownstreamHeadersFromUserContextNotRequestHeaders() {
        UserContext.set(42L, "testuser", false, List.of("ROLE_USER"));

        CapturingExchange exchange = new CapturingExchange();
        WorkbenchController controller = new WorkbenchController(
                WebClient.builder().exchangeFunction(exchange));

        Mono<?> result = controller.overview();
        StepVerifier.create(result).expectNextCount(1).expectComplete().verify();

        Map<String, String> headers = exchange.getLastHeaders();
        assertThat(headers).isNotNull();
        assertThat(headers.get("X-User-Id")).isEqualTo("42");
        assertThat(headers.get("X-Is-Admin")).isEqualTo("false");
    }

    @Test
    void statsSendsDownstreamHeadersFromUserContext() {
        UserContext.set(99L, "admin", true, List.of("ROLE_ADMIN"));

        CapturingExchange exchange = new CapturingExchange();
        WorkbenchController controller = new WorkbenchController(
                WebClient.builder().exchangeFunction(exchange));

        Mono<?> result = controller.stats();
        StepVerifier.create(result).expectNextCount(1).expectComplete().verify();

        Map<String, String> headers = exchange.getLastHeaders();
        assertThat(headers).isNotNull();
        assertThat(headers.get("X-User-Id")).isEqualTo("99");
        assertThat(headers.get("X-Is-Admin")).isEqualTo("true");
    }
}
