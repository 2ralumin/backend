package com.guide.run.user.repository;

import com.guide.run.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,String> {
    Optional<User> findBySocialId(String socialId);
}
