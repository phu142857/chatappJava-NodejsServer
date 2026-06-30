package com.example.chatappjava.models;

import androidx.annotation.NonNull;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Objects;

import com.example.chatappjava.constants.ModelFields;

/**
 * Base model class that provides common functionality for all models
 * Eliminates duplicate JSON parsing and validation logic
 */
public abstract class BaseModel {
    protected String id;
    protected long createdAt;
    protected long updatedAt;
    protected boolean isActive = true;
    
    // Constructors
    public BaseModel() {}
    
    // Common JSON parsing methods
    protected static String parseString(JSONObject json, String field, String defaultValue) {
        return json.optString(field, defaultValue);
    }
    
    protected static String parseStringWithFallback(JSONObject json, String primaryField, String fallbackField, String defaultValue) {
        String value = json.optString(primaryField, null);
        if (value == null || value.isEmpty()) {
            value = json.optString(fallbackField, defaultValue);
        }
        return value;
    }
    
    protected static long parseTimestamp(JSONObject json, String field, long defaultValue) {
        try {
            if (!json.has(field) || json.isNull(field)) return defaultValue;
            Object value = json.get(field);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            if (value instanceof String) {
                String iso = (String) value;
                if (iso.isEmpty()) return defaultValue;
                try {
                    return java.time.Instant.parse(iso).toEpochMilli();
                } catch (Throwable t) {
                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        java.util.Date d = sdf.parse(iso);
                        return d != null ? d.getTime() : defaultValue;
                    } catch (Throwable ignored) {
                        return defaultValue;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return defaultValue;
    }
    
    protected static boolean parseBoolean(JSONObject json, String field, boolean defaultValue) {
        return json.optBoolean(field, defaultValue);
    }
    
    protected static int parseInt(JSONObject json, String field, int defaultValue) {
        return json.optInt(field, defaultValue);
    }
    
    // Common JSON building methods
    protected void putCommonFields(JSONObject json) throws JSONException {
        json.put(ModelFields.ID, id);
        json.put(ModelFields.CREATED_AT, createdAt);
        json.put(ModelFields.UPDATED_AT, updatedAt);
        json.put(ModelFields.IS_ACTIVE, isActive);
    }
    
    // Template method for subclasses to implement their specific JSON parsing
    public abstract void fromJson(JSONObject json) throws JSONException;
    
    // Template method for subclasses to implement their specific JSON building
    public abstract JSONObject toJson() throws JSONException;
    
    // Getters and setters for common fields
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    // Common utility methods
    public boolean isNew() {
        return id == null || id.isEmpty();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BaseModel baseModel = (BaseModel) obj;
        return Objects.equals(id, baseModel.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
    
    @NonNull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id='" + id + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", isActive=" + isActive +
                '}';
    }
}
