package com.zjl.knowledge.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * 使用 Apache Tika 从常见办公/版式文档中提取纯文本（PDF、Office、HTML、纯文本等）
 */
@Component
public class TikaDocumentParser {

    /**
     * 单次解析写入 handler 的最大字符数，防止畸形文件撑爆内存
     */
    private static final int MAX_BODY_CHARS = 32 * 1024 * 1024;

    private final Parser parser = new AutoDetectParser();
    private final Tika detector = new Tika();

    /**
     * 从流中解析正文（调用方负责关闭 {@code input}）
     *
     * @param input 文件字节流
     * @param resourceName 原始文件名（供类型探测）
     * @param hintContentType 上传时的 Content-Type，可为空
     * @return 提取后的纯文本（可能为空串）
     */
    public String extractText(InputStream input, String resourceName, String hintContentType)
            throws IOException, TikaException, SAXException {
        Metadata metadata = new Metadata();
        if (StringUtils.hasText(resourceName)) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, resourceName);
        }
        if (StringUtils.hasText(hintContentType)) {
            metadata.set(Metadata.CONTENT_TYPE, hintContentType);
        }
        BodyContentHandler handler = new BodyContentHandler(MAX_BODY_CHARS);
        ParseContext context = new ParseContext();
        parser.parse(input, handler, metadata, context);
        return handler.toString() == null ? "" : handler.toString().trim();
    }

    /**
     * 根据文件名/魔数猜测 MIME（用于落库展示）
     */
    public String detectMime(byte[] prefix, String resourceName) {
        try {
            return detector.detect(prefix, resourceName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 生成短摘要（非模型，仅截断）
     */
    public String summarize(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String t = text.replace("\r", " ").replace("\n", " ").trim();
        return t.length() <= maxLen ? t : t.substring(0, maxLen) + "...";
    }
}
