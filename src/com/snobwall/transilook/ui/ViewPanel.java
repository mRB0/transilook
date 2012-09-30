package com.snobwall.transilook.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.Immutable;

import com.snobwall.transilook.osm.BoundingBox;
import com.snobwall.transilook.osm.Mercator;
import com.snobwall.transilook.osm.SlippyUtil;

public class ViewPanel extends JPanel implements MapLayerObserver {

    private int zoom = 14;
    private double lat = 44.64363574997914, lon = -63.60092639923096;

    private boolean watchingMouse = false;
    private int mouseLastX, mouseLastY;
    
    private int lastWidth = -1, lastHeight = -1, lastZoom = -1;
    private BoundingBox lastBounds;
    
    @GuardedBy("AWT EDT")
    private HashMap<MapLayer, BufferedImage> cachedImages = new HashMap<MapLayer, BufferedImage>();

    @GuardedBy("AWT EDT")
    private HashMap<Dimension, Stack<BufferedImage>> imagePool = new HashMap<Dimension, Stack<BufferedImage>>();

    @GuardedBy("AWT EDT")
    private ArrayList<MapLayer> layers = new ArrayList<MapLayer>();
    
    public ViewPanel() {
        super();

        enableEvents(java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK | java.awt.AWTEvent.MOUSE_EVENT_MASK | java.awt.AWTEvent.MOUSE_WHEEL_EVENT_MASK);
    }

    private BufferedImage newImageFromPool(int w, int h) {
        Dimension d = new Dimension(w, h);
        
        Stack<BufferedImage> stack = imagePool.get(d);
        
        final BufferedImage img;
        
        if (stack != null && !stack.isEmpty()) {
            img = stack.pop();
            img.getGraphics().clearRect(0, 0, w, h);
        } else {
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }
        
        return img;
    }
    
    private void returnImageToPool(BufferedImage img) {
        Dimension d = new Dimension(img.getWidth(), img.getHeight());
        
        Stack<BufferedImage> stack = imagePool.get(d);
        
        if (stack == null) {
            stack = new Stack<BufferedImage>();
            imagePool.put(d, stack);
        }
        
        stack.push(img);
    }
    
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(Color.white);
        g.fillRect(0, 0, getWidth(), getHeight());
        
        updateBounds(false);
        
        for(MapLayer l : layers) {
            final BufferedImage writeImg;
            if (cachedImages.containsKey(l)) {
                writeImg = cachedImages.get(l);
            } else {
                writeImg = newImageFromPool(getWidth(), getHeight());
                l.paintLayer(writeImg.getGraphics(), getWidth(), getHeight());
                cachedImages.put(l, writeImg);
            }
            
            g.drawImage(writeImg, 0, 0, null);
        }

        g.setColor(Color.red);
        g.fillOval(getWidth() / 2, getHeight() / 2, 5, 5);
    }

    private boolean updateBounds(boolean force) {
        double mercY = Mercator.mercY(lat);
        double mercX = lon;

        double mercUnitsPerPixel = SlippyUtil.mercUnitsPerPixel(zoom);
        
        double tlMercX = mercX - mercUnitsPerPixel * (getWidth() / 2);
        double tlMercY = mercY + mercUnitsPerPixel * (getHeight() / 2);
        double brMercX = tlMercX + mercUnitsPerPixel * getWidth();
        double brMercY = tlMercY - mercUnitsPerPixel * getHeight();
        
        BoundingBox windowBoundingBox = new BoundingBox(tlMercY, brMercY, brMercX, tlMercX);
        
        boolean needsUpdate = force ||
                lastWidth != getWidth() || 
                lastHeight != getHeight() ||
                lastZoom != zoom ||
                !windowBoundingBox.equals(lastBounds);
        
        if (needsUpdate) {
            for(BufferedImage img : cachedImages.values()) {
                returnImageToPool(img);
            }
            cachedImages.clear();
            
            lastWidth = getWidth();
            lastHeight = getHeight();
            lastZoom = zoom;
            lastBounds = windowBoundingBox;
            
            // bounds changed, let's tell everyone
            for(MapLayer l : layers) {
                l.updateLayerBounds(getWidth(), getHeight(), windowBoundingBox, zoom);
            }
        }
        
        return needsUpdate;
    }

    //
    // Map layer management
    //
    
    @Override
    public void invalidateLayer(final MapLayer layer) {
        SwingUtilities.invokeLater(new Runnable() {
            
            @Override
            public void run() {
                BufferedImage image = cachedImages.remove(layer);
                returnImageToPool(image);
                
                repaint();
            }
        });
    }
    
    public void addLayer(final MapLayer layer) {
        SwingUtilities.invokeLater(new Runnable() {
            
            @Override
            public void run() {
                layers.add(layer);
                layer.registerLayerObserver(ViewPanel.this);
                updateBounds(true);
                //layer.updateLayerBounds(getWidth(), getHeight(), , lastZoom);
                repaint();
            }
        });
    }
    
    //
    // Listeners
    //
    
    @Override
    protected void processMouseEvent(MouseEvent e) {
        super.processMouseEvent(e);

        if (e.getButton() == 1 && e.getID() == MouseEvent.MOUSE_PRESSED && !watchingMouse) {
            mouseLastX = e.getX();
            mouseLastY = e.getY();
            watchingMouse = true;
        } else if (e.getButton() == 1 && e.getID() == MouseEvent.MOUSE_RELEASED && watchingMouse) {
            watchingMouse = false;
        }
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
        super.processMouseMotionEvent(e);

        if (watchingMouse) {
            double deltaX = (e.getX() - mouseLastX) * SlippyUtil.mercUnitsPerPixel(zoom);
            lon = Mercator.unmercX(Mercator.mercX(lon) - deltaX);
            double deltaY = (e.getY() - mouseLastY) * SlippyUtil.mercUnitsPerPixel(zoom);
            lat = Mercator.unmercY(Mercator.mercY(lat) + deltaY);

            mouseLastX = e.getX();
            mouseLastY = e.getY();

            repaint();
        }
    }

    @Override
    protected void processMouseWheelEvent(MouseWheelEvent e) {
        super.processMouseWheelEvent(e);
        if (!watchingMouse) {
            int rot = e.getWheelRotation();
            zoom -= (rot / Math.abs(rot));
            repaint();
        }
    }
    
    //
    // Inner classes
    //

    @Immutable
    public static class TileRef {
        public final int x, y, zoom;

        public TileRef(int x, int y, int zoom) {
            super();
            this.x = x;
            this.y = y;
            this.zoom = zoom;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + x;
            result = prime * result + y;
            result = prime * result + zoom;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            TileRef other = (TileRef) obj;
            if (x != other.x)
                return false;
            if (y != other.y)
                return false;
            if (zoom != other.zoom)
                return false;
            return true;
        }

    }
}
