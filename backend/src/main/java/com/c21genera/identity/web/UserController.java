package com.c21genera.identity.web;

import com.c21genera.identity.application.UserService;
import com.c21genera.identity.domain.User;
import com.c21genera.identity.web.UserDtos.CreateUserRequest;
import com.c21genera.identity.web.UserDtos.UpdateUserRequest;
import com.c21genera.identity.web.UserDtos.UserResponse;
import com.c21genera.shared.web.PageResponse;
import com.c21genera.shared.web.Pagination;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/users")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public PageResponse<UserResponse> list(@PageableDefault(size = 20) Pageable pageable) {
    return PageResponse.of(userService.list(Pagination.cap(pageable)).map(UserResponse::from));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
    User user = userService.create(request.name(), request.email(), request.password(), request.roleCode());
    return UserResponse.from(user);
  }

  @PatchMapping("/{id}")
  public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
    User user = userService.rename(id, request.name(), request.email(), request.roleCode());
    return UserResponse.from(user);
  }

  @PostMapping("/{id}/activate")
  public UserResponse activate(@PathVariable UUID id) {
    return UserResponse.from(userService.activate(id));
  }

  @PostMapping("/{id}/deactivate")
  public UserResponse deactivate(@PathVariable UUID id) {
    return UserResponse.from(userService.deactivate(id));
  }
}
