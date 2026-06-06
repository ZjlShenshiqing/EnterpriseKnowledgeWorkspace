package com.zjl.collaboration.web;

import com.zjl.collaboration.dto.IntentBindKbReq;
import com.zjl.collaboration.dto.IntentMatchReq;
import com.zjl.collaboration.dto.IntentSortReq;
import com.zjl.collaboration.entity.KbIntentKbRel;
import com.zjl.collaboration.entity.KbIntentNode;
import com.zjl.collaboration.entity.KbIntentRule;
import com.zjl.collaboration.service.IntentService;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/intents")
@RequiredArgsConstructor
public class IntentController {

    private final IntentService intentService;

    @GetMapping("/nodes")
    public Result<List<KbIntentNode>> getTree() {
        return Results.success(intentService.getTree());
    }

    @GetMapping("/nodes/{id}")
    public Result<KbIntentNode> getNode(@PathVariable Long id) {
        return Results.success(intentService.getNode(id));
    }

    @PostMapping("/nodes")
    public Result<KbIntentNode> createNode(@RequestBody KbIntentNode node) {
        return Results.success(intentService.createNode(node));
    }

    @PutMapping("/nodes/{id}")
    public Result<Void> updateNode(@PathVariable Long id, @RequestBody KbIntentNode node) {
        intentService.updateNode(id, node);
        return Results.success();
    }

    @DeleteMapping("/nodes/{id}")
    public Result<Void> deleteNode(@PathVariable Long id) {
        intentService.deleteNode(id);
        return Results.success();
    }

    @PutMapping("/nodes/{id}/sort")
    public Result<Void> updateSort(@PathVariable Long id, @RequestBody IntentSortReq req) {
        intentService.updateSort(id, req.getParentId(), req.getSortOrder());
        return Results.success();
    }

    @GetMapping("/nodes/{id}/rules")
    public Result<List<KbIntentRule>> getRules(@PathVariable Long id) {
        return Results.success(intentService.getRules(id));
    }

    @PostMapping("/nodes/{id}/rules")
    public Result<KbIntentRule> createRule(@PathVariable Long id, @RequestBody KbIntentRule rule) {
        return Results.success(intentService.createRule(id, rule));
    }

    @PutMapping("/rules/{ruleId}")
    public Result<Void> updateRule(@PathVariable Long ruleId, @RequestBody KbIntentRule rule) {
        intentService.updateRule(ruleId, rule);
        return Results.success();
    }

    @DeleteMapping("/rules/{ruleId}")
    public Result<Void> deleteRule(@PathVariable Long ruleId) {
        intentService.deleteRule(ruleId);
        return Results.success();
    }

    @GetMapping("/nodes/{id}/kbs")
    public Result<List<KbIntentKbRel>> getKbRels(@PathVariable Long id) {
        return Results.success(intentService.getKbRels(id));
    }

    @PostMapping("/nodes/{id}/kbs")
    public Result<KbIntentKbRel> bindKb(@PathVariable Long id, @RequestBody IntentBindKbReq req) {
        return Results.success(intentService.bindKb(id, req.getKbId(), req.getWeight()));
    }

    @PutMapping("/kb-rel/{relId}")
    public Result<Void> updateKbRel(@PathVariable Long relId, @RequestBody IntentBindKbReq req) {
        intentService.updateKbRel(relId, req.getWeight());
        return Results.success();
    }

    @DeleteMapping("/kb-rel/{relId}")
    public Result<Void> unbindKb(@PathVariable Long relId) {
        intentService.unbindKb(relId);
        return Results.success();
    }

    @PostMapping("/match")
    public Result<Map<String, Object>> match(@RequestBody IntentMatchReq req) {
        return Results.success(intentService.match(req.getQuery()));
    }
}
