package edu.hitsz.sky.android;

final class PlayfieldViewport {
    float left, top, width, height, scale = 1f;

    void fit(float availableLeft, float availableTop, float availableWidth,
             float availableHeight, float worldWidth, float worldHeight) {
        scale = Math.max(0.001f, Math.min(availableWidth / worldWidth, availableHeight / worldHeight));
        width = worldWidth * scale;
        height = worldHeight * scale;
        left = availableLeft + (availableWidth - width) * 0.5f;
        top = availableTop + (availableHeight - height) * 0.5f;
    }

    boolean contains(float x, float y) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    float worldX(float x) { return (x - left) / scale; }
    float worldY(float y) { return (y - top) / scale; }
}
