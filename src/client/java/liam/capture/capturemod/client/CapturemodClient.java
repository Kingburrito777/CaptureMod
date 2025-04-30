package liam.capture.capturemod.client;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CapturemodClient implements ClientModInitializer {
    private static KeyBinding captureKey;
    private final SchematicCaptureManager captureManager = new SchematicCaptureManager();

    @Override
    public void onInitializeClient() {
        captureKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.capturemod.capture",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.capturemod"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (captureKey.wasPressed()) {
                captureManager.startCapture(client);
            }
        });
    }
}

class SchematicCaptureManager {
    private List<CameraPosition> cameraPositions = new ArrayList<>();
    private int currentPositionIndex = 0;
    private int tickCounter = 0;
    private boolean isCapturing = false;
    private static final int DELAY_TICKS = 20; // 1 second delay (10 ticks per set) = 20
    private SchematicPlacement placement;
    private String schematicName;
    private MinecraftClient client;
    private MinecraftServer server;
    private World world;
    private boolean isPositionSet = false;
    private List<BlockPos> allBlockPositions = new ArrayList<>();

    public void startCapture(MinecraftClient client) {
        this.client = client;
        this.server = client.getServer(); // This is null in multiplayer client-only contexts
        this.world = server.getWorld(client.world.getRegistryKey());

        // Step 1: Load schematic
        File directory = new File(client.runDirectory, "schematics/");
        LitematicaSchematic schematic = LitematicaSchematic.createFromFile(directory, "24150.litematic");
        if (schematic == null) {
            System.err.println("Failed to load schematic.");
            return;
        }

        // Step 2: Create and position schematic placement at (0, 0, 0)
        placement = SchematicPlacement.createFor(schematic, new BlockPos(0, 0, 0), schematic.getMetadata().getName(), false, false);

        // Step 3: Place schematic in the world
        schematic.placeToWorld(world, placement, false, true);

        setupEnvironment();

        // Step 4: Calculate camera positions
        Vec3i dimensions = schematic.getTotalSize();
        cameraPositions = calculateCameraPositionsRad(dimensions);
        schematicName = schematic.getMetadata().getName();

        // Step 5: Start the capture process
        currentPositionIndex = 0;
        tickCounter = 0;
        isCapturing = true;

        // Register the tick handler (only needs to be done once, e.g., in mod init)
        registerTickHandler();
    }

    private void setupEnvironment() {
        if (client == null || client.player == null || world == null) return;

        server.getCommandManager().executeWithPrefix(server.getCommandSource(), "/gamerule doDaylightCycle false");
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), "/time set noon");
        // Clear weather for consistent skybox
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), "/weather clear");
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), "/gamemode spectator @e[type=minecraft:player]");

        // Hide HUD (F1 mode)
        client.options.hudHidden = true;
        cleanup();
        // Set player to spectator mode to avoid interference
    }

    private void registerTickHandler() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isCapturing || client == null) return;

            // Increment tick counter
            tickCounter++;

            // Wait until the delay is reached
            if (tickCounter >= DELAY_TICKS) {
                if (currentPositionIndex < cameraPositions.size()) {
                    CameraPosition pos = cameraPositions.get(currentPositionIndex);

                    if (!isPositionSet) {
                        // First set the position and orientation
                        setCameraPosition(client, pos);
                        isPositionSet = true;
                        tickCounter = 0; // Wait another delay for position to apply
                    } else {
                        // Then capture after position has been set
                        captureFromPosition(client, pos, placement, schematicName, allBlockPositions);
                        currentPositionIndex++;
                        isPositionSet = false;
                        tickCounter = 0;
                    }
                } else {
                    isCapturing = false;
                    clearHitResults(allBlockPositions);
                }
            }
        });
    }

    private void setCameraPosition(MinecraftClient client, CameraPosition pos) {
        client.player.setPosition(pos.position.x, pos.position.y, pos.position.z);
        Vec3d direction = pos.target.subtract(pos.position);
        float pitch = (float) Math.toDegrees(Math.atan2(-direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)));
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        yaw = (yaw + 360) % 360;

        client.player.setPitch(pitch);
        client.player.setYaw(yaw);
    }

    private void clearHitResults(List<BlockPos> allBlockPositions) {
        for (BlockPos pos : allBlockPositions){
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        }
    }

    private void cleanup() {
        if (placement != null) {
            // Clear all blocks placed by the schematic
            clearSchematicBlocks(world);
            clearSchematicEntities(world);
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "/time set noon");
            // Clear weather for consistent skybox
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "/weather clear");
            // Remove the schematic placement from Litematica’s manager
            DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement);
        }
        cameraPositions.clear();
    }
    private void clearSchematicBlocks(World world) {
        if (client.world == null || client.player == null || placement == null) return;

        // Get the schematic’s origin and size
        BlockPos origin = placement.getOrigin(); // Assuming this returns the placement origin
        Vec3i size = placement.getSchematic().getTotalSize();
        // Iterate over the schematic’s bounding box and set blocks to air
        for (int x = 0; x < size.getX() + 10; x++) {
            for (int y = 0; y < size.getY() + 10; y++) {
                for (int z = 0; z < size.getZ() + 10; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);                }
            }
        }
    }

    private void clearSchematicEntities(World world) {
        if (client.world == null || client.player == null || placement == null) return;

        // Get the schematic’s boundaries
        BlockPos origin = placement.getOrigin();
        Vec3i size = placement.getSchematic().getTotalSize();
        BlockPos maxPos = origin.add(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

        // Create a box for the schematic area
        Box boundingBox = new Box(new Vec3d(-100, -100, -100), new Vec3d(100, 255, 100));

        // Get all entities within the bounding box
        List<Entity> entities = world.getEntitiesByClass(
                Entity.class,
                boundingBox,
                entity -> true // Get all entities, you could filter specific types if needed
        );
        if (entities == null) return;
        // Remove all found entities
        for (Entity entity : entities) {
            // Don't remove players
            if (!(entity instanceof PlayerEntity)) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
    }

    private List<CameraPosition> calculateCameraPositionsOrtho(Vec3i dimensions) {
        int width = dimensions.getX();
        int length = dimensions.getZ();
        int height = dimensions.getY();
        Vec3d origin = new Vec3d(width / 2.0, height / 2.0, length / 2.0);
        double distance = Math.max(Math.max(width, length), height);

        List<CameraPosition> positions = new ArrayList<>();
        positions.add(new CameraPosition(new Vec3d(origin.x, origin.y, origin.z - distance), origin, "front"));
        positions.add(new CameraPosition(new Vec3d(origin.x, origin.y, origin.z + distance), origin, "back"));
        positions.add(new CameraPosition(new Vec3d(origin.x - distance, origin.y, origin.z), origin, "left"));
        positions.add(new CameraPosition(new Vec3d(origin.x + distance, origin.y, origin.z), origin, "right"));
        positions.add(new CameraPosition(new Vec3d(origin.x, origin.y + distance, origin.z), origin, "top"));
        positions.add(new CameraPosition(new Vec3d(origin.x, origin.y - distance, origin.z), origin, "bottom"));

        return positions;
    }

    private List<CameraPosition> calculateCameraPositions(Vec3i dimensions) {
        int width = dimensions.getX();   // X-axis
        int length = dimensions.getZ();  // Z-axis
        int height = dimensions.getY();  // Y-axis

        // Center point of the structure
        Vec3d origin = new Vec3d(width / 2.0, height / 2.0, length / 2.0);

        List<CameraPosition> positions = new ArrayList<>();

        // Distance multiplier for padding (adjustable: 1.5x to 2x works well)
        double distanceMultiplier = 1.5;

        // Front view (along Z-axis, facing negative Z, Y-X plane)
        double frontBackDistance = Math.max(height, width) * distanceMultiplier;
        Vec3d frontPos = new Vec3d(origin.x, origin.y, origin.z + frontBackDistance);
        positions.add(new CameraPosition(frontPos, origin, "front"));

        // Back view (along Z-axis, facing positive Z, Y-X plane)
        Vec3d backPos = new Vec3d(origin.x, origin.y, origin.z - frontBackDistance);
        positions.add(new CameraPosition(backPos, origin, "back"));

        // Left view (along X-axis, facing negative X, Y-Z plane)
        double sideDistance = Math.max(height, length) * distanceMultiplier;
        Vec3d leftPos = new Vec3d(origin.x - sideDistance, origin.y, origin.z);
        positions.add(new CameraPosition(leftPos, origin, "left"));

        // Right view (along X-axis, facing positive X, Y-Z plane)
        Vec3d rightPos = new Vec3d(origin.x + sideDistance, origin.y, origin.z);
        positions.add(new CameraPosition(rightPos, origin, "right"));

        // Top view (along Y-axis, facing downward, X-Z plane)
        double topBottomDistance = Math.max(width, length) * distanceMultiplier;
        Vec3d topPos = new Vec3d(origin.x, origin.y + topBottomDistance, origin.z);
        positions.add(new CameraPosition(topPos, origin, "top"));

        // Bottom view (along Y-axis, facing upward, X-Z plane)
        Vec3d bottomPos = new Vec3d(origin.x, origin.y - topBottomDistance, origin.z);
        positions.add(new CameraPosition(bottomPos, origin, "bottom"));

        return positions;
    }

    private List<CameraPosition> calculateCameraPositionsRad(Vec3i dimensions) {
        int width = dimensions.getX();
        int length = dimensions.getZ();
        int height = dimensions.getY();

        // Center point of the structure
        Vec3d origin = new Vec3d(width / 2.0, height / 2.0, length / 2.0);

        // Calculate radius based on structure size, with some padding
        double baseDistance = Math.max(Math.max(width, length), height) * 1.15;

        // Height offset to be slightly above the center
        double heightOffset = height * 0.5; // Adjust this multiplier (0.5-1.0) for desired height

        List<CameraPosition> positions = new ArrayList<>();

        // Number of positions and angle increment
        int numPositions = 8;
        double angleStep = 2 * Math.PI / numPositions; // 360° / 6 = 60° increments

        // Generate circular positions
        for (int i = 0; i < numPositions; i++) {
            double angle = i * angleStep;

            // Calculate X and Z coordinates using polar coordinates
            double x = origin.x + baseDistance * Math.cos(angle);
            double z = origin.z + baseDistance * Math.sin(angle);
            double y = origin.y + heightOffset; // Elevated position

            Vec3d cameraPos = new Vec3d(x, y, z);
            String name = "view_" + i;

            positions.add(new CameraPosition(cameraPos, origin, name));
        }
        positions.add(new CameraPosition(new Vec3d(origin.x, origin.y + baseDistance, origin.z), origin, "top"));
//        positions.add(new CameraPosition(new Vec3d(origin.x, origin.y - baseDistance, origin.z), origin, "bottom"));


        return positions;
    }

    private void captureFromPosition(MinecraftClient client, CameraPosition pos, SchematicPlacement placement, String schematicName, List<BlockPos> allBlockPositions) {
        // Set camera position and orientation
        client.player.setPosition(pos.position.x, pos.position.y, pos.position.z);
        Vec3d direction = pos.target.subtract(pos.position);
        // Calculate pitch (vertical angle)
        float pitch = (float) Math.toDegrees(Math.atan2(-direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)));

        // Calculate yaw (horizontal angle) adjusted for Minecraft's system
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        yaw = (yaw + 360) % 360; // Normalize to 0-360 range

        client.player.setPitch(pitch);
        client.player.setYaw(yaw);

        // wait until placement is loaded fully

        // Capture data
        String jsonData = CaptureLogic.captureAllPixels(client, schematicName, pos.label, allBlockPositions);

        // Save data with schematic metadata
        saveCaptureData(client, schematicName, pos, jsonData);
    }

    private void saveCaptureData(MinecraftClient client, String schematicName, CameraPosition pos, String jsonData) {
        File captureDir = new File(client.runDirectory, "captures/" + schematicName);
        if (!captureDir.exists()) {
            captureDir.mkdirs();
        }
        File jsonFile = new File(captureDir, "capture_" + pos.label + ".json");
        try (FileWriter writer = new FileWriter(jsonFile)) {
            writer.write(jsonData);
            System.out.println("Capture saved: " + jsonFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class CameraPosition {
    final Vec3d position;
    final Vec3d target;
    final String label;

    CameraPosition(Vec3d position, Vec3d target, String label) {
        this.position = position;
        this.target = target;
        this.label = label;
    }
}

class CaptureLogic {
    public static String captureAllPixels(MinecraftClient client, String schematicName, String positionLabel, List<BlockPos> allBlockPositions) {
        try (NativeImage screenshotFile = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            File screenshotDir = new File(client.runDirectory, "screenshots");
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs();
            }
            String screenshotName = String.format("%s_%s.png", schematicName, positionLabel);
            File outputFile = new File(screenshotDir, screenshotName);
            screenshotFile.writeTo(outputFile);
            System.out.println("Screenshot saved: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save screenshot: " + e.getMessage());
        }

        int width = client.getWindow().getWidth();
        int height = client.getWindow().getHeight();
        float tickDelta = 1.0F;

        Vec3d cameraDirection = client.cameraEntity.getRotationVec(tickDelta);
        double fov = client.options.getFov().getValue();
        double fovRad = Math.toRadians(fov);
        double tanFov = Math.tan(fovRad / 2);
        double aspect = (double) width / height;

        Vector3f forward = new Vector3f((float) cameraDirection.x, (float) cameraDirection.y, (float) cameraDirection.z);
        Vector3f tempY = new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(forward).cross(tempY);
//        if (right.lengthSquared() == 0) {
//            System.out.println("Camera pointing straight up/down, skipping.");
//            return;
//        }
        right.normalize();
        Vector3f up = new Vector3f(forward).cross(right).normalize();

        int step = 8;
        List<String> pixelData = new ArrayList<>();
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                Vec3d direction = map(tanFov, aspect, forward, right, up, x, y, width, height);
                HitResult hit = raycastInDirection(client, tickDelta, direction);

                if (hit != null && hit.getType() != HitResult.Type.MISS) {
                    Vec3d hitPos = hit.getPos();
                    double coordX = hitPos.x;
                    double coordY = hitPos.y;
                    double coordZ = hitPos.z;
                    String blockStateId;

                    switch (hit.getType()) {
                        case BLOCK:
                            BlockHitResult blockHit = (BlockHitResult) hit;
                            BlockPos blockPos = blockHit.getBlockPos();
                            allBlockPositions.add(blockPos);
                            String rawBlockState = client.world.getBlockState(blockPos).toString();
                            blockStateId = rawBlockState.replaceAll("^Block\\{([^}]*)\\}(.*)$", "$1$2");
                            break;
                        case ENTITY:
                            EntityHitResult entityHit = (EntityHitResult) hit;
                            blockStateId = "entity:" + entityHit.getEntity().getType().toString();
                            break;
                        default:
                            continue;
                    }

                    pixelData.add(String.format(
                            "{\"x\": %d, \"y\": %d, \"coords\": {\"x\": %f, \"y\": %f, \"z\": %f}, \"blockstate\": \"%s\"}",
                            x, y, coordX, coordY, coordZ, blockStateId
                    ));
                }
            }
        }

        return String.format(
                "{\"width\": %d, \"height\": %d, \"pixels\": [%s]}",
                width, height, String.join(",", pixelData)
        );
    }

    private static Vec3d map(double tanFov, double aspect, Vector3f forward, Vector3f right, Vector3f up,
                             int x, int y, int width, int height) {
        // Calculate normalized device coordinates (NDC)
        float ndcX = 2f * (x + 0.5f) / width - 1f;
        float ndcY = 2f * (y + 0.5f) / height - 1f; // Fixed Y-axis mirroring

        // Direction in camera space
        float dirX = ndcX * (float) tanFov * (float) aspect;
        float dirY = ndcY * (float) tanFov;
        float dirZ = -1f;

        // Normalize the camera-space direction
        float length = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        dirX /= length;
        dirY /= length;
        dirZ /= length;

        // Transform to world space
        Vec3d worldDir = new Vec3d(
                right.x() * dirX + up.x() * dirY - forward.x() * dirZ,
                right.y() * dirX + up.y() * dirY - forward.y() * dirZ,
                right.z() * dirX + up.z() * dirY - forward.z() * dirZ
        );

        return worldDir;
    }

    private static HitResult raycastInDirection(MinecraftClient client, float tickDelta, Vec3d direction) {
        Entity entity = client.getCameraEntity();
        if (entity == null || client.world == null) return null;

        double reachDistance = 100.0;
        HitResult target = raycast(entity, reachDistance, tickDelta, true, direction);
        Vec3d cameraPos = entity.getCameraPosVec(tickDelta);

        Vec3d vec3d3 = cameraPos.add(direction.multiply(reachDistance));
        Box box = entity.getBoundingBox()
                .stretch(entity.getRotationVec(1.0F).multiply(reachDistance))
                .expand(1.0D, 1.0D, 1.0D);
        EntityHitResult entityHitResult = ProjectileUtil.raycast(
                entity,
                cameraPos,
                vec3d3,
                box,
                (e) -> !e.isSpectator() && e.isCollidable(),
                reachDistance * reachDistance
        );

        if (entityHitResult != null) {
            Vec3d hitPos = entityHitResult.getPos();
            double distance = cameraPos.squaredDistanceTo(hitPos);
            if (distance < (target != null ? cameraPos.squaredDistanceTo(target.getPos()) : reachDistance * reachDistance)) {
                target = entityHitResult;
            }
        }

        return target;
    }

    private static HitResult raycast(Entity entity, double maxDistance, float tickDelta, boolean includeFluids, Vec3d direction) {
        Vec3d end = entity.getCameraPosVec(tickDelta).add(direction.multiply(maxDistance));
        return entity.getWorld().raycast(new RaycastContext(
                entity.getCameraPosVec(tickDelta),
                end,
                RaycastContext.ShapeType.OUTLINE,
                includeFluids ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE,
                entity
        ));
    }
}