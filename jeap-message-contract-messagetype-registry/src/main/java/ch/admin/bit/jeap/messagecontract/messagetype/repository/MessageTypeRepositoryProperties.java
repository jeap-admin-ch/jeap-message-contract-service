package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "messages", ignoreUnknownFields = false)
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class MessageTypeRepositoryProperties {
    private List<RepositoryProperties> repositories;
}
