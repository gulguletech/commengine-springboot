package com.commengine.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.commengine.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mirrors middleware/upload.js (multer-storage-cloudinary config). */
@Service
public class CloudinaryService {

    private static final Set<String> ALLOWED_FORMATS = Set.of(
            "jpg", "jpeg", "png", "webp", "mp4", "mov", "mp3", "wav", "m4a"
    );

    public record UploadResult(String url, String publicId, String resourceType) {}

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public UploadResult upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.unprocessable("No file uploaded");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType();
        boolean isVideo = contentType.startsWith("video/");
        boolean isAudio = contentType.startsWith("audio/");
        String resourceType = (isVideo || isAudio) ? "video" : "image"; // Cloudinary buckets audio under "video"

        String extension = extensionOf(file.getOriginalFilename());
        if (extension != null && !ALLOWED_FORMATS.contains(extension.toLowerCase())) {
            throw ApiException.unprocessable("Unsupported file format: " + extension);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "commengine",
                    "resource_type", resourceType
            ));
            return new UploadResult(
                    String.valueOf(result.get("secure_url")),
                    String.valueOf(result.get("public_id")),
                    String.valueOf(result.get("resource_type"))
            );
        } catch (IOException e) {
            throw new ApiException("File upload failed: " + e.getMessage(), org.springframework.http.HttpStatus.BAD_GATEWAY);
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null) return null;
        int idx = filename.lastIndexOf('.');
        return idx == -1 ? null : filename.substring(idx + 1);
    }
}
