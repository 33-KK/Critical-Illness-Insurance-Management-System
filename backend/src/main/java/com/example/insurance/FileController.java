package com.example.insurance;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.insurance.Domain.StoredFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@Tag(name = "文件管理")
@RequiredArgsConstructor
public class FileController {
  private final Store db;
  private final Access access;

  @Value("${app.upload-dir:./uploads}")
  private String directory;

  private Path root() throws IOException {
    Path root = Path.of(directory).toAbsolutePath().normalize();
    Files.createDirectories(root);
    return root;
  }

  private Path path(StoredFile file) throws IOException {
    Path root = root(), path = root.resolve(file.getStorageName()).normalize();
    if (!path.startsWith(root)) throw Api.forbidden();
    return path;
  }

  private Map<String, Object> view(StoredFile f) {
    return Map.of(
        "id",
        f.getId(),
        "originalName",
        f.getOriginalName(),
        "size",
        f.getSize(),
        "contentType",
        f.getContentType(),
        "uploaderName",
        f.getUploaderName(),
        "createdAt",
        f.getCreatedAt());
  }

  private StoredFile allowed(Long id) {
    StoredFile file = Access.found(db.files.selectById(id), "文件");
    if (!"ADMIN".equals(access.user().getRole())
        && !access.user().getId().equals(file.getUploaderId())) throw Api.forbidden();
    return file;
  }

  @GetMapping
  @Operation(summary = "查询可管理文件")
  public Api<?> list() {
    access.require("file:manage");
    QueryWrapper<StoredFile> q = new QueryWrapper<>();
    if (!"ADMIN".equals(access.user().getRole())) q.eq("uploader_id", access.user().getId());
    return Api.ok(db.files.selectList(q.orderByDesc("id")).stream().map(this::view).toList());
  }

  @PostMapping
  @Operation(
      summary = "上传文件",
      description =
          "multipart/form-data 的 file 字段。支持 10 MB 以内 PDF、PNG、JPG、DOCX、XLSX、UTF-8 TXT，服务端校验实际文件类型。")
  @Transactional
  public Api<?> upload(@RequestParam("file") MultipartFile upload) throws IOException {
    access.require("file:manage");
    if (upload.isEmpty() || upload.getSize() > 10 * 1024 * 1024) throw Api.bad("请选择 10 MB 以内的非空文件");
    String name = Objects.toString(upload.getOriginalFilename(), "file").replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
    if (name.isBlank() || name.length() > 200) throw Api.bad("文件名为空或超过 200 个字符");
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    byte[] bytes = upload.getBytes();
    String type = validateType(ext, bytes);
    StoredFile f = new StoredFile();
    f.setOriginalName(name);
    f.setStorageName(UUID.randomUUID() + "." + ext);
    f.setContentType(type);
    f.setSize((long) bytes.length);
    f.setUploaderId(access.user().getId());
    f.setUploaderName(access.user().getDisplayName());
    f.setCreatedAt(LocalDateTime.now());
    Path stored = path(f);
    Files.write(stored, bytes, StandardOpenOption.CREATE_NEW);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED)
              try {
                Files.deleteIfExists(stored);
              } catch (IOException ignored) {
              }
          }
        });
    db.files.insert(f);
    access.log("FILE_UPLOAD", f.getOriginalName(), "上传 " + f.getSize() + " 字节");
    return Api.ok(view(f));
  }

  private String validateType(String ext, byte[] bytes) throws IOException {
    boolean valid;
    String type;
    switch (ext) {
      case "pdf" -> {
        valid =
            bytes.length > 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
        type = "application/pdf";
      }
      case "png" -> {
        valid =
            bytes.length > 8
                && (bytes[0] & 255) == 137
                && bytes[1] == 80
                && bytes[2] == 78
                && bytes[3] == 71;
        type = "image/png";
      }
      case "jpg", "jpeg" -> {
        valid =
            bytes.length > 3
                && (bytes[0] & 255) == 255
                && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255;
        type = "image/jpeg";
      }
      case "txt" -> {
        try {
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .decode(java.nio.ByteBuffer.wrap(bytes));
          valid = true;
          for (byte b : bytes) if (b == 0) valid = false;
        } catch (CharacterCodingException e) {
          valid = false;
        }
        type = "text/plain";
      }
      case "docx", "xlsx" -> {
        valid = false;
        int entries = 0;
        long expanded = 0;
        boolean types = false, document = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
          ZipEntry entry;
          byte[] buffer = new byte[8192];
          while ((entry = zip.getNextEntry()) != null) {
            if (++entries > 2000) throw Api.bad("压缩包条目过多");
            if (entry.getName().equals("[Content_Types].xml")) types = true;
            if (entry
                .getName()
                .equals(ext.equals("docx") ? "word/document.xml" : "xl/workbook.xml"))
              document = true;
            int read;
            while ((read = zip.read(buffer)) != -1) {
              expanded += read;
              if (expanded > 40 * 1024 * 1024) throw Api.bad("文档展开后过大");
            }
          }
          valid = types && document;
        }
        type =
            ext.equals("docx")
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
      }
      default -> throw Api.bad("仅支持 PDF、PNG、JPG、DOCX、XLSX、UTF-8 TXT 文件");
    }
    if (!valid) throw Api.bad("文件实际内容与扩展名不符");
    return type;
  }

  @GetMapping("/{id}/download")
  @Operation(summary = "下载文件", description = "管理员可下载全部文件，其他用户只可下载自己上传的文件。")
  public ResponseEntity<Resource> download(@PathVariable Long id) throws IOException {
    access.require("file:manage");
    StoredFile file = allowed(id);
    Path path = path(file);
    if (!Files.isRegularFile(path)) throw Api.missing("文件内容");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.getContentType()))
        .contentLength(file.getSize())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(file.getOriginalName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(new FileSystemResource(path));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "删除文件", description = "管理员可删除全部文件，其他用户只可删除自己上传的文件。")
  @Transactional
  public Api<?> delete(@PathVariable Long id) throws IOException {
    access.require("file:manage");
    StoredFile file = allowed(id);
    Path path = path(file);
    db.files.deleteById(id);
    access.log("FILE_DELETE", file.getOriginalName(), "删除文件");
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              Files.deleteIfExists(path);
            } catch (IOException e) {
              org.slf4j.LoggerFactory.getLogger(FileController.class)
                  .warn("Unable to remove unreferenced file {}", path, e);
            }
          }
        });
    return Api.ok();
  }
}
