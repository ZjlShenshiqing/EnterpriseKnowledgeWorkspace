package com.zjl.knowledge.preprocess;

import com.zjl.knowledge.entity.KbDocument;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认文档预处理器测试。
 */
class DefaultDocumentPreprocessorTest {

    @Test
    void preprocessAddsContextHeaderAndPreservesBodyText() {
        KbDocument document = new KbDocument();
        document.setTitle("差旅报销制度");
        document.setFileName("travel.pdf");
        document.setFileType("application/pdf");
        document.setSourceLocation("https://example.com/travel.pdf");

        DefaultDocumentPreprocessor preprocessor = new DefaultDocumentPreprocessor();

        DocumentPreprocessingResult result = preprocessor.preprocess(new DocumentPreprocessingContext(
                document,
                "高铁二等座可报销。",
                Map.of("Content-Type", "application/pdf")
        ));

        assertThat(result.chunkInputText())
                .contains("文档：差旅报销制度")
                .contains("文档类型：application/pdf")
                .contains("来源：https://example.com/travel.pdf")
                .endsWith("正文：\n高铁二等座可报销。");
        assertThat(result.documentMetadata())
                .containsEntry("title", "差旅报销制度")
                .containsEntry("doc_type", "application/pdf")
                .containsEntry("source_location", "https://example.com/travel.pdf")
                .containsEntry("preprocess_strategy", "DEFAULT");
        assertThat(result.chunkMetadataDefaults())
                .containsEntry("title", "差旅报销制度")
                .containsEntry("doc_type", "application/pdf")
                .containsEntry("source_location", "https://example.com/travel.pdf")
                .containsEntry("preprocess_strategy", "DEFAULT");
        assertThat(result.chunkMetadataDefaults()).containsKey("section_path");
    }

    @Test
    void preprocessFallsBackWhenOptionalMetadataIsMissing() {
        KbDocument document = new KbDocument();
        document.setFileName("faq.docx");

        DefaultDocumentPreprocessor preprocessor = new DefaultDocumentPreprocessor();

        DocumentPreprocessingResult result = preprocessor.preprocess(new DocumentPreprocessingContext(
                document,
                "如何申请报销？提交审批单。",
                Map.of()
        ));

        assertThat(result.chunkInputText())
                .contains("文档：faq.docx")
                .contains("文档类型：unknown")
                .contains("正文：\n如何申请报销？提交审批单。");
        assertThat(result.chunkInputText()).doesNotContain("来源：");
        assertThat(result.documentMetadata())
                .containsEntry("title", "faq.docx")
                .containsEntry("doc_type", "unknown")
                .containsEntry("preprocess_strategy", "DEFAULT");
    }
}
