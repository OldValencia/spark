package to.sparkapp.app.ui.extensions;

import to.sparkapp.app.extensions.ExtensionEntry;

interface ExtensionRowActions {
    void setEnabled(ExtensionEntry entry, boolean enabled);

    void remove(ExtensionEntry entry);

    void update(ExtensionEntry entry);
}
