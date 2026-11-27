package com.game.sage.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemPromptTest {

    private static final String PROMPT = SystemPrompt.GAME_GUIDE_PROMPT;

    @Test
    @DisplayName("系统提示词声明了「加页手记」范围限制")
    void prompt_shouldDeclareScopeRestriction() {
        assertTrue(PROMPT.contains("加页手记"));
        assertTrue(PROMPT.contains("范围限制"));
        assertTrue(PROMPT.contains("排位赛"));
        assertTrue(PROMPT.contains("其他游戏"));
    }

    @Test
    @DisplayName("系统提示词包含代打/脚本/封号等安全红线")
    void prompt_shouldContainSafetyRedLines() {
        assertTrue(PROMPT.contains("代打"));
        assertTrue(PROMPT.contains("脚本"));
        assertTrue(PROMPT.contains("封号"));
        assertTrue(PROMPT.contains("连点器"));
        assertTrue(PROMPT.contains("挂机"));
    }

    @Test
    @DisplayName("系统提示词要求不确定时禁止编造")
    void prompt_shouldRequireHonestyWhenUnsure() {
        assertTrue(PROMPT.contains("这个我不确定"));
        assertTrue(PROMPT.contains("禁止编造"));
    }

    @Test
    @DisplayName("提示词长度合理，且没有未解析的占位符")
    void prompt_shouldBeWellFormed() {
        assertTrue(PROMPT.length() > 500, "提示词内容不应被意外截断");
        assertTrue(PROMPT.length() < 10_000, "提示词不应无限膨胀");
        assertFalse(PROMPT.contains("${"), "提示词中不应残留占位符");
        assertFalse(PROMPT.isBlank());
    }
}
