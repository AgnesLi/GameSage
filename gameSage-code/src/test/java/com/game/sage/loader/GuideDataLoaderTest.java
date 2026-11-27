package com.game.sage.loader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuideDataLoaderTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private IGuideData firstLoader;

    @Mock
    private IGuideData secondLoader;

    private static Document document(String id) {
        return new Document(id, "片段内容-" + id, Map.of("heading", id));
    }

    private static GuideDataLoader loader(VectorStore vectorStore,
                                          List<IGuideData> guideDataList,
                                          boolean update) {
        GuideDataLoader loader = new GuideDataLoader(vectorStore, guideDataList);
        ReflectionTestUtils.setField(loader, "isDatasourceUpdate", update);
        return loader;
    }

    @SuppressWarnings("unchecked")
    private List<Document> captureAddedDocuments() {
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("datasource.update=false 时不执行加载器，也不写入向量库")
    void run_shouldSkipWhenUpdateDisabled() {
        loader(vectorStore, List.of(firstLoader, secondLoader), false).run();

        verifyNoInteractions(firstLoader, secondLoader, vectorStore);
    }

    @Test
    @DisplayName("datasource.update=true 时合并所有加载器结果，只写入一次")
    void run_shouldMergeAllLoaderResultsAndAddOnce() throws IOException {
        Document first = document("first");
        Document second = document("second");
        when(firstLoader.loadGuideData()).thenReturn(List.of(first));
        when(secondLoader.loadGuideData()).thenReturn(List.of(second));

        loader(vectorStore, List.of(firstLoader, secondLoader), true).run();

        List<Document> added = captureAddedDocuments();
        assertEquals(List.of(first, second), added);
    }

    @Test
    @DisplayName("单个加载器失败不影响其他加载器，向量库仍写入剩余片段")
    void run_shouldContinueWhenOneLoaderFails() throws IOException {
        Document second = document("second");
        when(firstLoader.loadGuideData()).thenThrow(new IOException("文件读取失败"));
        when(secondLoader.loadGuideData()).thenReturn(List.of(second));

        loader(vectorStore, List.of(firstLoader, secondLoader), true).run();

        assertEquals(List.of(second), captureAddedDocuments());
    }

    @Test
    @DisplayName("向量库写入失败时吞掉异常，保证应用仍能启动")
    void run_shouldNotThrowWhenVectorStoreAddFails() throws IOException {
        when(firstLoader.loadGuideData()).thenReturn(List.of(document("first")));
        doThrow(new IllegalStateException("embedding 账户余额不足"))
                .when(vectorStore).add(anyList());

        GuideDataLoader loader = loader(vectorStore, List.of(firstLoader), true);

        assertDoesNotThrow(() -> loader.run());
        verify(vectorStore).add(anyList());
    }

    @Test
    @DisplayName("加载器列表为 null 时不抛异常，也不访问向量库")
    void run_shouldHandleNullLoaderList() {
        GuideDataLoader loader = loader(vectorStore, null, true);

        assertDoesNotThrow(() -> loader.run());
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    @DisplayName("加载器列表为空时写入空列表，不抛异常")
    void run_shouldHandleEmptyLoaderList() {
        loader(vectorStore, List.of(), true).run();

        assertTrue(captureAddedDocuments().isEmpty());
    }
}
