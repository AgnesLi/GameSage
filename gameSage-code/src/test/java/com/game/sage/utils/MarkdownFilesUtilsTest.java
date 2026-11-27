package com.game.sage.utils;

import com.game.sage.model.DocumentsMetaData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.document.Document;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarkdownFilesUtilsTest {

    private static final String SOURCE_FILE = "IdentityVMonsters.md";

    // ------------------------------------------------------------------
    // splitByMarkdownHeadings
    // ------------------------------------------------------------------

    private static Document document(String id, String text) {
        return document(id, text, new HashMap<>());
    }

    private static Document document(String id, String text, Map<String, Object> metadata) {
        return new Document(id, text, metadata);
    }

    private static List<String> ids(List<Document> documents) {
        return documents.stream().map(Document::getId).toList();
    }

    private static void assertValidUuids(List<Document> documents) {
        documents.forEach(doc -> assertDoesNotThrow(
                () -> UUID.fromString(doc.getId()), "ID 应为合法 UUID: " + doc.getId()));
    }

    @Test
    @DisplayName("按 ## / ### 标题切分，三级标题拼接所属二级标题，正文带标题前缀")
    void splitByMarkdownHeadings_shouldSplitSections() {
        String markdown = """
                # 一级标题不参与切分
                前言内容
                
                ## 贪婪的盗匪
                这是贪婪的盗匪的攻略正文。
                
                ### 技能解析
                技能解析正文。
                """;

        List<Document> documents = MarkdownFilesUtils.splitByMarkdownHeadings(
                markdown, new HashMap<>());

        assertEquals(2, documents.size());

        Document first = documents.get(0);
        assertEquals("【贪婪的盗匪】\n这是贪婪的盗匪的攻略正文。", first.getText());
        assertEquals("贪婪的盗匪", first.getMetadata().get("heading"));

        Document second = documents.get(1);
        assertEquals("贪婪的盗匪::技能解析", second.getMetadata().get("heading"));
        assertTrue(second.getText().startsWith("【贪婪的盗匪::技能解析】"));

        // 第一个标题之前的前言不会进入任何分块
        assertTrue(documents.stream().noneMatch(doc -> doc.getText().contains("前言内容")));
    }

    @Test
    @DisplayName("标题切分生成的 ID 是确定性的，且为合法 UUID")
    void splitByMarkdownHeadings_shouldGenerateDeterministicIds() {
        String markdown = """
                ## 贪婪的盗匪
                这是贪婪的盗匪的攻略正文。
                
                ### 技能解析
                技能解析正文。
                """;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("file", SOURCE_FILE);

        List<Document> first = MarkdownFilesUtils.splitByMarkdownHeadings(markdown, metadata);
        List<Document> second = MarkdownFilesUtils.splitByMarkdownHeadings(markdown, new HashMap<>(metadata));

        assertEquals(2, first.size());
        assertEquals(ids(first), ids(second));
        assertEquals(ids(first).size(), ids(first).stream().distinct().count());
        assertValidUuids(first);
    }

    @Test
    @DisplayName("文件变化生成不同 ID；正文变化保持同一 ID 以便覆盖更新")
    void splitByMarkdownHeadings_shouldKeepIdWhenContentChangesButChangeWhenFileChanges() {
        String markdown = "## 贪婪的盗匪\n这是贪婪的盗匪的攻略正文。";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("file", SOURCE_FILE);

        String id = MarkdownFilesUtils.splitByMarkdownHeadings(markdown, metadata).get(0).getId();
        String changedContentId = MarkdownFilesUtils.splitByMarkdownHeadings(
                "## 贪婪的盗匪\n这是修改后的攻略正文。", metadata).get(0).getId();

        Map<String, Object> otherFile = new HashMap<>();
        otherFile.put("file", "IdentityVClassics.md");
        String otherFileId = MarkdownFilesUtils.splitByMarkdownHeadings(markdown, otherFile).get(0).getId();

        // ID 由 文件 + 标题 + 分片序号 决定，正文更新时复用同一 ID，便于 PgVectorStore 覆盖旧向量
        assertEquals(id, changedContentId);
        assertNotEquals(id, otherFileId);
    }

    @Test
    @DisplayName("三级标题拼接最近的二级标题，切换二级标题后重新拼接")
    void splitByMarkdownHeadings_shouldComposeH3WithLatestH2() {
        String markdown = """
                ## 贪婪的盗匪
                ### 技能解析
                技能正文
                
                ## 无名布偶
                ### 打法
                打法正文
                """;

        List<Document> documents = MarkdownFilesUtils.splitByMarkdownHeadings(
                markdown, new HashMap<>());

        assertEquals(2, documents.size());
        assertEquals("贪婪的盗匪::技能解析", documents.get(0).getMetadata().get("heading"));
        assertEquals("无名布偶::打法", documents.get(1).getMetadata().get("heading"));
    }

    @Test
    @DisplayName("没有二级标题时三级标题使用自身标题")
    void splitByMarkdownHeadings_shouldUseOwnTitleWhenH3HasNoParent() {
        List<Document> documents = MarkdownFilesUtils.splitByMarkdownHeadings(
                "### 技能解析\n正文内容", new HashMap<>());

        assertEquals(1, documents.size());
        assertEquals("技能解析", documents.get(0).getMetadata().get("heading"));
    }

    @Test
    @DisplayName("metadata 缺省时根据标题推断 category / mode / game")
    void splitByMarkdownHeadings_shouldInferMetadataFromHeading() {
        String markdown = """
                ## 第五人格角色求生者介绍
                正文一
                
                ## 监管者辞章异像
                正文二
                """;

        List<Document> documents = MarkdownFilesUtils.splitByMarkdownHeadings(
                markdown, new HashMap<>());

        assertEquals(2, documents.size());

        Map<String, Object> first = documents.get(0).getMetadata();
        assertEquals("IdentityV", first.get("game"));
        assertEquals("character", first.get("category"));
        assertEquals("survivor", first.get("mode"));

        Map<String, Object> second = documents.get(1).getMetadata();
        assertEquals("both", second.get("game"));
        assertEquals("classics", second.get("category"));
        assertEquals("regulator", second.get("mode"));
    }

    // ------------------------------------------------------------------
    // refineChunks
    // ------------------------------------------------------------------

    @Test
    @DisplayName("已提供的 metadata 不会被标题推断覆盖")
    void splitByMarkdownHeadings_shouldKeepPresetMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", "monster");
        metadata.put("mode", "both");
        metadata.put("game", "IdentityV");

        List<Document> documents = MarkdownFilesUtils.splitByMarkdownHeadings(
                "## 角色求生者\n正文内容", metadata);

        assertEquals(1, documents.size());
        assertEquals("monster", documents.get(0).getMetadata().get("category"));
        assertEquals("both", documents.get(0).getMetadata().get("mode"));
        assertEquals("IdentityV", documents.get(0).getMetadata().get("game"));
    }

    @Test
    @DisplayName("空章节会被跳过，只有一级标题时返回空列表")
    void splitByMarkdownHeadings_shouldSkipEmptySections() {
        String markdown = """
                ## 空章节
                
                ### 有内容
                实际正文
                """;

        List<Document> documents = MarkdownFilesUtils.splitByMarkdownHeadings(
                markdown, new HashMap<>());

        assertEquals(1, documents.size());
        // 二级标题本身没有正文，但仍作为三级标题的上级出现在 heading 中
        assertEquals("空章节::有内容", documents.get(0).getMetadata().get("heading"));

        assertTrue(MarkdownFilesUtils.splitByMarkdownHeadings(
                "# 只有一级标题\n正文", new HashMap<>()).isEmpty());
    }

    @Test
    @DisplayName("长度在 30~450 之间的块原样保留")
    void refineChunks_shouldKeepChunksWithinLengthRange() {
        Document chunk = document("doc-1", "这是一段长度适中的攻略内容。".repeat(4));

        List<Document> refined = MarkdownFilesUtils.refineChunks(List.of(chunk));

        assertEquals(1, refined.size());
        assertSame(chunk, refined.get(0));
    }

    // ------------------------------------------------------------------
    // reassignIdsAfterSplit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("长度小于 30 的块会被丢弃")
    void refineChunks_shouldDropTooShortChunks() {
        Document shortChunk = document("doc-2", "太短了");

        assertTrue(MarkdownFilesUtils.refineChunks(List.of(shortChunk)).isEmpty());
    }

    // ------------------------------------------------------------------
    // getDocuments
    // ------------------------------------------------------------------

    @Test
    @DisplayName("超过 450 字符的块会二次切分，并生成稳定的分片 ID 与 metadata")
    void refineChunks_shouldSplitLongChunks() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "local_guide");
        metadata.put("file", SOURCE_FILE);
        metadata.put("heading", "超长章节");

        String longText = "这是一段用于测试超长文本切分的中文攻略内容。".repeat(150);
        Document parent = document("IdentityVMonsters.md::超长章节", longText, metadata);

        List<Document> refined = MarkdownFilesUtils.refineChunks(List.of(parent));

        assertTrue(refined.size() >= 2, "超长文本应被切分为多个块");
        for (int i = 0; i < refined.size(); i++) {
            Document child = refined.get(i);
            assertEquals(i, child.getMetadata().get("chunk_index"));
            assertEquals(refined.size(), child.getMetadata().get("total_chunks"));
            assertEquals("local_guide", child.getMetadata().get("source"));
            assertFalse(child.getText().isBlank());
        }

        List<Document> refinedAgain = MarkdownFilesUtils.refineChunks(List.of(parent));
        assertEquals(ids(refined), ids(refinedAgain));
        assertEquals(refined.size(), ids(refined).stream().distinct().count());
        assertValidUuids(refined);
    }

    @Test
    @DisplayName("reassignIdsAfterSplit 生成序号 ID 并保留原 metadata")
    void reassignIdsAfterSplit_shouldRebuildDocuments() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("file", SOURCE_FILE);
        metadata.put("heading", "贪婪的盗匪");

        List<Document> subChunks = List.of(
                document("random-0", "第一段内容", metadata),
                document("random-1", "第二段内容", metadata));

        List<Document> fixed = MarkdownFilesUtils.reassignIdsAfterSplit(subChunks);

        assertEquals(2, fixed.size());
        for (int i = 0; i < fixed.size(); i++) {
            Document doc = fixed.get(i);
            assertEquals(i, doc.getMetadata().get("chunk_index"));
            assertEquals(2, doc.getMetadata().get("total_chunks"));
            assertEquals(SOURCE_FILE, doc.getMetadata().get("file"));
        }
        assertEquals("第一段内容", fixed.get(0).getText());
        assertEquals("第二段内容", fixed.get(1).getText());

        List<Document> fixedAgain = MarkdownFilesUtils.reassignIdsAfterSplit(subChunks);
        assertEquals(ids(fixed), ids(fixedAgain));
        assertEquals(2, ids(fixed).stream().distinct().count());
        assertValidUuids(fixed);
    }

    @Test
    @DisplayName("getDocuments 从 classpath 读取 Markdown 并写入基础 metadata")
    void getDocuments_shouldLoadClasspathGuide() throws IOException {
        String[] guideFiles = {"classpath:guides/IdentityV/IdentityVMonsters.md"};

        DocumentsMetaData documentsMetaData = new DocumentsMetaData(
                "IdentityV_guide", "monster", "both", "IdentityV");
        List<Document> documents = MarkdownFilesUtils.getDocuments(
                guideFiles, new DefaultResourceLoader(), documentsMetaData);

        assertFalse(documents.isEmpty());
        for (Document doc : documents) {
            Map<String, Object> metadata = doc.getMetadata();
            assertEquals("IdentityV_guide", metadata.get("source"));
            assertEquals("IdentityVMonsters.md", metadata.get("file"));
            assertEquals("monster", metadata.get("category"));
            assertEquals("both", metadata.get("mode"));
            assertEquals("IdentityV", metadata.get("game"));
            assertNotNull(metadata.get("heading"));
        }

        // 同一份文件重复加载应得到完全相同的 ID，便于 PgVectorStore upsert
        List<Document> documentsAgain = MarkdownFilesUtils.getDocuments(
                guideFiles, new DefaultResourceLoader(), documentsMetaData);
        assertEquals(ids(documents), ids(documentsAgain));
        assertEquals(documents.size(), ids(documents).stream().distinct().count());
        assertValidUuids(documents);
    }

    @Test
    @DisplayName("资源不存在时抛出 IOException")
    void getDocuments_shouldThrowForMissingResource() {
        String[] guideFiles = {"classpath:guides/IdentityV/NotExist.md"};

        DocumentsMetaData documentsMetaData = new DocumentsMetaData(
                "IdentityV_guide", "monster", "both", "IdentityV");
        assertThrows(IOException.class, () -> MarkdownFilesUtils.getDocuments(
                guideFiles, new DefaultResourceLoader(), documentsMetaData));
    }

    @Test
    @DisplayName("传入空文件数组时返回空列表")
    void getDocuments_shouldReturnEmptyListForNoFiles() throws IOException {
        DocumentsMetaData documentsMetaData = new DocumentsMetaData(
                "IdentityV_guide", "monster", "both", "IdentityV");
        List<Document> documents = MarkdownFilesUtils.getDocuments(
                new String[0], new DefaultResourceLoader(), documentsMetaData);

        assertTrue(documents.isEmpty());
    }

    // ------------------------------------------------------------------
    // metadata 推断全分支
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0} -> category={1}, mode={2}, game={3}")
    @CsvSource({
            "角色攻略, character, both, both",
            "辞章价值, classics, both, both",
            "异像机制, monster, both, both",
            "求生者路线, both, survivor, both",
            "智者推荐, both, survivor, both",
            "主角推荐, both, survivor, both",
            "信使推荐, both, survivor, both",
            "英雄推荐, both, survivor, both",
            "监管者武器, both, regulator, both",
            "第五人格基础, both, both, IdentityV",
            "第五人格角色介绍, character, both, IdentityV"
    })
    @DisplayName("标题推断覆盖 category / mode / game 的所有分支")
    void splitByMarkdownHeadings_shouldInferMetadataForAllBranches(String heading,
                                                                   String category,
                                                                   String mode,
                                                                   String game) {
        List<Document> documents = MarkdownFilesUtils.splitByMarkdownHeadings(
                "## " + heading + "\n正文内容", new HashMap<>());

        assertEquals(1, documents.size());
        Map<String, Object> metadata = documents.get(0).getMetadata();
        assertEquals(category, metadata.get("category"));
        assertEquals(mode, metadata.get("mode"));
        assertEquals(game, metadata.get("game"));
    }

    // ------------------------------------------------------------------
    // 已知限制与健壮性
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同名且同内容的章节会生成相同 ID（已知限制：写入时后写覆盖前写）")
    void splitByMarkdownHeadings_shouldReuseIdForIdenticalDuplicateHeadings() {
        String markdown = """
                ## 同名章节
                完全相同的正文内容。

                ## 同名章节
                完全相同的正文内容。
                """;

        List<Document> documents = MarkdownFilesUtils.splitByMarkdownHeadings(
                markdown, new HashMap<>());

        assertEquals(2, documents.size());
        assertEquals(documents.get(0).getId(), documents.get(1).getId());
    }

    @Test
    @DisplayName("refineChunks 跳过 null 文档、null 文本与空白文本")
    void refineChunks_shouldSkipNullOrBlankChunks() {
        Document nullText = mock(Document.class);
        when(nullText.getText()).thenReturn(null);
        Document blankText = document("doc-blank", "   ");
        Document valid = document("doc-valid", "这是一段长度适中的攻略内容。".repeat(4));

        List<Document> input = new ArrayList<>();
        input.add(null);
        input.add(nullText);
        input.add(blankText);
        input.add(valid);

        List<Document> refined = MarkdownFilesUtils.refineChunks(input);

        assertEquals(1, refined.size());
        assertSame(valid, refined.get(0));
    }

    @Test
    @DisplayName("长度边界：29 丢弃，30 与 450 保留")
    void refineChunks_shouldRespectLengthBoundaries() {
        Document tooShort = document("doc-29", "字".repeat(29));
        Document minLength = document("doc-30", "字".repeat(30));
        Document maxLength = document("doc-450", "字".repeat(450));

        List<Document> refined = MarkdownFilesUtils.refineChunks(
                List.of(tooShort, minLength, maxLength));

        assertEquals(2, refined.size());
        assertFalse(ids(refined).contains("doc-29"));
        assertTrue(ids(refined).containsAll(List.of("doc-30", "doc-450")));
    }

    @Test
    @DisplayName("超过 450 字符的块会被二次切分")
    void refineChunks_shouldSplitChunksOverBoundary() {
        Document longChunk = document("doc-451", "测试文本。".repeat(400));

        List<Document> refined = MarkdownFilesUtils.refineChunks(List.of(longChunk));

        assertTrue(refined.size() >= 2, "超过 450 字符的块应至少切成两片");
    }

    @Test
    @DisplayName("Windows CRLF 换行也能正确识别二级 / 三级标题")
    void splitByMarkdownHeadings_shouldHandleCrlf() {
        String markdown = "## 贪婪的盗匪\r\n正文内容\r\n\r\n### 技能解析\r\n技能正文";

        List<Document> documents = MarkdownFilesUtils.splitByMarkdownHeadings(
                markdown, new HashMap<>());

        assertEquals(2, documents.size());
        assertEquals("贪婪的盗匪", documents.get(0).getMetadata().get("heading"));
        assertEquals("贪婪的盗匪::技能解析", documents.get(1).getMetadata().get("heading"));
    }

    @Test
    @DisplayName("空文档或纯空白文档返回空列表")
    void splitByMarkdownHeadings_shouldReturnEmptyForBlankMarkdown() {
        assertTrue(MarkdownFilesUtils.splitByMarkdownHeadings("", new HashMap<>()).isEmpty());
        assertTrue(MarkdownFilesUtils.splitByMarkdownHeadings("   \n  ", new HashMap<>()).isEmpty());
    }
}
