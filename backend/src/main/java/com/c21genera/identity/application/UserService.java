package com.c21genera.identity.application;

import com.c21genera.identity.domain.DuplicateEmailException;
import com.c21genera.identity.domain.Role;
import com.c21genera.identity.domain.RoleCode;
import com.c21genera.identity.domain.User;
import com.c21genera.identity.infrastructure.RoleRepository;
import com.c21genera.identity.infrastructure.UserRepository;
import com.c21genera.shared.domain.NotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public User create(String name, String email, String rawPassword, RoleCode roleCode) {
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new DuplicateEmailException(email);
    }
    Role role = findRole(roleCode);
    User user = new User(name, email, passwordEncoder.encode(rawPassword), role);
    return userRepository.save(user);
  }

  public User rename(UUID userId, String name, String email, RoleCode roleCode) {
    User user = get(userId);
    if (!user.getEmail().equalsIgnoreCase(email) && userRepository.existsByEmailIgnoreCase(email)) {
      throw new DuplicateEmailException(email);
    }
    user.rename(name);
    user.changeEmail(email);
    user.changeRole(findRole(roleCode));
    return user;
  }

  public User activate(UUID userId) {
    User user = get(userId);
    user.activate();
    return user;
  }

  public User deactivate(UUID userId) {
    User user = get(userId);
    user.deactivate();
    return user;
  }

  @Transactional(readOnly = true)
  public User get(UUID userId) {
    User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Usuario", userId));
    // role es LAZY y open-in-view está desactivado: los controllers mapean a
    // UserResponse (que lee user.getRole()) después de que esta transacción
    // ya cerró, así que hay que inicializarlo aquí o revienta con
    // LazyInitializationException.
    org.hibernate.Hibernate.initialize(user.getRole());
    return user;
  }

  @Transactional(readOnly = true)
  public Page<User> list(Pageable pageable) {
    return userRepository.findAll(pageable);
  }

  private Role findRole(RoleCode code) {
    return roleRepository.findByCode(code).orElseThrow(() -> new NotFoundException("Rol", code));
  }
}
