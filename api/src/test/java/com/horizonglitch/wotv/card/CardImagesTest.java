package com.horizonglitch.wotv.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The card images of static/cards: one folder per faction, three widths per card, public without a token. The
 * URL of a width is the card's {@code imageUrl} plus {@code -320w.webp}, {@code -640w.webp} or {@code -848w.webp}.
 */
@SpringBootTest
class CardImagesTest {

	private static final Path CARDS = Path.of("src", "main", "resources", "static", "cards");
	private static final List<String> FACTIONS = List.of("lava", "electro", "radio", "ice", "metal", "matte", "gem",
			"noble-gas");
	private static final List<String> WIDTHS = List.of("320w", "640w", "848w");

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void everyFactionHasAFolderAndEveryCardHasTheThreeWidths() throws IOException {
		try (Stream<Path> folders = Files.list(CARDS)) {
			assertThat(folders.map(p -> p.getFileName().toString())).containsExactlyInAnyOrderElementsOf(FACTIONS);
		}
		try (Stream<Path> files = Files.walk(CARDS)) {
			List<String> names = files.filter(Files::isRegularFile).map(p -> CARDS.relativize(p).toString()).toList();
			assertThat(names).isNotEmpty().allMatch(n -> n.matches("[a-z-]+/[a-z0-9-]+-(320|640|848)w\\.webp"));
			Map<String, Long> widthsPerCard = names.stream()
					.collect(Collectors.groupingBy(n -> n.replaceAll("-\\d+w\\.webp$", ""), Collectors.counting()));
			assertThat(widthsPerCard).allSatisfy((card, widths) -> assertThat(widths).as(card).isEqualTo(3L));
		}
	}

	@Test
	void theImagesArePublicWebpFiles() throws Exception {
		for (String width : WIDTHS) {
			byte[] image = mvc.perform(get("/cards/lava/basaltaur-" + width + ".webp"))
					.andExpect(status().isOk())
					.andExpect(content().contentType("image/webp"))
					.andReturn().getResponse().getContentAsByteArray();
			assertThat(new String(image, 0, 4)).isEqualTo("RIFF");
			assertThat(new String(image, 8, 4)).isEqualTo("WEBP");
		}
		mvc.perform(get("/cards/noble-gas/cetargos-848w.webp")).andExpect(status().isOk());
	}

	@Test
	void aMissingImageIsNotFound() throws Exception {
		mvc.perform(get("/cards/lava/nobody-320w.webp")).andExpect(status().isNotFound());
	}

	@Test
	void everyFileIsServed() throws Exception {
		List<Path> files;
		try (Stream<Path> walk = Files.walk(CARDS)) {
			files = walk.filter(Files::isRegularFile).toList();
		}
		Map<String, Long> sizes = files.stream()
				.collect(Collectors.toMap(p -> "/cards/" + CARDS.relativize(p), p -> p.toFile().length()));
		for (Map.Entry<String, Long> file : sizes.entrySet()) {
			int length = mvc.perform(get(file.getKey())).andExpect(status().isOk())
					.andReturn().getResponse().getContentAsByteArray().length;
			assertThat((long) length).as(file.getKey()).isEqualTo(file.getValue());
		}
	}
}
