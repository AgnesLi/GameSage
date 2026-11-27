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
public class IdentityVClassicsGuideData implements IGuideData {

    private static final String[] GUIDE_FILES = {
            "classpath:guides/IdentityV/IdentityVClassics.md"
    };

    private final ResourceLoader resourceLoader;

    @Override
    public List<Document> loadGuideData() throws IOException {
        DocumentsMetaData documentsMetaData = new DocumentsMetaData(
                INFER_SOURCE_IdentityV_GUIDE, INFER_CATEGORY_CLASSICS, INFER_MODE_BOTH, INFER_GAME_IdentityV);
        return MarkdownFilesUtils.getDocuments(GUIDE_FILES, resourceLoader, documentsMetaData);
    }

}
