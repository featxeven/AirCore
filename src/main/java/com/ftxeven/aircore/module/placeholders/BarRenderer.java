package com.ftxeven.aircore.module.placeholders;

import com.ftxeven.aircore.module.placeholders.PlaceholdersConfig.Bar;

final class BarRenderer {

    private BarRenderer() {
    }

    static String render(Bar bar, double current, double max) {
        int segments = Math.max(1, bar.segments());
        double ratio = max > 0 ? Math.clamp(current / max, 0.0, 1.0) : 0.0;
        int filled = (int) Math.round(ratio * segments);

        StringBuilder out = new StringBuilder();
        out.append(bar.filledColor()).append(bar.filledChar().repeat(filled));
        out.append(bar.emptyColor()).append(bar.emptyChar().repeat(segments - filled));
        return out.toString();
    }
}