package com.crane.bandwebbackend.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.crane.bandwebbackend.domain.auth.config.AuthLoginProperties;
import com.crane.bandwebbackend.domain.auth.dto.KakaoProfile;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoOAuthClientTest {

    private static final String TOKEN_OK = "{\"access_token\":\"at-1\",\"token_type\":\"bearer\",\"expires_in\":21599}";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();

    private KakaoOAuthClient client(String secret) {
        return new KakaoOAuthClient(
                builder, new AuthLoginProperties("cid", secret, "http://localhost:8080/cb", "http://localhost/done"));
    }

    @Test
    @DisplayName("코드를 토큰으로 바꾸고 프로필(회원번호·닉네임·이미지)을 읽는다")
    void success() {
        server.expect(requestTo(KakaoOAuthClient.TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content()
                        .formDataContains(Map.of(
                                "grant_type", "authorization_code",
                                "client_id", "cid",
                                "redirect_uri", "http://localhost:8080/cb",
                                "code", "code-1",
                                "client_secret", "sec")))
                .andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(KakaoOAuthClient.PROFILE_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer at-1"))
                .andRespond(withSuccess(
                        "{\"id\":123456789,\"connected_at\":\"x\",\"kakao_account\":{\"profile_nickname_needs_agreement\":false,"
                                + "\"profile\":{\"nickname\":\"홍길동\",\"profile_image_url\":\"http://k.kakaocdn.net/a.jpg\","
                                + "\"is_default_image\":false}}}",
                        MediaType.APPLICATION_JSON));

        KakaoProfile profile = client("sec").fetchProfile("code-1");

        assertThat(profile).isEqualTo(new KakaoProfile("123456789", "홍길동", "http://k.kakaocdn.net/a.jpg"));
        server.verify();
    }

    @Test
    @DisplayName("Client Secret이 비어 있으면 보내지 않고, 프로필이 없어도 회원번호만으로 성공한다")
    void noSecretAndNoProfile() {
        server.expect(requestTo(KakaoOAuthClient.TOKEN_URL))
                .andExpect(request -> assertThat(request.getBody().toString()).doesNotContain("client_secret"))
                .andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(KakaoOAuthClient.PROFILE_URL))
                .andRespond(withSuccess("{\"id\":7}", MediaType.APPLICATION_JSON));

        assertThat(client("").fetchProfile("c")).isEqualTo(new KakaoProfile("7", null, null));
    }

    @Test
    @DisplayName("토큰 교환이 4xx·5xx이면 KakaoOAuthException이고 응답 본문이 메시지에 없다")
    void tokenErrors() {
        server.expect(requestTo(KakaoOAuthClient.TOKEN_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"secret\":\"leak-me\"}"));

        assertThatThrownBy(() -> client("s").fetchProfile("bad"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessageNotContaining("leak-me")
                .hasMessageNotContaining("bad");
    }

    @Test
    @DisplayName("프로필 조회가 5xx이거나 access_token·회원번호가 없으면 실패")
    void profileErrors() {
        server.expect(requestTo(KakaoOAuthClient.TOKEN_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client("s").fetchProfile("c")).isInstanceOf(KakaoOAuthException.class);

        server.reset();
        server.expect(requestTo(KakaoOAuthClient.TOKEN_URL))
                .andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(KakaoOAuthClient.PROFILE_URL)).andRespond(withServerError());
        assertThatThrownBy(() -> client("s").fetchProfile("c")).isInstanceOf(KakaoOAuthException.class);

        server.reset();
        server.expect(requestTo(KakaoOAuthClient.TOKEN_URL))
                .andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(KakaoOAuthClient.PROFILE_URL))
                .andRespond(withSuccess("{\"kakao_account\":{}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client("s").fetchProfile("c")).isInstanceOf(KakaoOAuthException.class);
    }
}
