package com.zjl.knowledge.service;

import org.apache.tika.exception.TikaException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Apache Tika 文档解析器
 */
public interface TikaDocumentParser {

    String extractText(InputStream input, String resourceName,
                       String hintContentType) throws IOException, TikaException, SAXException;

    ParseResult extractWithMetadata(InputStream input, String resourceName,
                                    String hintContentType) throws IOException, TikaException, SAXException;

    String detectMime(byte[] prefix, String resourceName);

    String summarize(String text, int maxLen);

    record ParseResult(String text, Map<String, String> metadata) {}
}
