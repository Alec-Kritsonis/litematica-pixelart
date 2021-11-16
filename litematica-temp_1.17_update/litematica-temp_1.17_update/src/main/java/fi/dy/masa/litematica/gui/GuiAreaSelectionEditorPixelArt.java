package fi.dy.masa.litematica.gui;

import javax.annotation.Nullable;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.widgets.WidgetListSelectionSubRegions;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.selection.SelectionManager;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.litematica.util.PositionUtils.Corner;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.util.math.BlockPos;

public class GuiAreaSelectionEditorPixelArt extends GuiAreaSelectionEditorNormal
{
    protected GuiTextFieldGeneric textFieldBoxName;

    public GuiAreaSelectionEditorPixelArt(AreaSelection selection)
    {
        super(selection);

        if (DataManager.getSchematicProjectsManager().hasProjectOpen())
        {
            this.title = StringUtils.translate("litematica.gui.title.area_editor_normal_schematic_projects");
        }
        else
        {
            this.title = StringUtils.translate("litematica.gui.title.area_editor_simple");
        }
    }

    @Override
    protected int addSubRegionFields(int x, int y)
    {
        x = 12;
        String label = StringUtils.translate("litematica.gui.label.area_editor.box_name");
        this.addLabel(x, y, -1, 16, 0xFFFFFFFF, label);
        y += 13;

        boolean currentlyOn = this.selection.getExplicitOrigin() != null;
        this.createButtonOnOff(this.xOrigin, 24, -1, currentlyOn, ButtonListener.Type.TOGGLE_ORIGIN_ENABLED);

        int width = 202;
        this.textFieldBoxName = new GuiTextFieldGeneric(x, y + 2, width, 16, this.textRenderer);
        this.textFieldBoxName.setText(this.getBox().getName());
        this.addTextField(this.textFieldBoxName, new TextFieldListenerDummy());
        this.createButton(x + width + 4, y, -1, ButtonListener.Type.SET_BOX_NAME);

        width = 404;
        this.addLabel(x + 206 + 35, 44, -1, 16, 0xFFFFFFFF, "Image Path");
        this.textFieldImagePath = new GuiTextFieldGeneric(x + 202 + 35, 57 + 2, width, 16, this.textRenderer);
        this.textFieldImagePath.setText(this.selection.getImagePath());
        this.addTextField(this.textFieldImagePath, new TextFieldListenerDummy());
        this.createButton(x + width + 202 + 35 + 4, 57, -1, ButtonListener.Type.SET_IMAGE_PATH);

        this.addLabel(x + 206 + 35, y - 13, -1, 16, 0xFFFFFFFF, "Pallet Path");
        this.textFieldPalletPath = new GuiTextFieldGeneric(x + 202 + 35, y + 2, width, 16, this.textRenderer);
        this.textFieldPalletPath.setText(this.selection.getPalletPath());
        this.addTextField(this.textFieldPalletPath, new TextFieldListenerDummy());
        this.createButton(x + width + 202 + 35 + 4, y, -1, ButtonListener.Type.SET_PALLET_PATH);

        y += 20;

        x = 12;
        width = 98;

        int nextY = 0;
        this.createCoordinateInputs(x, y, width, Corner.CORNER_1);
        x += width + 42;
        nextY = this.createCoordinateInputs(x, y, width, Corner.CORNER_2);
        this.createButton(x + 10, nextY, -1, ButtonListener.Type.ANALYZE_AREA);
        x += width + 42;

        // Manual Origin defined
        if (this.selection.getExplicitOrigin() != null)
        {
            this.createCoordinateInputs(x, y, width, Corner.NONE);
        }

        x = this.createButton(22, nextY, -1, ButtonListener.Type.CREATE_PIXELART) + 26;

        this.addRenderingDisabledWarning(250, 48);

        return y;
    }

    @Override
    @Nullable
    protected Box getBox()
    {
        return this.selection.getSelectedSubRegionBox();
    }

    @Override
    protected void renameSubRegion()
    {
        String oldName = this.selection.getCurrentSubRegionBoxName();
        String newName = this.textFieldBoxName.getText();
        this.selection.renameSubRegionBox(oldName, newName);
    }

    @Override
    protected void renameSelection(String newName)
    {
        SelectionManager.renameSubRegionBoxIfSingle(this.selection, newName);

        // Only rename the special simple selection - it doesn't have a file
        this.selection.setName(newName);
    }

    @Override
    protected void createOrigin()
    {
        if (this.getBox() != null)
        {
            BlockPos pos1 = this.getBox().getPos1();
            BlockPos pos2 = this.getBox().getPos2();
            BlockPos origin = PositionUtils.getMinCorner(pos1, pos2);
            this.selection.setExplicitOrigin(origin);
        }
    }

    @Override
    protected WidgetListSelectionSubRegions getListWidget()
    {
        return null;
    }

    @Override
    protected void reCreateListWidget()
    {
        // NO-OP
    }
}
