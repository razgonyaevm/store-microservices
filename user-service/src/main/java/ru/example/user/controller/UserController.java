package ru.example.user.controller;

import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.example.user.dto.AuthRequest;
import ru.example.user.dto.AuthResponse;
import ru.example.user.dto.RegisterRequest;
import ru.example.user.dto.UserResponse;
import ru.example.user.service.UserService;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public String register(@RequestBody RegisterRequest registerRequest) {
    return userService.register(registerRequest);
  }

  @PostMapping("/login")
  @ResponseStatus(HttpStatus.OK)
  public AuthResponse login(@RequestBody AuthRequest authRequest) {
    String token = userService.login(authRequest);
    return new AuthResponse(token);
  }

  @GetMapping("/balance")
  @ResponseStatus(HttpStatus.OK)
  public BigDecimal getBalance(@RequestHeader("X-User-Name") String username) {
    return userService.getBalance(username);
  }

  @PostMapping("/recharge")
  @ResponseStatus(HttpStatus.OK)
  public BigDecimal rechargeBalance(
      @RequestHeader("X-User-Name") String username, @RequestParam BigDecimal amount) {
    return userService.rechargeBalance(username, amount);
  }

  @PutMapping("/deduct")
  @ResponseStatus(HttpStatus.OK)
  public void deductBalance(@RequestParam String username, @RequestParam BigDecimal amount) {
    userService.deductBalance(username, amount);
  }

  @GetMapping("/all")
  @ResponseStatus(HttpStatus.OK)
  public List<UserResponse> getAllUsers() {
    return userService.getAllUsers();
  }

  @PutMapping("/{id}/role")
  @ResponseStatus(HttpStatus.OK)
  public void changeRole(@PathVariable Long id, @RequestParam String role) {
    userService.changeRole(id, role);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  public void deleteUser(@PathVariable Long id) {
    userService.deleteUser(id);
  }

  @GetMapping("/me")
  @ResponseStatus(HttpStatus.OK)
  public UserResponse getCurrentUser(@RequestHeader("X-User-Name") String username) {
    return userService.getCurrentUser(username);
  }
}
