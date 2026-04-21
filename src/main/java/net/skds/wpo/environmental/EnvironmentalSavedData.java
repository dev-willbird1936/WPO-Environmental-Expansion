package net.skds.wpo.environmental;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

public class EnvironmentalSavedData extends SavedData {

    private static final String DATA_NAME = "wpo_environmental_expansion";
    private static final String ABSORBED = "absorbed";
    private static final String DROUGHT = "drought";
    private static final String AMBIENT_WETNESS = "ambient_wetness";
    private static final SavedData.Factory<EnvironmentalSavedData> FACTORY =
        new SavedData.Factory<>(EnvironmentalSavedData::new, EnvironmentalSavedData::load, null);

    private final Long2IntOpenHashMap absorbedWater = new Long2IntOpenHashMap();
    private final transient Object2LongOpenHashMap<UUID> playerChunks = new Object2LongOpenHashMap<>();
    private final Long2LongOpenHashMap chunkLastVisit = new Long2LongOpenHashMap();
    private int droughtScore;
    private int ambientWetness;
    private transient long runtimeCursor;

    public EnvironmentalSavedData() {
        absorbedWater.defaultReturnValue(0);
        playerChunks.defaultReturnValue(Long.MIN_VALUE);
        chunkLastVisit.defaultReturnValue(Long.MIN_VALUE);
    }

    public static EnvironmentalSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static EnvironmentalSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EnvironmentalSavedData data = new EnvironmentalSavedData();
        data.droughtScore = tag.getInt(DROUGHT);
        data.ambientWetness = tag.getInt(AMBIENT_WETNESS);
        ListTag list = tag.getList(ABSORBED, Tag.TAG_COMPOUND);
        for (Tag element : list) {
            if (element instanceof CompoundTag entry) {
                int amount = entry.getInt("amount");
                if (amount > 0) {
                    data.absorbedWater.put(entry.getLong("pos"), amount);
                }
            }
        }
        if (tag.contains("chunkLastVisit", Tag.TAG_COMPOUND)) {
            CompoundTag cvTag = tag.getCompound("chunkLastVisit");
            for (String key : cvTag.getAllKeys()) {
                data.chunkLastVisit.put(Long.parseLong(key), cvTag.getLong(key));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(DROUGHT, droughtScore);
        tag.putInt(AMBIENT_WETNESS, ambientWetness);
        ListTag list = new ListTag();
        for (Long2IntMap.Entry entry : absorbedWater.long2IntEntrySet()) {
            if (entry.getIntValue() <= 0) {
                continue;
            }
            CompoundTag cell = new CompoundTag();
            cell.putLong("pos", entry.getLongKey());
            cell.putInt("amount", entry.getIntValue());
            list.add(cell);
        }
        tag.put(ABSORBED, list);
        CompoundTag cvTag = new CompoundTag();
        chunkLastVisit.forEach((chunkKey, tick) -> cvTag.putLong(String.valueOf(chunkKey), tick));
        tag.put("chunkLastVisit", cvTag);
        return tag;
    }

    public int getAbsorbed(BlockPos pos) {
        return absorbedWater.get(pos.asLong());
    }

    public int getDroughtScore() {
        return droughtScore;
    }

    public int getAmbientWetness() {
        return ambientWetness;
    }

    public boolean isDroughtActive() {
        return droughtScore >= EnvironmentalConfig.COMMON.droughtThreshold.get();
    }

    public void adjustDrought(int delta) {
        int next = Math.max(0, droughtScore + delta);
        if (next != droughtScore) {
            droughtScore = next;
            setDirty();
        }
    }

    public void setDroughtScore(int value) {
        int next = Math.max(0, value);
        if (next != droughtScore) {
            droughtScore = next;
            setDirty();
        }
    }

    public void adjustAmbientWetness(int delta, int cap) {
        int next = Math.max(0, Math.min(Math.max(0, cap), ambientWetness + delta));
        if (next != ambientWetness) {
            ambientWetness = next;
            setDirty();
        }
    }

    public void setAmbientWetness(int value, int cap) {
        int next = Math.max(0, Math.min(Math.max(0, cap), value));
        if (next != ambientWetness) {
            ambientWetness = next;
            setDirty();
        }
    }

    public void clearAmbientWetness() {
        if (ambientWetness != 0) {
            ambientWetness = 0;
            setDirty();
        }
    }

    public int addAbsorbed(BlockPos pos, int amount, int capacity) {
        if (amount <= 0 || capacity <= 0) {
            return 0;
        }
        long key = pos.asLong();
        int current = absorbedWater.get(key);
        int applied = Math.min(amount, Math.max(0, capacity - current));
        if (applied <= 0) {
            return 0;
        }
        absorbedWater.put(key, current + applied);
        setDirty();
        return applied;
    }

    public int consumeAbsorbed(BlockPos pos, int amount) {
        if (amount <= 0) {
            return 0;
        }
        long key = pos.asLong();
        int current = absorbedWater.get(key);
        int removed = Math.min(current, amount);
        if (removed <= 0) {
            return 0;
        }
        int remaining = current - removed;
        if (remaining > 0) {
            absorbedWater.put(key, remaining);
        } else {
            absorbedWater.remove(key);
        }
        setDirty();
        return removed;
    }

    public void clearAbsorbed(BlockPos pos) {
        if (absorbedWater.remove(pos.asLong()) != 0) {
            setDirty();
        }
    }

    public void setAbsorbed(BlockPos pos, int amount) {
        long key = pos.asLong();
        int next = Math.max(0, amount);
        if (next <= 0) {
            if (absorbedWater.remove(key) != 0) {
                setDirty();
            }
            return;
        }
        if (absorbedWater.put(key, next) != next) {
            setDirty();
        }
    }

    public long nextCursor(int delta) {
        long cursor = runtimeCursor;
        runtimeCursor += Math.max(1, delta);
        return cursor;
    }

    public boolean updatePlayerChunk(UUID playerId, long chunkKey) {
        long previous = playerChunks.put(playerId, chunkKey);
        chunkLastVisit.put(chunkKey, runtimeCursor);
        return previous != chunkKey;
    }

    public int sweepStaleAbsorbedWater(long currentTick) {
        int evictionThreshold = (int) (EnvironmentalConfig.COMMON.absorptionEvictionDays.get() * 24000L);
        int evicted = 0;
        long cutoff = currentTick - evictionThreshold;

        Long2IntOpenHashMap toRemove = null;
        for (Long2IntMap.Entry entry : absorbedWater.long2IntEntrySet()) {
            long blockKey = entry.getLongKey();
            int cx = BlockPos.getX(blockKey) >> 4;
            int cz = BlockPos.getZ(blockKey) >> 4;
            long chunkKey = ChunkPos.asLong(cx, cz);
            long lastVisit = chunkLastVisit.get(chunkKey);
            if (lastVisit != Long.MIN_VALUE && lastVisit < cutoff) {
                if (toRemove == null) {
                    toRemove = new Long2IntOpenHashMap();
                }
                toRemove.put(blockKey, entry.getIntValue());
            }
        }
        if (toRemove != null) {
            for (Long2IntMap.Entry entry : toRemove.long2IntEntrySet()) {
                absorbedWater.remove(entry.getLongKey());
                evicted++;
            }
        }
        if (evicted > 0) {
            setDirty();
        }
        return evicted;
    }
}
