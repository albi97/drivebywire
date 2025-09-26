package edn.stratodonut.drivebywire;

import edn.stratodonut.drivebywire.compat.LinkedControllerWireServerHandler;
import edn.stratodonut.drivebywire.compat.TweakedControllerWireServerHandler;
import edn.stratodonut.drivebywire.wire.ShipWireNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.Comparator;
import java.util.EnumSet;

@Mod.EventBusSubscriber(
        modid = DriveByWireMod.MOD_ID
)
public class ServerEvents {
    public ServerEvents() {}

    @SubscribeEvent
    public static void onServerWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.START)
            return;
        if (event.side == LogicalSide.CLIENT)
            return;
        Level world = event.level;
        LinkedControllerWireServerHandler.tick(world);
        TweakedControllerWireServerHandler.tick(world);
        
        if (debugScanDelay > 0) {
            debugScanDelay--;
            if (debugScanDelay == 0 && world instanceof ServerLevel serverLevel) {
                DriveByWireMod.warn("Starting wire network debug scan...");
                debugLogAllWireNetworks(serverLevel);
            }
        }
        
        if (triggerManualScan && world instanceof ServerLevel serverLevel) {
            triggerManualScan = false;
            DriveByWireMod.warn("Running requested debug scan...");
            debugLogAllWireNetworks(serverLevel);
        }
    }

    private static int debugScanDelay = 0;
    private static boolean triggerManualScan = false;
    
    @SubscribeEvent
    public static void onWorldLoad(net.minecraftforge.event.level.LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel) {
            debugScanDelay = 100;
            DriveByWireMod.warn("World loaded, will scan wire networks in 5 seconds...");
        }
    }
    
    public static void requestDebugScan() {
        triggerManualScan = true;
        DriveByWireMod.warn("Debug scan requested, will run on next server tick...");
    }
    
    public static void triggerDebugScan(ServerLevel level) {
        DriveByWireMod.warn("Manual debug scan triggered!");
        debugLogAllWireNetworks(level);
    }
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        dispatcher.register(Commands.literal("wirenetwork")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("debug")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (source.getLevel() instanceof ServerLevel) {
                        ServerLevel serverLevel = (ServerLevel) source.getLevel();
                        source.sendSuccess(() -> Component.literal("Starting wire network debug scan..."), false);
                        triggerDebugScan(serverLevel);
                        return 1;
                    } else {
                        source.sendFailure(Component.literal("This command can only be used in a server world!"));
                        return 0;
                    }
                })
            )
            .then(Commands.literal("scan")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (source.getLevel() instanceof ServerLevel) {
                        source.sendSuccess(() -> Component.literal("Requesting wire network scan on next tick..."), false);
                        requestDebugScan();
                        return 1;
                    } else {
                        source.sendFailure(Component.literal("This command can only be used in a server world!"));
                        return 0;
                    }
                })
            )
        );
    }

    private static final java.util.Set<String> DEBUG_BLOCKS = java.util.Set.of(
        "minecraft:redstone_lamp",
        "vsch:powerful_thruster_block", 
        "createpropulsion:redstone_magnet",
        "vs_clockwork:flap_bearing"
    );

    private static void debugLogAllWireNetworks(ServerLevel level) {
        DriveByWireMod.warn("=== WIRE NETWORK DEBUG: Scanning all ships for wire nodes ===");
        
        var allShips = VSGameUtilsKt.getAllShips(level);
        int shipCount = 0;
        int totalNodes = 0;
        int totalConsumers = 0;
        
        DriveByWireMod.warn("Total ships in level: " + allShips.size());
        
        for (var ship : allShips) {
            if (ship instanceof ServerShip serverShip) {
                var managerOpt = ShipWireNetworkManager.get(serverShip);
                if (managerOpt.isPresent()) {
                    var manager = managerOpt.get();
                    shipCount++;
                    
                    DriveByWireMod.warn("--- Ship ID: " + serverShip.getId() + " Network: " + manager.getName() + " ---");
                    
                    var nodes = manager.getNetwork();
                    DriveByWireMod.warn("Ship has " + nodes.size() + " source positions");
                    
                    for (var sourceEntry : nodes.entrySet()) {
                        BlockPos sourcePos = BlockPos.of(sourceEntry.getKey());
                        DriveByWireMod.warn("WIRE SOURCE: " + sourcePos + " (provides power)");
                        totalNodes++;
                        
                        for (var channelEntry : sourceEntry.getValue().entrySet()) {
                            String channel = channelEntry.getKey();
                            for (var sink : channelEntry.getValue()) {
                                BlockPos sinkPos = BlockPos.of(sink.getPosition());
                                Direction sinkDir = Direction.from3DDataValue(sink.getDirection());
                                DriveByWireMod.warn("  -> WIRE SINK: " + sinkPos + " direction " + sinkDir + " channel " + channel + " (receives power)");
                                totalNodes++;
                                
                                totalConsumers += scanAroundNodeForConsumers(level, sinkPos, serverShip);
                            }
                        }
                        
                        totalConsumers += scanAroundNodeForConsumers(level, sourcePos, serverShip);
                    }
                } else {
                    DriveByWireMod.warn("Ship ID: " + serverShip.getId() + " has no wire network manager");
                }
            }
        }
        
        DriveByWireMod.warn("=== WIRE NETWORK DEBUG: Found " + shipCount + " ships with " + totalNodes + " wire nodes and " + totalConsumers + " potential consumers ===");
    }
    
    private static int scanAroundNodeForConsumers(ServerLevel level, BlockPos nodePos, ServerShip ship) {
        int foundConsumers = 0;
        
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos checkPos = nodePos.offset(x, y, z);
                    
                    try {
                        Ship blockShip = VSGameUtilsKt.getShipManagingPos(level, checkPos);
                        
                        var blockState = level.getBlockState(checkPos);
                        var block = blockState.getBlock();
                        
                        var blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block);
                        String registryId = blockId != null ? blockId.toString() : "null";
                        
                        String descriptionId = block.getDescriptionId();
                        
                        if (blockId != null && !registryId.equals("minecraft:air")) {
                            if (blockShip != null && blockShip.getId() == ship.getId()) {
                                DriveByWireMod.warn("      Block on ship: " + registryId + " | desc: " + descriptionId + " at " + checkPos);
                                
                                boolean matchesRegistry = DEBUG_BLOCKS.contains(registryId);
                                boolean matchesDescription = DEBUG_BLOCKS.contains(descriptionId);
                                
                                if (matchesRegistry || matchesDescription) {
                                    DriveByWireMod.warn("    *** CONSUMER FOUND: " + registryId + " at " + checkPos + " near wire node " + nodePos + " ***");
                                    foundConsumers++;
                                }
                            } else if (blockShip == null) {
                                boolean matchesRegistry = DEBUG_BLOCKS.contains(registryId);
                                boolean matchesDescription = DEBUG_BLOCKS.contains(descriptionId);
                                
                                if (matchesRegistry || matchesDescription) {
                                    DriveByWireMod.warn("      World block: " + registryId + " | desc: " + descriptionId + " at " + checkPos);
                                    DriveByWireMod.warn("    *** WORLD CONSUMER FOUND: " + registryId + " at " + checkPos + " near wire node " + nodePos + " ***");
                                    foundConsumers++;
                                } else {
                                    DriveByWireMod.warn("Other block found on not ship: " + registryId + " | desc: " + descriptionId + " at " + checkPos);
                                }
                            }
                        }
                    } catch (Exception e) {
                        DriveByWireMod.warn("      Error checking position " + checkPos + ": " + e.getMessage());
                    }
                }
            }
        }
        
        return foundConsumers;
    }

    @SubscribeEvent
    public static void onBlockUpdate(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Ship s = VSGameUtilsKt.getShipManagingPos(level, event.getPos());
            if (s instanceof ServerShip ss) {
                BlockState state = level.getBlockState(event.getPos());
                if (state.isSignalSource()) {
                    int maxSignal = EnumSet.allOf(Direction.class).stream()
                            .map(d -> state.getSignal(level, event.getPos(), d))
                            .max(Comparator.naturalOrder())
                            .orElse(0);
                    ShipWireNetworkManager.get(ss).ifPresent(m -> m.setSource(level, event.getPos(),
                            ShipWireNetworkManager.WORLD_REDSTONE_CHANNEL, maxSignal)
                    );
                }

                for (Direction d : event.getNotifiedSides()) {
                    BlockPos nPos = event.getPos().relative(d);
                    BlockState nState = level.getBlockState(nPos);
                    if (!nState.isSignalSource()) {
                        ShipWireNetworkManager.get(ss).ifPresent(m -> m.setSource(level, nPos,
                                ShipWireNetworkManager.WORLD_REDSTONE_CHANNEL, level.getBestNeighborSignal(nPos))
                        );
                    }
                }
            }
        }
    }
}
