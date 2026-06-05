package com.zjl.knowledge.preprocess;

import com.zjl.knowledge.entity.KbDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档预处理器选择器测试。
 */
class DocumentPreprocessorSelectorTest {

    @Test
    void selectFallsBackToDefaultPreprocessorWhenNoSpecificMatchExists() {
        DefaultDocumentPreprocessor defaultPreprocessor = new DefaultDocumentPreprocessor();
        DocumentPreprocessorSelector selector = new DocumentPreprocessorSelector(List.of(
                new NeverMatchPreprocessor(),
                defaultPreprocessor
        ));

        KbDocument document = new KbDocument();
        document.setFileName("policy.txt");
        DocumentPreprocessor selected = selector.select(new DocumentPreprocessingContext(
                document,
                "制度正文",
                Map.of()
        ));

        assertThat(selected).isSameAs(defaultPreprocessor);
        assertThat(selected.preprocess(new DocumentPreprocessingContext(document, "制度正文", Map.of()))
                .chunkMetadataDefaults())
                .containsEntry("preprocess_strategy", "DEFAULT");
    }

    private static class NeverMatchPreprocessor implements DocumentPreprocessor {

        @Override
        public boolean supports(DocumentPreprocessingContext context) {
            return false;
        }

        @Override
        public DocumentPreprocessingResult preprocess(DocumentPreprocessingContext context) {
            return new DocumentPreprocessingResult("never", Map.of(), Map.of());
        }
    }
}
