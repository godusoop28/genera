package com.c21genera.identity.application;

import com.c21genera.identity.UserDirectory;
import com.c21genera.identity.domain.UserStatus;
import com.c21genera.identity.infrastructure.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class UserDirectoryService implements UserDirectory {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  UserDirectoryService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public boolean passwordMatches(UUID userId, String rawPassword) {
    if (userId == null || rawPassword == null || rawPassword.isEmpty()) {
      return false;
    }
    return userRepository
        .findById(userId)
        .filter(u -> u.getStatus() == UserStatus.ACTIVE)
        .map(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
        .orElse(false);
  }

  @Override
  public Optional<UserContact> contactOf(UUID userId) {
    if (userId == null) {
      return Optional.empty();
    }
    return userRepository.findById(userId).map(u -> new UserContact(u.getId(), u.getName(), u.getEmail(), u.getStatus() == UserStatus.ACTIVE));
  }

  @Override
  public List<StaffMember> activeStaff() {
    return userRepository.findByStatus(UserStatus.ACTIVE).stream()
        .map(u -> new StaffMember(u.getId(), u.getName(), u.getEmail(), u.getRole().getCode().name()))
        .toList();
  }
}
