package com.snobwall.transilook.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import net.jcip.annotations.GuardedBy;

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
    private ArrayList<MapLayer> layers = new ArrayList<MapLayer>();
    
    public ViewPanel() {
        super();

        enableEvents(java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK | java.awt.AWTEvent.MOUSE_EVENT_MASK | java.awt.AWTEvent.MOUSE_WHEEL_EVENT_MASK);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        g.setColor(Color.white);
        g.fillRect(0, 0, getWidth(), getHeight());
        
        updateBounds(false);
        
        for(MapLayer l : layers) {
            l.paintLayer(g, getWidth(), getHeight());
        }

        g.setColor(Color.red);
        g.fillOval(getWidth() / 2, getHeight() / 2, 5, 5);
    }

    private void updateBounds(boolean force) {
        double mercY = Mercator.mercY(lat);
        double mercX = lon;

        double mercUnitsPerPixel = SlippyUtil.mercUnitsPerPixel(zoom);
        
        double tlMercX = mercX - mercUnitsPerPixel * (getWidth() / 2);
        double tlMercY = mercY + mercUnitsPerPixel * (getHeight() / 2);
        double brMercX = tlMercX + mercUnitsPerPixel * getWidth();
        double brMercY = tlMercY - mercUnitsPerPixel * getHeight();
        
        BoundingBox windowBoundingBox = new BoundingBox(tlMercY, brMercY, brMercX, tlMercX);
        
        if (force ||
                lastWidth != getWidth() || 
                lastHeight != getHeight() ||
                lastZoom != zoom ||
                !windowBoundingBox.equals(lastBounds)) {
            
            lastWidth = getWidth();
            lastHeight = getHeight();
            lastZoom = zoom;
            lastBounds = windowBoundingBox;
            
            // bounds changed, let's tell everyone
            for(MapLayer l : layers) {
                l.updateLayerBounds(getWidth(), getHeight(), windowBoundingBox, zoom);
            }
        }
    }

    //
    // Map layer management
    //
    
    @Override
    public void invalidateLayer(MapLayer layer) {
        SwingUtilities.invokeLater(new Runnable() {
            
            @Override
            public void run() {
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
    
}
