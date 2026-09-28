package com.it.greendeer.chat;

import com.it.greendeer.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 回复音频落盘：{audioDir}/{folder}/{fileName}，返回相对路径供回放/排查。
 */
@Slf4j
@Component
public class AudioStore {

    private final Path root;

    public AudioStore(AppProperties props) {
        this.root = Paths.get(props.getAudioDir()).toAbsolutePath().normalize();
    }

    public synchronized String save(String folder, String fileName, byte[] audio) {
        try {
            String safeFolder = (folder == null || folder.isBlank()) ? "default" : folder.replaceAll("[^a-zA-Z0-9_-]", "_");
            Path dir = root.resolve(safeFolder);
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName);
            Files.write(file, audio);
            return root.relativize(file).toString().replace('\\', '/');
        } catch (IOException e) {
            log.warn("音频落盘失败 folder={} file={}: {}", folder, fileName, e.getMessage());
            return null;
        }
    }
}
