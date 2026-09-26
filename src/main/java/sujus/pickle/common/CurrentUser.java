package sujus.pickle.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

public final class CurrentUser {
    private CurrentUser() { }

    public static Long id(Jwt jwt) {
        Object claim = jwt == null ? null : jwt.getClaim("userId");
        if (!(claim instanceof Number number) || number.longValue() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in again");
        }
        return number.longValue();
    }
}
