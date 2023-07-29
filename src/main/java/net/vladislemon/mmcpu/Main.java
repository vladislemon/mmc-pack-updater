package net.vladislemon.mmcpu;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Main {
    private static final String LOCAL_INFO_FILE_NAME = "mmc-pack-updater.local";

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Usage: <cmd> <instance path> <url to modpack>");
            System.exit(1);
        }
        System.out.println("mmc-pack-updater start");
        Path instancePath = Paths.get(args[0]);
        System.out.println("Instance path: " + instancePath);
        URI modpackUri = asDirectory(args[1]);
        System.out.println("Modpack URI: " + modpackUri);
        URI remoteInstanceUri = asDirectory(removeTrailingSlash(args[1]) + "-instance");
        System.out.println("Remote instance URI: " + remoteInstanceUri);
        URI remoteInfoUri = remoteInstanceUri.resolve("mmc-pack-updater.remote");
        System.out.println("Remote info uri: " + remoteInfoUri);
        Path localInfoPath = instancePath.resolve(LOCAL_INFO_FILE_NAME);
        Map<String, String> localInfo = loadMap(localInfoPath);
        Map<String, String> remoteInfo = loadMap(remoteInfoUri);
        {
            Iterator<Map.Entry<String, String>> iterator = localInfo.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, String> entry = iterator.next();
                String fileName = entry.getKey();
                if (!remoteInfo.containsKey(fileName)) {
                    System.out.printf("Local %s not found in remote, removing\n", fileName);
                    Files.deleteIfExists(instancePath.resolve(fileName));
                    iterator.remove();
                }
            }
        }
        for (Map.Entry<String, String> entry : remoteInfo.entrySet()) {
            String fileName = entry.getKey();
            String remoteHash = entry.getValue();
            String localHash = localInfo.get(fileName);
            if (!remoteHash.equals(localHash)) {
                System.out.printf("Local %s is outdated, updating\n", fileName);
                URI uri = remoteInstanceUri.resolve(fileName);
                Path target = instancePath.resolve(fileName);
                try (InputStream inputStream = uri.toURL().openStream()) {
                    Files.createDirectories(target.getParent());
                    Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                }
                localInfo.put(fileName, remoteHash);
            } else {
                System.out.printf("Local %s is up to date\n", fileName);
            }
        }
        saveMap(localInfo, localInfoPath);
        System.out.println("mmc-pack-updater end");
    }

    private static Map<String, String> loadMap(Path path) {
        try {
            if (Files.notExists(path)) {
                Files.createFile(path);
            }
            return loadMap(Files.newInputStream(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, String> loadMap(URI uri) {
        try {
            return loadMap(uri.toURL().openStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, String> loadMap(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            Map<String, String> map = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    map.put(parts[0].trim(), parts[1].trim());
                }
            }
            return map;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void saveMap(Map<String, String> map, Path path) {
        try {
            saveMap(map, Files.newOutputStream(path, StandardOpenOption.TRUNCATE_EXISTING));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void saveMap(Map<String, String> map, OutputStream outputStream) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                writer.write(entry.getKey());
                writer.write('=');
                writer.write(entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static URI asDirectory(String uri) {
        return !uri.endsWith("/") ? URI.create(uri.concat("/")) : URI.create(uri);
    }

    private static String removeTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
