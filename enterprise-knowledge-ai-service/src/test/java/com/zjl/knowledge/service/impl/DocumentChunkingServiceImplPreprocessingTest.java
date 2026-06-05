package com.zjl.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.knowledge.chunk.ChunkingStrategy;
import com.zjl.knowledge.chunk.ChunkingStrategyFactory;
import com.zjl.knowledge.chunk.TextChunk;
import com.zjl.knowledge.domain.ChunkingMode;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.entity.KbDocumentChunkLog;
import com.zjl.knowledge.mapper.KbDocumentChunkLogMapper;
import com.zjl.knowledge.mapper.KbDocumentChunkMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.milvus.VectorDocChunk;
import com.zjl.knowledge.metadata.SensitivityKeywordService;
import com.zjl.knowledge.preprocess.DocumentPreprocessingContext;
import com.zjl.knowledge.preprocess.DocumentPreprocessingResult;
import com.zjl.knowledge.preprocess.DocumentPreprocessor;
import com.zjl.knowledge.preprocess.DocumentPreprocessorSelector;
import com.zjl.knowledge.service.FileStorageService;
import com.zjl.knowledge.service.TikaDocumentParser;
import com.zjl.knowledge.service.VectorSyncService;
import com.zjl.knowledge.token.TokenCounterService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档分块服务预处理接入测试。
 */
@ExtendWith(MockitoExtension.class)
class DocumentChunkingServiceImplPreprocessingTest {

    @Mock
    private KbDocumentMapper kbDocumentMapper;

    @Mock
    private KbDocumentChunkMapper kbDocumentChunkMapper;

    @Mock
    private KbDocumentChunkLogMapper kbDocumentChunkLogMapper;

    @Mock
    private TikaDocumentParser tikaDocumentParser;

    @Mock
    private ChunkingStrategyFactory chunkingStrategyFactory;

    @Mock
    private VectorSyncService vectorSyncService;

    @Mock
    private TokenCounterService tokenCounterService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private SensitivityKeywordService sensitivityKeywordService;

    @Mock
    private DocumentPreprocessorSelector documentPreprocessorSelector;

    @Mock
    private DocumentPreprocessor documentPreprocessor;

    private ObjectMapper objectMapper;

    private CapturingChunkingStrategy capturingStrategy;

    private DocumentChunkingServiceImpl service;

    @BeforeEach
    void setUp() {
        initTableInfo(KbDocument.class);
        initTableInfo(KbDocumentChunk.class);
        objectMapper = new ObjectMapper();
        capturingStrategy = new CapturingChunkingStrategy();
        service = new DocumentChunkingServiceImpl(
                kbDocumentMapper,
                kbDocumentChunkMapper,
                kbDocumentChunkLogMapper,
                tikaDocumentParser,
                chunkingStrategyFactory,
                vectorSyncService,
                tokenCounterService,
                transactionTemplate,
                applicationEventPublisher,
                objectMapper,
                fileStorageService,
                sensitivityKeywordService,
                documentPreprocessorSelector
        );
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
    }

    @Test
    void executeChunkPassesPreprocessedTextToChunkStrategyAndPersistsMetadata() throws Exception {
        KbDocument document = baseDocument();
        when(kbDocumentMapper.selectById(1001L)).thenReturn(document);
        when(fileStorageService.read("storage/travel.pdf"))
                .thenReturn(new ByteArrayInputStream("raw".getBytes(StandardCharsets.UTF_8)));
        when(tikaDocumentParser.extractWithMetadata(any(), eq("travel.pdf"), eq("application/pdf")))
                .thenReturn(new TikaDocumentParser.ParseResult("原始正文", Map.of("Content-Type", "application/pdf")));
        when(documentPreprocessorSelector.select(any(DocumentPreprocessingContext.class)))
                .thenReturn(documentPreprocessor);
        when(documentPreprocessor.preprocess(any(DocumentPreprocessingContext.class)))
                .thenReturn(new DocumentPreprocessingResult(
                        "文档：差旅制度\n\n正文：\n预处理正文",
                        Map.of("doc_type", "application/pdf", "title", "差旅制度", "preprocess_strategy", "DEFAULT"),
                        Map.of("doc_type", "application/pdf", "title", "差旅制度", "preprocess_strategy", "DEFAULT")
                ));
        when(chunkingStrategyFactory.requireStrategy(ChunkingMode.PARAGRAPH)).thenReturn(capturingStrategy);
        when(vectorSyncService.shouldEmbed(document)).thenReturn(false);
        when(tokenCounterService.countTokens(any())).thenReturn(12);
        when(tikaDocumentParser.summarize(any(), eq(200))).thenReturn("文档：差旅制度 正文： 预处理正文");
        executeTransactionCallbacks();

        service.executeChunk(1001L, 3001L);

        assertThat(capturingStrategy.receivedText).isEqualTo("文档：差旅制度\n\n正文：\n预处理正文");

        ArgumentCaptor<KbDocumentChunk> chunkCaptor = ArgumentCaptor.forClass(KbDocumentChunk.class);
        verify(kbDocumentChunkMapper).insert(chunkCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> chunkMetadata = objectMapper.readValue(chunkCaptor.getValue().getMetadataJson(), Map.class);
        assertThat(chunkMetadata)
                .containsEntry("doc_type", "application/pdf")
                .containsEntry("title", "差旅制度")
                .containsEntry("preprocess_strategy", "DEFAULT")
                .containsKeys("docId", "fileName", "chunkIndex", "startOffset", "endOffset", "sensitivityLevel");

        ArgumentCaptor<KbDocumentChunkLog> logCaptor = ArgumentCaptor.forClass(KbDocumentChunkLog.class);
        verify(kbDocumentChunkLogMapper).insert(logCaptor.capture());
        verify(kbDocumentMapper).update(eq(null), any(Wrapper.class));
    }

    @Test
    void executeChunkPassesPreprocessingMetadataToVectorSyncWhenEmbeddingIsEnabled() throws Exception {
        KbDocument document = baseDocument();
        when(kbDocumentMapper.selectById(1001L)).thenReturn(document);
        when(fileStorageService.read("storage/travel.pdf"))
                .thenReturn(new ByteArrayInputStream("raw".getBytes(StandardCharsets.UTF_8)));
        when(tikaDocumentParser.extractWithMetadata(any(), eq("travel.pdf"), eq("application/pdf")))
                .thenReturn(new TikaDocumentParser.ParseResult("原始正文", Map.of()));
        when(documentPreprocessorSelector.select(any(DocumentPreprocessingContext.class)))
                .thenReturn(documentPreprocessor);
        when(documentPreprocessor.preprocess(any(DocumentPreprocessingContext.class)))
                .thenReturn(new DocumentPreprocessingResult(
                        "文档：差旅制度\n\n正文：\n预处理正文",
                        Map.of("doc_type", "application/pdf", "title", "差旅制度", "preprocess_strategy", "DEFAULT"),
                        Map.of("doc_type", "application/pdf", "title", "差旅制度", "preprocess_strategy", "DEFAULT")
                ));
        when(chunkingStrategyFactory.requireStrategy(ChunkingMode.PARAGRAPH)).thenReturn(capturingStrategy);
        when(vectorSyncService.shouldEmbed(document)).thenReturn(true);
        when(vectorSyncService.embedBatch(List.of("文档：差旅制度\n\n正文：\n预处理正文"), document))
                .thenReturn(List.of(List.of(0.1f, 0.2f)));
        when(tokenCounterService.countTokens(any())).thenReturn(12);
        when(tikaDocumentParser.summarize(any(), eq(200))).thenReturn("summary");
        executeTransactionCallbacks();

        service.executeChunk(1001L, 3001L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VectorDocChunk>> vectorCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorSyncService).indexDocumentChunks(eq(document), vectorCaptor.capture());
        assertThat(vectorCaptor.getValue()).hasSize(1);
        assertThat(vectorCaptor.getValue().get(0).getMetadata())
                .containsEntry("doc_type", "application/pdf")
                .containsEntry("title", "差旅制度")
                .containsEntry("preprocess_strategy", "DEFAULT")
                .containsKeys("docId", "fileName", "chunkIndex", "startOffset", "endOffset", "sensitivityLevel");
    }

    @Test
    void executeChunkMarksDocumentFailedWhenPreprocessingThrows() throws Exception {
        KbDocument document = baseDocument();
        when(kbDocumentMapper.selectById(1001L)).thenReturn(document);
        when(fileStorageService.read("storage/travel.pdf"))
                .thenReturn(new ByteArrayInputStream("raw".getBytes(StandardCharsets.UTF_8)));
        when(tikaDocumentParser.extractWithMetadata(any(), eq("travel.pdf"), eq("application/pdf")))
                .thenReturn(new TikaDocumentParser.ParseResult("原始正文", Map.of()));
        when(documentPreprocessorSelector.select(any(DocumentPreprocessingContext.class)))
                .thenReturn(documentPreprocessor);
        when(documentPreprocessor.preprocess(any(DocumentPreprocessingContext.class)))
                .thenThrow(new IllegalStateException("预处理失败"));
        executeTransactionCallbacks();

        service.executeChunk(1001L, 3001L);

        verify(kbDocumentChunkMapper, never()).insert(any(KbDocumentChunk.class));
        ArgumentCaptor<KbDocumentChunkLog> logCaptor = ArgumentCaptor.forClass(KbDocumentChunkLog.class);
        verify(kbDocumentChunkLogMapper).updateById(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(logCaptor.getValue().getErrorMessage()).contains("预处理失败");
    }

    @Test
    void executeChunkKeepsBlankParsedTextFailureBeforePreprocessing() throws Exception {
        KbDocument document = baseDocument();
        when(kbDocumentMapper.selectById(1001L)).thenReturn(document);
        when(fileStorageService.read("storage/travel.pdf"))
                .thenReturn(new ByteArrayInputStream("raw".getBytes(StandardCharsets.UTF_8)));
        when(tikaDocumentParser.extractWithMetadata(any(), eq("travel.pdf"), eq("application/pdf")))
                .thenReturn(new TikaDocumentParser.ParseResult("   ", Map.of()));
        executeTransactionCallbacks();

        service.executeChunk(1001L, 3001L);

        verify(documentPreprocessorSelector, never()).select(any());
        verify(kbDocumentChunkMapper, never()).insert(any(KbDocumentChunk.class));
        ArgumentCaptor<KbDocumentChunkLog> logCaptor = ArgumentCaptor.forClass(KbDocumentChunkLog.class);
        verify(kbDocumentChunkLogMapper).updateById(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(logCaptor.getValue().getErrorMessage()).contains("未能从文件中提取正文");
    }

    private KbDocument baseDocument() {
        KbDocument document = new KbDocument();
        document.setId(1001L);
        document.setOwnerId(2001L);
        document.setFileName("travel.pdf");
        document.setFileType("application/pdf");
        document.setFileUrl("storage/travel.pdf");
        document.setTitle("差旅制度");
        document.setSourceLocation("https://example.com/travel.pdf");
        document.setChunkStrategy("PARAGRAPH");
        document.setProcessMode("CHUNK");
        return document;
    }

    private void executeTransactionCallbacks() {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<Object> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private static class CapturingChunkingStrategy implements ChunkingStrategy {

        private String receivedText;

        @Override
        public ChunkingMode mode() {
            return ChunkingMode.PARAGRAPH;
        }

        @Override
        public List<TextChunk> chunk(String text, com.zjl.knowledge.chunk.ChunkingOptions options) {
            this.receivedText = text;
            return new ArrayList<>(List.of(new TextChunk(0, text, 0, text.length())));
        }
    }
}
