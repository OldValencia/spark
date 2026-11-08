package to.sparkapp.app.extensions;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Compares extension versions: up to four dot-separated integers, as defined by the manifest format. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExtensionVersion {
    static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    static int compare(String first, String second) {
        var left = parts(first);
        var right = parts(second);
        for (var index = 0; index < Math.max(left.length, right.length); index++) {
            var difference = Long.compare(part(left, index), part(right, index));
            if (difference != 0) {
                return difference;
            }
        }

        return 0;
    }

    private static String[] parts(String version) {
        return version == null || version.isBlank() ? new String[0] : version.trim().split("\\.");
    }

    private static long part(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }

        try {
            return Long.parseLong(parts[index]);
        } catch (NumberFormatException error) {
            return 0;
        }
    }
}
