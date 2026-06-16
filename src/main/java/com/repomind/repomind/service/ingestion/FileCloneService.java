package com.repomind.repomind.service.ingestion;

import com.repomind.repomind.utility.FileCloneUtil;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
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

    // Whitelist of extensions worth embedding
    // We whitelist (only allow these) rather than blacklist (block specific ones)
    // because there are too many binary/junk types to block exhaustively
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".java", ".js", ".ts", ".jsx", ".tsx", ".py", ".go",
            ".rb", ".php", ".cs", ".cpp", ".c", ".h", ".rs",
            ".kt", ".swift", ".yml", ".yaml", ".json", ".xml",
            ".sql", ".sh", ".md", ".properties", ".gradle", ".toml",
            ".html", ".css"
    );
    // Folders that contain zero useful code
    // node_modules = npm dependencies (not your code)
    // target = compiled Java output
    // .git = git history (binary, not readable)
    // build/dist = build outputs
    private static final Set<String> SKIP_FOLDERS = Set.of(
            "node_modules", ".git", ".idea", ".vscode",
            "build", "dist", "target", ".gradle",
            "__pycache__", ".next", "vendor", "coverage", "out"
    );

    // 500KB max per file
    // Files larger than this are almost always generated or minified
    // Minified JS is one line of gibberish — useless for semantic search
    private static final long MAX_FILE_BYTES = 500 * 1024;

    public Path cloneRepository(String githubUrl, String token) throws Exception {

        // createTempDirectory makes a folder like /tmp/repomind-clone-4729382
        // Spring Boot and the OS will clean this up eventually
        // but we explicitly delete it after ingestion to save disk space
        Path tempDir = Files.createTempDirectory("repomind-clone-");
        log.info("Cloning {} into {}", githubUrl, tempDir);

        try {

            var cmd = Git.cloneRepository()
                    .setURI(githubUrl)
                    .setDirectory(tempDir.toFile())
                    // Depth 1 = shallow clone: only downloads the latest snapshot
                    // NOT the full git history. For a repo with 3 years of history,
                    // this is the difference between 30MB and 800MB download.
                    .setDepth(1);

            //private repo requires authentication. Public repo can skip this.
            if (token != null && !token.isEmpty()) {
                // Github PAT as username, empty password.
                // This is excatly how Github's token auth works over HTTPS.

                cmd.setCredentialsProvider((
                        new UsernamePasswordCredentialsProvider(token, "")
                ));
            }

            cmd.call().close();
            log.info("Clone complete");
            return tempDir;

        } catch (Exception e) {
            //if clone fails, clean up immediately to avoid leaving temp folders lying around
            deleteDirectory(tempDir);
            throw e;
        }
    }

    public List<ParsedFile> extractFiles(Path repoRoot) throws IOException {
        List<ParsedFile> result = new ArrayList<>();

        //walkFileTree visits every file and directory recursively
        //simpleFileVisitor lets you override only the methods you need
        //Java automatically traverses everything recursively.
        Files.walkFileTree(repoRoot, new SimpleFileVisitor<>(){

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // Called BEFORE entering each directory
                // SKIP_SUBTREE = do not enter this folder at all
                // We never even list the contents of node_modules
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

                    if(!SUPPORTED_EXTENSIONS.contains(ext)) return FileVisitResult.CONTINUE;

                    if(attrs.size() > MAX_FILE_BYTES){
                        log.debug("Skipping large filee: {}",file);
                        return FileVisitResult.CONTINUE;
                    }

                    String content = Files.readString(file);

                    if(content.isBlank())return FileVisitResult.CONTINUE;

                    // relativize turns:
                    // /tmp/repomind-clone-4729/src/main/AuthService.java
                    // into: src/main/AuthService.java
                    // This is the path users see in the "sources" section of chat
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
                        // Log but DO NOT return TERMINATE or throw
                        // Returning CONTINUE means the walk keeps going to next file
                        log.warn("Could not delete {}: {}",f,e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // visitFileFailed is called when the walker itself cannot
                    // even access a file (permissions, locked by OS etc)
                    // Without overriding this, the default implementation throws
                    // the exception and stops the entire walk
                    log.warn("Could not access file for deletion {}: {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) {
                    if (exc != null) {
                        // exc here means something went wrong iterating this directory
                        // We still try to delete it — it might be empty now
                        log.warn("Issue iterating directory {}: {}", d, exc.getMessage());
                    }
                    try {
                        Files.delete(d);
                    } catch (IOException e) {
                        // Directory might not be empty because some files above failed
                        // That is acceptable — OS will clean temp dirs eventually
                        log.warn("Could not delete directory {}: {}", d, e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }catch (IOException e){
            log.warn("Partial cleanup failure: {}", e.getMessage());
        }
    }
    // Java record: immutable data holder
    // Auto-generates constructor, getters, equals, hashCode
    // Perfect for passing data between services without risk of mutation
    public record ParsedFile(String relativePath, String content, String extension) {}
}

