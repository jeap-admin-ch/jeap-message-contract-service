package ch.admin.bit.jeap.messagecontract.web.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
public class UserProperty {

    private String username;

    @ToString.Exclude
    private String password;

}
