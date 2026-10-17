package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.config.ServerConfigSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * The shared server configuration: {@code config/immersive_smithing-server.toml}, when present at start, is the one
 * configuration of every world; without it each world keeps its own file. Driven through
 * {@link ServerConfigSource#bindForTest} with temporary files standing in for the shared and the world files, and put
 * back afterwards, so the server's own files are never touched.
 */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SharedConfigGameTests {

    private static String capacity(int units) {
        return "[forge]\n    forgeCapacityUnits = " + units + "\n";
    }

    private static int capacity() {
        return ServerConfig.get(ServerConfig.FORGE_CAPACITY_UNITS);
    }

    private interface Body {
        void run(Path dir) throws IOException;
    }

    /** Runs the body with a scratch directory and puts the server's own configuration back afterwards. */
    private static void withFiles(GameTestHelper h, Body body) {
        ServerConfigSource.Snapshot before = ServerConfigSource.snapshot();
        Path dir = null;
        try {
            dir = Files.createTempDirectory("immersive_smithing_config");
            body.run(dir);
        } catch (IOException e) {
            throw new AssertionError("scratch files: " + e, e);
        } finally {
            ServerConfigSource.restore(before);
            if (dir != null) {
                try (Stream<Path> files = Files.walk(dir)) {
                    files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                } catch (IOException ignored) {
                    // scratch space only
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedFileWinsOverEveryWorldFile(GameTestHelper h) {
        withFiles(h, dir -> {
            Path shared = Files.writeString(dir.resolve(ServerConfigSource.FILE_NAME), capacity(99));
            Path worldA = Files.writeString(dir.resolve("world-a.toml"), capacity(200));
            Path worldB = Files.writeString(dir.resolve("world-b.toml"), capacity(300));
            ServerConfigSource.Status status = ServerConfigSource.bindForTest(shared, worldA);
            h.assertTrue(status.mode() == ServerConfigSource.Mode.SHARED && shared.equals(status.activePath()),
                    "The shared file is the source: " + status);
            h.assertTrue(capacity() == 99, "Its value applies, not the world file's: " + capacity());
            status = ServerConfigSource.bindForTest(shared, worldB);
            h.assertTrue(status.mode() == ServerConfigSource.Mode.SHARED && capacity() == 99,
                    "Another world's file does not apply either: " + capacity());
            String written = Files.readString(shared);
            h.assertTrue(written.contains("forgeCapacityUnits = 99") && written.contains("recipeBlacklist") && written.contains("#"),
                    "The shared file is completed with every missing key and its comment, the value kept");
            h.assertTrue(Files.readString(worldA).contains("forgeCapacityUnits = 200"), "The world files are left as they were");
        });
    }

    @GameTest(template = "empty")
    public static void withoutSharedFileEachWorldUsesItsOwn(GameTestHelper h) {
        withFiles(h, dir -> {
            Path absent = dir.resolve(ServerConfigSource.FILE_NAME);
            Path worldA = Files.writeString(dir.resolve("world-a.toml"), capacity(200));
            Path worldB = Files.writeString(dir.resolve("world-b.toml"), capacity(300));
            ServerConfigSource.Status status = ServerConfigSource.bindForTest(absent, worldA);
            h.assertTrue(status.mode() == ServerConfigSource.Mode.WORLD && worldA.equals(status.activePath()) && !status.sharedFileExists(),
                    "No shared file: world mode, " + status);
            h.assertTrue(capacity() == 200, "World A's value: " + capacity());
            ServerConfigSource.bindForTest(absent, worldB);
            h.assertTrue(capacity() == 300, "World B's value: " + capacity());
            h.assertTrue(!Files.exists(absent), "World mode never creates the shared file");
        });
    }

    @GameTest(template = "empty")
    public static void brokenSharedFileKeepsTheLastGoodConfig(GameTestHelper h) {
        withFiles(h, dir -> {
            Path shared = Files.writeString(dir.resolve(ServerConfigSource.FILE_NAME), capacity(99));
            Path world = Files.writeString(dir.resolve("world.toml"), capacity(200));
            ServerConfigSource.bindForTest(shared, world);
            Files.writeString(shared, "[forge]\n    forgeCapacityUnits = 120\n    this is not toml\n");
            ServerConfigSource.ReloadResult result = ServerConfigSource.reload();
            h.assertTrue(!result.ok() && result.error().contains(shared.toString()) && result.error().contains("line 3"),
                    "A broken edit names the file and the line: " + result.error());
            h.assertTrue(capacity() == 99, "The last good configuration stays in use: " + capacity());
            h.assertTrue(String.valueOf(ServerConfigSource.status().lastError()).contains(shared.toString()),
                    "/immersivesmithing config reports it");
            Files.writeString(shared, capacity(120));
            result = ServerConfigSource.reload();
            h.assertTrue(result.ok() && capacity() == 120, "The fixed file applies on the next reload: " + capacity());
            h.assertTrue(ServerConfigSource.status().lastError() == null, "And the error is cleared");

            // Broken at start: an integrated server falls back to the world file and says so (a dedicated one refuses to start).
            Files.writeString(shared, "[forge\nforgeCapacityUnits = 99\n");
            ServerConfigSource.Status status = ServerConfigSource.bindForTest(shared, world);
            h.assertTrue(status.mode() == ServerConfigSource.Mode.SHARED_INVALID && world.equals(status.activePath()),
                    "Loud fallback to the world file: " + status);
            h.assertTrue(capacity() == 200, "The world file's value: " + capacity());
            h.assertTrue(String.valueOf(status.lastError()).contains(shared.toString()) && String.valueOf(status.lastError()).contains("line 1"),
                    "The error names the shared file and the line: " + status.lastError());
        });
    }

    @GameTest(template = "empty")
    public static void effectiveConfigReachesRemoteClients(GameTestHelper h) {
        withFiles(h, dir -> {
            Path shared = Files.writeString(dir.resolve(ServerConfigSource.FILE_NAME),
                    capacity(99) + "[recipes]\n    modBlacklist = [\"examplemod\"]\n");
            Path world = Files.writeString(dir.resolve("world.toml"), capacity(200));
            ServerConfigSource.bindForTest(shared, world);
            byte[] toml = ServerConfigSource.effectiveToml();
            h.assertTrue(toml != null && toml.length > 0, "The effective TOML is written");
            // What a remote client does with the packet: parse it and bind it, the call Forge's own sync makes.
            ServerConfig.SPEC.setConfig(ServerConfigSource.decode(toml));
            h.assertTrue(capacity() == 99, "The client sees the shared value, not the world file's: " + capacity());
            h.assertTrue(ServerConfig.list(ServerConfig.MOD_BLACKLIST).equals(java.util.List.of("examplemod")),
                    "Lists survive the trip: " + ServerConfig.list(ServerConfig.MOD_BLACKLIST));
            h.assertTrue(ServerConfig.get(ServerConfig.LOOT_QUALITY_MODE) == ServerConfig.LootQualityMode.STANDARD,
                    "So do enums and every key the file did not name");
        });
    }

    private SharedConfigGameTests() {}
}
