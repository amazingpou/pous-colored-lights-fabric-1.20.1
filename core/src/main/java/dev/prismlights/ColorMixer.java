package dev.prismlights;

/** Shared CPU mixing for tests and the GPU light-volume builder. */
public final class ColorMixer {
    private static final double TAU = Math.PI * 2;
    private final double[] rgb = new double[3];
    private final double[] hue = new double[3];
    private double energy;

    public void add(int color, double weight) {
        if (!(weight > 0) || !Double.isFinite(weight)) return;
        double[] rgbPart = components(color, false);
        double[] huePart = components(color, true);
        for (int i = 0; i < 3; i++) {
            rgb[i] += rgbPart[i] * weight;
            hue[i] += huePart[i] * weight;
        }
        energy += weight;
    }

    public double energy() { return energy; }

    public float[] color(boolean artistic) {
        float[] result = new float[3];
        if (energy > 0) {
            double[] sums = artistic ? hue : rgb;
            writeColor(sums[0], sums[1], sums[2], energy, artistic, result, 0);
        }
        return result;
    }

    /** Circular hue averaging gives red+blue purple and yellow+blue green.
     * Opposing hues lose saturation instead of producing a hard edge. */
    public static double[] components(int color, boolean artistic) {
        double red = ((color >>> 16) & 255) / 255.0;
        double green = ((color >>> 8) & 255) / 255.0;
        double blue = (color & 255) / 255.0;
        if (!artistic) return new double[]{red, green, blue};
        double max = Math.max(red, Math.max(green, blue));
        double min = Math.min(red, Math.min(green, blue));
        double delta = max - min;
        if (delta == 0) return new double[]{0, 0, max};
        double sector;
        if (max == red) sector = ((green - blue) / delta + 6) % 6;
        else if (max == green) sector = (blue - red) / delta + 2;
        else sector = (red - green) / delta + 4;
        double angle = sector * TAU / 6;
        double saturation = delta / max;
        // Decoding takes a square root to soften desaturation between hues.
        // Encode squared saturation so a single hue (including duplicates)
        // round-trips exactly instead of acquiring a different saturation.
        double chromaWeight = saturation * saturation;
        return new double[]{Math.cos(angle) * chromaWeight, Math.sin(angle) * chromaWeight, max};
    }

    /** Writes RGB into an existing volume without allocating per voxel. */
    public static void writeColor(double first, double second, double third, double energy,
                                  boolean artistic, float[] target, int offset) {
        if (!(energy > 0)) return;
        if (!artistic) {
            target[offset] = (float)(first / energy);
            target[offset + 1] = (float)(second / energy);
            target[offset + 2] = (float)(third / energy);
            return;
        }
        double magnitude = Math.hypot(first, second) / energy;
        double saturation = Math.sqrt(Math.min(1, magnitude));
        double value = third / energy;
        if (saturation < 0.0001) {
            target[offset] = target[offset + 1] = target[offset + 2] = (float)value;
            return;
        }
        double sector = (Math.atan2(second, first) / TAU * 6 + 6) % 6;
        double chroma = value * saturation;
        double auxiliary = chroma * (1 - Math.abs(sector % 2 - 1));
        double minimum = value - chroma;
        double red, green, blue;
        switch ((int)sector) {
            case 0 -> { red = chroma; green = auxiliary; blue = 0; }
            case 1 -> { red = auxiliary; green = chroma; blue = 0; }
            case 2 -> { red = 0; green = chroma; blue = auxiliary; }
            case 3 -> { red = 0; green = auxiliary; blue = chroma; }
            case 4 -> { red = auxiliary; green = 0; blue = chroma; }
            default -> { red = chroma; green = 0; blue = auxiliary; }
        }
        target[offset] = (float)(red + minimum);
        target[offset + 1] = (float)(green + minimum);
        target[offset + 2] = (float)(blue + minimum);
    }
}
