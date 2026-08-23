package com.tvkeyboard.tv;

public enum PanelSizeMode {
    FULL(1.00f),
    TALL(0.75f),
    HALF(0.50f),
    QUARTER(0.25f);

    public final float heightFraction;

    PanelSizeMode(float heightFraction) {
        this.heightFraction = heightFraction;
    }

    public PanelSizeMode next() {
        PanelSizeMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static PanelSizeMode fromFraction(float fraction) {
        for (PanelSizeMode mode : values()) {
            if (Math.abs(mode.heightFraction - fraction) < 0.001f) {
                return mode;
            }
        }
        return TALL;
    }

    public String displayName() {
        switch (this) {
            case FULL:
                return "全屏";
            case TALL:
                return "75%";
            case HALF:
                return "半屏";
            default:
                return "1/4 屏";
        }
    }
}
