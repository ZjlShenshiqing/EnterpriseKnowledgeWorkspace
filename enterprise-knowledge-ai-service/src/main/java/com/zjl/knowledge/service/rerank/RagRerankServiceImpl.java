package com.zjl.knowledge.service.rerank;

import com.zjl.knowledge.config.RagRerankProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RAG rerank 服务实现：策略分发、关闭模式、超时和失败回退
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRerankServiceImpl implements RagRerankService {

    private final RagRerankProperties properties;
    private final List<RagReranker> rerankers;

    @Override
    public List<RerankedCandidate> rerank(RerankRequest request) {
        if (!properties.isEnabled() || properties.getStrategy() == RerankStrategy.NONE) {
            return passthrough(request.candidates());
        }

        RagReranker reranker = rerankers.stream()
                .filter(r -> r.supports(properties.getStrategy()))
                .findFirst()
                .orElse(null);

        if (reranker == null) {
            log.warn("No reranker found for strategy {}, falling back to original order",
                    properties.getStrategy());
            return passthrough(request.candidates());
        }

        int candidateLimit = properties.getCandidateLimit();
        List<RerankedCandidate> limited = request.candidates();
        if (limited.size() > candidateLimit) {
            log.debug("Truncating candidates from {} to {} for rerank", limited.size(), candidateLimit);
            limited = limited.subList(0, candidateLimit);
        }

        RerankRequest limitedRequest = new RerankRequest(request.query(), limited);
        long timeoutMs = properties.getTimeoutMs();

        try {
            return executeWithTimeout(() -> reranker.rerank(limitedRequest), timeoutMs);
        } catch (Exception e) {
            log.warn("Rerank failed with strategy {}: {}", properties.getStrategy(), e.getMessage());
            if (properties.isFallbackToOriginalOrder()) {
                return passthrough(request.candidates());
            }
            throw new RuntimeException("Rerank failed and fallback is disabled", e);
        }
    }

    private List<RerankedCandidate> passthrough(List<RerankedCandidate> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingInt(RerankedCandidate::originalRank))
                .toList();
    }

    private List<RerankedCandidate> executeWithTimeout(Callable<List<RerankedCandidate>> task, long timeoutMs)
            throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<List<RerankedCandidate>> future = null;
        try {
            future = executor.submit(task);
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            if (future != null) future.cancel(true);
            throw new TimeoutException("Rerank timed out after " + timeoutMs + "ms");
        } finally {
            executor.shutdownNow();
        }
    }
}
