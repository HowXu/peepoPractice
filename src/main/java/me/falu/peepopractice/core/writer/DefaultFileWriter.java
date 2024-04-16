package me.falu.peepopractice.core.writer;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.falu.peepopractice.PeepoPractice;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.io.FileUtils;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class DefaultFileWriter {
    public static final Path FOLDER = FabricLoader.getInstance().getConfigDir().resolve(PeepoPractice.MOD_NAME);
    public static final DefaultFileWriter INSTANCE = new DefaultFileWriter();

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void writeDefaultFiles() {
        try {
            FOLDER.toFile().mkdirs();
            List<String> resources = this.getResourceFiles();
            for (String resource : resources) {
                File destination = FOLDER.resolve(resource.replace("writer/", "")).toFile();
                InputStream stream = this.getClass().getClassLoader().getResourceAsStream(resource);
                if (stream != null) {
                    JsonParser parser = new JsonParser();
                    JsonElement defaultElement = parser.parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    if (!destination.exists() || destination.length() == 0) {
                        destination.getParentFile().mkdirs();
                        FileUtils.writeStringToFile(destination, PracticeWriter.GSON.toJson(defaultElement), StandardCharsets.UTF_8);
                    } else if (defaultElement.isJsonObject()) {
                        JsonElement element = parser.parse(FileUtils.readFileToString(destination, StandardCharsets.UTF_8));
                        if (element.isJsonObject()) {
                            JsonObject defaultObject = defaultElement.getAsJsonObject();
                            JsonObject object = element.getAsJsonObject();
                            boolean hasChanged = false;
                            for (Map.Entry<String, JsonElement> entry : defaultObject.entrySet()) {
                                if (!object.has(entry.getKey())) {
                                    object.add(entry.getKey(), entry.getValue());
                                    hasChanged = true;
                                }
                            }
                            if (hasChanged) {
                                destination.getParentFile().mkdirs();
                                FileUtils.writeStringToFile(destination, PracticeWriter.GSON.toJson(object), StandardCharsets.UTF_8);
                            }
                        }
                    }
                    stream.close();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> getResourceFiles() {
        Reflections reflections = new Reflections("writer", new ResourcesScanner());
        Set<String> resources = reflections.getResources(Pattern.compile(".*\\.json"));
        return Lists.newArrayList(resources.toArray(new String[0]));
    }

    public JsonElement getResourceJson(String resourceName) {
        JsonParser parser = new JsonParser();
        InputStream stream = this.getClass().getClassLoader().getResourceAsStream(resourceName);
        if (stream != null) {
            JsonElement element = parser.parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
            try {
                stream.close();
            } catch (IOException ignored) {
            }
            return element;
        }
        return null;
    }

    public File getResourceAsFile(String resourcePath, String name) {
        try {
            File tempFile = new File(System.getProperty("java.io.tmpdir") + "/" + name);
            if (tempFile.exists()) {
                return tempFile;
            } else {
                boolean ignored = tempFile.createNewFile();
            }
            InputStream in = this.getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (in == null) {
                PeepoPractice.LOGGER.error("Couldn't open input stream for datapack '{}'.", name);
                return null;
            }
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            in.close();
            return tempFile;
        } catch (IOException e) {
            PeepoPractice.LOGGER.error("Couldn't get resource '{}' as file:", resourcePath, e);
        }
        return null;
    }
}
