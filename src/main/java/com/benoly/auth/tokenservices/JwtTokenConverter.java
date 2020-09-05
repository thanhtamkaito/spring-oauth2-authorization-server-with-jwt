package com.benoly.auth.tokenservices;

import com.benoly.auth.model.token.IDToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.jsonwebtoken.Claims.ISSUER;
import static io.jsonwebtoken.Claims.SUBJECT;
import static org.springframework.security.oauth2.provider.token.UserAuthenticationConverter.USERNAME;

/**
 * Custom access token converter to add custom claims
 */
public class JwtTokenConverter extends DefaultAccessTokenConverter {
    public static final String ROLE_CLAIM = "role";
    public static final String USERNAME_CLAIM = "user_name";


    @Autowired
    private ClaimPopulationDelegate claimsPopulationDelegate;
    @Autowired
    private ObjectMapper objectMapper;

    public JwtTokenConverter() {
    }

    /**
     * Convert access token using the default converter and add custom claims
     *
     * @param token          OAuth2 access token to convert
     * @param authentication authentication to convert
     */
    @Override
    public Map<String, ?> convertAccessToken(OAuth2AccessToken token, OAuth2Authentication authentication) {
        if (token.getTokenType().equals("id_token")) {
            var idToken = (IDToken) token;
            Claims claims = new DefaultClaims();
            claims.setIssuer(idToken.getIssuer());
            claims.setSubject(idToken.getSubject());
            claims.setAudience(idToken.getAudience());
            claims.setExpiration(idToken.getExpiration());
            claims.setIssuedAt(idToken.getIssuedAt());
            claims.put("auth_time", idToken.getAuthTime());
            claims.put("nonce", idToken.getNonce());
            claims.put("azp", idToken.getAuthorizedParty());
            claims.put("at_hash", idToken.getAccessTokenHash());

            for (String key : idToken.getAdditionalInformation().keySet()) {
                claims.put(key, idToken.getAdditionalInformation().get(key));
            }

            return claims;
        }
        var superToken = super.convertAccessToken(token, authentication);
        var mutableToken = new HashMap<String, Object>(superToken);
        return claimsPopulationDelegate.populateClaims(mutableToken);
    }

    /**
     * Extract access token from a previously converted Token
     *
     * @param value the value of the token
     * @param map   A map of the previously converted token
     * @return Original Token
     */
    @Override
    public OAuth2AccessToken extractAccessToken(String value, Map<String, ?> map) {
        var superToken = super.extractAccessToken(value, map);
        var info = superToken.getAdditionalInformation();

        DefaultOAuth2AccessToken oAuth2AccessToken = new DefaultOAuth2AccessToken(superToken);
        oAuth2AccessToken.setAdditionalInformation(info);
        info.remove(ISSUER);

        return oAuth2AccessToken;
    }

    @Override
    public OAuth2Authentication extractAuthentication(Map<String, ?> map) {
        var response = new HashMap<String, Object>(map);
        Object username = response.remove(SUBJECT);
        response.put(USERNAME, username);
        if (response.containsKey("azp") && !response.containsKey(CLIENT_ID))
            response.put(CLIENT_ID, response.get("azp"));
        return super.extractAuthentication(response);
    }
}
