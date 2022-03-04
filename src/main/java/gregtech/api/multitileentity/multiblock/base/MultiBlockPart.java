package gregtech.api.multitileentity.multiblock.base;

import gregtech.api.enums.GT_Values;
import gregtech.api.interfaces.ITexture;
import gregtech.api.multitileentity.base.BaseNontickableMultiTileEntity;
import gregtech.api.multitileentity.interfaces.IMultiBlockController;
import gregtech.api.util.GT_CoverBehaviorBase;
import gregtech.api.util.ISerializableObject;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import gregtech.api.multitileentity.IMultiTileEntity.IMTE_BreakBlock;

import java.util.ArrayList;

import static gregtech.api.enums.GT_Values.ALL_VALID_SIDES;
import static gregtech.api.enums.GT_Values.NBT;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_CASINGS;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

public class MultiBlockPart extends BaseNontickableMultiTileEntity implements IMTE_BreakBlock {
    public static final int
        NOTHING         = 0,
        ENERGY_IN       = (1 << 0),
        ENERGY_OUT      = (1 << 1),
        FLUID_IN        = (1 << 2),
        FLUID_OUT       = (1 << 3),
        ITEM_IN         = (1 << 4),
        ITEM_OUT        = (1 << 5);


    protected ChunkCoordinates mTargetPos = null;
    protected IMultiBlockController mTarget = null;
    protected int mMode = NOTHING;

    public void setTarget(IMultiBlockController aTarget, int aMode) {
        mTarget = aTarget;
        mTargetPos = (mTarget == null ? null : mTarget.getCoords());
        mMode = aMode;
    }

    @Override
    protected void addDebugInfo(EntityPlayer aPlayer, int aLogLevel, ArrayList<String> tList) {
        final IMultiBlockController controller = getTarget(false);
        if(controller != null) {
            tList.add("Has controller");
        } else {
            tList.add("No Controller");
        }

    }

    public IMultiBlockController getTarget(boolean aCheckValidity) {
        if (mTargetPos == null) return null;
        if (mTarget == null || mTarget.isDead()) {
            if (worldObj.blockExists(mTargetPos.posX, mTargetPos.posY, mTargetPos.posZ)) {
                final TileEntity te = worldObj.getTileEntity(mTargetPos.posX, mTargetPos.posY, mTargetPos.posZ);
                if (te instanceof IMultiBlockController) {
                    mTarget = (IMultiBlockController)te;
                } else {
                    mTargetPos = null;
                }
            }
        }
        if(aCheckValidity) {
            return mTarget != null && mTarget.checkStructure(false) ? mTarget : null;
        }
        else
            return mTarget;
    }

    public boolean hasMode(int aMode) {
        return (mMode & aMode) != 0;
    }

    @Override
    public void readMultiTileNBT(NBTTagCompound aNBT) {
        if (aNBT.hasKey(NBT.MODE)) mMode = aNBT.getInteger(NBT.MODE);
        if (aNBT.hasKey(NBT.TARGET)) {
            mTargetPos = new ChunkCoordinates(aNBT.getInteger(NBT.TARGET_X), aNBT.getShort(NBT.TARGET_Y), aNBT.getInteger(NBT.TARGET_Z));
        }

    }

    @Override
    public void writeMultiTileNBT(NBTTagCompound aNBT) {
        if (mMode != NOTHING) aNBT.setInteger(NBT.MODE, mMode);
        if (mTargetPos != null) {
            aNBT.setBoolean(NBT.TARGET, true);
            aNBT.setInteger(NBT.TARGET_X, mTargetPos.posX);
            aNBT.setShort(NBT.TARGET_Y, (short)mTargetPos.posY);
            aNBT.setInteger(NBT.TARGET_Z, mTargetPos.posZ);
        }
    }


    @Override
    public boolean breakBlock() {
        final IMultiBlockController tTarget = getTarget(false);
        if (tTarget != null) tTarget.onStructureChange();
        return false;
    }

    @Override
    public void onBlockAdded() {
        for (byte tSide : ALL_VALID_SIDES) {
            final TileEntity te = getTileEntityAtSide(tSide);
            if (te instanceof MultiBlockPart) {
                final IMultiBlockController tController = ((MultiBlockPart)te).getTarget(false);
                if (tController != null) tController.onStructureChange();
            } else if (te instanceof IMultiBlockController) {
                ((IMultiBlockController)te).onStructureChange();
            }
        }
    }

    @Override
    public ITexture[] getTexture(Block aBlock, byte aSide, boolean isActive, int aRenderPass) {
        return new ITexture[]{ MACHINE_CASINGS[1][2]};
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer entityPlayer) {
        return false;
    }


    @Override
    public void setLightValue(byte aLightValue) {

    }

    @Override
    public byte getComparatorValue(byte aSide) {
        return 0;
    }


    @Override public String getTileEntityName() {
        return "gt.multitileentity.multiblock.part";
    }

    /**
     * Fluid - Depending on the party time - proxy it to the multiblock controller, if we have one
     */
    @Override
    public int fill(ForgeDirection aDirection, FluidStack aFluidStack, boolean aDoFill) {
        if (!hasMode(FLUID_IN)) return 0;
        final byte aSide = (byte)aDirection.ordinal();
        if(aDirection != ForgeDirection.UNKNOWN && !getCoverBehaviorAtSideNew(aSide).letsFluidIn(aSide, getCoverIDAtSide(aSide), getComplexCoverDataAtSide(aSide), aFluidStack == null ? null : aFluidStack.getFluid(), this))
            return 0;
        final IMultiBlockController controller = getTarget(true);
        return controller == null ? 0 : controller.fill(aDirection, aFluidStack, aDoFill);
    }

    @Override
    public FluidStack drain(ForgeDirection aDirection, FluidStack aFluidStack, boolean aDoDrain) {
        if (!hasMode(FLUID_OUT)) return null;
        final byte aSide = (byte)aDirection.ordinal();
        if(aDirection != ForgeDirection.UNKNOWN && !getCoverBehaviorAtSideNew(aSide).letsFluidOut(aSide, getCoverIDAtSide(aSide), getComplexCoverDataAtSide(aSide), aFluidStack == null ? null : aFluidStack.getFluid(), this))
            return null;
        final IMultiBlockController controller = getTarget(true);
        return controller == null ? null : controller.drain(aDirection, aFluidStack, aDoDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection aDirection, int aAmountToDrain, boolean aDoDrain) {
        if (!hasMode(FLUID_OUT)) return null;
        final byte aSide = (byte)aDirection.ordinal();
        final IMultiBlockController controller = getTarget(true);
        if (controller == null) return null;
        final FluidStack aFluidStack = controller.getDrainableFluid(aSide);
        if(aDirection != ForgeDirection.UNKNOWN && !getCoverBehaviorAtSideNew(aSide).letsFluidOut(aSide, getCoverIDAtSide(aSide), getComplexCoverDataAtSide(aSide), aFluidStack == null ? null : aFluidStack.getFluid(), this))
            return null;
        return controller.drain(aDirection, aAmountToDrain, aDoDrain);
    }

    @Override
    public boolean canFill(ForgeDirection aDirection, Fluid aFluid) {
        if (!hasMode(FLUID_IN)) return false;
        final byte aSide = (byte)aDirection.ordinal();
        if(aDirection != ForgeDirection.UNKNOWN && !getCoverBehaviorAtSideNew(aSide).letsFluidIn(aSide, getCoverIDAtSide(aSide), getComplexCoverDataAtSide(aSide), aFluid, this))
            return false;
        final IMultiBlockController controller = getTarget(true);
        return controller != null && controller.canFill(aDirection, aFluid);
    }

    @Override
    public boolean canDrain(ForgeDirection aDirection, Fluid aFluid) {
        if (!hasMode(FLUID_OUT)) return false;
        final byte aSide = (byte)aDirection.ordinal();
        if(aDirection != ForgeDirection.UNKNOWN && !getCoverBehaviorAtSideNew(aSide).letsFluidOut(aSide, getCoverIDAtSide(aSide), getComplexCoverDataAtSide(aSide), aFluid, this))
            return false;
        final IMultiBlockController controller = getTarget(true);
        return controller != null && controller.canDrain(aDirection, aFluid);
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection aDirection) {
        if (!hasMode(FLUID_IN | FLUID_OUT)) return GT_Values.emptyFluidTankInfo;
        final IMultiBlockController controller = getTarget(true);
        if(controller == null)
            return GT_Values.emptyFluidTankInfo;
        final byte tSide = (byte) aDirection.ordinal();

        final GT_CoverBehaviorBase<?> tCover = getCoverBehaviorAtSideNew(tSide);
        final int coverId = getCoverIDAtSide(tSide);
        final ISerializableObject complexCoverData = getComplexCoverDataAtSide(tSide);

        if((controller.isLiquidInput(tSide) && tCover.letsFluidIn(tSide, coverId, complexCoverData, null, controller)) ||
           (controller.isLiquidOutput(tSide) && tCover.letsFluidOut(tSide, coverId, complexCoverData, null, controller)))
            return controller.getTankInfo(aDirection);

        return GT_Values.emptyFluidTankInfo;
    }

    /**
     * Energy - Depending on the part type - proxy to the multiblock controller, if we have one
     */
    @Override
    public boolean isUniversalEnergyStored(long aEnergyAmount) {
        final IMultiBlockController controller = getTarget(true);
        return controller != null && hasMode(ENERGY_OUT | ENERGY_IN) && controller.isUniversalEnergyStored(aEnergyAmount);

    }

    @Override
    public long getUniversalEnergyStored() {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ENERGY_OUT | ENERGY_IN)) ? controller.getUniversalEnergyStored() : 0;
    }

    @Override
    public long getUniversalEnergyCapacity() {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ENERGY_OUT | ENERGY_IN)) ? controller.getUniversalEnergyCapacity() : 0;
    }

    @Override
    public long getOutputAmperage() {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ENERGY_OUT)) ? controller.getOutputAmperage() : 0;
    }

    @Override
    public long getOutputVoltage() {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ENERGY_OUT)) ? controller.getOutputVoltage() : 0;
    }

    @Override
    public long getInputAmperage() {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ENERGY_IN)) ? controller.getInputAmperage() : 0;
    }

    @Override
    public long getInputVoltage() {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ENERGY_IN)) ? controller.getInputVoltage() : 0;
    }

    @Override
    public boolean decreaseStoredEnergyUnits(long aEnergy, boolean aIgnoreTooLittleEnergy) {
        final IMultiBlockController controller = getTarget(true);
        return controller != null && hasMode(ENERGY_OUT) && controller.decreaseStoredEnergyUnits(aEnergy, aIgnoreTooLittleEnergy);
    }

    @Override
    public boolean increaseStoredEnergyUnits(long aEnergy, boolean aIgnoreTooMuchEnergy) {
        final IMultiBlockController controller = getTarget(true);
        return controller != null && hasMode(ENERGY_IN) && controller.increaseStoredEnergyUnits(aEnergy, aIgnoreTooMuchEnergy);
    }

    @Override
    public boolean drainEnergyUnits(byte aSide, long aVoltage, long aAmperage) {
        final IMultiBlockController controller = getTarget(true);
        return controller != null && hasMode(ENERGY_OUT) && controller.drainEnergyUnits(aSide, aVoltage, aAmperage);
    }


    @Override
    public long injectEnergyUnits(byte aSide, long aVoltage, long aAmperage) {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ENERGY_IN)) ? controller.injectEnergyUnits(aSide, aVoltage, aAmperage) : 0;
    }


    @Override
    public long getAverageElectricInput() {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ENERGY_IN)) ? controller.getAverageElectricInput() : 0;
    }

    @Override
    public long getAverageElectricOutput() {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ENERGY_OUT)) ? controller.getAverageElectricOutput() : 0;
    }

    @Override
    public long getStoredEU() {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ENERGY_OUT | ENERGY_IN)) ? controller.getStoredEU() : 0;
    }

    @Override
    public long getEUCapacity() {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ENERGY_OUT | ENERGY_IN)) ? controller.getEUCapacity() : 0;
    }

    @Override
    public boolean inputEnergyFrom(byte aSide) {
        final IMultiBlockController controller = getTarget(true);
        return controller != null && hasMode(ENERGY_IN) && controller.inputEnergyFrom(aSide);
    }

    @Override
    public boolean outputsEnergyTo(byte aSide) {
        final IMultiBlockController controller = getTarget(true);
        return controller != null && hasMode(ENERGY_OUT) && controller.outputsEnergyTo(aSide);
    }

    // End Energy

    /**
     * Inventory - Depending on the part type - proxy to the multiblock controller, if we have one
     */
    @Override
    public boolean hasInventoryBeenModified() {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && controller.hasInventoryBeenModified());
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && controller.isValidSlot(aIndex));
    }

    @Override
    public boolean addStackToSlot(int aIndex, ItemStack aStack) {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ITEM_IN) && controller.addStackToSlot(aIndex, aStack));
    }

    @Override
    public boolean addStackToSlot(int aIndex, ItemStack aStack, int aAmount) {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ITEM_IN) && controller.addStackToSlot(aIndex, aStack, aAmount));
    }


    @Override
    public int[] getAccessibleSlotsFromSide(int aSide) {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ITEM_IN | ITEM_OUT)) ? controller.getAccessibleSlotsFromSide(aSide) : GT_Values.emptyIntArray;
    }

    @Override
    public boolean canInsertItem(int aSlot, ItemStack aStack, int aSide) {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ITEM_IN) && controller.canInsertItem(aSlot, aStack, aSide));
    }

    @Override
    public boolean canExtractItem(int aSlot, ItemStack aStack, int aSide) {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ITEM_OUT) && controller.canExtractItem(aSlot, aStack, aSide));
    }

    @Override
    public int getSizeInventory() {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ITEM_IN | ITEM_OUT)) ? controller.getSizeInventory() : 0;
    }

    @Override
    public ItemStack getStackInSlot(int aSlot) {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ITEM_IN | ITEM_OUT)) ? controller.getStackInSlot(aSlot) : null;
    }

    @Override
    public ItemStack decrStackSize(int aSlot, int aDecrement) {
        final IMultiBlockController controller = getTarget(true);
        return (controller != null && hasMode(ITEM_OUT)) ? controller.decrStackSize(aSlot, aDecrement) : null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int aSlot) {
        final IMultiBlockController controller = getTarget(true);
        return controller != null ? controller.getStackInSlotOnClosing(aSlot) : null;
    }

    @Override
    public void setInventorySlotContents(int aSlot, ItemStack aStack) {
        final IMultiBlockController controller = getTarget(true);
        if(controller != null) controller.setInventorySlotContents(aSlot, aStack);
    }

    @Override
    public String getInventoryName() {
        final IMultiBlockController controller = getTarget(true);
        if(controller != null)
            return controller.getInventoryName();
        return firstNonNull(getCustomName(), getTileEntityName());
    }

    @Override
    public int getInventoryStackLimit() {
        final IMultiBlockController controller = getTarget(true);
        return controller != null ? controller.getInventoryStackLimit() : 0;
    }


    @Override
    public boolean isItemValidForSlot(int aSlot, ItemStack aStack) {
        final IMultiBlockController controller = getTarget(true);
        return controller != null && controller.isItemValidForSlot(aSlot, aStack);
    }

    // End Inventory

}
