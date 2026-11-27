package com.game.sage.loader.identityV;

import com.game.sage.loader.IGuideData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.document.Document;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityVGuideDataTest {

    private static final Set<String> VALID_MODES = Set.of("survivor", "regulator", "both");

    static Stream<Arguments> loaders() {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        return Stream.of(
                Arguments.of(new IdentityVMonstersGuideData(resourceLoader),
                        "monster", "IdentityVMonsters.md"),
                Arguments.of(new IdentityVClassicsGuideData(resourceLoader),
                        "classics", "IdentityVClassics.md"),
                Arguments.of(new IdentityVCharacterGuideData(resourceLoader),
                        "character", "IdentityVCharacter.md"));
    }

    private static List<String> ids(List<Document> documents) {
        return documents.stream().map(Document::getId).toList();
    }

    @ParameterizedTest(name = "{1} / {2}")
    @MethodSource("loaders")
    @DisplayName("加载 classpath 攻略，并写入正确的 source/category/game/mode/file metadata")
    void loadGuideData_shouldLoadGuideWithExpectedMetadata(IGuideData loader,
                                                           String expectedCategory,
                                                           String expectedFile) throws Exception {
        List<Document> documents = loader.loadGuideData();

        assertFalse(documents.isEmpty(), "攻略文件应至少切分出一个片段");
        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            String heading = (String) metadata.get("heading");

            assertEquals("IdentityV_guide", metadata.get("source"));
            assertEquals(expectedFile, metadata.get("file"));
            assertEquals(expectedCategory, metadata.get("category"));
            assertEquals("IdentityV", metadata.get("game"));
            assertTrue(VALID_MODES.contains(metadata.get("mode")),
                    "mode 应为合法视角，实际: " + metadata.get("mode"));

            assertNotNull(heading, "每个片段都应带 heading");
            assertFalse(heading.isBlank());

            // 超长章节会被二次切分，只有首个分片保留标题前缀
            Object chunkIndex = metadata.get("chunk_index");
            boolean firstChunk = chunkIndex == null || Integer.valueOf(0).equals(chunkIndex);
            if (firstChunk) {
                assertTrue(document.getText().startsWith("【" + heading + "】"),
                        "首个片段应以章节标题开头，实际: " + document.getText());
            }
            assertTrue(document.getText().length() >= 30, "过短片段应被过滤");
        }

        assertEquals(documents.size(), ids(documents).stream().distinct().count(),
                "同一次加载内 ID 不应重复");
    }

    @ParameterizedTest(name = "{1} / {2}")
    @MethodSource("loaders")
    @DisplayName("重复加载同一份攻略生成的 ID 完全一致")
    void loadGuideData_shouldGenerateDeterministicIds(IGuideData loader,
                                                      String expectedCategory,
                                                      String expectedFile) throws Exception {
        assertEquals(ids(loader.loadGuideData()), ids(loader.loadGuideData()));
    }
}
