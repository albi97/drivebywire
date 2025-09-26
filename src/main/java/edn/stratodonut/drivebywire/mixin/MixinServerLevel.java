package edn.stratodonut.drivebywire.mixin;

import edn.stratodonut.drivebywire.wire.ShipWireNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;

import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.function.Supplier;

@Mixin(value =ServerLevel.class, priority = Integer.MAX_VALUE)
public abstract class MixinServerLevel extends Level {
    protected MixinServerLevel(WritableLevelData p_270739_, ResourceKey<Level> p_270683_, RegistryAccess p_270200_, Holder<DimensionType> p_270240_, Supplier<ProfilerFiller> p_270692_, boolean p_270904_, boolean p_270470_, long p_270248_, int p_270466_) {
        super(p_270739_, p_270683_, p_270200_, p_270240_, p_270692_, p_270904_, p_270470_, p_270248_, p_270466_);
    }

    @Override
    public int getSignal(BlockPos pos, Direction direction) {
        int original = super.getSignal(pos, direction);
        int shipWireSignal = this.getShipWireSignal(pos, direction);

        return Math.max(original, shipWireSignal);
    }
    
    @Override
    public int getDirectSignal(BlockPos pos, Direction direction) {
        int original = super.getDirectSignal(pos, direction);
        int shipWireSignal = this.getShipWireSignal(pos, direction, true);

        return Math.max(original, shipWireSignal);
    }

    @Override
    public int getBestNeighborSignal(BlockPos pos) {
        int original = super.getBestNeighborSignal(pos);
        
        int shipWireSignal = this.getShipWireSignalAroundAndSelf(pos);

        return Math.max(original, shipWireSignal);
    }

    @Override
    public int getControlInputSignal(BlockPos pos, Direction direction, boolean diodeOnly) {
        int original = super.getControlInputSignal(pos, direction, diodeOnly);
        int shipWireSignal = this.getShipWireSignal(pos, direction);

        return Math.max(original, shipWireSignal);
    }

    @Override
    public int getDirectSignalTo(BlockPos pos) {
        int original = super.getDirectSignalTo(pos);
        
        int shipWireSignal = this.getShipWireSignalAroundAndSelf(pos);

        return Math.max(original, shipWireSignal);
    }

    @Override
    public boolean hasNeighborSignal(BlockPos pos) {
        boolean original = super.hasNeighborSignal(pos);
        if (original) {
            return true;
        }
        
        int shipWireSignal = this.getShipWireSignalAroundAndSelf(pos);

        return shipWireSignal > 0;
    }

    @Override
    public boolean hasSignal(BlockPos pos, Direction direction) {
        boolean original = super.hasSignal(pos, direction);
        if (original) {
            return true;
        }
        
        int shipWireSignal = this.getShipWireSignal(pos, direction);

        return shipWireSignal > 0;
    }

    private int getShipWireSignal(BlockPos pos, Direction direction) {
        return this.getShipWireSignal(pos, direction, false);
    }

    /* private int getShipWireSignal(BlockPos pos, Direction direction, boolean checkOwn) {
        // pos is the SOURCE block position, direction is where it's providing power TO
        Ship s = VSGameUtilsKt.getShipManagingPos((ServerLevel)(Object) this, pos);
        if (s instanceof ServerShip serverShip) {
            var managerOpt = ShipWireNetworkManager.get(serverShip);
            if (managerOpt.isPresent()) {
                var manager = managerOpt.get();
                
                if (checkOwn) {
                    int ownSignal = manager.getSignalAt(pos, direction);
                    if (ownSignal > 0) {
                        return ownSignal;
                    }
                }

                Direction nodeDir = direction.getOpposite();
                BlockPos nodePos = pos.relative(direction);

                int adjacentNodeSignal = manager.getSignalAt(nodePos, nodeDir);
                if (adjacentNodeSignal > 0) {
                    return adjacentNodeSignal;
                }
            }
        }
        
        return 0;
    } */

    private int getShipWireSignal(BlockPos pos, Direction direction, boolean checkOwn) {
        BlockPos target = pos.relative(direction.getOpposite());
        
        Ship s = VSGameUtilsKt.getShipManagingPos((ServerLevel)(Object) this, target);
        if (s instanceof ServerShip serverShip) {
            if (checkOwn) {
                return ShipWireNetworkManager.get(serverShip)
                            .map(m -> m.getSignalAt(pos, direction)).orElse(0);
            }

            return ShipWireNetworkManager.get(serverShip).map(m -> m.getSignalAt(target, direction)).orElse(0);
        }
        
        return 0;
    }

    private int getShipWireSignalAroundAndSelf(BlockPos pos) {
        Ship s = VSGameUtilsKt.getShipManagingPos((ServerLevel)(Object) this, pos);
        if (s instanceof ServerShip serverShip) {
            var managerOpt = ShipWireNetworkManager.get(serverShip);
            if (managerOpt.isPresent()) {
                var manager = managerOpt.get();
                
                int maxSignal = 0;
                for (Direction dir : Direction.values()) {
                    BlockPos nodePos = pos.relative(dir);
                    Direction nodeDir = dir.getOpposite();
                    int nodeSignal = manager.getSignalAt(nodePos, nodeDir);
                    int nodeSignalOnCurrent = manager.getSignalAt(pos, dir);
                    if (nodeSignal > 0 || nodeSignalOnCurrent > 0) {
                        maxSignal = Math.max(maxSignal, Math.max(nodeSignal, nodeSignalOnCurrent));
                    }
                }
                return maxSignal;
            }
        }
        
        return 0;
    }
}
