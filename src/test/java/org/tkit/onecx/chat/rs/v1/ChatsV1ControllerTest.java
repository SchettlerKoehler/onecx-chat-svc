package org.tkit.onecx.chat.rs.v1;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.tkit.onecx.chat.test.AbstractTest;
import org.tkit.quarkus.security.test.GenerateKeycloakClient;
import org.tkit.quarkus.test.WithDBData;

import gen.org.tkit.onecx.chat.rs.v1.model.ChatV1DTO;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for the principal-aware {@code GET /v1/chats/{chatId}} operation.
 * <p>
 * The requesting user is the principal resolved from the {@code apm-principal-token}
 * ({@code sub} claim, "test-user" in these tests). The operation returns 200 with a
 * {@link ChatV1DTO} only when the principal is the chat owner or a participant; it
 * returns 404 for every other case (including cross-tenant and unknown IDs) so callers
 * cannot probe for the existence of a chat they may not access.
 */
@QuarkusTest
@TestHTTPEndpoint(ChatsV1Controller.class)
@WithDBData(value = "data/testdata-v1.xml", deleteBeforeInsert = true, deleteAfterTest = true, rinseAndRepeat = true)
@GenerateKeycloakClient(clientName = "testClient", scopes = { "ocx-chat:read" })
class ChatsV1ControllerTest extends AbstractTest {

    @Test
    void ownerReceivesChat() {
        var dto = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .header(APM_HEADER_PARAM, createToken("org1"))
                .pathParam("chatId", "t-chat-v1-owner-01")
                .get()
                .then()
                .statusCode(OK.getStatusCode())
                .contentType(APPLICATION_JSON)
                .extract()
                .body().as(ChatV1DTO.class);

        assertThat(dto).isNotNull()
                .returns("t-chat-v1-owner-01", ChatV1DTO::getId)
                .returns(ChatV1DTO.TypeEnum.HUMAN_DIRECT_CHAT, ChatV1DTO::getType)
                .returns("v1 owner chat", ChatV1DTO::getTopic)
                .returns("appId", ChatV1DTO::getAppId)
                .returns("test-user", ChatV1DTO::getUserId);
    }

    @Test
    void participantReceivesChat() {
        var dto = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .header(APM_HEADER_PARAM, createToken("org1"))
                .pathParam("chatId", "t-chat-v1-group-01")
                .get()
                .then()
                .statusCode(OK.getStatusCode())
                .contentType(APPLICATION_JSON)
                .extract()
                .body().as(ChatV1DTO.class);

        // owner is user1, test-user is a participant -> still accessible
        assertThat(dto).isNotNull()
                .returns("t-chat-v1-group-01", ChatV1DTO::getId)
                .returns(ChatV1DTO.TypeEnum.HUMAN_GROUP_CHAT, ChatV1DTO::getType)
                .returns("v1 group chat", ChatV1DTO::getTopic)
                .returns("appId", ChatV1DTO::getAppId)
                .returns("user1", ChatV1DTO::getUserId);
    }

    @Test
    void nonParticipantSameTenantReceivesNotFound() {
        // t-chat-v1-other-01 is owned by user1 and test-user is NOT a participant
        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .header(APM_HEADER_PARAM, createToken("org1"))
                .pathParam("chatId", "t-chat-v1-other-01")
                .get()
                .then()
                .statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    void crossTenantReceivesNotFound() {
        // t-chat-v1-xtenant-01 belongs to tenant-200; a tenant-100 principal cannot access it
        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .header(APM_HEADER_PARAM, createToken("org1"))
                .pathParam("chatId", "t-chat-v1-xtenant-01")
                .get()
                .then()
                .statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    void unknownIdReceivesNotFound() {
        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .header(APM_HEADER_PARAM, createToken("org1"))
                .pathParam("chatId", "does-not-exist")
                .get()
                .then()
                .statusCode(NOT_FOUND.getStatusCode());
    }
}
