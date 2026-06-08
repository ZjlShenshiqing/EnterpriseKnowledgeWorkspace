package com.zjl.knowledge.tokenization;

import com.zjl.knowledge.config.RagTokenizationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 IK Analyzer 的 RAG 中文分词器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IkChineseTokenizer implements ChineseTokenizer {

    private static final Pattern MIXED_IDENTIFIER = Pattern.compile(
            "[A-Za-z0-9]+(?:[-_.][A-Za-z0-9]+)+");
    private static final Pattern ASCII_TERM = Pattern.compile(
            "[A-Za-z0-9]+(?:[-_.][A-Za-z0-9]+)*");

    private final IkTokenizationEngine tokenizationEngine;
    private final RagTokenizationProperties properties;

    @Override
    public List<String> tokenizeQuery(String text) {
        return tokenize(text, true);
    }

    @Override
    public List<String> tokenizeDocument(String text) {
        return tokenize(text, false);
    }

    private List<String> tokenize(String text, boolean smart) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens;
        if (!properties.isEnabled()) {
            tokens = fallbackTokenize(text);
        } else {
            try {
                tokens = normalize(tokenizationEngine.tokenize(text, smart));
            } catch (RuntimeException ex) {
                if (!properties.isFallbackEnabled()) {
                    throw ex;
                }
                log.warn("IK tokenization failed, using local fallback: type={}",
                        ex.getClass().getSimpleName());
                tokens = fallbackTokenize(text);
            }
        }
        appendMixedIdentifiers(tokens, text);
        return tokens;
    }

    private List<String> normalize(List<String> rawTokens) {
        if (rawTokens == null || rawTokens.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> tokens = new ArrayList<>(rawTokens.size());
        for (String rawToken : rawTokens) {
            if (rawToken == null) {
                continue;
            }
            String token = rawToken.trim().toLowerCase(Locale.ROOT);
            if (!token.isEmpty() && containsLetterOrDigit(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private List<String> fallbackTokenize(String text) {
        List<String> tokens = new ArrayList<>();
        List<Character> cjkRun = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isCjk(ch)) {
                cjkRun.add(ch);
                tokens.add(String.valueOf(ch));
            } else {
                appendBigrams(tokens, cjkRun);
                cjkRun.clear();
            }
        }
        appendBigrams(tokens, cjkRun);

        Matcher matcher = ASCII_TERM.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return tokens;
    }

    private void appendBigrams(List<String> tokens, List<Character> cjkRun) {
        for (int i = 0; i + 1 < cjkRun.size(); i++) {
            tokens.add(new String(new char[]{cjkRun.get(i), cjkRun.get(i + 1)}));
        }
    }

    private void appendMixedIdentifiers(List<String> tokens, String text) {
        Matcher matcher = MIXED_IDENTIFIER.matcher(text);
        while (matcher.find()) {
            String identifier = matcher.group().toLowerCase(Locale.ROOT);
            if (!tokens.contains(identifier)) {
                tokens.add(identifier);
            }
        }
    }

    private boolean containsLetterOrDigit(String token) {
        return token.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }
}
