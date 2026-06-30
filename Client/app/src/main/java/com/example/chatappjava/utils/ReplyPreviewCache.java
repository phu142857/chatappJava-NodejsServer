package com.example.chatappjava.utils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReplyPreviewCache {
    private static final int MAX_ENTRIES = 200;
    private static final Map<String, String> idToThumb = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_ENTRIES;
                }
            }
    );

    public static void put(String replyToMessageId, String thumbUrlOrUri) {
        if (replyToMessageId == null || replyToMessageId.isEmpty()) return;
        if (thumbUrlOrUri == null || thumbUrlOrUri.isEmpty()) return;
        idToThumb.put(replyToMessageId, thumbUrlOrUri);
    }

    public static String get(String replyToMessageId) {
        if (replyToMessageId == null || replyToMessageId.isEmpty()) return null;
        return idToThumb.get(replyToMessageId);
    }
}


