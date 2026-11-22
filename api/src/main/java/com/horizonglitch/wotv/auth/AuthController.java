package com.horizonglitch.wotv.auth;

import com.horizonglitch.wotv.security.CurrentPlayer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Routes of api-contract-draft/01-auth-profile.md, section 1. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@Operation(summary = "Register a Summoner")
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponses.Registered register(@Valid @RequestBody AuthRequests.Register request) {
		return authService.register(request);
	}

	@Operation(summary = "Log in with username or email; grants the daily and comeback bonuses")
	@PostMapping("/login")
	public AuthResponses.Login login(@Valid @RequestBody AuthRequests.Login request) {
		return authService.login(request);
	}

	@Operation(summary = "Claim due daily and comeback bonuses", security = @SecurityRequirement(name = "bearer"))
	@PostMapping("/daily-bonus")
	public AuthResponses.DailyBonuses claimDailyBonuses(@AuthenticationPrincipal Jwt jwt) {
		return authService.claimDailyBonuses(CurrentPlayer.id(jwt));
	}

	@Operation(summary = "Exchange a refresh token for a new token pair")
	@PostMapping("/refresh")
	public AuthResponses.Tokens refresh(@Valid @RequestBody AuthRequests.RefreshToken request) {
		return authService.refresh(request.refreshToken());
	}

	@Operation(summary = "Invalidate a refresh token", security = @SecurityRequirement(name = "bearer"))
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthRequests.RefreshToken request) {
		authService.logout(CurrentPlayer.id(jwt), request.refreshToken());
	}

	@Operation(summary = "Verify the email with the token sent by email")
	@PostMapping("/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void verifyEmail(@Valid @RequestBody AuthRequests.ActionToken request) {
		authService.verifyEmail(request.token());
	}

	@Operation(summary = "Send the verification email again", security = @SecurityRequirement(name = "bearer"))
	@PostMapping("/resend-verification")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resendVerification(@AuthenticationPrincipal Jwt jwt) {
		authService.resendVerification(CurrentPlayer.id(jwt));
	}

	@Operation(summary = "Send a password reset email; same response whether the email exists or not")
	@PostMapping("/forgot-password")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void forgotPassword(@Valid @RequestBody AuthRequests.ForgotPassword request) {
		authService.forgotPassword(request.email());
	}

	@Operation(summary = "Set a new password with the token sent by email")
	@PostMapping("/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetPassword(@Valid @RequestBody AuthRequests.ResetPassword request) {
		authService.resetPassword(request.token(), request.newPassword());
	}
}
