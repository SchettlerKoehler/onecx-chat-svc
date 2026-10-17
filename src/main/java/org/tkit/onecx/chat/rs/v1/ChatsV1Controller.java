package org.tkit.onecx.chat.rs.v1;

import static jakarta.transaction.Transactional.TxType.NOT_SUPPORTED;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import org.tkit.onecx.chat.domain.daos.ChatDAO;
import org.tkit.onecx.chat.domain.models.Chat;
import org.tkit.onecx.chat.rs.internal.mappers.ChatMapper;
import org.tkit.quarkus.context.ApplicationContext;

import gen.org.tkit.onecx.chat.rs.v1.ChatApi;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@Transactional(value = NOT_SUPPORTED)
public class ChatsV1Controller implements ChatApi {

    @Inject
    ChatDAO dao;

    @Inject
    ChatMapper mapper;

    @Override
    public Response getChatForUser(String chatId) {
        var chat = dao.findById(chatId);
        if (chat == null) {
            // chat does not exist (or belongs to another tenant)
            return Response.status(NOT_FOUND).build();
        }
        var principal = ApplicationContext.get().getPrincipal();
        if (principal == null || principal.isBlank() || !mayAccess(chat, principal)) {
            // not accessible by the requesting user; 404 so callers cannot probe for existence
            return Response.status(NOT_FOUND).build();
        }
        return Response.ok(mapper.mapChatV1(chat)).build();
    }

    private boolean mayAccess(Chat chat, String principal) {
        if (principal.equals(chat.getUserId())) {
            return true;
        }
        return chat.getParticipants().stream()
                .anyMatch(p -> principal.equals(p.getUserId()));
    }
}
