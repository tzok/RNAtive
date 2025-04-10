package pl.poznan.put.api.dto;

import java.util.Base64;

public record FileData(String name, String content, boolean isBinary) {
    // Constructor with default isBinary=false for backward compatibility
    public FileData(String name, String content) {
        this(name, content, false);
    }
    
    // Helper method to get content as bytes
    public byte[] getContentBytes() {
        if (isBinary) {
            return Base64.getDecoder().decode(content);
        } else {
            return content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
