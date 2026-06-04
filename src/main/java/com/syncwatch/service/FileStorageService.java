package com.syncwatch.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileStorageService {

    private Path storageDir;

    // fileId -> {path, contentType, originalName}
    private final Map<String, StoredFile> files = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() throws IOException {
        storageDir = Files.createTempDirectory("syncwatch-");
    }

    public StoredFile store(MultipartFile file) throws IOException {
        String fileId = UUID.randomUUID().toString();
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "video";
        String contentType = file.getContentType() != null ? file.getContentType() : "video/mp4";

        Path dest = storageDir.resolve(fileId);
        file.transferTo(dest);

        StoredFile stored = new StoredFile(fileId, dest, contentType, originalName);
        files.put(fileId, stored);
        return stored;
    }

    public StoredFile get(String fileId) {
        return files.get(fileId);
    }

    public record StoredFile(String id, Path path, String contentType, String name) {}
}
