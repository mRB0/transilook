package com.snobwall.transilook.ui;

import java.awt.image.BufferedImage;

import com.google.common.base.Optional;
import com.snobwall.transilook.osm.BoundingBox;

/**
 * These methods should all be called from the AWT Event Dispatch Thread
 * 
 * @author mrb
 *
 */
public interface MapLayer {
    public void registerLayerObserver(MapLayerObserver observer);
    public void unregisterLayerObserver(MapLayerObserver observer);
    
    public void updateLayerBounds(int width, int height, BoundingBox mercatorBounds, int zoom);
    
    public Optional<BufferedImage> getLatestLayerImage();
}
