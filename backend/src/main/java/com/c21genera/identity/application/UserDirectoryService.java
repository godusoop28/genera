package com.c21genera.identity.application;

import com.c21genera.identity.UserDirectory;
import com.c21genera.identity.domain.UserStatus;
import com.c21genera.identity.infrastructure.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class UserDirectoryService implements UserDirectory {

  private final UserRepository userRepository;

  UserDirectoryService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public Optional<UserContact> contactOf(UUID userId) {
    if (userId == null) {
      return Optional.empty();
    }
    return userRepository.findById(userId).map(u -> new UserContact(u.getId(), u.getName(), u.getEmail(), u.getStatus() == UserStatus.ACTIVE));
  }
}
