package gregtech.api.multitileentity.machine;

import gregtech.api.enums.GT_Values;
import gregtech.api.enums.GT_Values.NBT;
import gregtech.api.fluid.FluidTankGT;
import gregtech.api.interfaces.ITexture;
import gregtech.api.multitileentity.MultiTileEntityRegistry;
import gregtech.api.multitileentity.base.BaseTickableMultiTileEntity;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

import static com.google.common.primitives.Ints.saturatedCast;

public class MultiTileBasicMachine extends BaseTickableMultiTileEntity  {
    public int mParallel = 1;
    public FluidTankGT[] mTanksInput = GT_Values.emptyFluidTankGT, mTanksOutput = GT_Values.emptyFluidTankGT;
    public ItemStack[] mOutputItems = GT_Values.emptyItemStack;
    public FluidStack[] mOutputFluids = GT_Values.emptyFluidStack;

    @Override
    public String getTileEntityName() {
        return "gt.multitileentity.machine.basic";
    }


    @Override
    public void writeMultiTileNBT(NBTTagCompound aNBT) {
        super.writeMultiTileNBT(aNBT);

    }

    @Override
    public void readMultiTileNBT(NBTTagCompound aNBT) {
        super.readMultiTileNBT(aNBT);
        if (aNBT.hasKey(NBT.PARALLEL))
            mParallel = Math.max(1, aNBT.getInteger(NBT.PARALLEL));



        long tCapacity = 1000;
        if (aNBT.hasKey(NBT.TANK_CAPACITY)) tCapacity = saturatedCast(aNBT.getLong(NBT.TANK_CAPACITY));

        mTanksInput = new FluidTankGT[getFluidInputCount()];
        mTanksOutput = new FluidTankGT[getFluidOutputCount()];
        mOutputFluids = new FluidStack[getFluidOutputCount()];
        mOutputItems = new ItemStack[getItemOutputCount()];

        // TODO: See if we need the adjustable map here `.setCapacity(mRecipes, mParallel * 2L)` in place of the `setCapacityMultiplier`
        for (int i = 0; i < mTanksInput.length; i++)
            mTanksInput[i] = new FluidTankGT(tCapacity).setCapacityMultiplier(mParallel * 2L).readFromNBT(aNBT, NBT.TANK_IN + i);

        for (int i = 0; i < mTanksOutput.length; i++)
            mTanksOutput[i] = new FluidTankGT().readFromNBT(aNBT, NBT.TANK_OUT + i);

        for (int i = 0; i < mOutputFluids.length; i++)
            mOutputFluids[i] = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag(NBT.FLUID_OUT+"."+i));

        for (int i = 0; i < mOutputItems.length; i++)
            mOutputItems[i] = ItemStack.loadItemStackFromNBT(aNBT.getCompoundTag(NBT.INV_OUT+"."+i));

    }

    /**
     * The number of fluid (input) slots available for this machine
     */
    public int getFluidInputCount() {
        return 2;
    }

    /**
     * The number of fluid (output) slots available for this machine
     */
    public int getFluidOutputCount() {
        return 2;
    }

    /**
     * The number of item (input) slots available for this machine
     */
    public int getItemInputCount() {
        return 2;
    }

    /**
     * The number of item (output) slots available for this machine
     */
    public int getItemOutputCount() {
        return 2;
    }

    @Override
    public ITexture[] getTexture(Block aBlock, byte aSide, boolean isActive, int aRenderPass) {
        return new ITexture[0];
    }

    @Override
    public void setLightValue(byte aLightValue) {

    }

    @Override
    public String getInventoryName() {
        final String name = getCustomName();
        if(name != null) return name;
        final MultiTileEntityRegistry tRegistry = MultiTileEntityRegistry.getRegistry(getMultiTileEntityRegistryID());
        return tRegistry == null ? getClass().getName() : tRegistry.getLocal(getMultiTileEntityID());
    }


    @Override
    public boolean isUseableByPlayer(EntityPlayer aPlayer) {
        return playerOwnsThis(aPlayer, false) && mTickTimer > 40 &&
            getTileEntityOffset(0, 0, 0) == this &&
            aPlayer.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) < 64 && allowInteraction(aPlayer);
    }


    @Override
    public boolean isLiquidInput(byte aSide) {
        return aSide != mFacing;
    }

    @Override
    public boolean isLiquidOutput(byte aSide) {
        return aSide != mFacing;
    }


    @Override
    protected IFluidTank[] getFluidTanks(byte aSide) {
        final boolean fluidInput = isLiquidInput(aSide);
        final boolean fluidOutput = isLiquidOutput(aSide);

        if(fluidInput && fluidOutput) {
            final IFluidTank[] rTanks = new IFluidTank[ mTanksInput.length + mTanksOutput.length];
            System.arraycopy(mTanksInput, 0, rTanks, 0, mTanksInput.length);
            System.arraycopy(mTanksOutput, 0, rTanks, mTanksInput.length, mTanksOutput.length);
            return rTanks;
        } else if(fluidInput) {
            return mTanksInput;
        } else if(fluidOutput) {
            return mTanksOutput;
        }
        return GT_Values.emptyFluidTank;
    }

    @Override
    public IFluidTank getFluidTankFillable(byte aSide, FluidStack aFluidToFill) {
        if(!isLiquidInput(aSide)) return null;
        for (FluidTankGT tankGT : mTanksInput) if (tankGT.contains(aFluidToFill)) return tankGT;
//        if (!mRecipes.containsInput(aFluidToFill, this, slot(mRecipes.mInputItemsCount + mRecipes.mOutputItemsCount))) return null;
        for (FluidTankGT fluidTankGT : mTanksInput) if (fluidTankGT.isEmpty()) return fluidTankGT;
        return null;
    }

    @Override
    protected IFluidTank getFluidTankDrainable(byte aSide, FluidStack aFluidToDrain) {
        if(!isLiquidOutput(aSide)) return null;
        for (FluidTankGT fluidTankGT : mTanksOutput)
            if (aFluidToDrain == null ? fluidTankGT.has() : fluidTankGT.contains(aFluidToDrain))
                return fluidTankGT;

        return null;
    }
}
