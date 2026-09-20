package org.example.lifecomposer.importer;

import lombok.Getter;

import java.nio.file.Path;

/** Parsed command-line options for the standalone import CLI. */
public class ImportOptions {

    @Getter
    private Path importDir;
    @Getter
    private Path resourcesPath;
    @Getter
    private Path profilesPath;
    @Getter
    private Path ragChunksPath;
    @Getter
    private Path capabilityTagsPath;
    @Getter
    private Path creditRulesDir;
    @Getter
    private Path reportPath;
    @Getter
    private boolean rebuildEmbeddings;
    @Getter
    private boolean dryRun;
    @Getter
    private boolean help;
    @Getter
    private String demoPassword = "testuser";

    /**
     * Internal importer-selection flags parsed from the CLI. They deliberately
     * have no getters: only {@code shouldImport*()} derives the public decision,
     * so callers cannot depend on the raw flags (v0.0.7 audit remediation).
     */
    private boolean resourcesRequested;
    private boolean profilesRequested;
    private boolean ragRequested;
    private boolean capabilityRequested;
    private boolean creditRulesRequested;

    public static ImportOptions parse(String[] args) {
        ImportOptions options = new ImportOptions();
        if (args == null) {
            return options;
        }
        for (int i = 0; i < args.length; i++) {
            String raw = args[i];
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String flag = raw;
            String value = null;
            int eq = raw.indexOf('=');
            if (eq > 0) {
                flag = raw.substring(0, eq);
                value = raw.substring(eq + 1);
            }
            switch (flag) {
                case "--import-dir" -> options.importDir = Path.of(value != null ? value : next(args, ++i, flag));
                case "--resources" -> options.resourcesRequested = true;
                case "--profiles" -> options.profilesRequested = true;
                case "--rag-chunks" -> options.ragRequested = true;
                case "--capability-tags" -> options.capabilityRequested = true;
                case "--credit-rules-dir" -> {
                    String path = value != null ? value : next(args, ++i, flag);
                    options.creditRulesDir = Path.of(path);
                    options.creditRulesRequested = true;
                }
                case "--rebuild-embeddings" -> options.rebuildEmbeddings = true;
                case "--dry-run" -> options.dryRun = true;
                case "--report" -> options.reportPath = Path.of(value != null ? value : next(args, ++i, flag));
                case "--demo-password" -> options.demoPassword = value != null ? value : next(args, ++i, flag);
                case "--help", "-h" -> options.help = true;
                default -> {
                    if (flag.startsWith("--import-dir")) {
                        options.importDir = Path.of(flag.substring("--import-dir".length()));
                    }
                }
            }
        }
        options.fillDefaultPaths();
        return options;
    }

    private static String next(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("参数 " + flag + " 缺少取值");
        }
        return args[index];
    }

    private void fillDefaultPaths() {
        if (importDir == null) {
            return;
        }
        if (resourcesPath == null) {
            resourcesPath = importDir.resolve("data/resources.json");
        }
        if (profilesPath == null) {
            profilesPath = importDir.resolve("data/student_profiles.json");
        }
        if (ragChunksPath == null) {
            ragChunksPath = importDir.resolve("rag/chunks.json");
        }
        if (capabilityTagsPath == null) {
            capabilityTagsPath = importDir.resolve("draft/capability-tags.json");
        }
        if (creditRulesDir == null) {
            creditRulesDir = importDir.resolve("extracted");
        }
    }

    /**
     * Explicit type flags select only those importers. With no type flag we
     * import every type discoverable under --import-dir.
     */
    public boolean shouldImportResources() {
        return resourcesRequested || !anyTypeRequested();
    }

    public boolean shouldImportProfiles() {
        return profilesRequested || !anyTypeRequested();
    }

    public boolean shouldImportRag() {
        return ragRequested || !anyTypeRequested();
    }

    public boolean shouldImportCapability() {
        return capabilityRequested || !anyTypeRequested();
    }

    public boolean shouldImportCreditRules() {
        return creditRulesRequested || !anyTypeRequested();
    }

    private boolean anyTypeRequested() {
        return resourcesRequested || profilesRequested || ragRequested
                || capabilityRequested || creditRulesRequested;
    }

    public boolean hasImportTarget() {
        return importDir != null || resourcesPath != null || profilesPath != null
                || ragChunksPath != null || capabilityTagsPath != null || creditRulesDir != null;
    }

    /** Human-readable summary for the CLI banner; never contains the demo password. */
    public String describe() {
        return "import-dir=" + importDir
                + ", resources=" + shouldImportResources()
                + ", profiles=" + shouldImportProfiles()
                + ", ragChunks=" + shouldImportRag()
                + ", capabilityTags=" + shouldImportCapability()
                + ", creditRules=" + shouldImportCreditRules()
                + ", rebuildEmbeddings=" + rebuildEmbeddings
                + ", dryRun=" + dryRun;
    }
}
