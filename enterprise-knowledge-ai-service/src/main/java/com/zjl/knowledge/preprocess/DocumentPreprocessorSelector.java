package com.zjl.knowledge.preprocess;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 文档预处理器选择器。
 */
@Component
public class DocumentPreprocessorSelector {

    private final List<DocumentPreprocessor> preprocessors;

    /**
     * 构造选择器。
     *
     * @param preprocessors Spring 容器中的预处理器
     */
    public DocumentPreprocessorSelector(List<DocumentPreprocessor> preprocessors) {
        this.preprocessors = preprocessors.stream()
                .sorted(Comparator.comparing(DocumentPreprocessor::fallback))
                .toList();
    }

    /**
     * 选择匹配的文档预处理器。
     *
     * @param context 预处理上下文
     * @return 匹配的预处理器
     */
    public DocumentPreprocessor select(DocumentPreprocessingContext context) {
        return preprocessors.stream()
                .filter(preprocessor -> preprocessor.supports(context))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM_ERROR, "未找到可用的文档预处理器"));
    }
}
