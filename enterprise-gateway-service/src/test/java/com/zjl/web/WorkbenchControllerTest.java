package com.zjl.web;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkbenchControllerTest {

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
}
