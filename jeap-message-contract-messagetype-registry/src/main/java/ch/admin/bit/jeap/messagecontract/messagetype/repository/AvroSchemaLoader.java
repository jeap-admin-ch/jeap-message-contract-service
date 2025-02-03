package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import ch.admin.bit.jeap.messaging.avro.plugin.compiler.IdlFileParser;
import ch.admin.bit.jeap.messaging.avro.plugin.compiler.ImportClassLoader;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Protocol;
import org.apache.avro.compiler.idl.ParseException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

@Slf4j
class AvroSchemaLoader {

    static String loadSchemaAsJsonProtocol(String schemaFilename, SchemaLocations schemaLocations) {
        try {
            IdlFileParser idlFileParser = createIdlFileParser(schemaLocations);
            File schemaFile = schemaLocations.findSchemaFile(schemaFilename);
            return loadSchemaFile(idlFileParser, schemaFile);
        } catch (IOException | ParseException ex) {
            throw MessageTypeRepoException.schemaLoadingFailed(schemaFilename, ex);
        }
    }

    private static String loadSchemaFile(IdlFileParser idlFileParser, File source) throws IOException, ParseException {
        Protocol protocol = idlFileParser.parseIdlFile(source);
        return protocol.toString();
    }

    private static IdlFileParser createIdlFileParser(SchemaLocations schemaLocations) throws MalformedURLException {
        // Note: This uses current thread context classloader as parent, which will load domain event base types
        // schemas because jeap-messaging-avro is on the classpath.
        log.debug("Loading schema files from {}, {} and {}",
                schemaLocations.messageTypeDir(), schemaLocations.systemCommonDir(), schemaLocations.rootCommonDir());
        ImportClassLoader parent = new ImportClassLoader(schemaLocations.messageTypeDir());
        ImportClassLoader loader = new ImportClassLoader(parent, schemaLocations.systemCommonDir(), schemaLocations.rootCommonDir());
        return new IdlFileParser(loader);
    }
}
