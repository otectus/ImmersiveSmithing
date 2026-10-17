package com.otectus.immersivesmithing.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Where the server configuration comes from. {@code config/immersive_smithing-server.toml} (Forge's config directory),
 * when it exists at server start, is the one configuration of every world of the installation: shared mode. Without it
 * each world uses its own {@code serverconfig/} file exactly as Forge loads it, including the {@code defaultconfigs/}
 * template for new worlds: world mode. The file is the switch, looked at once per server start.
 *
 * <p>Registration stays {@link ModConfig.Type#SERVER}, so world mode keeps Forge's loading, correction and file watcher.
 * Shared mode binds the shared file over it at {@code ServerAboutToStartEvent}, which fires after Forge bound the world
 * file. Forge's {@code ModConfig} still points at the world file and Forge's handshake sends that file's bytes to remote
 * clients, so the mod sends the effective TOML itself ({@code ConfigSyncPacket}) on login and after every rebuild. The
 * handshake's world values reach a joining client milliseconds before the effective ones replace them, before the player
 * can act. The shared file has no watcher: edits apply through {@code /immersivesmithing reload}, which validates a fresh
 * parse before anything is swapped and keeps the last good configuration when it fails. Mod config screens edit Forge's
 * {@code ModConfig}, the world file, so in shared mode their changes do nothing; the startup log and
 * {@code /immersivesmithing config} name the file in use.</p>
 */
public final class ServerConfigSource {
    public static final String FILE_NAME = ImmersiveSmithing.MOD_ID + "-server.toml";
    /**
     * A shared file that cannot be parsed at start: an integrated server (singleplayer, LAN host) falls back to the world
     * file loudly; a dedicated server always refuses to start. {@code false} makes the integrated server refuse as well.
     */
    static final boolean INTEGRATED_FALLBACK = true;

    public enum Mode {
        /** The world's own {@code serverconfig/} file, bound by Forge. */
        WORLD,
        /** The installation's {@code config/} file, bound over the world file. */
        SHARED,
        /** A shared file exists but could not be read at start: the world file is in use until it is fixed and the world restarts. */
        SHARED_INVALID
    }

    /** What {@code /immersivesmithing config} reports. */
    public record Status(Mode mode, @Nullable Path activePath, Path sharedPath, boolean sharedFileExists, @Nullable String lastError) {}

    /** The outcome of re-reading the active source; {@code error} is null on success. */
    public record ReloadResult(@Nullable Path path, @Nullable String error) {
        public boolean ok() {
            return error == null;
        }
    }

    private static volatile Mode mode = Mode.WORLD;
    /** The config this class bound into the spec (the shared file, or a test's); null while Forge's world file is bound. */
    @Nullable
    private static volatile CommentedConfig bound;
    @Nullable
    private static volatile Path sharedPath;
    /** GameTests: the file standing in for the world file Forge would have bound. */
    @Nullable
    private static volatile CommentedFileConfig testWorld;
    @Nullable
    private static volatile String startError;
    @Nullable
    private static volatile String reloadError;

    /** Server start, after Forge bound the world file and before anything reads the configuration. */
    public static synchronized void onServerAboutToStart(MinecraftServer server) {
        closeShared();
        testWorld = null;
        select(FMLPaths.CONFIGDIR.get().resolve(FILE_NAME), server.isDedicatedServer());
    }

    /** Chooses the source for this run: the shared file when it exists, otherwise the world file. */
    private static Status select(Path sharedFile, boolean dedicated) {
        mode = Mode.WORLD;
        sharedPath = sharedFile;
        startError = null;
        reloadError = null;
        if (!Files.exists(sharedFile)) {
            ImmersiveSmithing.LOGGER.info("Immersive Smithing config: source=WORLD path={}", worldPath());
            return status();
        }
        try {
            bind(open(sharedFile));
            mode = Mode.SHARED;
            ImmersiveSmithing.LOGGER.info("Immersive Smithing config: source=SHARED path={} (world file {} inactive)", sharedFile, worldPath());
        } catch (InvalidConfigException e) {
            startError = e.getMessage();
            if (dedicated || !INTEGRATED_FALLBACK) {
                ImmersiveSmithing.LOGGER.error("Immersive Smithing config: the shared config {}. The server will not start.", e.getMessage());
                // ServerAboutToStartEvent cannot be cancelled: an exception is how a server refuses to start.
                throw new IllegalStateException("Immersive Smithing: the shared server config " + e.getMessage()
                        + ". Fix or remove the file to start the server.", e);
            }
            mode = Mode.SHARED_INVALID;
            ImmersiveSmithing.LOGGER.error("Immersive Smithing config: source=WORLD path={} because the shared config {}. "
                    + "Fix the shared file and restart the world to use it.", worldPath(), e.getMessage());
        }
        return status();
    }

    /**
     * Re-reads the active source before a datapack reload. A fresh parse is validated before anything is swapped: on an
     * error the last good configuration stays bound and the caller aborts the reload. The shared file is opened anew and
     * bound with one reference swap; the world file, which Forge and its watcher hold, is re-read in place and bound
     * again. {@code setConfig} corrects the result and clears the value caches. Switching between shared and world mode
     * needs a restart and never happens here.
     */
    public static synchronized ReloadResult reload() {
        CommentedConfig active = activeConfig();
        if (!(active instanceof CommentedFileConfig file)) return new ReloadResult(null, null); // a test's in-memory copy
        Path path = mode == Mode.SHARED ? sharedPath() : file.getNioPath();
        try {
            if (mode == Mode.SHARED) {
                bind(open(path));
                file.close();
            } else {
                validate(path);
                file.load();
                ServerConfig.SPEC.setConfig(file);
            }
            reloadError = null;
            return new ReloadResult(path, null);
        } catch (InvalidConfigException e) {
            reloadError = e.getMessage();
            ImmersiveSmithing.LOGGER.error("Immersive Smithing: reload kept the last good configuration because the config {}", e.getMessage());
            return new ReloadResult(path, e.getMessage());
        } catch (RuntimeException e) {
            reloadError = describe(path, -1, e);
            ImmersiveSmithing.LOGGER.error("Immersive Smithing: reload of {} failed", path, e);
            return new ReloadResult(path, reloadError);
        }
    }

    public static Mode mode() {
        return mode;
    }

    /** The shared file's location: Forge's config directory (a test's file while one is bound). */
    public static Path sharedPath() {
        Path path = sharedPath;
        return path != null ? path : FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public static Status status() {
        Path shared = sharedPath();
        String error = reloadError != null ? reloadError : startError;
        return new Status(mode, mode == Mode.SHARED ? shared : worldPath(), shared, Files.exists(shared), error);
    }

    /** The configuration in effect: the shared file, a test's, or the world file Forge bound. Null before a server starts. */
    @Nullable
    public static CommentedConfig activeConfig() {
        CommentedConfig ours = bound;
        if (ours != null) return ours;
        CommentedFileConfig test = testWorld;
        if (test != null) return test;
        ModConfig world = worldConfig();
        return world != null ? world.getConfigData() : null;
    }

    /** The effective configuration as TOML text, for remote clients; null when nothing is loaded. */
    @Nullable
    public static byte[] effectiveToml() {
        CommentedConfig config = activeConfig();
        if (config == null) return null;
        try {
            return new TomlWriter().writeToString(config).getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            ImmersiveSmithing.LOGGER.error("Immersive Smithing: could not serialise the server config for clients", e);
            return null;
        }
    }

    /** Parses what {@link #effectiveToml} wrote, as the receiving client does. */
    public static CommentedConfig decode(byte[] toml) {
        return TomlFormat.instance().createParser().parse(new ByteArrayInputStream(toml));
    }

    /** Server stop. Forge unloads the world file next, which unbinds the spec as well. */
    public static synchronized void reset() {
        closeShared();
        bound = null;
        testWorld = null;
        mode = Mode.WORLD;
        sharedPath = null;
        startError = null;
        reloadError = null;
    }

    // ------------------------------------------------------------------ GameTests

    /** Everything a test may change, to put back with {@link #restore}. */
    public record Snapshot(@Nullable CommentedConfig active, @Nullable CommentedConfig bound, Mode mode, @Nullable Path sharedPath,
                           @Nullable CommentedFileConfig testWorld, @Nullable String startError, @Nullable String reloadError) {}

    public static synchronized Snapshot snapshot() {
        return new Snapshot(activeConfig(), bound, mode, sharedPath, testWorld, startError, reloadError);
    }

    /** Puts the source back as it was at {@link #snapshot}; files a test opened since are closed, the server's own never. */
    public static synchronized void restore(Snapshot snapshot) {
        closeTestFile(bound, snapshot);
        closeTestFile(testWorld, snapshot);
        bound = snapshot.bound();
        testWorld = snapshot.testWorld();
        mode = snapshot.mode();
        sharedPath = snapshot.sharedPath();
        startError = snapshot.startError();
        reloadError = snapshot.reloadError();
        ServerConfig.SPEC.setConfig(snapshot.active());
    }

    /** Binds a configuration that is no file (a test's in-memory copy) without changing the source. */
    public static synchronized void bindDetached(CommentedConfig config) {
        ServerConfig.SPEC.setConfig(config);
        bound = config;
    }

    /**
     * Selects the source from these files as an integrated server's start does (a broken shared file falls back), with
     * {@code worldFile} standing in for the world file Forge would have bound. Undo with {@link #restore}.
     */
    public static synchronized Status bindForTest(Path sharedFile, Path worldFile) {
        CommentedFileConfig world = fileConfig(worldFile);
        world.load();
        ServerConfig.SPEC.setConfig(world);
        bound = null;
        testWorld = world;
        return select(sharedFile, false);
    }

    private static void closeTestFile(@Nullable CommentedConfig config, Snapshot snapshot) {
        if (config instanceof CommentedFileConfig file && config != snapshot.bound() && config != snapshot.testWorld()
                && config != snapshot.active()) {
            file.close();
        }
    }

    // ------------------------------------------------------------------ files

    private static void bind(CommentedFileConfig config) {
        ServerConfig.SPEC.setConfig(config);
        bound = config;
    }

    private static void closeShared() {
        if (mode == Mode.SHARED && bound instanceof CommentedFileConfig file) file.close();
        if (mode == Mode.SHARED) bound = null;
    }

    /** Validates, then opens and loads a config file as Forge does (written synchronously, autosaved, insertion order, rewritten whole), without a watcher. */
    private static CommentedFileConfig open(Path path) throws InvalidConfigException {
        validate(path);
        CommentedFileConfig config = fileConfig(path);
        try {
            config.load();
        } catch (RuntimeException e) {
            config.close();
            throw new InvalidConfigException(describe(path, -1, e), e);
        }
        return config;
    }

    private static CommentedFileConfig fileConfig(Path path) {
        return CommentedFileConfig.builder(path).sync().preserveInsertionOrder().autosave().writingMode(WritingMode.REPLACE).build();
    }

    /** Parses the file on its own first, so a broken file never replaces a good configuration. */
    private static void validate(Path path) throws InvalidConfigException {
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InvalidConfigException(describe(path, -1, e), e);
        }
        LineCountingReader reader = new LineCountingReader(new StringReader(text));
        try {
            new TomlParser().parse(reader);
        } catch (ParsingException e) {
            throw new InvalidConfigException(describe(path, reader.line(), e), e);
        }
    }

    private static String describe(Path path, int line, Exception e) {
        String what = e instanceof NoSuchFileException ? "the file is missing"
                : String.valueOf(e.getMessage()).replace("\r", "\\r").replace("\n", "\\n");
        return path + (line > 0 ? " (line " + line + ")" : "") + " cannot be read: " + what;
    }

    @Nullable
    private static ModConfig worldConfig() {
        var configs = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER);
        if (configs == null) return null;
        for (ModConfig config : configs) {
            if (config.getModId().equals(ImmersiveSmithing.MOD_ID)) return config;
        }
        return null;
    }

    @Nullable
    private static Path worldPath() {
        CommentedFileConfig test = testWorld;
        if (test != null) return test.getNioPath();
        ModConfig world = worldConfig();
        if (world == null || world.getConfigData() == null) return null;
        try {
            return world.getFullPath();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class InvalidConfigException extends Exception {
        InvalidConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Counts the lines the parser has read, so a parse error can name its line. */
    private static final class LineCountingReader extends FilterReader {
        private int newlines;
        private int last = -1;

        LineCountingReader(Reader in) {
            super(in);
        }

        /** The line of the last character read; a parser that stops on a newline is still on the line it ends. */
        int line() {
            return newlines + 1 - (last == '\n' ? 1 : 0);
        }

        @Override
        public int read() throws IOException {
            int c = super.read();
            if (c == '\n') newlines++;
            if (c >= 0) last = c;
            return c;
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            // One character at a time, so the count stops where the parser stopped.
            if (length == 0) return 0;
            int c = read();
            if (c < 0) return -1;
            buffer[offset] = (char) c;
            return 1;
        }
    }

    private ServerConfigSource() {}
}
