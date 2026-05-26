package com.zjl.knowledge.service.impl;

import com.zjl.knowledge.service.TikaDocumentParser;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TikaDocumentParserImpl implements TikaDocumentParser {

    private static final int MAX_BODY_CHARS = 32 * 1024 * 1024;

    private final Parser parser = new AutoDetectParser();
    private final Tika detector = new Tika();

    @Override
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

    @Override
    public ParseResult extractWithMetadata(InputStream input, String resourceName, String hintContentType)
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

        String text = handler.toString() == null ? "" : handler.toString().trim();

        Map<String, String> metaMap = new LinkedHashMap<>();
        for (String name : metadata.names()) {
            String value = metadata.get(name);
            if (value != null && !value.isEmpty()) {
                metaMap.put(name, value);
            }
        }
        return new ParseResult(text, metaMap);
    }

    @Override
    public String detectMime(byte[] prefix, String resourceName) {
        try {
            return detector.detect(prefix, resourceName);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String summarize(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String t = text.replace("\r", " ").replace("\n", " ").trim();
        return t.length() <= maxLen ? t : t.substring(0, maxLen) + "...";
    }
}
