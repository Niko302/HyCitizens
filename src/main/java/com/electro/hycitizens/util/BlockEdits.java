package com.electro.hycitizens.util;

import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Block edits for Hytale Server 0.7.0+, where {@code World#setBlock} and {@code World#breakBlock}
 * were removed and block mutation moved onto {@link BlockOperations} against the world's chunk store.
 *
 * <p>Both methods must be called on the owning world's thread.
 */
public final class BlockEdits {
    private BlockEdits() {}

    /**
     * Replaces the removed {@code World#setBlock(int, int, int, String)}.
     *
     * @return true when the block was changed, false when the block type is unknown or the chunk
     *         section is not loaded
     */
    public static boolean setBlock(World world, int x, int y, int z, String blockId, int settings) {
        BlockTypeAssetMap<String, BlockType> assetMap = BlockType.getAssetMap();
        int blockIndex = assetMap.getIndex(blockId);
        if (blockIndex == Integer.MIN_VALUE) {
            return false;
        }

        BlockType blockType = assetMap.getAsset(blockIndex);
        if (blockType == null) {
            return false;
        }

        return setBlock(world, x, y, z, blockIndex, blockType, settings);
    }

    /**
     * Replaces the removed {@code World#breakBlock(int, int, int, int)} by clearing the block,
     * which is how the server's own systems remove blocks on 0.7.0.
     *
     * @return true when the block was cleared, false when the chunk section is not loaded
     */
    public static boolean breakBlock(World world, int x, int y, int z, int settings) {
        return setBlock(world, x, y, z, BlockType.EMPTY_ID, BlockType.EMPTY, settings);
    }

    private static boolean setBlock(World world, int x, int y, int z, int blockIndex, BlockType blockType, int settings) {
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return false;
        }

        return BlockOperations.setBlock(chunkStore, sectionRef, x, y, z, blockIndex, blockType, 0, 0, settings);
    }
}
