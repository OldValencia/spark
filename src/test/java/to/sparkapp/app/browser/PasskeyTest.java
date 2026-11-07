package to.sparkapp.app.browser;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.utils.NativeWindowUtils;

@Tag("native")
@Timeout(60)
class PasskeyTest {
    @Test
    void untouchedCredentialApiCreatesAndUsesALocalVirtualPasskey() throws Exception {
        try (var server = new AuthenticationFixture(); var browser = new WindowsBrowserTest.Harness()) {
            BrowserAuthenticationTest.installPageScript(browser);
            browser.load(server.identity + "/passkey");
            InlineBrowserPagesTest.enable(browser);
            browser.loads.clear();
            browser.eval("window.open('" + server.identity + "/passkey-child','passkey')");
            assertEquals(Boolean.TRUE, browser.loads.poll(20, TimeUnit.SECONDS));
            var protocol = new NativeDevTools(browser);
            protocol.request("WebAuthn.enable", "{}");
            protocol.request("WebAuthn.addVirtualAuthenticator", """
                    {"options":{"protocol":"ctap2","transport":"internal","hasResidentKey":true,
                    "hasUserVerification":true,"isUserVerified":true,"automaticPresenceSimulation":true}}
                    """);
            var focused = new CompletableFuture<Boolean>();
            browser.run(view -> {
                NativeWindowUtils.setVisible(view.getNativeWindowPointer(), true);
                view.windows().setPresentation(true, false);
                focused.complete(view.windows().focus());
            });
            assertTrue(focused.get(10, TimeUnit.SECONDS));
            browser.eval("""
                    (async()=>{
                        try {
                            const challenge=new Uint8Array(32);crypto.getRandomValues(challenge);
                            const credential=await navigator.credentials.create({publicKey:{
                                challenge,rp:{name:'Local Spark test'},
                                user:{id:new Uint8Array([1,2,3]),name:'local-test',displayName:'Local test'},
                                pubKeyCredParams:[{type:'public-key',alg:-7}],
                                authenticatorSelection:{residentKey:'required',userVerification:'required'},timeout:10000}});
                            crypto.getRandomValues(challenge);
                            const assertion=await navigator.credentials.get({publicKey:{challenge,
                                allowCredentials:[{type:'public-key',id:credential.rawId}],
                                userVerification:'required',timeout:10000}});
                            const client=JSON.parse(new TextDecoder().decode(assertion.response.clientDataJSON));
                            window.probe(JSON.stringify({sameId:assertion.id===credential.id,
                                type:client.type,origin:client.origin,signature:assertion.response.signature.byteLength}));
                        } catch(error) { window.probe(JSON.stringify({error:error.name,message:error.message})); }
                    })()
                    """);
            var result = new ObjectMapper().readTree(browser.message());
            assertFalse(result.has("error"), result.toString());
            assertTrue(result.get("sameId").asBoolean());
            assertEquals("webauthn.get", result.get("type").asText());
            assertEquals(server.identity, result.get("origin").asText());
            assertTrue(result.get("signature").asInt() > 0);
        }
    }
}
