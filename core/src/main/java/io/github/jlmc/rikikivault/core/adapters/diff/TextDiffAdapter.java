package io.github.jlmc.rikikivault.core.adapters.diff;

import io.github.jlmc.rikikivault.core.ports.out.DiffPort;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Line-based diff. Text files get a {@code --- previous}/{@code +++ current} header
 * followed by one line per input line, prefixed with a space (unchanged), {@code -} (removed) or
 * {@code +} (added), computed via the classic LCS dynamic-programming backtrack. Binary content
 * (either side fails to decode as UTF-8) just reports that the file changed - this
 * makes no attempt to diff at the byte level.
 */
public final class TextDiffAdapter implements DiffPort {

    @Override
    public String diff(byte[] previous, byte[] current) {
        String previousText = tryDecodeUtf8(previous);
        String currentText = tryDecodeUtf8(current);
        if (previousText == null || currentText == null) {
            return "File changed";
        }

        String[] a = previousText.isEmpty() ? new String[0] : previousText.split("\n", -1);
        String[] b = currentText.isEmpty() ? new String[0] : currentText.split("\n", -1);

        StringBuilder result = new StringBuilder("--- previous\n+++ current\n");
        for (String line : lineDiff(a, b)) {
            result.append(line).append('\n');
        }
        return result.toString();
    }

    private static List<String> lineDiff(String[] a, String[] b) {
        int n = a.length;
        int m = b.length;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        List<String> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                result.add(" " + a[i]);
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                result.add("-" + a[i]);
                i++;
            } else {
                result.add("+" + b[j]);
                j++;
            }
        }
        while (i < n) {
            result.add("-" + a[i]);
            i++;
        }
        while (j < m) {
            result.add("+" + b[j]);
            j++;
        }
        return result;
    }

    private static String tryDecodeUtf8(byte[] content) {
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
