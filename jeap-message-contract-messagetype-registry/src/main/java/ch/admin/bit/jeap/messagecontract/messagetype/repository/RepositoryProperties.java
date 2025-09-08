package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class RepositoryProperties {

    private String uri;
    private RepositoryType type;
    @ToString.Exclude
    private Map<String, String> parameters = Map.of();

    public enum RepositoryType {
        BITBUCKET,
        GITHUB,
        LOCAL
    }

}
