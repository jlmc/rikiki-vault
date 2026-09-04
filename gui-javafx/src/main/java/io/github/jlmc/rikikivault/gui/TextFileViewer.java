package io.github.jlmc.rikikivault.gui;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Fallback viewer: always {@link #supports}, decodes as UTF-8, reports unsupported for anything that isn't. */
final class TextFileViewer implements FileViewer {

    @Override
    public boolean supports(String fileName) {
        return true;
    }

    @Override
    public ViewerResult view(byte[] content, String fileName) {
        String text = tryDecodeUtf8(content);
        if (text == null) {
            return new ViewerResult.UnsupportedViewerResult(Messages.get("fileViewer.unsupported"));
        }
        return new ViewerResult.TextViewerResult(text);
    }

    static String tryDecodeUtf8(byte[] content) {
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return decoded.toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
