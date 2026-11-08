import java.nio.file.Path;

import to.sparkapp.app.extensions.ExtensionImporter;
import to.sparkapp.app.extensions.ExtensionManifest;

/** Imports explicitly supplied packages without embedding third-party extensions in Spark. */
public final class ImportPackages {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: ImportPackages <extensions-folder> <package> [package...]");
        }
        var root = Path.of(args[0]);
        for (var index = 1; index < args.length; index++) {
            var directory = new ExtensionImporter().importArchive(Path.of(args[index]), root);
            var manifest = ExtensionManifest.read(directory);
            System.out.println(manifest.name() + " " + manifest.version() + " -> " + directory);
        }
    }
}
