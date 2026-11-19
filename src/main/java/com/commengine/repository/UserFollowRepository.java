package com.commengine.repository;

import com.commengine.entity.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFollowRepository extends JpaRepository<UserFollow, String> {

    boolean existsByFollowerIdAndFollowingId(String followerId, String followingId);

    long countByFollowingId(String followingId);   // followers
    long countByFollowerId(String followerId);     // following

    /** Ids of people `userId` follows, newest first. */
    @Query("select f.followingId from UserFollow f where f.followerId = :userId order by f.createdAt desc")
    List<String> followingIds(@Param("userId") String userId);

    /** Ids of people who follow `userId`, newest first. */
    @Query("select f.followerId from UserFollow f where f.followingId = :userId order by f.createdAt desc")
    List<String> followerIds(@Param("userId") String userId);

    @Modifying
    @Query("delete from UserFollow f where f.followerId = :followerId and f.followingId = :followingId")
    int unfollow(@Param("followerId") String followerId, @Param("followingId") String followingId);
}
