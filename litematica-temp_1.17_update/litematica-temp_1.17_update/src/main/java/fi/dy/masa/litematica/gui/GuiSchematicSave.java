package fi.dy.masa.litematica.gui;

import java.io.File;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.Vector3d;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.scheduler.TaskScheduler;
import fi.dy.masa.litematica.scheduler.tasks.TaskSaveSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.SelectionManager;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.interfaces.ICompletionListener;
import fi.dy.masa.malilib.interfaces.IStringConsumer;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.StringUtils;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.swing.*;
import java.util.List;

import com.ibm.icu.text.Transliterator.Position;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import javax.imageio.ImageIO;
//import org.imgscalr.Scalr;
import fi.dy.masa.litematica.schematic.ColorPair;
import java.awt.Color;

public class GuiSchematicSave extends GuiSchematicSaveBase implements ICompletionListener
{
    protected final SelectionManager selectionManager;
    private static boolean isPixel;

    public GuiSchematicSave(boolean pixel)
    {
        this(null);
        isPixel = pixel;
    }

    public GuiSchematicSave(@Nullable LitematicaSchematic schematic)
    {
        super(schematic);

        if (schematic != null)
        {
            this.title = StringUtils.translate("litematica.gui.title.save_schematic_from_memory");
        }
        else
        {
            this.title = StringUtils.translate("litematica.gui.title.create_schematic_from_selection");
        }

        this.selectionManager = DataManager.getSelectionManager();

        AreaSelection area = this.selectionManager.getCurrentSelection();

        if (area != null)
        {
            this.defaultText = FileUtils.generateSafeFileName(area.getName());

            if (Configs.Generic.GENERATE_LOWERCASE_NAMES.getBooleanValue())
            {
                this.defaultText = FileUtils.generateSimpleSafeFileName(this.defaultText);
            }
        }
    }

    @Override
    public String getBrowserContext()
    {
        return "schematic_save";
    }

    @Override
    public File getDefaultDirectory()
    {
        return DataManager.getSchematicsBaseDirectory();
    }

    @Override
    protected IButtonActionListener createButtonListener(ButtonType type)
    {
        return new ButtonListener(type, this.selectionManager, this);
    }

    @Override
    public void onTaskCompleted()
    {
        if (this.mc.isOnThread())
        {
            this.refreshList();
        }
        else
        {
            this.mc.execute(GuiSchematicSave.this::refreshList);
        }
    }

    private void refreshList()
    {
        if (GuiUtils.getCurrentScreen() == this)
        {
            this.getListWidget().refreshEntries();
            this.getListWidget().clearSchematicMetadataCache();
        }
    }

    private static class ButtonListener implements IButtonActionListener
    {
        private final GuiSchematicSave gui;
        private final SelectionManager selectionManager;
        private final ButtonType type;

        public ButtonListener(ButtonType type, SelectionManager selectionManager, GuiSchematicSave gui)
        {
            this.type = type;
            this.selectionManager = selectionManager;
            this.gui = gui;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            if (this.type == ButtonType.SAVE && !isPixel)
            {
                File dir = this.gui.getListWidget().getCurrentDirectory();
                String fileName = this.gui.getTextFieldText();

                if (dir.isDirectory() == false)
                {
                    this.gui.addMessage(MessageType.ERROR, "litematica.error.schematic_save.invalid_directory", dir.getAbsolutePath());
                    return;
                }

                if (fileName.isEmpty())
                {
                    this.gui.addMessage(MessageType.ERROR, "litematica.error.schematic_save.invalid_schematic_name", fileName);
                    return;
                }
                
                // Saving a schematic from memory
                if (this.gui.schematic != null)
                {
                    LitematicaSchematic schematic = this.gui.schematic;

                    if (schematic.writeToFile(dir, fileName, GuiBase.isShiftDown()))
                    {
                        schematic.getMetadata().clearModifiedSinceSaved();
                        this.gui.getListWidget().refreshEntries();
                        this.gui.addMessage(MessageType.SUCCESS, "litematica.message.schematic_saved_as", fileName);
                    }
                }
                else
                {
                    AreaSelection area = this.selectionManager.getCurrentSelection();

                    if (area != null)
                    {
                        boolean overwrite = GuiBase.isShiftDown();
                        String fileNameTmp = fileName;

                        // The file name extension gets added in the schematic write method, so need to add it here for the check
                        if (fileNameTmp.endsWith(LitematicaSchematic.FILE_EXTENSION) == false)
                        {
                            fileNameTmp += LitematicaSchematic.FILE_EXTENSION;
                        }

                        if (FileUtils.canWriteToFile(dir, fileNameTmp, overwrite) == false)
                        {
                            this.gui.addMessage(MessageType.ERROR, "litematica.error.schematic_write_to_file_failed.exists", fileNameTmp);
                            return;
                        }

                        String author = this.gui.mc.player.getName().getString();
                        boolean ignoreEntities = this.gui.checkboxIgnoreEntities.isChecked();
                        boolean visibleOnly = this.gui.checkboxVisibleOnly.isChecked();
                        boolean fromSchematicWorld = this.gui.checkboxSaveFromSchematicWorld.isChecked();
                        LitematicaSchematic.SchematicSaveInfo info = new LitematicaSchematic.SchematicSaveInfo(visibleOnly, ignoreEntities, fromSchematicWorld);
                        LitematicaSchematic schematic = LitematicaSchematic.createEmptySchematic(area, author);
                        TaskSaveSchematic task = new TaskSaveSchematic(dir, fileName, schematic, area, info, overwrite);
                        task.setCompletionListener(this.gui);
                        TaskScheduler.getServerInstanceIfExistsOrClient().scheduleTask(task, 10);
                        this.gui.addMessage(MessageType.INFO, "litematica.message.schematic_save_task_created");
                    }
                    else
                    {
                        this.gui.addMessage(MessageType.ERROR, "litematica.message.error.schematic_save_no_area_selected");
                    }
                }
            }
            else if (isPixel)
            {
                File dir = this.gui.getListWidget().getCurrentDirectory();
                String fileName = this.gui.getTextFieldText();
                
                if (dir.isDirectory() == false)
                {
                    this.gui.addMessage(MessageType.ERROR, "litematica.error.schematic_save.invalid_directory", dir.getAbsolutePath());
                    return;
                }

                if (fileName.isEmpty())
                {
                    fileName += "FAILLLLLED";
                    this.gui.addMessage(MessageType.ERROR, "litematica.error.schematic_save.invalid_schematic_name", fileName);
                    return;
                }

                AreaSelection area = this.selectionManager.getCurrentSelection();
                List<fi.dy.masa.litematica.selection.Box> boxes = PositionUtils.getValidBoxes(area);
                BlockPos boxSize = PositionUtils.getEnclosingAreaSize(boxes);
                
                
                LitematicaSchematic schematic = LitematicaSchematic.createEmptySchematic(area, "MapArtGenerator");
                LitematicaBlockStateContainer container = schematic.getSubRegionContainer(area.getName()); // whatever sub-region name you used for the area

                Block[][][] blockArray = null;
                Path imagePath = Paths.get(area.getImagePath());
                Path palletPath = Paths.get(area.getPalletPath());
                
                // check if glass fog effect pallet is selected, else 2d pixel art
                boolean fogEffect = false;
                if (palletPath.toString().contains("glass_fog"))
                {
                    fogEffect = true;
                }

                try {
                    blockArray = imageToArray(boxSize.getX(), boxSize.getY(), boxSize.getZ(), imagePath, palletPath, fogEffect);
                } catch (IOException e) {
                    fileName = "Failed to create block map";
                    this.gui.addMessage(MessageType.ERROR, "litematica.error.schematic_save.invalid_schematic_name", fileName);
                    e.printStackTrace();
                }
                if (blockArray == null)
                {
                    this.gui.addMessage(MessageType.ERROR, "Could not create schematic. Most likely caused by bad file paths");
                    this.gui.addMessage(MessageType.ERROR, "Image path: " + imagePath.toString());
                    this.gui.addMessage(MessageType.ERROR, "Pallet path: " + palletPath.toString());
                    return;
                }

                // blockArray = caveGradient();
                int sizeZ = blockArray.length;
                int sizeX = blockArray[0].length;

                // loop over your array and set the blocks in the container
                try
                {
                    for (int z = 0; z < sizeZ; ++z) {
                        for (int x = 0; x < sizeX; ++x) {

                            // floor                            
                            container.set(sizeX - x - 1, 0, sizeZ - z - 1, blockArray[z][x][0].getDefaultState());

                            // glass fog effect up to the bounding box
                            if (fogEffect)
                            {
                                for (int y = 2; y < boxSize.getY(); y+=2)
                                {
                                    container.set(sizeX - x - 1, y - 1, sizeZ - z - 1, blockArray[z][x][1].getDefaultState());
                                    container.set(sizeX - x - 1, y, sizeZ - z - 1, blockArray[z][x][2].getDefaultState());
                                }
                            }
                        }
                    }
                }
                catch (IndexOutOfBoundsException e)
                {
                    this.gui.addMessage(MessageType.ERROR, "Index out of bounds!", fileName);
                    this.gui.addMessage(MessageType.ERROR, "sizeZ: " + blockArray.length);
                    this.gui.addMessage(MessageType.ERROR, "sizeX: " + blockArray[0].length);
                    this.gui.addMessage(MessageType.ERROR, "sizeY: " + blockArray[0][0].length);
                }

                // you might want to set the metadata values here, such as the total block count, creation time, enclosing size etc.
                SchematicMetadata meta = schematic.getMetadata();

                long time = System.currentTimeMillis();
                meta.setTimeCreated(time);
                meta.setTimeModified(time);
                meta.setTotalBlocks(sizeX * sizeZ);
                if (schematic.writeToFile(dir, fileName, GuiBase.isShiftDown()))
                {
                    fileName = this.gui.getTextFieldText() + " - AK Edit";
                    schematic.getMetadata().clearModifiedSinceSaved();
                    this.gui.getListWidget().refreshEntries();
                    this.gui.addMessage(MessageType.SUCCESS, "litematica.message.schematic_saved_as", fileName);
                }
            }
        }
    }

    // returns the min index 
    private static int getMin(double[] inputArray){ 
        int minIndex = 0;
        double minValue = inputArray[0]; 
        for(int i=1;i<inputArray.length;i++){ 
            if(inputArray[i] < minValue){ 
            minValue = inputArray[i]; 
            minIndex = i;
            } 
        } 
        return minIndex; 
    } 
    // euclidean color algorithm magic 
    // stack overflow question: https://stackoverflow.com/questions/6334311/whats-the-best-way-to-round-a-color-object-to-the-nearest-color-constant
    // Research paper: https://www.compuphase.com/cmetric.htm
    private static double colorDistance(Color c1, Color c2)
    {
        int red1 = c1.getRed();
        int red2 = c2.getRed();
        int rmean = (red1 + red2) >> 1;
        int r = red1 - red2;
        int g = c1.getGreen() - c2.getGreen();
        int b = c1.getBlue() - c2.getBlue();
        return Math.sqrt((((512+rmean)*r*r)>>8) + 4*g*g + (((767-rmean)*b*b)>>8));
    }

    private static BufferedImage readImage(String name)
    {
        BufferedImage img = null;
        try
        {
            img = ImageIO.read(new File(name));
        } catch (IOException e){}

        return img;
    }

    private static Color getColor(BufferedImage i) {
        BufferedImage bImg = null;
        try {
            bImg = resizeImage(i, 1, 1);
            return new Color(bImg.getRGB(0, 0));
        } catch (Exception e) {
            System.err.println(e);
        }
        return null;
    }

    public static BufferedImage resizeImage(BufferedImage i, int width, int height) throws IOException {
        Image tmp = i.getScaledInstance(width, height, i.SCALE_SMOOTH);
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.drawImage(tmp, 0, 0, null);
        graphics2D.dispose();
        return resizedImage;
    }

    private static Block[][][] caveGradient()
    {
        Block[][][] array = new Block[30][30][220];
        Random rand = new Random();
        int r = 0;
        double b = 0;
        String[] blockNames = {"coal_block", "black_wool", "obsidian", "blackstone", "deepslate_tiles", "gray_wool", "gray_concrete", "cobbled_deepslate", "basalt", "smooth_basalt", "tuff", "light_gray_concrete", "cobblestone", "andesite", "stone"};
        for (int y = 0; y < 220; y++)   // y
        {
            b += 0.06818;
            for (int x = 0; x < 30; x++)
            {
                for (int z = 0; z < 30; z++)
                {
                    r = (int)(rand.nextGaussian() + b);
                    if (r < 0 || r >= blockNames.length)
                        r = (int)b;
                    array[x][z][y] = Registry.BLOCK.get(new Identifier(blockNames[r]));
                }
            }
        }
        return array;
    }

    private static Block[][][] imageToArray(int sizeX, int sizeY, int sizeZ, Path imagePath, Path palletPath, boolean fogEffect) throws IOException
    {
        ArrayList<ColorPair> pallet = new ArrayList<ColorPair>();
        
        // create pallet 
        Files.walk(palletPath).forEach(path -> {
            try {
                File f = path.toFile().getAbsoluteFile();
                if (f.isFile()) {
                    try {
                        
                        String s = f.getName().toString();
        
                        s = s.substring(0, s.length() - 4);
        
                        // add new color, string pair to pallet
                        ColorPair nextColorPair = new ColorPair(getColor(ImageIO.read(f)), s);
                        if (nextColorPair.key() != null)
                            pallet.add(nextColorPair);
        
                    } catch (Exception e) {
                        System.err.println(e);
                    }
        
                }
            } catch (Exception e) {

            }
        });

        BufferedImage image = readImage(imagePath.toString());

        // check if image file was opened
        if (image == null)
        {
            return null;
        }

        // resize image to area selection size
        BufferedImage img = resizeImage(image, sizeX, sizeZ);

        Block[][][] blockMap = new Block[img.getHeight()][img.getWidth()][3];

        // read in pixels
        for (int w = 0; w < img.getWidth(); w ++)
        {
            for (int h = 0; h < img.getHeight(); h ++)
            {
                // get current pixel and find the closest color in the minecraft color list
                Color current = new Color(img.getRGB(w, h));
                double[] colorScore = new double[pallet.size()];
                for (int i = 0; i < pallet.size(); i++)
                {
                    if (pallet.get(i).key() == null)
                    {
                        return null;
                    }
                    colorScore[i] = colorDistance(pallet.get(i).key(), current);
                }
                int closest = getMin(colorScore);
                String choice = pallet.get(closest).value();

                if (fogEffect)
                {
                    // parsing glass_fog files to convert to stained glass id's
                    String first = choice.substring(0, choice.indexOf("-"));
                    String second = choice.substring(choice.indexOf("-") + 1);
                    blockMap[h][w][2] = Registry.BLOCK.get(new Identifier(first + "_stained_glass"));
                    if (first.equals(second))
                        blockMap[h][w][1] = Registry.BLOCK.get(new Identifier("air"));
                    else
                        blockMap[h][w][1] = Registry.BLOCK.get(new Identifier(second + "_stained_glass"));
                    // set light_source floor
                    blockMap[h][w][0] = Registry.BLOCK.get(new Identifier("sea_lantern"));
                }
                else
                {
                    blockMap[h][w][0] = Registry.BLOCK.get(new Identifier(choice));
                }
            }
        }

        return blockMap;
    }

    public static class InMemorySchematicCreator implements IStringConsumer
    {
        private final AreaSelection area;
        private final MinecraftClient mc;

        public InMemorySchematicCreator(AreaSelection area)
        {
            this.area = area;
            this.mc = MinecraftClient.getInstance();
        }

        @Override
        public void setString(String string)
        {
            LitematicaSchematic.SchematicSaveInfo info = new LitematicaSchematic.SchematicSaveInfo(false, false); // TODO
            String author = this.mc.player.getName().getString();
            LitematicaSchematic schematic = LitematicaSchematic.createEmptySchematic(this.area, author);

            if (schematic != null)
            {
                schematic.getMetadata().setName(string);
                TaskSaveSchematic task = new TaskSaveSchematic(schematic, this.area, info);
                TaskScheduler.getServerInstanceIfExistsOrClient().scheduleTask(task, 10);
            }
        }
    }
}
