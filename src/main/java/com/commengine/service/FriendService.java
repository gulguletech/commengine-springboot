package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.DtoMapper;
import com.commengine.entity.FriendRequest;
import com.commengine.entity.FriendRequestStatus;
import com.commengine.entity.User;
import com.commengine.repository.FriendRequestRepository;
import com.commengine.repository.UserRepository;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
// See AudioRoomService for why this class-level readOnly transaction is required
// (open-in-view=false + lazy relations mapped by DtoMapper after the repository call).
@Transactional(readOnly = true)
public class FriendService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final SocketIOServer socketIOServer;

    public FriendService(FriendRequestRepository friendRequestRepository, UserRepository userRepository,
                         SocketIOServer socketIOServer) {
        this.friendRequestRepository = friendRequestRepository;
        this.userRepository = userRepository;
        this.socketIOServer = socketIOServer;
    }

    @Transactional
    public Map<String, Object> sendRequest(User sender, String receiverId) {
        if (receiverId.equals(sender.getId())) {
            throw new ApiException("You can't friend yourself", HttpStatus.BAD_REQUEST);
        }
        User receiver = userRepository.findById(receiverId).orElseThrow(() -> ApiException.notFound("User not found"));

        FriendRequest reverse = friendRequestRepository.findBySenderIdAndReceiverId(receiverId, sender.getId()).orElse(null);
        if (reverse != null && reverse.getStatus() == FriendRequestStatus.PENDING) {
            reverse.setStatus(FriendRequestStatus.ACCEPTED);
            reverse.setResolvedAt(Instant.now());
            friendRequestRepository.save(reverse);

            Map<String, Object> notify = new LinkedHashMap<>();
            notify.put("requestId", reverse.getId());
            notify.put("user", DtoMapper.userBrief(sender));
            socketIOServer.getRoomOperations("user:" + receiverId).sendEvent("friend:request-accepted", notify);

            return Map.of("status", "ACCEPTED");
        }
        if (reverse != null && reverse.getStatus() == FriendRequestStatus.ACCEPTED) {
            return Map.of("status", "ACCEPTED");
        }

        FriendRequest request = friendRequestRepository.findBySenderIdAndReceiverId(sender.getId(), receiverId).orElse(null);
        if (request == null) {
            request = new FriendRequest();
            request.setSender(sender);
            request.setReceiver(receiver);
        }
        request.setStatus(FriendRequestStatus.PENDING);
        request.setResolvedAt(null);
        friendRequestRepository.save(request);

        Map<String, Object> notify = new LinkedHashMap<>();
        notify.put("requestId", request.getId());
        notify.put("user", DtoMapper.userBrief(sender));
        socketIOServer.getRoomOperations("user:" + receiverId).sendEvent("friend:request-received", notify);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "PENDING");
        data.put("requestId", request.getId());
        return data;
    }

    @Transactional
    public Map<String, Object> respondRequest(User user, String requestId, boolean accept) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Friend request not found"));
        if (!request.getReceiver().getId().equals(user.getId())) {
            throw ApiException.forbidden("This request isn't addressed to you");
        }
        if (request.getStatus() != FriendRequestStatus.PENDING) {
            throw new ApiException("This request was already resolved", HttpStatus.BAD_REQUEST);
        }

        FriendRequestStatus status = accept ? FriendRequestStatus.ACCEPTED : FriendRequestStatus.REJECTED;
        request.setStatus(status);
        request.setResolvedAt(Instant.now());
        friendRequestRepository.save(request);

        if (accept) {
            Map<String, Object> notify = new LinkedHashMap<>();
            notify.put("requestId", request.getId());
            notify.put("user", DtoMapper.userBrief(user));
            socketIOServer.getRoomOperations("user:" + request.getSender().getId()).sendEvent("friend:request-accepted", notify);
        }

        return Map.of("status", status.name());
    }

    public Map<String, Object> listFriends(User user) {
        List<FriendRequest> requests = friendRequestRepository.findAcceptedForUser(user.getId());
        List<Map<String, Object>> friends = requests.stream()
                .map(r -> r.getSender().getId().equals(user.getId()) ? r.getReceiver() : r.getSender())
                .map(DtoMapper::userWithOnline)
                .collect(Collectors.toList());
        return Map.of("friends", friends);
    }

    public Map<String, Object> listIncomingRequests(User user) {
        List<FriendRequest> requests = friendRequestRepository.findByReceiverIdAndStatusOrderByCreatedAtDesc(user.getId(), FriendRequestStatus.PENDING);
        return Map.of("requests", requests.stream().map(DtoMapper::incomingFriendRequest).collect(Collectors.toList()));
    }

    public Map<String, Object> getStatus(User user, String otherId) {
        if (otherId.equals(user.getId())) {
            return Map.of("status", "SELF");
        }
        FriendRequest request = friendRequestRepository.findBetween(user.getId(), otherId).orElse(null);
        if (request == null) {
            return Map.of("status", "NONE");
        }
        if (request.getStatus() == FriendRequestStatus.ACCEPTED) {
            return Map.of("status", "FRIENDS");
        }
        if (request.getStatus() == FriendRequestStatus.REJECTED) {
            return Map.of("status", "NONE");
        }
        String status = request.getSender().getId().equals(user.getId()) ? "PENDING_SENT" : "PENDING_RECEIVED";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        data.put("requestId", request.getId());
        return data;
    }
}