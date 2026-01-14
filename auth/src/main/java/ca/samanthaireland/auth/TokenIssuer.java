package ca.samanthaireland.auth;

public interface TokenIssuer {
    AuthToken issueToken(User user);
}
