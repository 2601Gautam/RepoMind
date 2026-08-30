package com.repomind.repomind.service.ingestion;

import com.repomind.repomind.utility.FileCloneUtil;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.apache.tomcat.util.http.fileupload.FileUtils.deleteDirectory;

@Service
@Slf4j
public class FileCloneService {

    @Autowired
    private FileCloneUtil fileCloneUtil;

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".java", ".kt", ".kts", ".gradle", ".pom",
            ".js", ".jsx", ".ts", ".tsx", ".mjs", ".cjs",
            ".py",
            ".c", ".cpp", ".cc", ".cxx", ".c++", ".h", ".hpp", ".hh", ".hxx",
            ".cs", ".go", ".rs", ".php", ".rb", ".swift", ".dart", ".scala",
            ".sh", ".bash", ".zsh", ".ps1",
            ".sql",
            ".html", ".css", ".scss", ".sass", ".less",
            ".json", ".jsonc", ".xml", ".yml", ".yaml",
            ".toml", ".ini", ".cfg", ".conf", ".properties",
            ".md", ".rst", ".txt",
            ".tf", ".tfvars", ".dockerfile"
    );
    private static final Set<String> SKIP_FOLDERS = Set.of(
            ".git", "node_modules", ".idea", ".vscode", ".settings",
            "build", "dist", "target", ".gradle", ".mvn", "out", "bin",
            "__pycache__", ".pytest_cache", ".mypy_cache", ".venv", "venv", "env",
            ".next", ".nuxt", ".angular", ".cache",
            "vendor", "coverage", ".nyc_output",
            "cmake-build-debug", "cmake-build-release", "CMakeFiles",
            ".yarn", ".pnpm-store", ".terraform",
            "logs", "log", "tmp", "temp",
            ".DS_Store", "$RECYCLE.BIN"
    );

    private static final Set<String> SUPPORTED_FILENAMES = Set.of(
            "Dockerfile",
            "Jenkinsfile",
            "Makefile",
            "Procfile",
            "README",
            "README.md",
            "LICENSE"
    );
    private static final long MAX_FILE_BYTES = 2000 * 1024;

    public Path cloneRepository(String githubUrl, String token) throws Exception {

        Path tempDir = Files.createTempDirectory("repomind-clone-");
        log.info("Cloning {} into {}", githubUrl, tempDir);

        try {

            var cmd = Git.cloneRepository()
                    .setURI(githubUrl)
                    .setDirectory(tempDir.toFile())
                    .setDepth(1);

            if (token != null && !token.isEmpty()) {

                cmd.setCredentialsProvider((
                        new UsernamePasswordCredentialsProvider(token, "")
                ));
            }

            cmd.call().close();
            log.info("Clone complete");
            return tempDir;

        } catch (Exception e) {
            deleteDirectory(tempDir);
            throw e;
        }
    }

    public List<ParsedFile> extractFiles(Path repoRoot) throws IOException {
        List<ParsedFile> result = new ArrayList<>();

        Files.walkFileTree(repoRoot, new SimpleFileVisitor<>(){

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (SKIP_FOLDERS.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs){
                try{
                    String name = file.getFileName().toString();
                    String ext = fileCloneUtil.getExtension(name);

                    if(!SUPPORTED_EXTENSIONS.contains(ext) && !SUPPORTED_FILENAMES.contains(name)) return FileVisitResult.CONTINUE;

                    if(attrs.size() > MAX_FILE_BYTES){
                        log.debug("Skipping large filee: {}",file);
                        return FileVisitResult.CONTINUE;
                    }

                    String content = Files.readString(file);

                    if(content.isBlank())return FileVisitResult.CONTINUE;

                    String relativePath = repoRoot.relativize(file).toString()
                            .replace("\\", "/");

                    result.add(new ParsedFile(relativePath, content, ext));
                } catch (IOException e) {
                    log.warn("Could not read {}: {}",file,e.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }

        });
        log.info("Extracted {} files from repo", result.size());
        return result;
    }
    public void deleteDirectory(Path dir){
        if (dir == null || !Files.exists(dir)) return;
        try{
            Files.walkFileTree(dir, new SimpleFileVisitor<>(){

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a)  {
                    try{
                        Files.delete(f);
                        log.info("file with path: {} deleted",f);
                    } catch (IOException e) {
                        log.warn("Could not delete {}: {}",f,e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.warn("Could not access file for deletion {}: {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) {
                    if (exc != null) {
                        log.warn("Issue iterating directory {}: {}", d, exc.getMessage());
                    }
                    try {
                        Files.delete(d);
                    } catch (IOException e) {
                        log.warn("Could not delete directory {}: {}", d, e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }catch (IOException e){
            log.warn("Partial cleanup failure: {}", e.getMessage());
        }
    }

    // ── Sync support ──────────────────────────────────────────────────
    public boolean isIngestable(String relativePath, long sizeBytes) {
        if (sizeBytes > MAX_FILE_BYTES) return false;
        for (String segment : relativePath.split("/")) {
            if (SKIP_FOLDERS.contains(segment)) return false;
        }
        String name = relativePath.contains("/")
                ? relativePath.substring(relativePath.lastIndexOf('/') + 1)
                : relativePath;
        String ext = fileCloneUtil.getExtension(name);
        return SUPPORTED_EXTENSIONS.contains(ext) || SUPPORTED_FILENAMES.contains(name);
    }

    public String extensionOf(String relativePath) {
        String name = relativePath.contains("/")
                ? relativePath.substring(relativePath.lastIndexOf('/') + 1)
                : relativePath;
        return fileCloneUtil.getExtension(name);
    }

    public String getHeadCommitSha(Path repoDir) {
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir.resolve(".git").toFile())
                .build()) {
            ObjectId head = repository.resolve("HEAD");
            return head != null ? head.getName() : null;
        } catch (IOException e) {
            log.warn("Could not resolve HEAD commit for {}: {}", repoDir, e.getMessage());
            return null;
        }
    }

    public record ParsedFile(String relativePath, String content, String extension) {}
}