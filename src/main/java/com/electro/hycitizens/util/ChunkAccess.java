package com.electro.hycitizens.util;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

public final class ChunkAccess {
    private ChunkAccess() {}

    /**
     * Replaces the {@code World#getChunkIfLoaded(long)} accessor removed in Hytale Server 0.7.0,
     * where chunk lookups moved onto the world's chunk store. Returns null when the chunk is not
     * currently loaded.
     */
    public static WorldChunk getChunkIfLoaded(World world, long chunkIndex) {
        return world.getChunkStore().getChunkComponent(chunkIndex, WorldChunk.getComponentType());
    }
}
