package fi.dy.masa.litematica.schematic;
import java.awt.Color;

public class ColorPair {
    private final Color key;
    private final String value;

    public ColorPair(Color aKey, String aValue)
    {
        key   = aKey;
        value = aValue;
    }

    public Color key()   { return key; }
    public String value() { return value; }
}
