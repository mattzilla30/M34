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
        if (saved.size() > 10000 || liked.size() > 10000 || groups.size() > 1000)
            throw new IllegalStateException("Library exceeds portable export limits");
        JSONArray artwork = new JSONArray();
        JSONArray likes = new JSONArray();
        JSONObject collections = new JSONObject();
        int skipped = 0;
        for (Catalog.Art art : saved) {
            if (art.id.matches("r34-[0-9]+")) {
                JSONObject entry = Library.encode(art);
                entry.put("preview", portableMedia(art.preview));
                entry.put("original", portableMedia(art.original));
                artwork.put(entry);
            }
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
        String document = new JSONObject().put("format", "M34-library").put("version", 1)
            .put("artwork", artwork).put("likes", likes).put("collections", collections)
            .put("skippedLegacyReferences", skipped).toString(2);
        if (document.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 8 * 1024 * 1024)
            throw new IllegalStateException("Library exceeds portable export size");
        return document;
    }

    private static String portableMedia(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            java.net.URI uri = new java.net.URI(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null)
                return "";
            host = host.toLowerCase(java.util.Locale.ROOT);
            if (!(host.equals("rule34.xxx") || host.endsWith(".rule34.xxx"))) return "";
            if (uri.getRawQuery() != null) for (String part : uri.getRawQuery().split("&")) {
                String name = java.net.URLDecoder.decode(part.split("=", 2)[0], "UTF-8").toLowerCase(java.util.Locale.ROOT);
                if (java.util.Set.of("api_key", "user_id", "password", "token", "access_token", "auth", "key").contains(name)) return "";
            }
            return value;
        } catch (Exception error) { return ""; }
    }
}
