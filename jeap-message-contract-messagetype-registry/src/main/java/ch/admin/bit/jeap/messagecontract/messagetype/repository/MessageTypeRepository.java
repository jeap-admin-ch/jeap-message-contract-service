package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import ch.admin.bit.jeap.messaging.avro.plugin.validator.MessageTypeRegistryConstants;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.eclipse.jgit.lib.Constants.HEAD;

@SuppressWarnings("findbugs:NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
@Slf4j
public class MessageTypeRepository implements Closeable {
    private static final String COMMON = "_common";

    private final JsonFactory jsonFactory = new JsonFactory();
    private final ObjectMapper objectMapper;
    private File gitRepoPath;
    private Git git;

    MessageTypeRepository(String gitUri) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        cloneGitRepo(gitUri);
    }

    @Override
    public void close() {
        forceDeleteDirectory(gitRepoPath);
    }

    public String getSchemaAsAvroProtocolJson(String branch, String commitReference, String messageTypeName, String messageTypeVersion) {
        log.info("Loading schema for {}:{}", messageTypeName, messageTypeVersion);
        try {
            checkoutAt(branch, commitReference);
        } catch (Exception ex) {
            throw MessageTypeRepoException.checkoutFailed(branch, commitReference, ex);
        }

        List<CommandDescriptor> commandDescriptors = getAllCommandDescriptors();
        List<EventDescriptor> eventDescriptors = getAllEventDescriptors();

        MessageTypeDescriptor descriptor = Stream.concat(eventDescriptors.stream(), commandDescriptors.stream())
                .filter(d -> messageTypeName.equals(d.getMessageTypeName()))
                .findFirst()
                .orElseThrow(MessageTypeRepoException.messageTypeNotFound(messageTypeName));

        MessageTypeVersion version = descriptor.findVersion(messageTypeVersion)
                .orElseThrow(MessageTypeRepoException.messageTypeVersionNotFound(messageTypeName, messageTypeVersion));
        String schemaFilename = version.getValueSchema();
        return AvroSchemaLoader.loadSchemaAsJsonProtocol(schemaFilename, descriptor.getSchemaLocations());
    }

    private void checkoutAt(String branch, String commitReference) throws GitAPIException {
        if (commitReference != null && !HEAD.equalsIgnoreCase(commitReference)) {
            log.info("Checking out commit {}", commitReference);
            git.checkout()
                    .setForced(true)
                    .setName(commitReference)
                    .call();
        } else {
            log.info("Checking out branch {}", branch);
            git.checkout()
                    .setForced(true)
                    .setName(branch)
                    .call();
        }
    }

    private List<EventDescriptor> getAllEventDescriptors() {
        return getSystemDirs()
                .flatMap(systemDir -> getMessageTypeDirs(systemDir, EventDescriptor.SUBDIR))
                .flatMap(messageTypeDir -> tryReadDescriptor(messageTypeDir, EventDescriptor.class).stream())
                .toList();
    }

    private List<CommandDescriptor> getAllCommandDescriptors() {
        return getSystemDirs()
                .flatMap(systemDir -> getMessageTypeDirs(systemDir, CommandDescriptor.SUBDIR))
                .flatMap(messageTypeDir -> tryReadDescriptor(messageTypeDir, CommandDescriptor.class).stream())
                .toList();
    }

    private void cloneGitRepo(String gitUri) {
        File tempDir = null;
        try {
            tempDir = Files.createTempDirectory("messageTypeRepo").toFile();
            FileUtils.forceDeleteOnExit(tempDir);
            log.info("Cloning repo {} to {}", gitUri, tempDir);
            this.git = Git.cloneRepository()
                    .setURI(gitUri)
                    .setDirectory(tempDir)
                    .call();
            this.gitRepoPath = tempDir;
        } catch (IOException | GitAPIException e) {
            forceDeleteDirectory(tempDir);
            throw MessageTypeRepoException.cloneFailed(gitUri, e);
        }
    }

    private Stream<File> getSystemDirs() {
        File descriptorDir = new File(gitRepoPath, "descriptor");
        return Arrays.stream(Objects.requireNonNull(descriptorDir.list()))
                .filter(name -> !name.equals(COMMON))
                .map(name -> new File(descriptorDir, name));
    }

    private static Stream<File> getMessageTypeDirs(File systemDir, String typeSubdir) {
        File typeDir = new File(systemDir, typeSubdir);
        if (!typeDir.isDirectory()) {
            return Stream.empty();
        }
        return Arrays.stream(Objects.requireNonNull(typeDir.list()))
                .map(eventDirName -> new File(typeDir, eventDirName));
    }

    private File getDescriptorFile(File messageTypeDir) {
        String[] jsonFileNames = messageTypeDir.list((f, n) -> FilenameUtils.getExtension(n).equals("json"));
        if (jsonFileNames == null || jsonFileNames.length == 0) {
            throw MessageTypeRepoException.missingDescriptor(messageTypeDir.getAbsolutePath());
        }
        return new File(messageTypeDir, jsonFileNames[0]);
    }

    private <T extends MessageTypeDescriptor> Optional<T> tryReadDescriptor(File messageTypeDir, Class<T> descriptorType) {
        try {
            File descriptorFile = getDescriptorFile(messageTypeDir);
            return Optional.of(readDescriptor(descriptorFile, descriptorType));
        } catch (MessageTypeRepoException ex) {
            log.warn("Failed to load message type descriptor {}", messageTypeDir, ex);
            return Optional.empty();
        }
    }

    private <T extends MessageTypeDescriptor> T readDescriptor(File descriptorFile, Class<T> descriptorType) {
        log.trace("Loading message type descriptor {}", descriptorFile);
        try {
            JsonParser jsonParser = jsonFactory.createParser(descriptorFile);
            T descriptor = objectMapper.readValue(jsonParser, descriptorType);
            File messageTypeDir = descriptorFile.getParentFile();
            File systemCommonDir = new File(messageTypeDir.getParentFile().getParentFile(), MessageTypeRegistryConstants.COMMON_DIR_NAME);
            File commonDir = new File(systemCommonDir.getParentFile().getParentFile(), MessageTypeRegistryConstants.COMMON_DIR_NAME);
            SchemaLocations schemaLocations = new SchemaLocations(messageTypeDir, systemCommonDir, commonDir);
            descriptor.setSchemaLocations(schemaLocations);
            return descriptor;
        } catch (IOException e) {
            throw MessageTypeRepoException.descriptorParsingFailed(descriptorFile.getAbsolutePath(), e);
        }
    }

    private static void forceDeleteDirectory(File dir) {
        if (dir == null) {
            return;
        }
        try {
            log.info("Deleting {}", dir);
            FileUtils.forceDelete(dir);
        } catch (IOException e) {
            log.error("Failed to delete {}", dir, e);
        }
    }
}
