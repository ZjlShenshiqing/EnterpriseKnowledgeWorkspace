package com.zjl.knowledge.tokenization;

import org.springframework.stereotype.Component;
import org.wltea.analyzer.core.IKSegmenter;
import org.wltea.analyzer.core.Lexeme;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * IK 分词引擎默认实现。
 */
@Component
public class DefaultIkTokenizationEngine implements IkTokenizationEngine {

    @Override
    public List<String> tokenize(String text, boolean smart) {
        IKSegmenter segmenter = new IKSegmenter(new StringReader(text), smart);
        List<String> tokens = new ArrayList<>();
        try {
            Lexeme lexeme;
            while ((lexeme = segmenter.next()) != null) {
                tokens.add(lexeme.getLexemeText());
            }
            return tokens;
        } catch (IOException ex) {
            throw new IllegalStateException("IK tokenization failed", ex);
        }
    }
}
