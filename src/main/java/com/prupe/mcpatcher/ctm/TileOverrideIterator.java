package com.prupe.mcpatcher.ctm;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.util.IIcon;

import com.prupe.mcpatcher.mal.block.BlockStateMatcher;

import jss.notfine.config.MCPatcherForgeConfig;

abstract public class TileOverrideIterator implements Iterator<TileOverride> {

    private final Map<Block, List<BlockStateMatcher>> allBlockOverrides;
    private final Map<String, List<TileOverride>> allTileOverrides;

    protected IIcon currentIcon;

    private List<BlockStateMatcher> blockOverrides;
    private List<TileOverride> tileOverrides;
    private final Set<TileOverride> skipOverrides = new HashSet<>();

    private RenderBlockState renderBlockState;
    private int blockPos;
    private int iconPos;
    private boolean foundNext;
    private TileOverride nextOverride;
    private TileOverride lastMatchedOverride;

    protected TileOverrideIterator(Map<Block, List<BlockStateMatcher>> allBlockOverrides,
        Map<String, List<TileOverride>> allTileOverrides) {
        this.allBlockOverrides = allBlockOverrides;
        this.allTileOverrides = allTileOverrides;
    }

    synchronized void clear() {
        currentIcon = null;
        blockOverrides = null;
        tileOverrides = null;
        nextOverride = null;
        lastMatchedOverride = null;
        skipOverrides.clear();
    }

    private synchronized void resetForNextPass() {
        blockOverrides = null;
        tileOverrides = allTileOverrides.get(currentIcon.getIconName());
        blockPos = 0;
        iconPos = 0;
        foundNext = false;
    }

    @Override
    public synchronized boolean hasNext() {
        if (foundNext) {
            return true;
        }
        if (tileOverrides != null) {
            while (iconPos < tileOverrides.size()) {
                if (checkOverride(tileOverrides.get(iconPos++))) {
                    renderBlockState.setFilter(null);
                    return true;
                }
            }
        }
        if (blockOverrides != null) {
            while (blockPos < blockOverrides.size()) {
                BlockStateMatcher matcher = blockOverrides.get(blockPos++);
                if (renderBlockState.match(matcher) && checkOverride((TileOverride) matcher.getData())) {
                    renderBlockState.setFilter(matcher);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public synchronized TileOverride next() {
        if (!foundNext) {
            throw new IllegalStateException("next called before hasNext() == true");
        }
        foundNext = false;
        return nextOverride;
    }

    @Override
    public synchronized void remove() {
        throw new UnsupportedOperationException("remove not supported");
    }

    private synchronized boolean checkOverride(TileOverride override) {
        if (override != null && !override.isDisabled() && !skipOverrides.contains(override)) {
            foundNext = true;
            nextOverride = override;
            return true;
        } else {
            return false;
        }
    }

    public synchronized TileOverride go(RenderBlockState renderBlockState, IIcon origIcon) {
        this.renderBlockState = renderBlockState;
        renderBlockState.setFilter(null);
        currentIcon = origIcon;
        blockOverrides = allBlockOverrides.get(renderBlockState.getBlock());
        tileOverrides = allTileOverrides.get(origIcon.getIconName());
        blockPos = 0;
        iconPos = 0;
        foundNext = false;
        nextOverride = null;
        lastMatchedOverride = null;
        skipOverrides.clear();

        pass: for (int pass = 0; pass < MCPatcherForgeConfig.ConnectedTextures.maxRecursion; pass++) {
            while (hasNext()) {
                TileOverride override = next();
                IIcon newIcon = getTile(override, renderBlockState, origIcon);
                if (newIcon != null) {
                    lastMatchedOverride = override;
                    skipOverrides.add(override);
                    currentIcon = newIcon;
                    resetForNextPass();
                    continue pass;
                }
            }
            break;
        }
        return lastMatchedOverride;
    }

    public synchronized IIcon getIcon() {
        return currentIcon;
    }

    abstract protected IIcon getTile(TileOverride override, RenderBlockState renderBlockState, IIcon origIcon);

    public static final class IJK extends TileOverrideIterator {

        IJK(Map<Block, List<BlockStateMatcher>> blockOverrides, Map<String, List<TileOverride>> tileOverrides) {
            super(blockOverrides, tileOverrides);
        }

        @Override
        protected synchronized IIcon getTile(TileOverride override, RenderBlockState renderBlockState, IIcon origIcon) {
            return override.getTileWorld(renderBlockState, origIcon);
        }
    }

    public static final class Metadata extends TileOverrideIterator {

        Metadata(Map<Block, List<BlockStateMatcher>> blockOverrides, Map<String, List<TileOverride>> tileOverrides) {
            super(blockOverrides, tileOverrides);
        }

        @Override
        protected synchronized IIcon getTile(TileOverride override, RenderBlockState renderBlockState, IIcon origIcon) {
            return override.getTileHeld(renderBlockState, origIcon);
        }
    }
}
