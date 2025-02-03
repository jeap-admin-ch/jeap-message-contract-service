package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import java.io.File;

record SchemaLocations(File messageTypeDir, File systemCommonDir, File rootCommonDir) {

    File findSchemaFile(String schemaFilename) {
        File fileInTypeDir = new File(messageTypeDir, schemaFilename);
        if (fileInTypeDir.exists()) {
            return fileInTypeDir;
        }

        File fileInSystemCommonDir = new File(systemCommonDir, schemaFilename);
        if (fileInSystemCommonDir.exists()) {
            return fileInSystemCommonDir;
        }

        File fileInRootCommonDir = new File(rootCommonDir, schemaFilename);
        if (fileInRootCommonDir.exists()) {
            return fileInRootCommonDir;
        }

        throw MessageTypeRepoException.missingSchema(schemaFilename, messageTypeDir, systemCommonDir, rootCommonDir);
    }
}
