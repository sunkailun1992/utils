package com.kellen.storage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 上传文件的类型认定与对象键：只收白名单里的 MIME，对象键按业务根目录与所有者隔离。
 */
final class UploadFiles {

    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> FILE_TYPES = Set.of("image", "video", "audio", "file");
    private static final Set<String> DOCUMENT_MIME_TYPES = Set.of(
            "application/pdf",
            "application/json",
            "text/plain",
            "text/markdown",
            "text/csv"
    );
    private static final Map<String, String> EXTENSION_MIME_TYPES = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("heic", "image/heic"),
            Map.entry("heif", "image/heif"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("json", "application/json"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("csv", "text/csv"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("m4v", "video/x-m4v"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("m4a", "audio/mp4")
    );

    private UploadFiles() {
    }

    /** 标准化 MIME 类型；未传时按扩展名推断，推断不出就拒绝。 */
    static String mimeType(String mimeType, String fileName) {
        if (hasText(mimeType)) {
            return mimeType.trim().toLowerCase(Locale.ROOT);
        }
        String inferred = EXTENSION_MIME_TYPES.get(rawExtension(fileName));
        if (hasText(inferred)) {
            return inferred;
        }
        throw new IllegalArgumentException("mimeType 不能为空，且无法从文件名推断");
    }

    /** 标准化文件类型：image/video/audio/file，未传或不认识时按 MIME 推断。 */
    static String fileType(String fileType, String mimeType) {
        if (hasText(fileType)) {
            String normalized = fileType.trim().toLowerCase(Locale.ROOT);
            if (FILE_TYPES.contains(normalized)) {
                return normalized;
            }
        }
        String mime = requiredText(mimeType, "mimeType");
        if (mime.startsWith("image/")) {
            return "image";
        }
        if (mime.startsWith("video/")) {
            return "video";
        }
        if (mime.startsWith("audio/")) {
            return "audio";
        }
        return "file";
    }

    /** 文件类型与 MIME 必须一致；file 只收文档类与纯文本 MIME。 */
    static void requireTypeMatchesMime(String fileType, String mimeType) {
        String type = requiredText(fileType, "fileType");
        String mime = requiredText(mimeType, "mimeType");
        boolean matched = switch (type) {
            case "image" -> mime.startsWith("image/");
            case "video" -> mime.startsWith("video/");
            case "audio" -> mime.startsWith("audio/");
            case "file" -> DOCUMENT_MIME_TYPES.contains(mime) || mime.startsWith("text/");
            default -> false;
        };
        if (!matched) {
            throw new IllegalArgumentException("fileType 与 mimeType 不匹配");
        }
    }

    /** 所有者目录前缀 {@code <baseDir>/<ownerScope>/}，只接受业务生成的相对路径。 */
    static String ownerPrefix(String baseDir, String ownerScope) {
        return relativePath(baseDir, "baseDir") + "/" + relativePath(ownerScope, "ownerScope") + "/";
    }

    /** 新对象键 {@code <所有者前缀><文件类型>/yyyy/MM/dd/<随机>.<扩展名>}。 */
    static String objectKey(String baseDir, String ownerScope, String fileType, String fileName, String mimeType) {
        return ownerPrefix(baseDir, ownerScope) + fileType + "/"
                + LocalDate.now().format(DATE_PATH_FORMATTER) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension(fileName, mimeType);
    }

    private static String extension(String fileName, String mimeType) {
        String raw = rawExtension(fileName);
        if (hasText(raw)) {
            return raw;
        }
        if ("image/jpeg".equals(mimeType)) {
            return "jpg";
        }
        if (mimeType != null && mimeType.contains("/")) {
            String subtype = mimeType.substring(mimeType.indexOf('/') + 1).replace("+", "-");
            String normalized = subtype.replaceAll("[^a-z0-9-]", "");
            if (hasText(normalized)) {
                return normalized;
            }
        }
        return "bin";
    }

    /** 文件名最后一段的扩展名，只留小写字母数字，超过 16 位视为没有。 */
    private static String rawExtension(String fileName) {
        if (!hasText(fileName)) {
            return "";
        }
        String normalized = fileName.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return extension.length() > 16 ? "" : extension;
    }

    private static String relativePath(String value, String name) {
        String normalized = requiredText(value, name)
                .replace('\\', '/')
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
        if (!hasText(normalized) || normalized.contains("..") || normalized.contains("//")) {
            throw new IllegalArgumentException(name + " 不合法");
        }
        return normalized;
    }

    private static String requiredText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
