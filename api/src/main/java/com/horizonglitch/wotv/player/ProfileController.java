package com.horizonglitch.wotv.player;

import com.horizonglitch.wotv.security.CurrentPlayer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Routes of api-contract-draft/01-auth-profile.md, section 2. */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Profile")
public class ProfileController {

	private final ProfileService profileService;

	public ProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@Operation(summary = "Private profile of the logged-in player", security = @SecurityRequirement(name = "bearer"))
	@GetMapping("/me")
	public ProfileResponses.Me me(@AuthenticationPrincipal Jwt jwt) {
		return profileService.me(CurrentPlayer.id(jwt));
	}

	@Operation(summary = "Change avatar or time zone", security = @SecurityRequirement(name = "bearer"))
	@PatchMapping("/me")
	public ProfileResponses.Me update(@AuthenticationPrincipal Jwt jwt,
			@RequestBody ProfileRequests.UpdateProfile request) {
		return profileService.update(CurrentPlayer.id(jwt), request);
	}

	@Operation(summary = "Accessibility settings", security = @SecurityRequirement(name = "bearer"))
	@GetMapping("/me/settings")
	public ProfileResponses.Settings settings(@AuthenticationPrincipal Jwt jwt) {
		return profileService.settings(CurrentPlayer.id(jwt));
	}

	@Operation(summary = "Change accessibility settings", security = @SecurityRequirement(name = "bearer"))
	@PatchMapping("/me/settings")
	public ProfileResponses.Settings updateSettings(@AuthenticationPrincipal Jwt jwt,
			@RequestBody ProfileRequests.UpdateSettings request) {
		return profileService.updateSettings(CurrentPlayer.id(jwt), request);
	}

	@Operation(summary = "Change password", security = @SecurityRequirement(name = "bearer"))
	@PutMapping("/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ProfileRequests.ChangePassword request) {
		profileService.changePassword(CurrentPlayer.id(jwt), request);
	}

	@Operation(summary = "Delete the account (soft delete)", security = @SecurityRequirement(name = "bearer"))
	@DeleteMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequests.DeleteAccount request) {
		profileService.delete(CurrentPlayer.id(jwt), request);
	}

	@Operation(summary = "Public profile of a player")
	@GetMapping("/{username}")
	public ProfileResponses.Public publicProfile(@PathVariable String username) {
		return profileService.publicProfile(username);
	}
}
