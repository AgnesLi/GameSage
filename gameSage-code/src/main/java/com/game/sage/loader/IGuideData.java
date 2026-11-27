package com.game.sage.loader;

import org.springframework.ai.document.Document;

import java.io.IOException;
import java.util.List;

public interface IGuideData {

    List<Document> loadGuideData() throws IOException;

    /**
     * 用于日志标识
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
