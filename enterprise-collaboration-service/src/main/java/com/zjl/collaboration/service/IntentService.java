package com.zjl.collaboration.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.entity.*;
import com.zjl.collaboration.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentService {

    private final KbIntentNodeMapper nodeMapper;
    private final KbIntentRuleMapper ruleMapper;
    private final KbIntentKbRelMapper kbRelMapper;

    /** 获取全量意图树 */
    public List<KbIntentNode> getTree() {
        List<KbIntentNode> all = nodeMapper.selectList(
                Wrappers.lambdaQuery(KbIntentNode.class).orderByAsc(KbIntentNode::getSortOrder));
        Map<Long, List<KbIntentNode>> childrenMap = all.stream()
                .filter(n -> n.getParentId() != null)
                .collect(Collectors.groupingBy(KbIntentNode::getParentId));
        List<KbIntentNode> roots = all.stream()
                .filter(n -> n.getParentId() == null)
                .collect(Collectors.toList());
        for (KbIntentNode root : roots) {
            buildChildren(root, childrenMap);
        }
        return roots;
    }

    private void buildChildren(KbIntentNode parent, Map<Long, List<KbIntentNode>> childrenMap) {
        List<KbIntentNode> children = childrenMap.getOrDefault(parent.getId(), List.of());
        parent.setChildren(children);
        for (KbIntentNode child : children) {
            buildChildren(child, childrenMap);
        }
    }

    /** 获取节点详情（含规则和知识库关联） */
    public KbIntentNode getNode(Long id) {
        KbIntentNode node = nodeMapper.selectById(id);
        if (node != null) {
            node.setRules(ruleMapper.selectList(
                    Wrappers.lambdaQuery(KbIntentRule.class).eq(KbIntentRule::getNodeId, id)));
            node.setKbRels(kbRelMapper.selectList(
                    Wrappers.lambdaQuery(KbIntentKbRel.class).eq(KbIntentKbRel::getNodeId, id)));
        }
        return node;
    }

    /** 新增节点 */
    public KbIntentNode createNode(KbIntentNode node) {
        if (node.getSortOrder() == null) node.setSortOrder(0);
        if (node.getLevel() == null) node.setLevel(1);
        if (node.getEnabled() == null) node.setEnabled(1);
        nodeMapper.insert(node);
        return node;
    }

    /** 更新节点 */
    public void updateNode(Long id, KbIntentNode node) {
        node.setId(id);
        nodeMapper.updateById(node);
    }

    /** 删除节点（级联删除子节点+规则+关联） */
    public void deleteNode(Long id) {
        List<KbIntentNode> children = nodeMapper.selectList(
                Wrappers.lambdaQuery(KbIntentNode.class).eq(KbIntentNode::getParentId, id));
        for (KbIntentNode child : children) {
            deleteNode(child.getId());
        }
        ruleMapper.delete(Wrappers.lambdaQuery(KbIntentRule.class).eq(KbIntentRule::getNodeId, id));
        kbRelMapper.delete(Wrappers.lambdaQuery(KbIntentKbRel.class).eq(KbIntentKbRel::getNodeId, id));
        nodeMapper.deleteById(id);
    }

    /** 排序 */
    public void updateSort(Long id, Long parentId, Integer sortOrder) {
        KbIntentNode node = new KbIntentNode();
        node.setId(id);
        node.setParentId(parentId);
        node.setSortOrder(sortOrder);
        nodeMapper.updateById(node);
    }

    /** 获取节点规则列表 */
    public List<KbIntentRule> getRules(Long nodeId) {
        return ruleMapper.selectList(
                Wrappers.lambdaQuery(KbIntentRule.class).eq(KbIntentRule::getNodeId, nodeId));
    }

    /** 新增规则 */
    public KbIntentRule createRule(Long nodeId, KbIntentRule rule) {
        rule.setNodeId(nodeId);
        if (rule.getWeight() == null) rule.setWeight(1.0);
        if (rule.getEnabled() == null) rule.setEnabled(1);
        ruleMapper.insert(rule);
        return rule;
    }

    /** 更新规则 */
    public void updateRule(Long ruleId, KbIntentRule rule) {
        rule.setId(ruleId);
        ruleMapper.updateById(rule);
    }

    /** 删除规则 */
    public void deleteRule(Long ruleId) {
        ruleMapper.deleteById(ruleId);
    }

    /** 获取节点关联知识库 */
    public List<KbIntentKbRel> getKbRels(Long nodeId) {
        return kbRelMapper.selectList(
                Wrappers.lambdaQuery(KbIntentKbRel.class).eq(KbIntentKbRel::getNodeId, nodeId));
    }

    /** 绑定知识库 */
    public KbIntentKbRel bindKb(Long nodeId, Long kbId, Double weight) {
        KbIntentKbRel rel = new KbIntentKbRel();
        rel.setNodeId(nodeId);
        rel.setKbId(kbId);
        rel.setWeight(weight != null ? weight : 1.0);
        kbRelMapper.insert(rel);
        return rel;
    }

    /** 更新关联权重 */
    public void updateKbRel(Long relId, Double weight) {
        KbIntentKbRel rel = new KbIntentKbRel();
        rel.setId(relId);
        rel.setWeight(weight);
        kbRelMapper.updateById(rel);
    }

    /** 解除绑定 */
    public void unbindKb(Long relId) {
        kbRelMapper.deleteById(relId);
    }

    /** 匹配测试 */
    public Map<String, Object> match(String query) {
        List<KbIntentNode> allNodes = nodeMapper.selectList(
                Wrappers.lambdaQuery(KbIntentNode.class).eq(KbIntentNode::getEnabled, 1));
        List<KbIntentRule> allRules = ruleMapper.selectList(
                Wrappers.lambdaQuery(KbIntentRule.class).eq(KbIntentRule::getEnabled, 1));

        Map<Long, List<KbIntentRule>> rulesByNode = allRules.stream()
                .collect(Collectors.groupingBy(KbIntentRule::getNodeId));

        Map<Long, KbIntentNode> nodeMap = allNodes.stream()
                .collect(Collectors.toMap(KbIntentNode::getId, n -> n));

        List<Map<String, Object>> hits = new ArrayList<>();

        for (KbIntentRule rule : allRules) {
            boolean matched = false;
            if ("keyword".equals(rule.getRuleType())) {
                matched = query.contains(rule.getExpression());
            } else if ("regex".equals(rule.getRuleType())) {
                try {
                    matched = Pattern.compile(rule.getExpression()).matcher(query).find();
                } catch (Exception ignored) {}
            }
            if (matched) {
                KbIntentNode node = nodeMap.get(rule.getNodeId());
                if (node != null) {
                    Map<String, Object> hit = new LinkedHashMap<>();
                    hit.put("nodeId", node.getId());
                    hit.put("nodeName", node.getName());
                    hit.put("ruleId", rule.getId());
                    hit.put("expression", rule.getExpression());
                    hit.put("ruleType", rule.getRuleType());
                    hit.put("weight", rule.getWeight());
                    hits.add(hit);
                }
            }
        }

        hits.sort((a, b) -> Double.compare(
                (Double) b.get("weight"), (Double) a.get("weight")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("hits", hits);
        if (!hits.isEmpty()) {
            result.put("bestMatch", hits.get(0));
        }
        return result;
    }
}
