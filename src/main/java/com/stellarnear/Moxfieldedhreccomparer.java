package com.stellarnear;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jasypt.util.text.BasicTextEncryptor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.stellarnear.CustomLog.Level;
import com.stellarnear.CustomLog.LogMsg;

/**
 * Hello world!
 */
public final class Moxfieldedhreccomparer {

	private static CustomLog log = new CustomLog(Moxfieldedhreccomparer.class);
	private static String edhRecToken;

	private static boolean doHistory = true;
	private static Integer daysHistoryAdded = 90;
	private static Integer daysHistoryCutted = 600;

	private static Integer percentRetainMissingCard = 60;

	private static String encryptedAgent = "lHAefqq+VW+GqIOzGqBz0oynoeez7psrip6XXYu1CeCnGIUznEHN5cCHFg2LR+X1";
	private static String customPassword;
	private static String decryptedAgent;
	private static List<String> gameChangers;

	private Moxfieldedhreccomparer() {
	}

	/**
	 * Says hello to the world.
	 *
	 * @param args The arguments of the program.
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		Scanner scanner = new Scanner(System.in);

		System.out.print("Enter the custom password to decrypt the agent moxfield : ");
		customPassword = scanner.nextLine();
		scanner.close();
		if (customPassword.isEmpty()) {
			log.err("You must provide the custom password");
		}

		try {
			BasicTextEncryptor textEncryptor = new BasicTextEncryptor();
			textEncryptor.setPassword(customPassword);
			decryptedAgent = textEncryptor.decrypt(encryptedAgent);

		} catch (Exception e) {
			log.err("Invalid custom password or encrypted value !");
			throw new Exception("Invalid custom password or encrypted value !");
		}

		long startTotal = System.currentTimeMillis();
		edhRecToken = getTokenFromEdhRec();
		if (edhRecToken == null) {
			log.err("Could not parse token !");
			return;
		}

		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		UsersData usersData = mapper.readValue(new File("./users.yml"), UsersData.class);

		/* Setting part */

		// if single from full list
		boolean singleDeckFromList = false;
		String deckName = "Y'Shtola";

		boolean singleUser = true;
		String singleUserName = "stellarnear";

		// manual
		// boolean manualSetting = false;
		// deckName = "Frodo";
		// List<String> edhRecPages = new ArrayList<>();
		// edhRecPages.add(
		// "https://edhrec.com/_next/data/GHPzhXhQMBhgMPrgEUOVI/commanders/frodo-adventurous-hobbit-sam-loyal-attendant.json?slug=frodo-adventurous-hobbit-sam-loyal-attendant");
		// edhRecPages.add(
		// "https://edhrec.com/_next/data/GHPzhXhQMBhgMPrgEUOVI/commanders/frodo-adventurous-hobbit-sam-loyal-attendant/lifegain.json?slug=frodo-adventurous-hobbit-sam-loyal-attendant&themeName=lifegain");
		// treatDeck("Custom", deckName, publicMoxfieldId, edhRecPages);
		/* enf of settign if all false we do all */

		gameChangers = mapper.readValue(new File("./game_changer.yml"),
				mapper.getTypeFactory().constructCollectionType(List.class, String.class));

		if (singleDeckFromList) {
			for (User user : usersData.getUsers()) {
				List<Deck> userDecks = buildDeckListForUser(user);
				for (Deck deck : userDecks) {
					if (deck.getName().equalsIgnoreCase(deckName)) {
						treatDeck(user.getName(), deck);
					}
				}
			}
		} else {
			for (User user : usersData.getUsers()) {
				if (singleUser) {
					if (!user.getName().equalsIgnoreCase(singleUserName)) {
						log.info("Skipping unwanted user : " + user.getName());
						continue;
					}
				}
				List<Deck> userDecks = buildDeckListForUser(user);

				log.info("Treating user : " + user.getName() + " having " + userDecks.size() + " decks.");

				if (!singleDeckFromList) {
					HashMap<String, List<CardChange>> mapNnewCardDeckName = new HashMap<>();
					HashMap<String, List<Deck>> mapCardsRemovedFromDecks = new HashMap<>();

					for (Deck deck : userDecks) {
						treatDeck(user.getName(), deck);

						if (doHistory) {
							List<CardChange> listAddition = getCardChangeHistory(deck.getMoxfieldId());
							Instant addedFromHistoryTime = Instant.now().minus(Duration.ofDays(daysHistoryAdded));
							Instant cuttedFromHistoryTime = Instant.now().minus(Duration.ofDays(daysHistoryCutted));

							List<CardChange> nNewCardFromHistory = listAddition.stream()
									.filter(cardAddition -> cardAddition.getUpdatedAtUtc().isAfter(addedFromHistoryTime)
											&& cardAddition.getQuantityDelta() > 0)
									.collect(Collectors.toList());

							List<CardChange> cuttedCardFromHistory = listAddition.stream()
									.filter(cardAddition -> cardAddition.getUpdatedAtUtc()
											.isAfter(cuttedFromHistoryTime)
											&& cardAddition.getQuantityDelta() < 0)
									.collect(Collectors.toList());

							mapNnewCardDeckName.put(deck.getName(), nNewCardFromHistory);

							for (CardChange cardChange : cuttedCardFromHistory) {
								mapCardsRemovedFromDecks.putIfAbsent(cardChange.getName(), new ArrayList<Deck>());
								mapCardsRemovedFromDecks.get(cardChange.getName()).add(deck);
							}

						}

					}
					if (mapNnewCardDeckName.size() > 0) {
						csvNewCardsHistoryOutputFile(user.getName(), mapNnewCardDeckName);
					}
					if (mapCardsRemovedFromDecks.size() > 0) {
						csvCuttedCardsHistoryOutputFile(user.getName(), mapCardsRemovedFromDecks);
					}
				}
			}
		}

		log.info("Summary of warnings and errors :");
		for (LogMsg logEntry : log.getAllLogs()) {
			if (logEntry.getLevel().equals(Level.WARN) || logEntry.getLevel().equals(Level.ERROR)) {
				log.display(logEntry);
			}
		}

		long endTotal = System.currentTimeMillis();
		log.info("MoxfieldEdhComparer ended it took a total time of " + convertTime(endTotal - startTotal));

	}

	private static String getTokenFromEdhRec() {
		log.info("Getting token from EDHrec");
		HttpURLConnection connection = null;
		String edhUrl = "https://edhrec.com/";

		try {
			// Create a connection
			URL url = new URL(edhUrl);
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setRequestProperty("Accept", "text/html");

			// Read response
			StringBuilder responseBuilder = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					responseBuilder.append(line);
				}
			}

			// Extract HTML content as string
			String htmlContent = responseBuilder.toString();

			// Extract the JSON string from the <script> tag with id="__NEXT_DATA__"
			String json = extractJsonData(htmlContent);
			if (json != null) {
				log.info("Extracted JSON!");
				log.info("Searching token...");
				ObjectMapper objectMapper = new ObjectMapper();
				JsonNode rootNode = objectMapper.readTree(json);

				// Extract the buildId value
				String buildId = rootNode.get("buildId").asText();
				log.info("Token found : " + buildId);
				return buildId;
			} else {
				log.info("No JSON data found!");
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
		return null;
	}

	private static String extractJsonData(String htmlContent) {
		// Use regex to find the <script> tag with id="__NEXT_DATA__"
		Pattern pattern = Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>",
				Pattern.DOTALL);
		Matcher matcher = pattern.matcher(htmlContent);

		if (matcher.find()) {
			// Return the JSON string
			return matcher.group(1);
		}
		return null;
	}

	private static void treatDeck(String user, Deck deck)
			throws IOException, InterruptedException {

		String name = deck.getName();
		String publicMoxfieldId = deck.getMoxfieldId();
		List<String> edhRecPages = deck.getEdhreclinks();

		log.info("Treating deck " + name);
		long startDeck = System.currentTimeMillis();
		List<Card> allDeckCards = getDeckListFor(publicMoxfieldId);

		int nData = 0;
		if (edhRecPages != null && edhRecPages.size() > 0) {
			Map<String, Card> nameCardStat = new HashMap<>();
			for (String edhUrl : edhRecPages) {
				addCardFromEdhrec(nameCardStat, edhUrl);
			}

			allDeckCards.sort(Comparator.comparing(Card::getName));

			List<Card> crea = new ArrayList<>();
			List<Card> sorcery = new ArrayList<>();
			List<Card> instant = new ArrayList<>();
			List<Card> artifact = new ArrayList<>();
			List<Card> enchantment = new ArrayList<>();
			List<Card> land = new ArrayList<>();
			List<Card> misc = new ArrayList<>();
			List<Card> missing = new ArrayList<>();

			Set<String> allDeckCardName = new HashSet<>();
			for (Card card : allDeckCards) {
				allDeckCardName.add(card.getName());
				if (nameCardStat.containsKey(card.getName())) {
					card.setnDeck(nameCardStat.get(card.getName()).getnDeck());
					card.setPercentPresentDeck(nameCardStat.get(card.getName()).getPercentPresentDeck());
					card.setSynergyPercent(nameCardStat.get(card.getName()).getSynergyPercent());
					nData++;
				} else {
					card.setnDeck(-1);
					card.setPercentPresentDeck(-1);
					card.setSynergyPercent(-1);
				}

				if (card.getType_line().toLowerCase().contains("creat")
						&& !card.getType_line().toLowerCase().contains("enchant")) {
					crea.add(card);
				} else if (card.getType_line().toLowerCase().contains("sorcer")) {
					sorcery.add(card);
				} else if (card.getType_line().toLowerCase().contains("instant")) {
					instant.add(card);
				} else if (card.getType_line().toLowerCase().contains("artifa")) {
					artifact.add(card);
				} else if (card.getType_line().toLowerCase().contains("enchant")) {
					enchantment.add(card);
				} else if (card.getType_line().toLowerCase().contains("land")) {
					land.add(card);
				} else {
					misc.add(card);
				}
			}

			for (Entry<String, Card> entry : nameCardStat.entrySet()) {
				if (!allDeckCardName.contains(entry.getKey())
						&& (entry.getValue().getSynergyPercent() >= percentRetainMissingCard
								|| entry.getValue().getPercentPresentDeck() >= percentRetainMissingCard)) {
					missing.add(entry.getValue());
				}
			}

			csvOutput(user, name, crea, "Creatures");

			csvOutput(user, name, sorcery, "Sorceries");

			csvOutput(user, name, instant, "Instants");

			csvOutput(user, name, artifact, "Artifacts");

			csvOutput(user, name, land, "Lands");

			csvOutput(user, name, enchantment, "Enchantments");

			csvOutput(user, name, misc, "Miscs");

			csvOutput(user, name, allDeckCards, "AllCards");

			List<Card> allDeckCardsExceptLands = allDeckCards.stream()
					.filter(card -> !card.getType_line().toLowerCase().contains("land")).collect(Collectors.toList());

			csvOutput(user, name, allDeckCardsExceptLands, "AllCardsButLands");

			if (missing.size() > 0) {
				csvOutput(user, name, missing, "Missings");
				for (Card card : missing) {
					log.warn("User " + user + ", Deck [" + name + "] don't have [" + card.getName()
							+ "] which is played on more than " + percentRetainMissingCard + " % of the EDHRec decks");
				}
			}
		}
		long endDeck = System.currentTimeMillis();

		log.info("Deck [" + name + "] ended it took a total time of " + convertTime(endDeck - startDeck));
		if (edhRecPages != null && edhRecPages.size() > 1) {
			log.info("Deck [" + name + "] We had " + allDeckCards.size() + " cards and found data on " + nData
					+ " cards ("
					+ ((int) (100.0 * ((1.0 * nData) / (1.0 * allDeckCards.size())))) + " %).");
		}
	}

	private static void addCardFromEdhrec(Map<String, Card> mapNameCard, String oldEdhUrl) throws InterruptedException {
		HttpURLConnection connection = null;
		try {

			String regex = "(?<=_next/data/)(.*?)(?=/commanders)";
			String edhUrl = oldEdhUrl.replaceAll(regex, edhRecToken);
			URL url = new URL(edhUrl);
			connection = (HttpURLConnection) url.openConnection();
			// Set connection properties as needed
			connection.setRequestMethod("GET");
			connection.setRequestProperty("Accept", "application/json");

			// Read response
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
				StringBuilder responseBuilder = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					responseBuilder.append(line);
				}
				String jsonResponse = responseBuilder.toString();

				// Parse the JSON response
				ObjectMapper objectMapper = new ObjectMapper();
				JsonNode rootNode = objectMapper.readTree(jsonResponse);

				// Navigate to the "cardviews" arrays
				JsonNode cardlistsNode = rootNode.path("pageProps").path("data").path("container").path("json_dict")
						.path("cardlists");

				if (cardlistsNode.isArray()) {
					for (JsonNode cardlistNode : cardlistsNode) {
						JsonNode cardviewsNode = cardlistNode.path("cardviews");
						if (cardviewsNode.isArray()) {
							for (JsonNode cardNode : cardviewsNode) {
								String name = cleanString(cardNode.path("name").asText());
								if (name.contains("//")) {
									name = name.split("//")[0].trim();
								}
								double synergy = cardNode.path("synergy").asDouble();

								int numDecks = cardNode.path("num_decks").asInt();
								int potentialDecks = cardNode.path("potential_decks").asInt();
								int percent = (int) (100.0 * ((1.0 * numDecks) / (1.0 * potentialDecks)));
								int synergPercent = (int) (1.0 * synergy * 100.0);
								String id = cardNode.path("id").asText();

								Card card = new Card(name, numDecks, percent, synergPercent, id);

								if (mapNameCard.containsKey(name)) {
									Card stored = mapNameCard.get(name);
									int numDecksStored = stored.getnDeck();
									if (numDecks > numDecksStored) {
										stored.setnDeck(numDecks);
									}

									int percentStored = stored.getPercentPresentDeck();
									if (percent > percentStored) {
										stored.setPercentPresentDeck(percent);
									}

									int synergPercentStored = stored.getSynergyPercent();
									if (synergPercent > synergPercentStored) {
										stored.setSynergyPercent(synergPercent);
									}
								} else {
									mapNameCard.put(name, card);
								}

							}
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
			// Thread.sleep(1000); not needed for EDHrec
		}
	}

	private static List<Card> getDeckListFor(String publicId) throws MalformedURLException, InterruptedException {
		// ex https://api.moxfield.com/v2/decks/all/HxV33izihky7KTwjU0ER9w
		String deckUrl = "https://api.moxfield.com/v2/decks/all/" + publicId;
		URL url = new URL(deckUrl);
		HttpURLConnection connection;

		try {
			connection = (HttpURLConnection) url.openConnection();
			setConenctionMoxfield(connection);

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
				StringBuilder responseBuilder = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					responseBuilder.append(line);
				}
				String jsonResponse = responseBuilder.toString();

				// Parse the JSON response
				ObjectMapper objectMapper = new ObjectMapper();
				JsonNode rootNode = objectMapper.readTree(jsonResponse);

				// Assuming "mainboard" is a direct child of the root node and contains the
				// cards
				JsonNode mainboardNode = rootNode.path("mainboard");
				Integer commandersCount = rootNode.path("commandersCount").asInt();
				String nameOfDeck = rootNode.path("name").asText();
				String user = rootNode.path("createdByUser").path("userName").asText();
				if (rootNode.path("mainboardCount").asInt() + commandersCount != 100) {
					log.err("User " + user + ", Deck [" + nameOfDeck + "] has " + commandersCount
							+ " commander(s) and the mainboard has " + rootNode.path("mainboardCount").asInt()
							+ " cards !! THAT IS NOT LEGAL");
				}

				Iterator<Map.Entry<String, JsonNode>> fields = mainboardNode.fields();

				List<Card> deckCards = new ArrayList<>();
				while (fields.hasNext()) {
					Map.Entry<String, JsonNode> entry = fields.next();

					JsonNode cardNode = entry.getValue().path("card");

					String name = cleanString(cardNode.path("name").asText());
					if (name.contains("//")) {
						name = name.split("//")[0].trim();
					}

					String rarity = cardNode.path("rarity").asText();
					String mana_cost = cardNode.path("mana_cost").asText();
					int cmc = cardNode.path("cmc").asInt();
					String type_line = cardNode.path("type_line").asText();

					String oracle_text = cardNode.path("oracle_text").asText();
					List<String> color_identity = objectMapper.convertValue(cardNode.path("color_identity"),
							new TypeReference<List<String>>() {
							});

					String commanderLegality = cardNode.path("legalities").path("commander").asText();
					String scryfall_id = cardNode.path("scryfall_id").asText();
					Card card = new Card(name, rarity, mana_cost, cmc, type_line, color_identity, commanderLegality,
							oracle_text, scryfall_id);
					deckCards.add(card);

					if (!commanderLegality.equalsIgnoreCase("legal")) {
						log.err("User " + user + ",  Deck  " + nameOfDeck + " has the card [" + name
								+ "] which is not legal in commander ("
								+ commanderLegality + ")");
					}
					if (thisCardIsGameChanger(card)) {

						log.warn("User " + user + ", Deck [" + nameOfDeck + "] has the card [" + name
								+ "] that is a game changer");
					}
				}

				return deckCards;
			} catch (Exception e1) {
				e1.printStackTrace();
				log.err("Error reading the user data", e1);
			}
		} catch (Exception e) {
			log.err("Error getting the connection to user data", e);
		} finally {
			Thread.sleep(1000);
		}
		return new ArrayList<>();
	}

	private static boolean thisCardIsGameChanger(Card card) {
		for (String cardGC : gameChangers) {
			if (card.getName().trim().equalsIgnoreCase(cardGC)) {
				return true;
			}
		}
		return false;
	}

	private static void setConenctionMoxfield(HttpURLConnection connection) throws Exception {
		connection.setRequestProperty("accept",
				"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");

		// connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br, zstd");
		// connection.setRequestProperty("Accept-Language",
		// "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7");
		connection.setRequestProperty("user-agent", decryptedAgent);

	}

	public static String cleanString(String inputRaw) {

		String input;
		if (needsEncodingFix(inputRaw)) {
			byte[] bytes = inputRaw.getBytes(StandardCharsets.ISO_8859_1);
			input = new String(bytes, StandardCharsets.UTF_8);
		} else {
			input = inputRaw;
		}

		return input;

		/*
		 * // Convert to lowercase
		 * String lowerCaseString = input.toLowerCase();
		 *
		 * // Normalize the string to decompose accents
		 * String normalizedString = Normalizer.normalize(lowerCaseString,
		 * Normalizer.Form.NFD);
		 *
		 * // Remove accents
		 * // String accentRemovedString =
		 * normalizedString.replaceAll("\p{InCombiningDiacriticalMarks}+", "");
		 *
		 * String accentRemovedString = normalizedString.replaceAll("\p{M}", "");
		 *
		 * // Remove non-alphabetic characters except spaces
		 * String cleanedString = accentRemovedString.replaceAll("[^a-zA-Z\s]", "");
		 *
		 * return cleanedString;
		 */

	}

	public static boolean needsEncodingFix(String input) {
		// Check for common signs of encoding issues:
		// 1. Presence of sequences that are unlikely to be valid UTF-8
		// 2. Characters outside the typical printable ASCII and Latin-1 range

		for (char c : input.toCharArray()) {
			// Detect non-ASCII, non-printable characters, which could indicate encoding
			// issues
			if ((c < 32 || c > 126) && (c < 160 || c > 255)) {
				return true;
			}
		}

		return false;
	}

	private static void csvOutput(String user, String nameDeck, List<Card> allCards, String nameFile)
			throws IOException {
		csvOutputFile(user, nameDeck, allCards, nameFile + "_alphabetical");
		allCards.sort(Comparator.comparing(Card::getPercentPresentDeck));
		csvOutputFile(user, nameDeck, allCards, nameFile + "_usage_percent");
	}

	private static void csvOutputFile(String user, String nameDeck, List<Card> allCards, String nameFile)
			throws IOException {

		File userDir = new File("OUT/" + user);
		if (!userDir.exists()) {
			userDir.mkdir();
		}
		File deckDir = new File(userDir.getAbsolutePath() + "/" + nameDeck);
		if (!deckDir.exists()) {
			deckDir.mkdir();
		}

		File csvFile = new File(deckDir, nameFile + ".csv");
		if (csvFile.exists()) {
			csvFile.delete();
		}

		// === CSV EXPORT ===
		try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
				new FileOutputStream(csvFile), StandardCharsets.UTF_8))) {

			// CSV Header
			out.println("CardName;PercentPresence;Ndeck;Synergy");

			// Write each card's data
			for (Card card : allCards) {
				out.printf("\"%s\";%d;%d;%d%n",
						card.getName(),
						card.getPercentPresentDeck(),
						card.getnDeck(),
						card.getSynergyPercent());
			}
		}

		// === HTML EXPORT ===
		File htmlFile = new File(deckDir, nameFile + ".html");
		if (htmlFile.exists()) {
			htmlFile.delete();
		}

		try (PrintWriter html = new PrintWriter(new OutputStreamWriter(
				new FileOutputStream(htmlFile), StandardCharsets.UTF_8))) {

			html.println("<!DOCTYPE html>");
			html.println("<html lang=\"en\">");
			html.println("<head>");
			html.println("<meta charset=\"UTF-8\">");
			html.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
			html.println("<title>" + nameDeck + " - " + nameFile + "</title>");
			html.println("<style>");
			html.println("body { font-family: Arial, sans-serif; background: #f9f9f9; color: #333; padding: 20px; }");
			html.println(
					"table { border-collapse: collapse; width: 100%; background: white; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }");
			html.println("th, td { border: 1px solid #ddd; padding: 8px; text-align: center; }");
			html.println("th { background-color: #4CAF50; color: white; }");
			html.println("tr:nth-child(even) { background-color: #f2f2f2; }");
			html.println(".card-name { position: relative; cursor: pointer; color: #2a72d4; font-weight: bold; }");
			html.println(".card-name:hover .card-image { display: block; }");
			html.println(
					".card-image { display: none; position: absolute; top: 120%; left: 50%; transform: translateX(-50%);");
			html.println(
					"  border: 1px solid #ccc; box-shadow: 0 2px 6px rgba(0,0,0,0.3); z-index: 10; background: white; }");
			html.println(".card-image img { width: 240px; height: auto; border-radius: 4px; }");
			html.println("</style>");
			html.println("</head>");
			html.println("<body>");
			html.println("<h2>" + nameDeck + " - " + nameFile + "</h2>");
			html.println("<table>");
			html.println("<tr><th>Card Name</th><th>Percent Presence</th><th>Ndeck</th><th>Synergy</th></tr>");

			for (Card card : allCards) {
				html.println("<tr>");
				html.println("<td class='card-name'>" + escapeHtml(card.getName())
						+ "<div class='card-image'><img src='" + escapeHtml(card.getScryfallImg()) + "' alt='"
						+ escapeHtml(card.getName()) + "'></div></td>");
				html.println("<td>" + card.getPercentPresentDeck() + "%</td>");
				html.println("<td>" + card.getnDeck() + "</td>");
				html.println("<td>" + card.getSynergyPercent() + "%</td>");
				html.println("</tr>");
			}

			html.println("</table>");
			html.println("</body>");
			html.println("</html>");
		}
	}

	/**
	 * Escapes HTML special characters to prevent broken markup.
	 */
	private static String escapeHtml(String s) {
		if (s == null)
			return "";
		return s.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}

	private static void csvNewCardsHistoryOutputFile(String user, HashMap<String, List<CardChange>> mapNnewCardDeckName)
			throws IOException {
		// reordering
		Map<String, List<CardChange>> orderedMap = mapNnewCardDeckName.entrySet()
				.stream()
				.sorted((entry1, entry2) -> Integer.compare(entry2.getValue().size(), entry1.getValue().size()))
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						Map.Entry::getValue,
						(e1, e2) -> e1, // handle any duplicate keys
						LinkedHashMap::new // maintain order of insertion
				));

		File userDir = new File("OUT/" + user);
		if (!userDir.exists()) {
			userDir.mkdir();
		}

		String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("dd_MM_yy"));
		try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
				new FileOutputStream(userDir + "/" + timestamp + "_nNewCard" + daysHistoryAdded + "days.csv"),
				StandardCharsets.UTF_8))) {
			// CSV Header
			out.println("DeckName;CardCount;CardNames");

			// Write each deck's data
			for (String deckName : orderedMap.keySet()) {
				List<CardChange> cardAdditions = orderedMap.get(deckName);
				int cardCount = cardAdditions.size();

				// Join all card names with the '|' symbol
				String cardNames = cardAdditions.stream()
						.map(CardChange::getName)
						.collect(Collectors.joining(" | "));

				out.printf("\"%s\";%d;\"%s\"%n", deckName, cardCount, cardNames);
			}
		}
	}

	private static void csvCuttedCardsHistoryOutputFile(String user,
			HashMap<String, List<Deck>> mapCardsRemovedFromDecks)
			throws IOException {
		// Reordering the map: order by the number of decks in descending order
		Map<String, List<Deck>> orderedMap = mapCardsRemovedFromDecks.entrySet()
				.stream()
				.sorted((entry1, entry2) -> Integer.compare(entry2.getValue().size(), entry1.getValue().size()))
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						Map.Entry::getValue,
						(e1, e2) -> e1, // handle duplicate keys
						LinkedHashMap::new // maintain insertion order
				));

		File userDir = new File("OUT/" + user);
		if (!userDir.exists()) {
			userDir.mkdir();
		}

		String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("dd_MM_yy"));
		try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
				new FileOutputStream(userDir + "/" + timestamp + "_cuttedCardsHistory" + daysHistoryCutted + ".csv"),
				StandardCharsets.UTF_8))) {
			// CSV Header
			out.println("CardName;CardCount;DecksNames");

			// Write each card's data
			for (String cardName : orderedMap.keySet()) {
				List<Deck> decks = orderedMap.get(cardName);
				int deckCount = decks.size();

				// Join all deck names with the '|' symbol
				String deckNames = decks.stream()
						.map(Deck::getName)
						.collect(Collectors.joining(" | "));

				out.printf("\"%s\";%d;\"%s\"%n", cardName, deckCount, deckNames);
			}
		}
	}

	private static String convertTime(long l) {
		int nHour = (int) (((l / 1000) / 60) / 60);
		if (nHour > 0) {
			int nMinute = (int) ((l / 1000) / 60) - 60 * nHour;
			return nHour + " hours " + nMinute + " minutes";
		} else {
			int nMinute = (int) ((l / 1000) / 60);
			if (nMinute > 0) {
				return nMinute + " minutes";
			} else {
				return (int) (l / 1000) + " seconds";
			}
		}
	}

	private static List<Deck> buildDeckListForUser(User user) throws Exception {
		List<Deck> decks = new ArrayList<>();
		String deckUrl = "https://api2.moxfield.com/v2/users/" + user.getName().trim() + "/decks?pageSize=100";
		URL url = new URL(deckUrl);
		HttpURLConnection connection;

		try {
			connection = (HttpURLConnection) url.openConnection();
			setConenctionMoxfield(connection);

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
				StringBuilder responseBuilder = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					responseBuilder.append(line);
				}
				String jsonResponse = responseBuilder.toString();

				// Parse the JSON response
				ObjectMapper objectMapper = new ObjectMapper();
				JsonNode rootNode = objectMapper.readTree(jsonResponse);

				JsonNode decksArray = rootNode.path("data");
				for (JsonNode deckNode : decksArray) {
					String name = deckNode.path("name").asText();
					String publicId = deckNode.path("publicId").asText();

					Deck deckFromConfig = findDeckFromIdForUser(user, publicId);
					if (deckFromConfig != null) {
						decks.add(deckFromConfig);
					} else {
						decks.add(new Deck(name, publicId));
					}

				}
				return decks;
			} catch (Exception e1) {
				e1.printStackTrace();
				log.err("Error reading the user decks", e1);
			}
		} catch (Exception e) {
			log.err("Error getting the connection to user decks", e);
		} finally {
			Thread.sleep(1000);
		}
		return new ArrayList<>();
	}

	private static Deck findDeckFromIdForUser(User user, String publicId) {
		if (user.getDecks() == null || user.getDecks().size() < 1) {
			return null;
		}
		for (Deck deck : user.getDecks()) {
			if (deck.getMoxfieldId().equalsIgnoreCase(publicId)) {
				return deck;
			}
		}
		return null;
	}

	/*
	 * For the history
	 */
	public static List<CardChange> getCardChangeHistory(String publicId)
			throws MalformedURLException, InterruptedException {
		List<CardChange> allCardAdditions = new ArrayList<>();
		int currentPage = 1;
		int totalPages = 1;

		do {
			String historyUrl = "https://api.moxfield.com/v2/decks/all/" + publicId
					+ "/history?pageSize=100&pageNumber=" + currentPage;
			URL url = new URL(historyUrl);
			HttpURLConnection connection;

			try {
				connection = (HttpURLConnection) url.openConnection();
				setConenctionMoxfield(connection);

				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
					StringBuilder responseBuilder = new StringBuilder();
					String line;
					while ((line = reader.readLine()) != null) {
						responseBuilder.append(line);
					}
					String jsonResponse = responseBuilder.toString();

					// Parse the JSON response
					ObjectMapper objectMapper = new ObjectMapper();
					JsonNode rootNode = objectMapper.readTree(jsonResponse);

					// Retrieve totalPages if this is the first page
					if (currentPage == 1) {
						totalPages = rootNode.path("totalPages").asInt();
					}

					JsonNode dataNode = rootNode.path("data");

					// Parse and filter cards
					for (JsonNode entryNode : dataNode) {
						String boardType = entryNode.path("boardType").asText();
						int quantityDelta = entryNode.path("quantityDelta").asInt();
						String type = entryNode.path("card").path("type_line").asText();

						if ("mainboard".equals(boardType) && !type.toLowerCase().contains("land")) {
							JsonNode cardNode = entryNode.path("card");
							String name = cleanString(cardNode.path("name").asText());

							// Parse updatedAtUtc as an Instant
							Instant updatedAtUtc = Instant.parse(entryNode.path("updatedAtUtc").asText());

							// Create a new CardAddition object and add it to the list
							CardChange cardAddition = new CardChange(name, updatedAtUtc, quantityDelta);
							allCardAdditions.add(cardAddition);
						}
					}
					currentPage++;
				} catch (Exception e1) {
					e1.printStackTrace();
					log.err("Error reading the card additions history data on page " + currentPage, e1);
					break;
				}
			} catch (Exception e) {
				log.err("Error getting the connection to card additions history on page " + currentPage, e);
				break;
			} finally {
				Thread.sleep(1000);
			}
		} while (currentPage <= totalPages);

		return allCardAdditions;
	}

	public static List<String> readGameChangerCards() throws Exception {
		String urlString = "https://moxfield.com/commanderbrackets/gamechangers";
		URL url = new URL(urlString);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestProperty("User-Agent", "Mozilla/5.0");

		StringBuilder responseBuilder = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				responseBuilder.append(line);
			}
		}

		Document doc = Jsoup.parse(responseBuilder.toString());
		List<String> cardNames = new ArrayList<>();

		// Assuming that each card name is within an element with class 'card-name'
		Elements cardElements = doc.select(".card-name");
		for (Element cardElement : cardElements) {
			String cardName = cardElement.text().trim();
			if (!cardName.isEmpty()) {
				cardNames.add(cardName);
			}
		}

		return cardNames;
	}

}
