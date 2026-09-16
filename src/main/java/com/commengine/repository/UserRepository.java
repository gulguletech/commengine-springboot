package com.commengine.repository;

import com.commengine.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByPhone(String phone);

    @Query("select u from User u where u.id <> :excludeId and (:search is null or :search = '' or lower(u.name) like lower(concat('%', :search, '%')))")
    List<User> searchUsers(@Param("excludeId") String excludeId, @Param("search") String search,
                            org.springframework.data.domain.Pageable pageable);
}
