package gregtech.api.multitileentity.interfaces;

import gregtech.api.multitileentity.IMultiTileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraftforge.fluids.FluidStack;

public interface IMultiBlockController extends IMultiTileEntity {
    boolean checkStructure(boolean aForceReset);

    /** Set the structure as having changed, and trigger an update */
    void onStructureChange();

    @Override ChunkCoordinates getCoords();

    FluidStack getDrainableFluid(byte aSide);

    boolean isLiquidInput(byte aSide);
    boolean isLiquidOutput(byte aSide);

}
