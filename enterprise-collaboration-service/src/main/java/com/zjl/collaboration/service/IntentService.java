package com.zjl.collaboration.service;

import com.zjl.collaboration.entity.*;
import java.util.List;
import java.util.Map;

/**
 * 意图配置服务
 */
public interface IntentService {

    List<KbIntentNode> getTree();

    KbIntentNode getNode(Long id);

    KbIntentNode createNode(KbIntentNode node);

    void updateNode(Long id, KbIntentNode node);

    void deleteNode(Long id);

    void updateSort(Long id, Long parentId, Integer sortOrder);

    List<KbIntentRule> getRules(Long nodeId);

    KbIntentRule createRule(Long nodeId, KbIntentRule rule);

    void updateRule(Long ruleId, KbIntentRule rule);

    void deleteRule(Long ruleId);

    List<KbIntentKbRel> getKbRels(Long nodeId);

    KbIntentKbRel bindKb(Long nodeId, Long kbId, Double weight);

    void updateKbRel(Long relId, Double weight);

    void unbindKb(Long relId);

    Map<String, Object> match(String query);
}
