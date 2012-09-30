package com.snobwall.transilook.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.ByteArrayInputStream;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.Immutable;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import com.google.common.base.Optional;
import com.snobwall.transilook.osm.BoundingBox;
import com.snobwall.transilook.osm.Mercator;
import com.snobwall.transilook.osm.SlippyUtil;

public class ViewPanel extends JPanel {

    private ExecutorService tileFetchPool = Executors.newFixedThreadPool(15);

//    private AtomicReference<Image> imageRef = new AtomicReference<Image>();
    private ConcurrentHashMap<TileRef, Optional<Image>> tiles = new ConcurrentHashMap<ViewPanel.TileRef, Optional<Image>>();
    
    @GuardedBy("AWT event dispatch thread")
    private LinkedList<Future<?>> loadTasks = new LinkedList<Future<?>>();
    
    private int zoom = 14;
    private double lat = 44.64363574997914, lon = -63.60092639923096;

    private boolean watchingMouse = false;
    private int mouseLastX, mouseLastY;

    public ViewPanel() {
        super();

        enableEvents(java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK | java.awt.AWTEvent.MOUSE_EVENT_MASK | java.awt.AWTEvent.MOUSE_WHEEL_EVENT_MASK);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);

        for(Future<?> task : loadTasks) {
            task.cancel(false);
        }
        loadTasks.clear();
        
        g.setColor(Color.white);
        g.fillRect(0, 0, getWidth(), getHeight());

        double mercY = Mercator.mercY(lat);
        double mercX = lon;

        int[] centerTile = SlippyUtil.getTileNumber(mercX, mercY, zoom);

        BoundingBox tileBoundingBox = SlippyUtil.tile2boundingBox(centerTile[0], centerTile[1], zoom);

        // Find out where our location lives in this tile
        // and remember that screen y is inverted compared to mercator y
        double yRatio = (mercY - tileBoundingBox.north) / (tileBoundingBox.south - tileBoundingBox.north);
        double xRatio = (mercX - tileBoundingBox.west) / (tileBoundingBox.east - tileBoundingBox.west);

        int centerTileX = (int) (getWidth() / 2 - (xRatio * 256));
        int centerTileY = (int) (getHeight() / 2 - (yRatio * 256));

        int numXTilesOnLeft = (int) Math.ceil(centerTileX / 256.0);
        int leftTileIdx = centerTile[0] - numXTilesOnLeft;
        int leftTilePos = centerTileX - (numXTilesOnLeft * 256);

        int numYTilesOnTop = (int) Math.ceil(centerTileY / 256.0);
        int topTileIdx = centerTile[1] - numYTilesOnTop;
        int topTilePos = centerTileY - (numYTilesOnTop * 256);

        for (int y = 0; topTilePos + y * 256 < getHeight(); y++) {
            for (int x = 0; leftTilePos + x * 256 < getWidth(); x++) {
                TileRef tile = new TileRef(leftTileIdx + x, topTileIdx + y, zoom);
                Optional<Image> imageRef = tiles.get(tile);
                if (imageRef == null) {
                    fetchTile(tile);
                } else if (imageRef.isPresent()) {
                    Image img = imageRef.get();
                    g.drawImage(img, leftTilePos + x * 256, topTilePos + y * 256, null);
                }
            }
        }

        g.setColor(Color.red);
        g.fillOval(getWidth() / 2, getHeight() / 2, 5, 5);
    }

    private void fetchTile(final TileRef where) {
        Future<?> taskFuture = tileFetchPool.submit(new Runnable() {

            @Override
            public void run() {
                try {
                    if (tiles.putIfAbsent(where, Optional.<Image> absent()) != null) {
                        // another thread already started loading this tile: abort immediately
                        return;
                    }
                    HttpClient client = new HttpClient();

                    final String[] prefixes = new String[] { "a", "b", "c" };

                    // Create a method instance.
                    String tileURL = String.format("http://%s.tile.openstreetmap.org/%d/%d/%d.png", prefixes[new Random().nextInt(prefixes.length)],
                            where.zoom, where.x, where.y);
                    GetMethod method = new GetMethod(tileURL);

//                    System.err.println("Fetch " + tileURL);

                    int statusCode = client.executeMethod(method);
                    if (statusCode != HttpStatus.SC_OK) {
                        System.err.println("Method failed: " + method.getStatusLine());
                        throw new RuntimeException("Failed to fetch tile from " + tileURL);
                    }
                    // Read the response body.
                    byte[] responseBody = method.getResponseBody();

                    ByteArrayInputStream responseInputStream = new ByteArrayInputStream(responseBody);

                    Image img = ImageIO.read(responseInputStream);

                    tiles.put(where, Optional.of(img));

//                    System.err.println("Got " + tileURL);

                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            repaint();
                        }
                    });

                } catch (Throwable e) {
                    e.printStackTrace();
                    // fetchTile(where);
                    tiles.remove(where, Optional.<Image> absent());
                }
            }
        });
        
        loadTasks.add(taskFuture);
    }

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
            double deltaX = (e.getX() - mouseLastX) * (SlippyUtil.tileSpan(zoom) / 256.0);
            lon = Mercator.unmercX(Mercator.mercX(lon) - deltaX);
            double deltaY = (e.getY() - mouseLastY) * (SlippyUtil.tileSpan(zoom) / 256.0);
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
