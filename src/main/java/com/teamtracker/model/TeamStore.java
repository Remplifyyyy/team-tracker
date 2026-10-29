package com.teamtracker.model;

import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public final class TeamStore {
    private final ConcurrentHashMap<String, Teammate> teammates = new ConcurrentHashMap<>();
    // Nicknames teammates have chosen (only sent by players on 1.1.0+). Key = lowercase username.
    private final ConcurrentHashMap<String, String> nicknames = new ConcurrentHashMap<>();

    public void update(String username, double x, double y, double z, String dimension, long nowMs) {
        if (username == null || username.isBlank() || dimension == null || dimension.isBlank()) return;
        String key = username.toLowerCase(Locale.ROOT);
        PositionSample sample = new PositionSample(x, y, z, dimension, nowMs);
        teammates.compute(key, (ignored, existing) -> {
            if (existing == null) return new Teammate(username, sample);
            existing.update(sample);
            return existing;
        });
    }

    /** Empty / null nickname clears it so the real username is shown again. */
    public void setNickname(String username, String nickname) {
        if (username == null || username.isBlank()) return;
        String key = username.toLowerCase(Locale.ROOT);
        if (nickname == null || nickname.isBlank()) nicknames.remove(key);
        else nicknames.put(key, nickname);
    }

    /** The nickname if the teammate set one, otherwise their username. */
    public String displayName(Teammate teammate) {
        String nick = nicknames.get(teammate.username().toLowerCase(Locale.ROOT));
        return nick == null ? teammate.username() : nick;
    }

    public void remove(String username) {
        if (username == null) return;
        String key = username.toLowerCase(Locale.ROOT);
        teammates.remove(key);
        nicknames.remove(key);
    }

    public void removeStale(long nowMs, long staleMs) {
        teammates.entrySet().removeIf(entry -> {
            boolean stale = nowMs - entry.getValue().lastUpdateMs() > staleMs;
            if (stale) nicknames.remove(entry.getKey());
            return stale;
        });
    }

    public boolean contains(String username) {
        return username != null && teammates.containsKey(username.toLowerCase(Locale.ROOT));
    }

    public Collection<Teammate> values() {
        return teammates.values();
    }

    public int size() {
        return teammates.size();
    }

    public void clear() {
        teammates.clear();
        nicknames.clear();
    }
}
