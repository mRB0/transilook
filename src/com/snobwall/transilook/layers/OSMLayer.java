package com.snobwall.transilook.layers;

import java.awt.Graphics;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.Immutable;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import com.google.common.base.Optional;
import com.snobwall.transilook.osm.BoundingBox;
import com.snobwall.transilook.osm.SlippyUtil;
import com.snobwall.transilook.ui.MapLayer;
import com.snobwall.transilook.ui.MapLayerObserver;

public class OSMLayer implements MapLayer {

    private AtomicReference<Optional<MapLayerObserver>> mapLayerObserver = new AtomicReference<Optional<MapLayerObserver>>(Optional.<MapLayerObserver>absent());
    private ExecutorService imageUpdateExecutor = Executors.newSingleThreadExecutor();

    private ExecutorService tileFetchPool = Executors.newFixedThreadPool(15);
    private ConcurrentHashMap<OSMLayer.TileRef, Optional<Image>> tiles = new ConcurrentHashMap<OSMLayer.TileRef, Optional<Image>>();
    
    @GuardedBy("AWT event dispatch thread")
    private LinkedList<Future<?>> loadTasks = new LinkedList<Future<?>>();
    private Optional<Future<?>> lastImageUpdateTask = Optional.<Future<?>>absent();
    
    @GuardedBy("AWT EDT")
    private int width, height, zoom;
    @GuardedBy("AWT EDT")
    private BoundingBox boundingBox;
    
    @Override
    public void registerLayerObserver(MapLayerObserver observer) {
        mapLayerObserver.set(Optional.of(observer));
    }

    @Override
    public void unregisterLayerObserver(MapLayerObserver observer) {
        mapLayerObserver.set(Optional.<MapLayerObserver>absent());
    }

    @Override
    public void updateLayerBounds(final int width, final int height, final BoundingBox mercatorBounds, final int zoom) {
        for(Future<?> task : loadTasks) {
            task.cancel(false);
        }
        loadTasks.clear();
        
        this.width = width;
        this.height = height;
        this.boundingBox = mercatorBounds;
        this.zoom = zoom;
        
        updateTiles(this.width, this.height, this.boundingBox, this.zoom);
    }

    private void updateTiles(final int width, final int height, final BoundingBox boundingBox, final int zoom) {
        if (lastImageUpdateTask.isPresent()) {
            lastImageUpdateTask.get().cancel(true);
        }

        lastImageUpdateTask = Optional.<Future<?>>of(imageUpdateExecutor.submit(new Runnable() {
            @Override
            public void run() {
                
                double mercX = (boundingBox.east + boundingBox.west) / 2;
                double mercY = (boundingBox.north + boundingBox.south) / 2;
                
                int[] centerTile = SlippyUtil.getTileNumber(mercX, mercY, zoom);

                BoundingBox tileBoundingBox = SlippyUtil.tile2boundingBox(centerTile[0], centerTile[1], zoom);

                // Find out where our location lives in this tile
                // and remember that screen y is inverted compared to mercator y
                double yRatio = (mercY - tileBoundingBox.north) / (tileBoundingBox.south - tileBoundingBox.north);
                double xRatio = (mercX - tileBoundingBox.west) / (tileBoundingBox.east - tileBoundingBox.west);

                int centerTileX = (int) (width / 2 - (xRatio * 256));
                int centerTileY = (int) (height / 2 - (yRatio * 256));

                int numXTilesOnLeft = (int) Math.ceil(centerTileX / 256.0);
                int leftTileIdx = centerTile[0] - numXTilesOnLeft;
                int leftTilePos = centerTileX - (numXTilesOnLeft * 256);

                int numYTilesOnTop = (int) Math.ceil(centerTileY / 256.0);
                int topTileIdx = centerTile[1] - numYTilesOnTop;
                int topTilePos = centerTileY - (numYTilesOnTop * 256);

                for (int y = 0; topTilePos + y * 256 < height; y++) {
                    for (int x = 0; leftTilePos + x * 256 < width; x++) {
                        OSMLayer.TileRef tile = new OSMLayer.TileRef(leftTileIdx + x, topTileIdx + y, zoom);
                        Optional<Image> imageRef = tiles.get(tile);
                        if (imageRef == null) {
                            fetchTile(tile);
                        }
                    }
                }
                
                Optional<MapLayerObserver> observer = mapLayerObserver.get();
                if (observer.isPresent()) {
                    observer.get().invalidateLayer(OSMLayer.this);
                }
                
            }
        }));
    }

    @Override
    public void paintLayer(Graphics g, int width, int height) {
        double mercX = (boundingBox.east + boundingBox.west) / 2;
        double mercY = (boundingBox.north + boundingBox.south) / 2;
        
        int[] centerTile = SlippyUtil.getTileNumber(mercX, mercY, zoom);

        BoundingBox tileBoundingBox = SlippyUtil.tile2boundingBox(centerTile[0], centerTile[1], zoom);

        // Find out where our location lives in this tile
        // and remember that screen y is inverted compared to mercator y
        double yRatio = (mercY - tileBoundingBox.north) / (tileBoundingBox.south - tileBoundingBox.north);
        double xRatio = (mercX - tileBoundingBox.west) / (tileBoundingBox.east - tileBoundingBox.west);

        int centerTileX = (int) (width / 2 - (xRatio * 256));
        int centerTileY = (int) (height / 2 - (yRatio * 256));

        int numXTilesOnLeft = (int) Math.ceil(centerTileX / 256.0);
        int leftTileIdx = centerTile[0] - numXTilesOnLeft;
        int leftTilePos = centerTileX - (numXTilesOnLeft * 256);

        int numYTilesOnTop = (int) Math.ceil(centerTileY / 256.0);
        int topTileIdx = centerTile[1] - numYTilesOnTop;
        int topTilePos = centerTileY - (numYTilesOnTop * 256);

        for (int y = 0; topTilePos + y * 256 < height; y++) {
            for (int x = 0; leftTilePos + x * 256 < width; x++) {
                OSMLayer.TileRef tile = new OSMLayer.TileRef(leftTileIdx + x, topTileIdx + y, zoom);
                Optional<Image> imageRef = tiles.get(tile);
                if (imageRef != null && imageRef.isPresent()) {
                    Image img = imageRef.get();
                    g.drawImage(img, leftTilePos + x * 256, topTilePos + y * 256, null);
                }
            }
        }

    }
    
    private void fetchTile(final OSMLayer.TileRef where) {
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
                    
                    SwingUtilities.invokeLater(new Runnable() {
                        
                        @Override
                        public void run() {
                            updateTiles(OSMLayer.this.width, OSMLayer.this.height, OSMLayer.this.boundingBox, OSMLayer.this.zoom);
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
