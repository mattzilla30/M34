// Copyright (C) 2026 mattzilla30
// SPDX-License-Identifier: GPL-3.0-or-later
package com.m34.viewer;

import org.json.JSONArray;
import org.json.JSONObject;

/** Credential-free bridge to MBooru. Does not read private API credential preferences. */
final class LibraryExport {
    final java.util.List<Catalog.Art> saved;
    final java.util.Set<String> liked;
    final java.util.Map<String, java.util.Set<String>> groups;

    // Snapshot references on the UI thread; immutable artwork values can be serialized off-thread.
    LibraryExport(Library library) {
        saved = new java.util.ArrayList<>(library.saved.values());
        liked = new java.util.LinkedHashSet<>(library.likes);
        groups = new java.util.LinkedHashMap<>();
        for (var entry : library.collections.entrySet())
            groups.put(entry.getKey(), new java.util.LinkedHashSet<>(entry.getValue()));
    }

    String encode() throws Exception {
        JSONArray artwork = new JSONArray();
        JSONArray likes = new JSONArray();
        JSONObject collections = new JSONObject();
        int skipped = 0;
        for (Catalog.Art art : saved) {
            if (art.id.matches("r34-[0-9]+")) artwork.put(Library.encode(art));
            else skipped++;
        }
        for (String id : liked) {
            if (id.matches("r34-[0-9]+")) likes.put(id);
            else skipped++;
        }
        for (var group : groups.entrySet()) {
            JSONArray members = new JSONArray();
            for (String id : group.getValue()) {
                if (id.matches("r34-[0-9]+")) members.put(id);
                else skipped++;
            }
            collections.put(group.getKey(), members);
        }
        return new JSONObject().put("format", "M34-library").put("version", 1)
            .put("artwork", artwork).put("likes", likes).put("collections", collections)
            .put("skippedLegacyReferences", skipped).toString(2);
    }
}
