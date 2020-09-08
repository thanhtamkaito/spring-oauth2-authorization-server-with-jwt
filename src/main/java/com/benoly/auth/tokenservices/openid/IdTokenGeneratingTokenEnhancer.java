package com.benoly.auth.tokenservices.openid;

import com.benoly.auth.model.User;
import com.benoly.auth.model.token.IDToken;
import com.benoly.auth.service.UserService;
import com.benoly.auth.tokenservices.JwtTokenEnhancer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.springframework.security.core.Authentication;
import org.springframework.security.jwt.JwtHelper;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static com.benoly.auth.constants.Scopes.IDTokenScopes.EMAIL;
import static com.benoly.auth.constants.Scopes.IDTokenScopes.PROFILE;
import static com.benoly.auth.constants.Scopes.ID_SCOPE;
import static com.benoly.auth.util.TokenUtils.getMessageDigestInstance;
import static com.benoly.auth.util.TokenUtils.hashString;
import static io.jsonwebtoken.Claims.AUDIENCE;
import static org.springframework.security.oauth2.core.oidc.IdTokenClaimNames.*;

public class IdTokenGeneratingTokenEnhancer extends JwtTokenEnhancer {

    private final IDTokenClaimsEnhancer enhancer;
    private final UserService userService;

    public IdTokenGeneratingTokenEnhancer(UserService userService,
                                          IDTokenClaimsEnhancer enhancer,
                                          KeyPair keyPair,
                                          Map<String, String> headers) {
        super(keyPair, headers);
        this.userService = userService;
        this.enhancer = enhancer;
    }

    // TODO - limit openid connect generation to authorization code and implicit flows only
    @Override
    public OAuth2AccessToken enhance(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {
        OAuth2Request request = authentication.getOAuth2Request();
        if (request.getScope().contains(ID_SCOPE))
            accessToken = appendIdToken(accessToken, authentication);
        return accessToken;
    }

    /**
     * This method uses an access token to generate an ID token.
     * some claims are taken directly from the access toke and mapped to the ID token
     * <p>
     * The ID token is generated with base claims, then depending on the scopes requested
     * a delegate {@link IDTokenClaimsEnhancer} populates the required claims
     *
     * @param accessToken    access token
     * @param authentication authentication context containing the authentication request
     * @return IDToken
     */
    private OAuth2AccessToken appendIdToken(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {
        OAuth2Request request = authentication.getOAuth2Request();

        DefaultOAuth2AccessToken token = (DefaultOAuth2AccessToken) accessToken;
        String nonce = request.getRequestParameters().get(NONCE);
        Claims accessTokenClaims = new DefaultClaims(super.decode(accessToken.getValue()));
        accessTokenClaims.put(AUDIENCE, request.getClientId());

        OidcIdToken.Builder builder = OidcIdToken.withTokenValue(token.getValue())
                .issuer(accessTokenClaims.getIssuer())
                .subject(accessTokenClaims.getSubject())
                .audience(Set.of(accessTokenClaims.getAudience()))
                .authorizedParty(request.getClientId())
                .nonce(nonce)
                .expiresAt(accessTokenClaims.getExpiration().toInstant())
                .accessTokenHash(generateAccessTokenHash(accessToken))
                .authorizationCodeHash(generateCodeHash(accessToken, authentication))
                .authTime(accessTokenClaims.getIssuedAt().toInstant())
                .issuedAt(Instant.now())
                .authenticationMethods(getAuthenticationMethods(authentication));

        String username = accessTokenClaims.getSubject();
        User user = userService.findUserByUsername(username);

        if (request.getScope().contains(PROFILE))
            builder.claims(claimsMap -> enhancer.addProfileClaims(claimsMap, user));


        if (request.getScope().contains(EMAIL))
            builder.claims(claimsMap -> enhancer.addEmailClaims(claimsMap, user));

        OidcIdToken idToken = builder.build();

        String idTokenString = super.encode(new IDToken(idToken), authentication);

        token.setAdditionalInformation(Map.of(IDToken.TYPE, idTokenString));
        return token;
    }

    // generates the at_hash
    protected String generateAccessTokenHash(OAuth2AccessToken accessToken) {

        String algorithm = getHashAlgorithmForToken(accessToken.getValue());
        MessageDigest MD5 = getMessageDigestInstance(algorithm);
        // - get ascii representation of the token
        byte[] asciiValues = accessToken.getValue().getBytes(StandardCharsets.US_ASCII);

        // - hash the ascii value using the jwt hashing algorithm
        byte[] hashedToken = MD5.digest(asciiValues);

        // get the first 128 bits (hash alg length / 2 === 256 / 2)
        byte[] bytes = Arrays.copyOf(hashedToken, 16);

        return Base64.getEncoder().encodeToString(bytes);
    }

    // generate the c_hash claim value
    protected String generateCodeHash(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {
        OAuth2Request request = authentication.getOAuth2Request();
        Map<String, String> requestParameters = request.getRequestParameters();
        String authorizationCode = requestParameters.get("code");
        if (authorizationCode == null) return null;

        String algorithm = getHashAlgorithmForToken(accessToken.getValue());
        byte[] hashedCode = hashString(algorithm, authorizationCode);
        byte[] bytes = Arrays.copyOf(hashedCode, 16);

        return Base64.getEncoder().encodeToString(bytes);
    }

    // you should override this
    // RS256 is used to sign tokens so the algorithm returns SHA-256
    protected String getHashAlgorithmForToken(String token) {
        Map<String, String> headers = JwtHelper.headers(token);
        String tokenAlg = headers.get("alg");
        return "SHA-".concat(tokenAlg.substring(2));
    }

    protected List<String> getAuthenticationMethods(Authentication authentication) {
        return List.of("user");
    }
}