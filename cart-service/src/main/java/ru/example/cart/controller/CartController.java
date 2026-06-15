package ru.example.cart.controller;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.example.cart.dto.Cart;
import ru.example.cart.service.CartService;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

  private final CartService cartService;

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public Cart getCart(@RequestHeader("X-User-Name") String username) {
    return cartService.getCart(username);
  }

  @PostMapping("/add")
  @ResponseStatus(HttpStatus.OK)
  public Cart addToCart(
      @RequestHeader("X-User-Name") String username,
      @RequestParam String skuCode,
      @RequestParam BigDecimal price) {
    return cartService.addToCart(username, skuCode, price);
  }

  @PostMapping("/clear")
  @ResponseStatus(HttpStatus.OK)
  public void clearCart(@RequestHeader("X-User-Name") String username) {
    cartService.clearCart(username);
  }

  @PostMapping("/checkout")
  @ResponseStatus(HttpStatus.OK)
  public String checkout(
      @RequestHeader("X-User-Name") String username, @RequestHeader("Authorization") String token) {
    return cartService.checkout(username, token);
  }

  @PostMapping("/remove")
  @ResponseStatus(HttpStatus.OK)
  public Cart removeOneFromCart(
      @RequestHeader("X-User-Name") String username, @RequestParam String skuCode) {
    return cartService.removeOneFromCart(username, skuCode);
  }

  @PostMapping("/clear-admin")
  @ResponseStatus(HttpStatus.OK)
  public void clearCartAdmin(@RequestParam String username) {
    cartService.clearCart(username);
  }
}
