package ca.homefinance.batch;


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

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job transactionJob;

    @PostMapping
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file,
                                             @RequestParam("sourceType") String sourceType) throws Exception {
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename()).toLowerCase();

        Path tempFile = Files.createTempFile("upload-", "." + extension);
        file.transferTo(tempFile);

        if (!ALLOWED_SOURCES.contains(sourceType.toLowerCase())) {
            return ResponseEntity.badRequest()
                    .body("Invalid sourceType. Allowed values: " + ALLOWED_SOURCES);
        }

        JobParameters params = new JobParametersBuilder()
                .addString("filePath", tempFile.toAbsolutePath().toString())
                .addString("sourceType", sourceType.toLowerCase())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(transactionJob, params);

        return ResponseEntity.ok("Batch job triggered for file: " + file.getOriginalFilename());
    }
}
