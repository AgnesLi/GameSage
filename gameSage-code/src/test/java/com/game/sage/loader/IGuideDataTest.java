package com.game.sage.loader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IGuideDataTest {

    /** 用于验证默认方法的简单实现。 */
    static class DemoGuideData implements IGuideData {
        @Override
        public List<Document> loadGuideData() {
            return List.of();
        }
    }

    @Test
    @DisplayName("默认 getName 返回实现类的简单类名")
    void getName_shouldReturnSimpleClassName() {
        assertEquals("DemoGuideData", new DemoGuideData().getName());
    }

    @Test
    @DisplayName("实现类可返回空攻略列表")
    void loadGuideData_shouldAllowEmptyResult() throws Exception {
        assertTrue(new DemoGuideData().loadGuideData().isEmpty());
    }
}
