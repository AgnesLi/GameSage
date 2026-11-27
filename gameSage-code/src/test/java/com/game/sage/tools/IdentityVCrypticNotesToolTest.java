package com.game.sage.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityVCrypticNotesToolTest {

    private static final String NOT_FOUND = "本地知识库未找到相关攻略。";
    private static final String SEARCH_FAILED = "本地知识库检索失败";

    @Mock
    private VectorStore vectorStore;

    private IdentityVCrypticNotesTool tool() {
        return new IdentityVCrypticNotesTool(vectorStore);
    }

    private static Document document(String heading, String category, String text) {
        return new Document("doc-" + text, text, Map.of("heading", heading, "category", category));
    }

    private SearchRequest captureRequest() {
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("检索参数固定为 topK=4、相似度阈值 0.65，并原样传递查询词")
    void searchLocalGuide_shouldBuildSearchRequestWithFixedParameters() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        tool().searchLocalGuide("贪婪的盗匪 绿锤 应对", "both");

        SearchRequest request = captureRequest();
        assertEquals("贪婪的盗匪 绿锤 应对", request.getQuery());
        assertEquals(4, request.getTopK());
        assertEquals(0.65, request.getSimilarityThreshold(), 1e-9);
    }

    @Test
    @DisplayName("mode 为 both 时不添加视角过滤条件，且大小写不敏感")
    void searchLocalGuide_shouldNotFilterWhenModeIsBoth() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        tool().searchLocalGuide("职业推荐", "both");
        tool().searchLocalGuide("职业推荐", "BOTH");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore, times(2)).similaritySearch(captor.capture());

        captor.getAllValues().forEach(request -> assertFalse(
                request.hasFilterExpression(), "both 模式不应带过滤表达式"));
    }

    @Test
    @DisplayName("指定求生者视角时过滤 mode == 'survivor' 或 mode == 'both'")
    void searchLocalGuide_shouldFilterBySurvivorMode() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        tool().searchLocalGuide("角色推荐", "survivor");

        SearchRequest expected = SearchRequest.builder()
                .query("角色推荐")
                .topK(4)
                .similarityThreshold(0.65)
                .filterExpression("mode == 'survivor' or mode == 'both'")
                .build();
        assertEquals(expected, captureRequest());
    }

    @Test
    @DisplayName("指定监管者视角时过滤 mode == 'regulator' 或 mode == 'both'")
    void searchLocalGuide_shouldFilterByRegulatorMode() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        tool().searchLocalGuide("怪物机制", "regulator");

        SearchRequest expected = SearchRequest.builder()
                .query("怪物机制")
                .topK(4)
                .similarityThreshold(0.65)
                .filterExpression("mode == 'regulator' or mode == 'both'")
                .build();
        assertEquals(expected, captureRequest());
    }

    @Test
    @DisplayName("无检索结果时返回明确的未找到提示")
    void searchLocalGuide_shouldReturnNotFoundMessageWhenNoDocuments() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertEquals(NOT_FOUND, tool().searchLocalGuide("不存在的攻略", "both"));
    }

    @Test
    @DisplayName("拼接结果包含片段序号、标题、分类与正文，并按顺序编号")
    void searchLocalGuide_shouldSpliceSegmentsInOrder() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                document("贪婪的盗匪::技能解析", "monster", "绿锤会击倒范围内求生者。"),
                document("无名布偶::打法", "character", "优先拆除附近的傀儡。")));

        String result = tool().searchLocalGuide("绿锤 应对", "survivor");

        assertEquals("""
                【片段1｜贪婪的盗匪::技能解析｜monster】
                绿锤会击倒范围内求生者。

                【片段2｜无名布偶::打法｜character】
                优先拆除附近的傀儡。""", result);
    }

    @Test
    @DisplayName("metadata 缺少 heading / category 时使用默认值")
    void searchLocalGuide_shouldUseDefaultMetadataValues() {
        Document withoutMetadata = new Document("doc-1", "没有元数据的片段", Map.of());
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(withoutMetadata));

        String result = tool().searchLocalGuide("任意查询", "both");

        assertTrue(result.startsWith("【片段1｜未知章节｜general】"), result);
        assertTrue(result.contains("没有元数据的片段"), result);
    }

    @Test
    @DisplayName("检索异常时返回固定失败文案，只调用一次不重试")
    void searchLocalGuide_shouldReturnFailureMessageWhenSearchThrows() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("向量库连接失败"));

        String result = tool().searchLocalGuide("任意查询", "both");

        assertEquals(SEARCH_FAILED, result);
        verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("数据库访问失败时直接返回失败文案，不重试")
    void searchLocalGuide_shouldReturnFailureWithoutRetryWhenDatabaseFails() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new DataAccessResourceFailureException("无法连接数据库"));

        String result = tool().searchLocalGuide("职业推荐", "both");

        assertEquals(SEARCH_FAILED, result);
        verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("未知视角不会抛异常，按普通检索处理")
    void searchLocalGuide_shouldHandleUnknownMode() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertEquals(NOT_FOUND, tool().searchLocalGuide("角色推荐", "旁观者"));
    }

    @Test
    @DisplayName("包含引号的非法 mode 不会向外抛异常")
    void searchLocalGuide_shouldNotThrowForMalformedMode() {
        String result = tool().searchLocalGuide("角色推荐", "x' or '1'=='1");

        assertFalse(result.isBlank());
    }

    @Test
    @DisplayName("query 为 null 时返回失败信息，且不访问向量库")
    void searchLocalGuide_shouldReturnFailureMessageForNullQuery() {
        String result = tool().searchLocalGuide(null, "both");

        assertEquals(SEARCH_FAILED, result);
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("query 为空字符串时按无结果处理")
    void searchLocalGuide_shouldReturnNotFoundForEmptyQuery() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertEquals(NOT_FOUND, tool().searchLocalGuide("", "both"));
    }
}
