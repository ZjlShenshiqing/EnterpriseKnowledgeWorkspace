package com.zjl.knowledge.tokenization;

import java.util.List;

/**
 * IK 分词引擎适配接口。
 */
@FunctionalInterface
public interface IkTokenizationEngine {

    /**
     * 执行 IK 分词。
     *
     * @param text 输入文本
     * @param smart 是否使用智能分词
     * @return IK 原始词项
     */
    List<String> tokenize(String text, boolean smart);
}
