package gregtech.common.gui;

import gregtech.api.gui.GT_GUIContainerMetaTile_Machine;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GT_Utility;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import static gregtech.api.enums.GT_Values.RES_PATH_GUI;

public class GT_GUIContainer_OutputHatch extends GT_GUIContainerMetaTile_Machine {

    private final String mName;

    public GT_GUIContainer_OutputHatch(InventoryPlayer aInventoryPlayer, IGregTechTileEntity aTileEntity, String aName) {
        super(new GT_Container_OutputHatch(aInventoryPlayer, aTileEntity), RES_PATH_GUI + "OutputHatch.png");
        mName = aName;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 8, ySize - 96 + 2, 4210752);
        fontRendererObj.drawString(mName, 8, 6, 4210752);
        if (mContainer != null) {
            fontRendererObj.drawString("Liquid Amount", 10, 20, 16448255);
            fontRendererObj.drawString(GT_Utility.parseNumberToString(((GT_Container_OutputHatch) mContainer).mContent), 10, 30, 16448255);
            fontRendererObj.drawString("Locked Fluid", 101, 20, 16448255);
            ItemStack tLockedDisplayStack = (ItemStack) mContainer.getInventory().get(3);
            String fluidName = tLockedDisplayStack == null ? "None" : tLockedDisplayStack.getDisplayName();
            fontRendererObj.drawString(fluidName, 101, 30, 16448255);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float par1, int par2, int par3) {
        super.drawGuiContainerBackgroundLayer(par1, par2, par3);
        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;
        drawTexturedModalRect(x, y, 0, 0, xSize, ySize);
    }
}