package gregtech.api.multitileentity.multiblock.base;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import cpw.mods.fml.common.network.NetworkRegistry;
import gregtech.api.enums.GT_Values;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TextureSet;
import gregtech.api.fluid.FluidTankGT;
import gregtech.api.interfaces.IDescribable;
import gregtech.api.interfaces.tileentity.IMachineProgress;
import gregtech.api.multitileentity.IMultiTileEntity;
import gregtech.api.multitileentity.MultiTileEntityContainer;
import gregtech.api.multitileentity.MultiTileEntityRegistry;
import gregtech.api.multitileentity.interfaces.IMultiBlockController;
import gregtech.api.multitileentity.machine.MultiTileBasicMachine;
import gregtech.api.objects.GT_ItemStack;
import gregtech.api.util.GT_Multiblock_Tooltip_Builder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static gregtech.GT_Mod.GT_FML_LOGGER;
import static gregtech.api.enums.GT_Values.NBT;

public abstract class MultiBlockController<T extends MultiBlockController<T>> extends MultiTileBasicMachine
    implements IAlignment, IConstructable, IMultiBlockController, IDescribable, IMachineProgress
{
    private static final Map<Integer, GT_Multiblock_Tooltip_Builder> tooltip = new ConcurrentHashMap<>();

    protected BuildState buildState = new BuildState();

    protected ItemStack[] mInventory;


    private int mMaxProgresstime = 0, mProgresstime = 0;
    private boolean mStructureOkay = false, mStructureChanged = false;
    private boolean mWorks = true, mWorkUpdate = false, mWasShutdown = false, mActive = false;
    private ExtendedFacing mExtendedFacing = ExtendedFacing.DEFAULT;
    private IAlignmentLimits mLimits = getInitialAlignmentLimits();

    /** Registry ID of the required casing */
    public abstract short getCasingRegistryID();
    /** Meta ID of the required casing */
    public abstract short getCasingMeta();


    /**
     * Create the tooltip for this multi block controller.
     */
    protected abstract GT_Multiblock_Tooltip_Builder createTooltip();

    /**
     * @return The starting offset for the structure builder
     */
    public abstract Vec3Impl getStartingStructureOffset();

    /**
     * Due to limitation of Java type system, you might need to do an unchecked cast.
     * HOWEVER, the returned IStructureDefinition is expected to be evaluated against current instance only, and should
     * not be used against other instances, even for those of the same class.
     */
    public abstract IStructureDefinition<T> getStructureDefinition();

    /**
     * Checks the Machine.
     * <p>
     * NOTE: If using `buildState` be sure to `startBuilding()` and either `endBuilding()` or `failBuilding()`
     */
    public abstract boolean checkMachine();

    /**
     * Checks the Recipe
     */
    public abstract boolean checkRecipe(ItemStack aStack);


    @Override
    public void writeMultiTileNBT(NBTTagCompound aNBT) {
        super.writeMultiTileNBT(aNBT);

        aNBT.setBoolean(NBT.STRUCTURE_OK, mStructureOkay);
        aNBT.setByte(NBT.ROTATION, (byte) mExtendedFacing.getRotation().getIndex());
        aNBT.setByte(NBT.FLIP, (byte) mExtendedFacing.getFlip().getIndex());
    }

    @Override
    public void readMultiTileNBT(NBTTagCompound aNBT) {
        super.readMultiTileNBT(aNBT);

        mStructureOkay = aNBT.getBoolean(NBT.STRUCTURE_OK);
        mExtendedFacing = ExtendedFacing.of(
            ForgeDirection.getOrientation(getFrontFacing()),
            Rotation.byIndex(aNBT.getByte(NBT.ROTATION)),
            Flip.byIndex(aNBT.getByte(NBT.FLIP))
        );
    }

    @Override
    public String[] getDescription() {
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            return getTooltip().getStructureInformation();
        } else {
            return getTooltip().getInformation();
        }
    }

    @Override
    protected void addDebugInfo(EntityPlayer aPlayer, int aLogLevel, ArrayList<String> tList) {
        tList.add("Structure ok: " + checkStructure(false));
    }

    protected int getToolTipID() {
        return getMultiTileEntityRegistryID() << 16 + getMultiTileEntityID();
    }

    protected GT_Multiblock_Tooltip_Builder getTooltip() {
        final int tooltipId = getToolTipID();
        final GT_Multiblock_Tooltip_Builder tt = tooltip.get(tooltipId);
        if (tt == null) {
            return tooltip.computeIfAbsent(tooltipId, k -> createTooltip());
        }
        return tt;
    }


    @Override
    public boolean checkStructure(boolean aForceReset) {
        if(!isServerSide()) return mStructureOkay;

        // Only trigger an update if forced (from onPostTick, generally), or if the structure has changed
        if ((mStructureChanged || aForceReset)) {
            mStructureOkay = checkMachine();
        }
        mStructureChanged = false;
        return mStructureOkay;
    }

    @Override
    public void onStructureChange() {
        mStructureChanged = true;
    }

    public final boolean checkPiece(String piece, Vec3Impl offset) {
        return checkPiece(piece, offset.get0(), offset.get1(), offset.get2());
    }

    /**
     * Explanation of the world coordinate these offset means:
     * <p>
     * Imagine you stand in front of the controller, with controller facing towards you not rotated or flipped.
     * <p>
     * The horizontalOffset would be the number of blocks on the left side of the controller, not counting controller itself.
     * The verticalOffset would be the number of blocks on the top side of the controller, not counting controller itself.
     * The depthOffset would be the number of blocks between you and controller, not counting controller itself.
     * <p>
     * All these offsets can be negative.
     */
    protected final boolean checkPiece(String piece, int horizontalOffset, int verticalOffset, int depthOffset) {
        return getCastedStructureDefinition().check(
            this, piece, getWorld(), getExtendedFacing(), getXCoord(), getYCoord(), getZCoord(), horizontalOffset, verticalOffset,
            depthOffset, !mStructureOkay
        );
    }

    public final boolean buildPiece(String piece, ItemStack trigger, boolean hintsOnly, Vec3Impl offset) {
        return buildPiece(piece, trigger, hintsOnly, offset.get0(), offset.get1(), offset.get2());
    }

    protected final boolean buildPiece(String piece, ItemStack trigger, boolean hintOnly, int horizontalOffset, int verticalOffset, int depthOffset) {
        return getCastedStructureDefinition().buildOrHints(
            this, trigger, piece, getWorld(), getExtendedFacing(), getXCoord(), getYCoord(), getZCoord(), horizontalOffset,
            verticalOffset, depthOffset, hintOnly
        );
    }

    @SuppressWarnings("unchecked")
    private IStructureDefinition<MultiBlockController<T>> getCastedStructureDefinition() {
        return (IStructureDefinition<MultiBlockController<T>>) getStructureDefinition();
    }

    @Override
    public ExtendedFacing getExtendedFacing() {
        return mExtendedFacing;
    }

    @Override
    public void setExtendedFacing(ExtendedFacing newExtendedFacing) {
        if (mExtendedFacing != newExtendedFacing) {
            onStructureChange();
            if (mStructureOkay)
                stopMachine();
            mExtendedFacing = newExtendedFacing;
            mStructureOkay = false;
            if (isServerSide()) {
                StructureLibAPI.sendAlignment(
                    this, new NetworkRegistry.TargetPoint(getWorld().provider.dimensionId, getXCoord(), getYCoord(), getZCoord(), 512));
            } else {
                issueTextureUpdate();
            }
        }
    }

    @Override
    public boolean onWrenchRightClick(EntityPlayer aPlayer, ItemStack tCurrentItem, byte wrenchSide, float aX, float aY, float aZ) {
        if (wrenchSide != getFrontFacing())
            return super.onWrenchRightClick(aPlayer, tCurrentItem, wrenchSide, aX, aY, aZ);
        if (aPlayer.isSneaking()) {
            // we won't be allowing horizontal flips, as it can be perfectly emulated by rotating twice and flipping horizontally
            // allowing an extra round of flip make it hard to draw meaningful flip markers in GT_Proxy#drawGrid
            toolSetFlip(getFlip().isHorizontallyFlipped() ? Flip.NONE : Flip.HORIZONTAL);
        } else {
            toolSetRotation(null);
        }
        return true;
    }

    @Override
    public void onFirstTick(boolean aIsServerSide) {
        super.onFirstTick(aIsServerSide);
        if (aIsServerSide)
            checkStructure(true);
        else
            StructureLibAPI.queryAlignment(this);
    }

    @Override
    public void onPostTick(long aTick, boolean aIsServerSide) {
        if (aIsServerSide) {
            if (aTick % 600 == 5) {
                // Recheck the structure every 30 seconds or so
                if (!checkStructure(false)) checkStructure(true);
            }
        }
    }

    @Override
    public final boolean isFacingValid(byte aFacing) {
        return canSetToDirectionAny(ForgeDirection.getOrientation(aFacing));
    }

    @Override
    public void onFacingChange() {
        toolSetDirection(ForgeDirection.getOrientation(getFrontFacing()));
        onStructureChange();
    }

    @Override
    public boolean allowCoverOnSide(byte aSide, GT_ItemStack aCoverID) {
        return aSide != mFacing;
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        return getTooltip().getStructureHint();
    }

    @Override
    public IAlignmentLimits getAlignmentLimits() {
        return mLimits;
    }

    protected void setAlignmentLimits(IAlignmentLimits mLimits) {
        this.mLimits = mLimits;
    }

    // IMachineProgress
    @Override
    public int getProgress() {
        return mProgresstime;
    }

    @Override
    public int getMaxProgress() {
        return mMaxProgresstime;
    }

    @Override
    public boolean increaseProgress(int aProgressAmountInTicks) {
        return increaseProgressGetOverflow(aProgressAmountInTicks) != aProgressAmountInTicks;
    }

    @Override
    public FluidStack getDrainableFluid(byte aSide) {
        final IFluidTank tank = getFluidTankDrainable(aSide, null);
        return tank ==  null ? null : tank.getFluid();

    }

    /**
     * Increases the Progress, returns the overflown Progress.
     */
    public int increaseProgressGetOverflow(int aProgress) {
        return 0;
    }

    @Override
    public boolean hasThingsToDo() {
        return getMaxProgress() > 0;
    }

    @Override
    public boolean hasWorkJustBeenEnabled() {
        return mWorkUpdate;
    }

    @Override
    public void enableWorking() {
        if (!mWorks) mWorkUpdate = true;
        mWorks = true;
        mWasShutdown = false;
    }

    @Override
    public void disableWorking() {
        mWorks = false;
    }

    @Override
    public boolean isAllowedToWork() {
        return mWorks;
    }

    @Override
    public boolean isActive() {
        return mActive;
    }

    @Override
    public void setActive(boolean aActive) {
        mActive = aActive;
    }

    @Override
    public boolean wasShutdown() {
        return mWasShutdown;
    }

    // End IMachineProgress

    public void stopMachine() {
        disableWorking();
    }

    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> !f.isVerticallyFliped();
    }

    public static class BuildState {
        /**
         * Utility class to keep track of the build state of a multiblock
         */
        boolean building = false;
        Vec3Impl currentOffset;

        public void startBuilding(Vec3Impl structureOffset) {
            if (building) throw new IllegalStateException("Already building!");
            building = true;
            setCurrentOffset(structureOffset);
        }

        public Vec3Impl setCurrentOffset(Vec3Impl structureOffset) {
            verifyBuilding();
            return (currentOffset = structureOffset);
        }

        private void verifyBuilding() {
            if (!building) throw new IllegalStateException("Not building!");
        }

        public boolean failBuilding() {
            building = false;
            currentOffset = null;
            return false;
        }

        public Vec3Impl stopBuilding() {
            final Vec3Impl toReturn = getCurrentOffset();
            building = false;
            currentOffset = null;

            return toReturn;
        }

        public Vec3Impl getCurrentOffset() {
            verifyBuilding();
            return currentOffset;
        }

        public Vec3Impl addOffset(Vec3Impl offset) {
            verifyBuilding();
            return setCurrentOffset(currentOffset.add(offset));
        }
    }

    public <S> IStructureElement<S> addMultiTileCasing(int aRegistryID, int aBlockMeta, int aModes) {
        return new IStructureElement<S>() {
            private final short[] DEFAULT = new short[]{255, 255, 255, 0};
            private IIcon[] mIcons = null;

            @Override
            public boolean check(S t, World world, int x, int y, int z) {
                final TileEntity tileEntity = world.getTileEntity(x, y, z);
                if (!(tileEntity instanceof MultiBlockPart)) return false;

                final MultiBlockPart part = (MultiBlockPart) tileEntity;
                if (aRegistryID != part.getMultiTileEntityRegistryID() || aBlockMeta != part.getMultiTileEntityID()) return false;

                final IMultiBlockController tTarget = part.getTarget(false);
                if (tTarget != null && tTarget != MultiBlockController.this) return false;

                part.setTarget(MultiBlockController.this, aModes);
                return true;
            }

            @Override
            public boolean spawnHint(S t, World world, int x, int y, int z, ItemStack trigger) {
                if (mIcons == null) {
                    mIcons = new IIcon[6];
                    Arrays.fill(mIcons, TextureSet.SET_NONE.mTextures[OrePrefixes.block.mTextureIndex].getIcon());
//                    Arrays.fill(mIcons, getTexture(aCasing);
//                    for (int i = 0; i < 6; i++) {
//                        mIcons[i] = aCasing.getIcon(i, aMeta);
//                    }
                }
                final short[] RGBA = DEFAULT;
                StructureLibAPI.hintParticleTinted(world, x, y, z, mIcons, RGBA);
//                StructureLibAPI.hintParticle(world, x, y, z, aCasing, aMeta);
                return true;
            }

            @Override
            public boolean placeBlock(S t, World world, int x, int y, int z, ItemStack trigger) {
                final MultiTileEntityRegistry tRegistry = MultiTileEntityRegistry.getRegistry(aRegistryID);
                final MultiTileEntityContainer tContainer = tRegistry.getNewTileEntityContainer(world, x, y, z, aBlockMeta, null);
                if(tContainer == null) {
                    GT_FML_LOGGER.error("NULL CONTAINER");
                    return false;
                }
                final IMultiTileEntity te = ((IMultiTileEntity)tContainer.mTileEntity);
                if(!(te instanceof MultiBlockPart)) {
                    GT_FML_LOGGER.error("Not a multiblock part");
                    return false;
                }
                if (world.setBlock(x, y, z, tContainer.mBlock, 15 - tContainer.mBlockMetaData, 2)) {
                    tContainer.setMultiTile(world, x, y, z);
                    ((MultiBlockPart) te).setTarget(MultiBlockController.this, aModes);
                }

                return false;
            }

            public IIcon getTexture(OrePrefixes aBlock) {
                return TextureSet.SET_NONE.mTextures[OrePrefixes.block.mTextureIndex].getIcon();
            }
        };
    }
}
