package ch.admin.bit.jeap.messagecontract.test;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public record TestRegistryRepo(Path repoDir, String revision, String url) {

    public static final String EVENT_DIR = "descriptor/activ/event/activzoneenteredevent";
    private static final String SYSTEM_COMMON_DIR = "descriptor/activ/_common";
    private static final String ROOT_COMMON_DIR = "descriptor/_common";

    public static TestRegistryRepo createMessageTypeRegistryRepository() throws Exception {
        Path repoDir = Files.createTempDirectory("test-repo");

        // Init file-based repository, and copy/commit test files
        try (Git newRepo = initGitRepo(repoDir)) {
            copyTestRegistryFilesToRepositoryDir(repoDir);
            RevCommit commit = addAndCommitTestFiles(newRepo);
            return new TestRegistryRepo(repoDir, commit.getId().name(), repoDir.toUri().toString());
        } catch (Exception ex) {
            FileUtils.forceDelete(repoDir.toFile());
            throw ex;
        }
    }

    private static Git initGitRepo(Path repoDir) throws GitAPIException {
        return Git.init()
                .setDirectory(repoDir.toFile())
                .setGitDir(repoDir.resolve(".git").toFile())
                .setInitialBranch("master")
                .call();
    }

    private static RevCommit addAndCommitTestFiles(Git newRepo) throws GitAPIException {
        newRepo.add()
                .addFilepattern(".")
                .call();
        return newRepo.commit()
                .setAuthor("test", "test@mail.com")
                .setMessage("initial revision")
                .call();
    }

    private static void copyTestRegistryFilesToRepositoryDir(Path repoDir) throws IOException {
        File rootDirectory = repoDir.toFile();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "/test-registry/" + "**");
        for (Resource resource : resources) {
            if (resource.exists() && resource.isReadable() && resource.contentLength() > 0) {
                URL url = resource.getURL();
                String urlString = url.toExternalForm();
                String targetName = urlString.substring(urlString.indexOf("descriptor/"));
                File destination = new File(rootDirectory, targetName);
                FileUtils.copyURLToFile(url, destination);
            }
        }
    }

    public void addAndCommitFile(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);

        try (Git git = Git.open(repoDir.toFile())) {
            git.add()
                    .addFilepattern(".")
                    .call();
            git.commit()
                    .setAuthor("test", "test@mail.com")
                    .setMessage("add file")
                    .call();
        }
    }

    public void delete() throws IOException {
        FileUtils.forceDelete(repoDir.toFile());
    }
}
