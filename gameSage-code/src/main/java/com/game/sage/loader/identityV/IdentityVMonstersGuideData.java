package com.game.sage.loader.identityV;

import com.game.sage.loader.IGuideData;
import com.game.sage.model.DocumentsMetaData;
import com.game.sage.utils.MarkdownFilesUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static com.game.sage.utils.CommonUtils.*;

@Component
@RequiredArgsConstructor
public class IdentityVMonstersGuideData implements IGuideData {

    // 加页手记攻略 Markdown 文件路径（放在 resources/guides/IdentityV 下）
    private static final String[] GUIDE_FILES = {
            "classpath:guides/IdentityV/IdentityVMonsters.md"
    };

    private final ResourceLoader resourceLoader;

    @Override
    public List<Document> loadGuideData() throws IOException {
        DocumentsMetaData documentsMetaData = new DocumentsMetaData(
                INFER_SOURCE_IdentityV_GUIDE, INFER_CATEGORY_MONSTER, INFER_MODE_BOTH, INFER_GAME_IdentityV);
        return MarkdownFilesUtils.getDocuments(GUIDE_FILES, resourceLoader, documentsMetaData);
    }
}
