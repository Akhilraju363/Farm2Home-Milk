package com.farm2home.order.controller;

import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.order.config.UserPrincipal;
import com.farm2home.order.dto.request.AddCartItemRequest;
import com.farm2home.order.dto.request.UpdateCartItemRequest;
import com.farm2home.order.dto.response.CartResponse;
import com.farm2home.order.service.impl.CartServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Always self-scoped from the JWT (principal.userId()) - there is no admin "manage another
 * customer's cart" path, unlike Order/Address, since a cart only ever makes sense as the acting
 * customer's own staging area before checkout.
 */
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart", description = "Customer shopping cart - staging area before checkout.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CartController {

    private final CartServiceImpl cartService;

    @GetMapping
    @Operation(summary = "Get the caller's cart", description = "Creates an empty cart on first access. " +
            "Item name/image/price/unit are resolved live from inventory-service, not stored.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cart retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<CartResponse>> getCart(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Cart retrieved successfully", cartService.getCart(principal.userId())));
    }

    @PostMapping("/items")
    @Operation(summary = "Add a product to the cart",
            description = "If the product is already in the cart, the quantity is added to the existing line " +
                    "rather than creating a duplicate. Product must be active/available; quantity must not " +
                    "exceed current stock.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Item added, full cart returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing productId/quantity", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Product inactive/unavailable, or quantity invalid/exceeds stock", content = @Content)
    })
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @Valid @RequestBody AddCartItemRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", cartService.addItem(principal.userId(), request)));
    }

    @PutMapping("/items/{id}")
    @Operation(summary = "Set a cart item's quantity", description = "Absolute value, not a delta. Stock is revalidated.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Quantity updated, full cart returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing quantity", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No such item in the caller's own cart", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Product inactive/unavailable, or quantity invalid/exceeds stock", content = @Content)
    })
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(@PathVariable UUID id,
            @Valid @RequestBody UpdateCartItemRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Cart item updated", cartService.updateItem(principal.userId(), id, request)));
    }

    @DeleteMapping("/items/{id}")
    @Operation(summary = "Remove one item from the cart")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Item removed, full cart returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No such item in the caller's own cart", content = @Content)
    })
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", cartService.removeItem(principal.userId(), id)));
    }

    @DeleteMapping
    @Operation(summary = "Clear the cart", description = "Removes every item. Used after a successful checkout, " +
            "and available directly to the customer too.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cart cleared"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> clearCart(@AuthenticationPrincipal UserPrincipal principal) {
        cartService.clearCart(principal.userId());
        return ResponseEntity.ok(ApiResponse.success("Cart cleared successfully", null));
    }
}
