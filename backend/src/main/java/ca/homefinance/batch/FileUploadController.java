package ca.homefinance.batch;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static ca.homefinance.constant.General.ALLOWED_SOURCES;

@RestController
@RequestMapping("/api/v1/transactionBatchUpload")
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job transactionJob;

    @PostMapping
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file,
                                             @RequestParam("sourceType") String sourceType) throws Exception {
        logger.info("File upload request received - File: {}, Size: {}, ContentType: {}, SourceType: {}", 
                   file.getOriginalFilename(), file.getSize(), file.getContentType(), sourceType);

        try {
            // Validate file
            if (file.isEmpty()) {
                logger.error("Uploaded file is empty");
                return ResponseEntity.badRequest().body("File cannot be empty");
            }

            // Validate file extension
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.trim().isEmpty()) {
                logger.error("File name is null or empty");
                return ResponseEntity.badRequest().body("File name cannot be null or empty");
            }

            String extension = StringUtils.getFilenameExtension(originalFilename);
            if (extension == null) {
                logger.error("File has no extension: {}", originalFilename);
                return ResponseEntity.badRequest().body("File must have an extension");
            }

            extension = extension.toLowerCase();
            logger.info("File extension: {}", extension);

            // Create temporary file
            logger.info("Creating temporary file with extension: {}", extension);
            Path tempFile = Files.createTempFile("upload-", "." + extension);
            logger.info("Temporary file created at: {}", tempFile.toAbsolutePath());

            // Transfer file to temporary location
            logger.info("Transferring file to temporary location");
            file.transferTo(tempFile);
            logger.info("File transferred successfully. File size: {} bytes", Files.size(tempFile));

            // Validate source type
            logger.info("Validating source type: {}", sourceType);
            if (sourceType == null || sourceType.trim().isEmpty()) {
                logger.error("Source type is null or empty");
                return ResponseEntity.badRequest().body("Source type cannot be null or empty");
            }

            String normalizedSourceType = sourceType.toLowerCase();
            logger.info("Normalized source type: {}", normalizedSourceType);
            logger.info("Allowed sources: {}", ALLOWED_SOURCES);

            if (!ALLOWED_SOURCES.contains(normalizedSourceType)) {
                logger.error("Invalid source type: {}. Allowed values: {}", normalizedSourceType, ALLOWED_SOURCES);
                return ResponseEntity.badRequest()
                        .body("Invalid sourceType. Allowed values: " + ALLOWED_SOURCES);
            }

            // Create job parameters
            logger.info("Creating job parameters");
            JobParameters params = new JobParametersBuilder()
                    .addString("filePath", tempFile.toAbsolutePath().toString())
                    .addString("sourceType", normalizedSourceType)
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            logger.info("Job parameters created - FilePath: {}, SourceType: {}", 
                       params.getString("filePath"), params.getString("sourceType"));

            // Launch batch job
            logger.info("Launching batch job");
            jobLauncher.run(transactionJob, params);
            logger.info("Batch job launched successfully");

            return ResponseEntity.ok("Batch job triggered for file: " + originalFilename);

        } catch (Exception e) {
            logger.error("Error during file upload processing", e);
            return ResponseEntity.internalServerError()
                    .body("Error processing file upload: " + e.getMessage());
        }
    }
}
