package com.nankai.yuqing.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 通用系统设置，JSON 值存入 H2，避免重启后回到硬编码默认值。 */
@Entity
@Table(name = "system_settings")
public class SystemSetting {

    @Id
    @Column(name = "config_key", length = 80)
    private String key;

    @Lob
    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public SystemSetting() {}

    public SystemSetting(String key, String value) {
        this.key = key;
        this.value = value;
        this.updatedAt = LocalDateTime.now();
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
