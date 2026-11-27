package com.game.sage.model;

import lombok.Data;

@Data
public class DocumentsMetaData {
    private String source;
    private String inferCategory;
    private String inferMode;
    private String inferGame;

    public DocumentsMetaData(String source, String inferCategory, String inferMode, String inferGame) {
        this.source = source;
        this.inferCategory = inferCategory;
        this.inferMode = inferMode;
        this.inferGame = inferGame;
    }
}
