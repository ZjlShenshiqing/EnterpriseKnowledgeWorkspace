package com.zjl.web;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.OK;

/**
 * WebClient ExchangeFunction that captures the last request headers
 * and returns a minimal valid JSON response for all calls.
 */
class CapturingExchange implements ExchangeFunction {

    private final Map<String, String> lastHeaders = new HashMap<>();
    private int callCount;

    @Override
    public Mono<ClientResponse> exchange(ClientRequest request) {
        lastHeaders.clear();
        request.headers().forEach((name, values) -> {
            if (!values.isEmpty()) {
                lastHeaders.put(name, values.get(0));
            }
        });
        lastHeaders.put("__uri", request.url().toString());
        callCount++;

        return Mono.just(
                ClientResponse.create(OK)
                        .header("Content-Type", "application/json")
                        .body("{\"code\":\"200\",\"data\":[]}")
                        .build()
        );
    }

    Map<String, String> getLastHeaders() {
        return new HashMap<>(lastHeaders);
    }

    int getCallCount() {
        return callCount;
    }
}
