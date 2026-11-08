package to.sparkapp.app.extensions;

public record ExtensionEntry(ExtensionManifest manifest, String id, boolean enabled, String error) {
    public boolean installed() {
        return id != null && error == null;
    }
}
