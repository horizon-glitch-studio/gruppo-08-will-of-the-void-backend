package com.horizonglitch.wotv.security;

import com.horizonglitch.wotv.player.PlayerActivityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/** Records activity for requests authenticated with an access token. */
public class PlayerActivityFilter extends OncePerRequestFilter {

	private final PlayerActivityService activity;

	public PlayerActivityFilter(PlayerActivityService activity) {
		this.activity = activity;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated()
				&& authentication.getPrincipal() instanceof Jwt jwt) {
			activity.record(CurrentPlayer.id(jwt));
		}
		filterChain.doFilter(request, response);
	}
}
