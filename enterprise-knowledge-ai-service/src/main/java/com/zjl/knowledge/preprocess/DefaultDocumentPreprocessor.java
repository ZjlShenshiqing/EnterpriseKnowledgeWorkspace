package com.zjl.knowledge.preprocess;

import com.zjl.knowledge.entity.KbDocument;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认文档预处理器。
 */
@Component
public class DefaultDocumentPreprocessor implements DocumentPreprocessor {

    private static final String STRATEGY = "DEFAULT";

    @Override
    public boolean supports(DocumentPreprocessingContext context) {
        return true;
    }

    @Override
    public boolean fallback() {
        return true;
    }

    @Override
    public DocumentPreprocessingResult preprocess(DocumentPreprocessingContext context) {
        KbDocument document = context.document();
        String title = resolveTitle(document, context.parsedMetadata());
        String docType = resolveDocType(document, context.parsedMetadata());
        String sourceLocation = document == null ? null : document.getSourceLocation();

        StringBuilder input = new StringBuilder();
        input.append("文档：").append(title).append('\n');
        input.append("文档类型：").append(docType).append('\n');
        if (StringUtils.hasText(sourceLocation)) {
            input.append("来源：").append(sourceLocation).append('\n');
        }
        input.append("\n正文：\n");
        input.append(context.parsedText() == null ? "" : context.parsedText().trim());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("doc_type", docType);
        metadata.put("title", title);
        metadata.put("section_path", List.of(title));
        if (StringUtils.hasText(sourceLocation)) {
            metadata.put("source_location", sourceLocation);
        }
        metadata.put("preprocess_strategy", STRATEGY);

        return new DocumentPreprocessingResult(input.toString(), metadata, new LinkedHashMap<>(metadata));
    }

    private String resolveTitle(KbDocument document, Map<String, String> parsedMetadata) {
        if (document != null && StringUtils.hasText(document.getTitle())) {
            return document.getTitle().trim();
        }
        String metadataTitle = firstMetadata(parsedMetadata, "title", "dc:title", "pdf:docinfo:title");
        if (StringUtils.hasText(metadataTitle)) {
            return metadataTitle.trim();
        }
        if (document != null && StringUtils.hasText(document.getFileName())) {
            return document.getFileName().trim();
        }
        return "untitled";
    }

    private String resolveDocType(KbDocument document, Map<String, String> parsedMetadata) {
        if (document != null && StringUtils.hasText(document.getFileType())) {
            return document.getFileType().trim();
        }
        String contentType = firstMetadata(parsedMetadata, "Content-Type", "content-type");
        if (StringUtils.hasText(contentType)) {
            return contentType.trim();
        }
        return "unknown";
    }

    private String firstMetadata(Map<String, String> metadata, String... names) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        for (String name : names) {
            String value = metadata.get(name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
